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

import com.fasterxml.jackson.databind.JsonNode;
import com.plataforma.comum.tenant.ContextoTenant;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Cadastros do Radar (V018): clientes, fornecedores, categorias e embalagens.
 *
 * <p>Chamado pelo {@link RadarService}, que cuida de trava por tenant, idempotência e auditoria.
 * Toda query leva {@code tenant_id} explícito; o RLS da V018 é a segunda camada.
 */
@Service
public class RadarCadastros {

    static final Set<String> OPERACOES =
            Set.of(
                    "cliente",
                    "cliente_atualizar",
                    "fornecedor",
                    "fornecedor_atualizar",
                    "categoria",
                    "categoria_atualizar",
                    "embalagem",
                    "embalagem_atualizar");

    private static final Set<String> VEEM_CLIENTES =
            Set.of("DONO", "GESTOR", "ATENDIMENTO", "FINANCEIRO", "ANALISTA");
    private static final Set<String> VEEM_FORNECEDORES =
            Set.of("DONO", "GESTOR", "ESTOQUE", "FINANCEIRO");

    private final JdbcTemplate db;

    public RadarCadastros(JdbcTemplate db) {
        this.db = db;
    }

    /** Listas que entram na resposta de GET /api/radar, filtradas pelo cargo. */
    Map<String, Object> dados(String papel, boolean financeiro) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("clientes", VEEM_CLIENTES.contains(papel) ? linhas("radar_cliente") : List.of());
        out.put(
                "fornecedores",
                VEEM_FORNECEDORES.contains(papel) ? linhas("radar_fornecedor") : List.of());
        out.put("categorias", linhas("radar_categoria"));
        var embalagens = linhas("radar_embalagem");
        if (!financeiro) embalagens.forEach(e -> e.remove("custo"));
        out.put("embalagens", embalagens);
        return out;
    }

    /** Executa uma operação de cadastro e devolve o id afetado e a mensagem. */
    Map<String, Object> executar(String op, JsonNode n, String papel) {
        Map<String, Object> r = new LinkedHashMap<>();
        boolean novo = !op.endsWith("_atualizar");
        UUID id = novo ? UUID.randomUUID() : id(n, "id");
        switch (op) {
            case "cliente", "cliente_atualizar" -> {
                permitir(papel, "DONO", "GESTOR", "ATENDIMENTO");
                salvarCliente(id, n, novo);
                r.put("mensagem", novo ? "Cliente cadastrado." : "Cliente atualizado.");
            }
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

    private void salvarCliente(UUID id, JsonNode n, boolean novo) {
        String uf = opcional(n, "uf", 2);
        if (uf != null) uf = uf.toUpperCase();
        Object[] valores = {
            texto(n, "nome", 200),
            email(n),
            opcional(n, "telefone", 40),
            opcional(n, "cidade", 120),
            uf,
            opcional(n, "observacao", 1000)
        };
        if (novo)
            db.update(
                    "insert into"
                        + " radar_cliente(nome,email,telefone,cidade,uf,observacao,id,tenant_id)"
                        + " values(?,?,?,?,?,?,?,?)",
                    concat(valores, id, tenant()));
        else
            confirmar(
                    db.update(
                            "update radar_cliente set nome=?,email=?,telefone=?,cidade=?,uf=?,"
                                    + "observacao=? where id=? and tenant_id=?",
                            concat(valores, id, tenant())));
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
        Object[] valores = {
            texto(n, "nome", 120),
            valor(n, "custo"),
            medidaOpcional(n, "comprimento_cm"),
            medidaOpcional(n, "largura_cm"),
            medidaOpcional(n, "altura_cm"),
            inteiroOpcional(n, "peso_g", 0, 1_000_000)
        };
        if (novo)
            db.update(
                    "insert into radar_embalagem(nome,custo,comprimento_cm,largura_cm,altura_cm,"
                            + "peso_g,id,tenant_id) values(?,?,?,?,?,?,?,?)",
                    concat(valores, id, tenant()));
        else
            confirmar(
                    db.update(
                            "update radar_embalagem set"
                                + " nome=?,custo=?,comprimento_cm=?,largura_cm=?,altura_cm=?,peso_g=?"
                                + " where id=? and tenant_id=?",
                            concat(valores, id, tenant())));
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
