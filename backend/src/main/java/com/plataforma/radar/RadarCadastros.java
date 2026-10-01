package com.plataforma.radar;

import static com.plataforma.radar.RadarEntrada.confirmar;
import static com.plataforma.radar.RadarEntrada.email;
import static com.plataforma.radar.RadarEntrada.erro;
import static com.plataforma.radar.RadarEntrada.id;
import static com.plataforma.radar.RadarEntrada.inteiroOpcional;
import static com.plataforma.radar.RadarEntrada.medidaOpcional;
import static com.plataforma.radar.RadarEntrada.opcional;
import static com.plataforma.radar.RadarEntrada.permitir;
import static com.plataforma.radar.RadarEntrada.texto;
import static com.plataforma.radar.RadarEntrada.valor;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.plataforma.comum.tenant.ContextoTenant;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Cadastros do Radar (V018): fornecedores, categorias e embalagens. Clientes ficam em {@link
 * RadarClientes}.
 *
 * <p>Chamado pelo {@link RadarService}, que cuida de trava por tenant, idempotência e auditoria.
 * Toda query leva {@code tenant_id} explícito; o RLS da V018 é a segunda camada.
 */
@Service
public class RadarCadastros {

    static final Set<String> OPERACOES =
            Set.of(
                    "fornecedor",
                    "fornecedor_atualizar",
                    "categoria",
                    "categoria_atualizar",
                    "embalagem",
                    "embalagem_atualizar",
                    "categoria_vinculo",
                    "embalagens_sugeridas");

    private static final Set<String> TIPOS_EMBALAGEM =
            Set.of("CAIXA", "ENVELOPE", "ENVELOPE_BOLHA", "SACO", "TUBO", "PAPELAO", "OUTRO");

    /**
     * Embalagens sugeridas, em tamanhos comuns no envio de marketplace (medidas em cm). Custo e
     * peso ficam em branco: dependem do fornecedor de cada loja, e a calculadora de preços avisa
     * enquanto o custo não for informado.
     */
    record Sugerida(String nome, String tipo, String c, String l, String a, List<String> tags) {}

    static final List<Sugerida> SUGERIDAS =
            List.of(
                    new Sugerida(
                            "Caixa de papelão P (18×13,5×9)",
                            "CAIXA",
                            "18",
                            "13.5",
                            "9",
                            List.of("caixa", "papelão", "pequena")),
                    new Sugerida(
                            "Caixa de papelão M (27×18×9)",
                            "CAIXA",
                            "27",
                            "18",
                            "9",
                            List.of("caixa", "papelão", "média")),
                    new Sugerida(
                            "Caixa de papelão G (27×22,5×13,5)",
                            "CAIXA",
                            "27",
                            "22.5",
                            "13.5",
                            List.of("caixa", "papelão", "grande")),
                    new Sugerida(
                            "Caixa de papelão GG (36×27×18)",
                            "CAIXA",
                            "36",
                            "27",
                            "18",
                            List.of("caixa", "papelão", "extra grande")),
                    new Sugerida(
                            "Envelope de segurança P (19×25)",
                            "ENVELOPE",
                            "25",
                            "19",
                            "1",
                            List.of("envelope", "plástico", "lacre", "pequeno")),
                    new Sugerida(
                            "Envelope de segurança M (26×36)",
                            "ENVELOPE",
                            "36",
                            "26",
                            "1",
                            List.of("envelope", "plástico", "lacre", "médio")),
                    new Sugerida(
                            "Envelope de segurança G (32×40)",
                            "ENVELOPE",
                            "40",
                            "32",
                            "1",
                            List.of("envelope", "plástico", "lacre", "grande")),
                    new Sugerida(
                            "Envelope com plástico bolha P (15×22)",
                            "ENVELOPE_BOLHA",
                            "22",
                            "15",
                            "2",
                            List.of("envelope", "bolha", "proteção", "pequeno")),
                    new Sugerida(
                            "Envelope com plástico bolha M (19×25)",
                            "ENVELOPE_BOLHA",
                            "25",
                            "19",
                            "2",
                            List.of("envelope", "bolha", "proteção", "médio")),
                    new Sugerida(
                            "Envelope com plástico bolha G (26×36)",
                            "ENVELOPE_BOLHA",
                            "36",
                            "26",
                            "2",
                            List.of("envelope", "bolha", "proteção", "grande")),
                    new Sugerida(
                            "Saco plástico preto P (20×30)",
                            "SACO",
                            "30",
                            "20",
                            "1",
                            List.of("saco", "plástico", "preto", "pequeno")),
                    new Sugerida(
                            "Saco plástico preto M (30×40)",
                            "SACO",
                            "40",
                            "30",
                            "1",
                            List.of("saco", "plástico", "preto", "médio")),
                    new Sugerida(
                            "Saco plástico preto G (40×50)",
                            "SACO",
                            "50",
                            "40",
                            "1",
                            List.of("saco", "plástico", "preto", "grande")),
                    new Sugerida(
                            "Envelope kraft P (18×25)",
                            "ENVELOPE",
                            "25",
                            "18",
                            "1",
                            List.of("envelope", "papel", "kraft", "pequeno")),
                    new Sugerida(
                            "Envelope kraft M (26×36)",
                            "ENVELOPE",
                            "36",
                            "26",
                            "1",
                            List.of("envelope", "papel", "kraft", "médio")),
                    new Sugerida(
                            "Tubo de papelão 60 cm (7×7×60)",
                            "TUBO",
                            "60",
                            "7",
                            "7",
                            List.of("tubo", "papelão", "longo")),
                    new Sugerida(
                            "Tubo de papelão 100 cm (10×10×100)",
                            "TUBO",
                            "100",
                            "10",
                            "10",
                            List.of("tubo", "papelão", "longo")),
                    new Sugerida(
                            "Chapa de papelão (40×60)",
                            "PAPELAO",
                            "60",
                            "40",
                            "0.5",
                            List.of("papelão", "chapa", "proteção")),
                    new Sugerida(
                            "Chapa de papelão (60×80)",
                            "PAPELAO",
                            "80",
                            "60",
                            "0.5",
                            List.of("papelão", "chapa", "proteção")));

    private static final Set<String> VEEM_FORNECEDORES =
            Set.of("DONO", "GESTOR", "ESTOQUE", "FINANCEIRO");

    private final JdbcTemplate db;
    private final ObjectMapper json;

    public RadarCadastros(JdbcTemplate db, ObjectMapper json) {
        this.db = db;
        this.json = json;
    }

    /** Listas que entram na resposta de GET /api/radar, filtradas pelo cargo. */
    Map<String, Object> dados(String papel, boolean financeiro) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put(
                "fornecedores",
                VEEM_FORNECEDORES.contains(papel) ? linhas("radar_fornecedor") : List.of());
        out.put("categorias", linhas("radar_categoria"));
        out.put(
                "categoriaCanais",
                db.queryForList(
                        "select categoria_id, canal, codigo_externo, nome_externo"
                                + " from radar_categoria_canal where tenant_id=?",
                        tenant()));
        var embalagens = linhas("radar_embalagem");
        if (!financeiro) embalagens.forEach(e -> e.remove("custo"));
        out.put("embalagens", embalagens);
        return out;
    }

    /** Executa uma operação de cadastro e devolve o id afetado e a mensagem. */
    Map<String, Object> executar(String op, JsonNode n, String papel) {
        if (op.equals("categoria_vinculo")) return vincularCategoria(n, papel);
        if (op.equals("embalagens_sugeridas")) return adicionarSugeridas(papel);
        Map<String, Object> r = new LinkedHashMap<>();
        boolean novo = !op.endsWith("_atualizar");
        UUID id = novo ? UUID.randomUUID() : id(n, "id");
        switch (op) {
            case "fornecedor", "fornecedor_atualizar" -> {
                permitir(papel, "DONO", "GESTOR", "ESTOQUE");
                salvarFornecedor(id, n, novo);
                r.put("mensagem", novo ? "Fornecedor cadastrado." : "Fornecedor atualizado.");
            }
            case "categoria", "categoria_atualizar" -> {
                permitir(papel, "DONO", "GESTOR", "MARKETING");
                salvarCategoria(id, n, novo);
                r.put("mensagem", novo ? "Categoria criada." : "Categoria atualizada.");
            }
            case "embalagem", "embalagem_atualizar" -> {
                permitir(papel, "DONO", "GESTOR", "ESTOQUE");
                salvarEmbalagem(id, n, novo);
                r.put("mensagem", novo ? "Embalagem cadastrada." : "Embalagem atualizada.");
            }
            default -> erro("Operação não reconhecida.");
        }
        r.put("id", id);
        return r;
    }

    /** Confere que o cadastro opcional existe nesta empresa; devolve null quando vazio. */
    UUID vinculoOpcional(JsonNode n, String campo, String tabela) {
        if (n.path(campo).asText("").isBlank()) return null;
        UUID id = id(n, campo);
        Integer existe =
                db.queryForObject(
                        "select count(*) from " + tabela + " where tenant_id=? and id=?",
                        Integer.class,
                        tenant(),
                        id);
        if (existe == null || existe == 0) erro("Cadastro vinculado não encontrado: " + campo);
        return id;
    }

    String nomeDoCliente(UUID clienteId) {
        return db.queryForObject(
                "select nome from radar_cliente where tenant_id=? and id=?",
                String.class,
                tenant(),
                clienteId);
    }

    private void salvarFornecedor(UUID id, JsonNode n, boolean novo) {
        Object[] valores = {
            texto(n, "nome", 200),
            opcional(n, "documento", 18),
            opcional(n, "contato", 160),
            email(n),
            opcional(n, "telefone", 40),
            inteiroOpcional(n, "prazo_entrega_dias", 0, 365),
            opcional(n, "observacao", 1000)
        };
        if (novo)
            db.update(
                    "insert into radar_fornecedor(nome,documento,contato,email,telefone,"
                            + "prazo_entrega_dias,observacao,id,tenant_id)"
                            + " values(?,?,?,?,?,?,?,?,?)",
                    concat(valores, id, tenant()));
        else
            confirmar(
                    db.update(
                            "update radar_fornecedor set nome=?,documento=?,contato=?,email=?,"
                                    + "telefone=?,prazo_entrega_dias=?,observacao=?"
                                    + " where id=? and tenant_id=?",
                            concat(valores, id, tenant())));
    }

    private void salvarCategoria(UUID id, JsonNode n, boolean novo) {
        Object[] valores = {texto(n, "nome", 120), opcional(n, "descricao", 500)};
        if (novo)
            db.update(
                    "insert into radar_categoria(nome,descricao,id,tenant_id) values(?,?,?,?)",
                    concat(valores, id, tenant()));
        else
            confirmar(
                    db.update(
                            "update radar_categoria set nome=?,descricao=?"
                                    + " where id=? and tenant_id=?",
                            concat(valores, id, tenant())));
    }

    private void salvarEmbalagem(UUID id, JsonNode n, boolean novo) {
        String tipo = n.path("tipo").asText("OUTRO").trim().toUpperCase();
        if (!TIPOS_EMBALAGEM.contains(tipo)) erro("Tipo de embalagem inválido.");
        Object[] valores = {
            texto(n, "nome", 120),
            valor(n, "custo"),
            medidaOpcional(n, "comprimento_cm"),
            medidaOpcional(n, "largura_cm"),
            medidaOpcional(n, "altura_cm"),
            inteiroOpcional(n, "peso_g", 0, 1_000_000),
            tipo,
            tags(n.path("tags"))
        };
        if (novo)
            db.update(
                    "insert into radar_embalagem(nome,custo,comprimento_cm,largura_cm,altura_cm,"
                            + "peso_g,tipo,tags,id,tenant_id) values(?,?,?,?,?,?,?,?::jsonb,?,?)",
                    concat(valores, id, tenant()));
        else
            confirmar(
                    db.update(
                            "update radar_embalagem set nome=?,custo=?,comprimento_cm=?,"
                                    + "largura_cm=?,altura_cm=?,peso_g=?,tipo=?,tags=?::jsonb"
                                    + " where id=? and tenant_id=?",
                            concat(valores, id, tenant())));
    }

    /** Tags em minúsculas, sem repetir; até 20, de até 40 caracteres cada. */
    private String tags(JsonNode lista) {
        List<String> out = new ArrayList<>();
        for (JsonNode t : lista) {
            String v = t.asText("").trim().toLowerCase();
            if (v.isEmpty()) continue;
            if (v.length() > 40) erro("Cada tag pode ter até 40 caracteres.");
            if (!out.contains(v)) out.add(v);
        }
        if (out.size() > 20) erro("Use até 20 tags por embalagem.");
        try {
            return json.writeValueAsString(out);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Liga (ou desliga, com código vazio) a categoria da loja a uma categoria do marketplace. O
     * anúncio só fica certo no marketplace com esse vínculo.
     */
    private Map<String, Object> vincularCategoria(JsonNode n, String papel) {
        permitir(papel, "DONO", "GESTOR", "MARKETING");
        UUID categoria = vinculoOpcional(n, "categoria_id", "radar_categoria");
        if (categoria == null) erro("Escolha a categoria.");
        String canal = texto(n, "canal", 40);
        if (!RadarAnuncios.CANAIS.contains(canal)) erro("Canal inválido.");
        String codigo = opcional(n, "codigo_externo", 60);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("id", categoria);
        if (codigo == null) {
            db.update(
                    "delete from radar_categoria_canal where tenant_id=? and categoria_id=?"
                            + " and canal=?",
                    tenant(),
                    categoria,
                    canal);
            r.put("mensagem", "Vínculo com " + canal + " removido.");
            return r;
        }
        String nome = texto(n, "nome_externo", 300);
        db.update(
                "insert into"
                    + " radar_categoria_canal(tenant_id,categoria_id,canal,codigo_externo,nome_externo)"
                    + " values(?,?,?,?,?) on conflict (tenant_id,categoria_id,canal) do update set"
                    + " codigo_externo=excluded.codigo_externo,"
                    + "nome_externo=excluded.nome_externo,atualizado_em=now()",
                tenant(),
                categoria,
                canal,
                codigo,
                nome);
        r.put("mensagem", "Categoria vinculada a " + nome + " (" + canal + ").");
        return r;
    }

    /** Cadastra as embalagens sugeridas que ainda faltam (pelo nome). Repetir não duplica. */
    private Map<String, Object> adicionarSugeridas(String papel) {
        permitir(papel, "DONO", "GESTOR", "ESTOQUE");
        int novas = 0;
        for (Sugerida e : SUGERIDAS)
            novas +=
                    db.update(
                            "insert into radar_embalagem(id,tenant_id,nome,custo,comprimento_cm,"
                                    + "largura_cm,altura_cm,tipo,tags,sugerida)"
                                    + " values(?,?,?,0,?,?,?,?,?::jsonb,true)"
                                    + " on conflict (tenant_id,nome) do nothing",
                            UUID.randomUUID(),
                            tenant(),
                            e.nome(),
                            new java.math.BigDecimal(e.c()),
                            new java.math.BigDecimal(e.l()),
                            new java.math.BigDecimal(e.a()),
                            e.tipo(),
                            tags(json.valueToTree(e.tags())));
        return Map.of(
                "mensagem",
                novas == 0
                        ? "As embalagens sugeridas já estavam cadastradas."
                        : novas
                                + " embalagens sugeridas cadastradas. Informe o custo de cada"
                                + " uma.");
    }

    private static UUID tenant() {
        return ContextoTenant.atual();
    }

    private List<Map<String, Object>> linhas(String tabela) {
        return db.queryForList(
                "select * from " + tabela + " where tenant_id=? order by nome limit 1000",
                tenant());
    }

    private static Object[] concat(Object[] valores, Object... extras) {
        Object[] todos = new Object[valores.length + extras.length];
        System.arraycopy(valores, 0, todos, 0, valores.length);
        System.arraycopy(extras, 0, todos, valores.length, extras.length);
        return todos;
    }
}
