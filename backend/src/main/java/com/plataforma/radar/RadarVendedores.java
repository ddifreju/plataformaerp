package com.plataforma.radar;

import static com.plataforma.radar.RadarClientes.campo;
import static com.plataforma.radar.RadarClientes.digitos;
import static com.plataforma.radar.RadarClientes.email;
import static com.plataforma.radar.RadarClientes.exigir;
import static com.plataforma.radar.RadarClientes.mascarar;
import static com.plataforma.radar.RadarClientes.uf;
import static com.plataforma.radar.RadarEntrada.cnpjValido;
import static com.plataforma.radar.RadarEntrada.confirmar;
import static com.plataforma.radar.RadarEntrada.cpfValido;
import static com.plataforma.radar.RadarEntrada.decimalOpcional;
import static com.plataforma.radar.RadarEntrada.erro;
import static com.plataforma.radar.RadarEntrada.id;
import static com.plataforma.radar.RadarEntrada.inteiroOpcional;
import static com.plataforma.radar.RadarEntrada.permitir;
import static com.plataforma.radar.RadarEntrada.soAlfanumerico;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.plataforma.comum.tenant.ContextoTenant;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Cadastro de vendedores (V025): dados pessoais e fiscais, endereço, acesso ao sistema e comissão.
 *
 * <p>As restrições de acesso (horário, IP, módulos, perfil de contato) ficam gravadas aqui e passam
 * a valer quando o login do vendedor for ativado; enquanto isso, o vendedor só existe como cadastro
 * e como "vendedor padrão" do cliente.
 */
@Service
public class RadarVendedores {

    static final Set<String> OPERACOES = Set.of("vendedor_salvar");

    /** Veem a lista (para escolher o vendedor padrão do cliente, por exemplo). */
    static final Set<String> VEEM =
            Set.of("DONO", "GESTOR", "ATENDIMENTO", "FINANCEIRO", "ANALISTA");

    /** Cadastram vendedor e veem CPF/CNPJ e comissão completos. */
    static final Set<String> EDITAM = Set.of("DONO", "GESTOR");

    static final Set<String> VEEM_DETALHE = Set.of("DONO", "GESTOR", "FINANCEIRO");

    private static final Set<String> TIPOS_PESSOA = Set.of("F", "J", "E", "B");
    private static final Set<String> DIAS = Set.of("SEG", "TER", "QUA", "QUI", "SEX", "SAB", "DOM");
    private static final Set<String> PERFIS =
            Set.of("QUALQUER", "CLIENTE", "FORNECEDOR", "TRANSPORTADOR");
    static final Set<String> MODULOS =
            Set.of(
                    "CLIENTES",
                    "COMISSOES",
                    "CRM",
                    "PEDIDOS",
                    "PDV",
                    "PROPOSTAS",
                    "RELATORIO_PRECOS",
                    "PERFORMANCE",
                    "COTACAO_FRETE");
    private static final Set<String> COLUNAS_JSON = Set.of("acesso_dias", "acesso_ips", "modulos");
    // IPv4, com máscara opcional (ex.: 200.150.10.5 ou 200.150.10.0/24), ou IPv6 simples.
    private static final String IP =
            "^((25[0-5]|2[0-4]\\d|1?\\d?\\d)\\.){3}(25[0-5]|2[0-4]\\d|1?\\d?\\d)(/(3[0-2]|[12]?\\d))?$"
                + "|^[0-9a-fA-F:]{2,39}(/\\d{1,3})?$";

    private final JdbcTemplate db;
    private final ObjectMapper json;

    public RadarVendedores(JdbcTemplate db, ObjectMapper json) {
        this.db = db;
        this.json = json;
    }

    /** Lista de vendedores e, para quem cadastra, os usuários do sistema que podem ser ligados. */
    Map<String, Object> dados(String papel) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (!VEEM.contains(papel)) {
            out.put("vendedores", List.of());
            out.put("usuariosSistema", List.of());
            return out;
        }
        var vendedores =
                db.queryForList(
                        "select v.id, v.codigo, v.nome, v.fantasia, v.tipo_pessoa, v.documento,"
                            + " v.email, v.celular, v.telefone, v.cidade, v.uf, v.situacao,"
                            + " v.comissao_regra, v.comissao_aliquota, v.usuario_id, u.nome"
                            + " usuario_nome, (select count(*) from radar_cliente c where"
                            + " c.tenant_id=v.tenant_id and c.vendedor_id=v.id) clientes from"
                            + " radar_vendedor v left join usuario u on u.tenant_id=v.tenant_id and"
                            + " u.id=v.usuario_id where v.tenant_id=? order by v.nome",
                        tenant());
        for (var v : vendedores) {
            v.put("documento", mascarar((String) v.get("documento")));
            if (!VEEM_DETALHE.contains(papel)) {
                v.remove("comissao_regra");
                v.remove("comissao_aliquota");
            }
        }
        out.put("vendedores", vendedores);
        out.put(
                "usuariosSistema",
                EDITAM.contains(papel)
                        ? db.queryForList(
                                "select id, nome, email, papel from usuario where tenant_id=?"
                                        + " and ativo order by nome",
                                tenant())
                        : List.of());
        return out;
    }

    Map<String, Object> detalhe(String papel, UUID vendedorId) {
        permitir(papel, VEEM_DETALHE.toArray(String[]::new));
        var linhas =
                db.queryForList(
                        "select * from radar_vendedor where tenant_id=? and id=?",
                        tenant(),
                        vendedorId);
        if (linhas.isEmpty())
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Vendedor não encontrado.");
        Map<String, Object> v = new LinkedHashMap<>(linhas.getFirst());
        v.remove("tenant_id");
        for (String k : COLUNAS_JSON) v.put(k, lista(v.get(k)));
        for (String k : List.of("acesso_horario_inicio", "acesso_horario_fim"))
            if (v.get(k) != null) v.put(k, v.get(k).toString().substring(0, 5));
        v.replaceAll((k, x) -> x instanceof BigDecimal b ? b.toPlainString() : x);
        return v;
    }

    Map<String, Object> salvar(JsonNode n, String papel) {
        permitir(papel, EDITAM.toArray(String[]::new));
        boolean novo = n.path("id").asText("").isBlank();
        UUID id = novo ? UUID.randomUUID() : id(n, "id");
        Map<String, Object> c = colunas(n, id);
        if (c.get("codigo") == null) {
            if (novo) c.put("codigo", proximoCodigo());
            else c.remove("codigo");
        }
        List<String> nomes = new ArrayList<>(c.keySet());
        List<Object> valores = new ArrayList<>(c.values());
        valores.add(id);
        valores.add(tenant());
        if (novo)
            db.update(
                    "insert into radar_vendedor("
                            + String.join(",", nomes)
                            + ",id,tenant_id) values("
                            + String.join(
                                    ",", nomes.stream().map(RadarVendedores::marcador).toList())
                            + ",?,?)",
                    valores.toArray());
        else
            confirmar(
                    db.update(
                            "update radar_vendedor set "
                                    + String.join(
                                            ",",
                                            nomes.stream().map(k -> k + "=" + marcador(k)).toList())
                                    + ",atualizado_em=now() where id=? and tenant_id=?",
                            valores.toArray()));
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("id", id);
        r.put("mensagem", novo ? "Vendedor cadastrado." : "Vendedor atualizado.");
        return r;
    }

    /** Colunas validadas; junta os obrigatórios que faltam numa mensagem só. */
    private Map<String, Object> colunas(JsonNode n, UUID id) {
        List<String> faltando = new ArrayList<>();
        Map<String, Object> c = new LinkedHashMap<>();

        String codigo = campo(n, "codigo", 30, "Código");
        if (codigo != null && existe("codigo", codigo, id))
            erro("Já existe um vendedor com o código " + codigo + ".");
        c.put("codigo", codigo);
        c.put("nome", exigir(faltando, campo(n, "nome", 200, "Nome"), "Nome"));
        c.put("fantasia", campo(n, "fantasia", 200, "Fantasia"));

        String tipo = n.path("tipo_pessoa").asText("F").trim().toUpperCase();
        if (!TIPOS_PESSOA.contains(tipo)) erro("Tipo de pessoa inválido.");
        c.put("tipo_pessoa", tipo);
        String documento = soAlfanumerico(campo(n, "documento", 20, "CPF/CNPJ"));
        if (documento != null && documento.isEmpty()) documento = null;
        switch (tipo) {
            case "F" -> {
                if (documento == null) faltando.add("CPF");
                else if (!cpfValido(documento)) erro("CPF inválido: confira os dígitos.");
            }
            case "J" -> {
                if (documento == null) faltando.add("CNPJ");
                else if (!cnpjValido(documento)) erro("CNPJ inválido: confira os dígitos.");
            }
            case "B" -> {
                if (documento != null && !cpfValido(documento))
                    erro("CPF inválido: confira os dígitos.");
            }
            default -> documento = null;
        }
        if (documento != null && existe("documento", documento, id))
            erro("Já existe um vendedor com este CPF/CNPJ.");
        c.put("documento", documento);

        Integer contribuinte = inteiroOpcional(n, "contribuinte", 1, 9);
        if (contribuinte != null && !Set.of(1, 2, 9).contains(contribuinte))
            erro("Indicador de contribuinte inválido.");
        c.put("contribuinte", contribuinte);
        String ie = campo(n, "inscricao_estadual", 20, "Inscrição estadual");
        if (ie != null) {
            ie = ie.toUpperCase();
            if (!ie.equals("ISENTO")) ie = ie.replaceAll("[^0-9]", "");
            if (ie.isEmpty()) erro("Inscrição estadual inválida: use só números ou ISENTO.");
        }
        c.put("inscricao_estadual", ie);

        // Endereço: opcional, mas o que vier é conferido.
        String cep = digitos(campo(n, "cep", 9, "CEP"));
        if (cep != null && cep.isEmpty()) cep = null;
        if (cep != null && cep.length() != 8) erro("CEP deve ter 8 dígitos.");
        c.put("cep", cep);
        c.put("endereco", campo(n, "endereco", 200, "Endereço"));
        c.put("numero", campo(n, "numero", 20, "Número"));
        c.put("complemento", campo(n, "complemento", 120, "Complemento"));
        c.put("bairro", campo(n, "bairro", 120, "Bairro"));
        c.put("cidade", campo(n, "cidade", 120, "Cidade"));
        c.put("uf", uf(campo(n, "uf", 2, "UF")));
        String ibge = digitos(campo(n, "municipio_ibge", 7, "Código IBGE"));
        c.put("municipio_ibge", ibge != null && ibge.length() == 7 ? ibge : null);

        c.put("telefone", campo(n, "telefone", 40, "Telefone"));
        c.put("celular", campo(n, "celular", 40, "Celular"));
        c.put(
                "email",
                exigir(faltando, email(campo(n, "email", 320, "E-mail"), "E-mail"), "E-mail"));
        c.put(
                "email_comunicacoes",
                email(
                        campo(n, "email_comunicacoes", 320, "E-mail para comunicações"),
                        "E-mail para comunicações"));
        String situacao = n.path("situacao").asText("ATIVO").trim().toUpperCase();
        if (!Set.of("ATIVO", "INATIVO").contains(situacao)) erro("Situação inválida.");
        c.put("situacao", situacao);
        c.put("deposito", campo(n, "deposito", 60, "Depósito"));

        // Dados de acesso.
        c.put("usuario_id", usuario(n, id));
        LocalTime inicio = hora(campo(n, "acesso_horario_inicio", 5, "Horário inicial"));
        LocalTime fim = hora(campo(n, "acesso_horario_fim", 5, "Horário final"));
        if ((inicio == null) != (fim == null))
            erro("Informe o horário inicial e o final do acesso, ou deixe os dois em branco.");
        if (inicio != null && !inicio.isBefore(fim))
            erro("O horário inicial do acesso precisa ser antes do final.");
        c.put("acesso_horario_inicio", inicio);
        c.put("acesso_horario_fim", fim);
        c.put("acesso_dias", conjunto(n.path("acesso_dias"), DIAS, "Dia de acesso inválido."));
        c.put("acesso_ips", ips(n.path("acesso_ips")));
        String perfil = n.path("perfil_contatos").asText("QUALQUER").trim().toUpperCase();
        if (!PERFIS.contains(perfil)) erro("Perfil de contato inválido.");
        c.put("perfil_contatos", perfil);
        c.put("modulos", conjunto(n.path("modulos"), MODULOS, "Módulo inválido."));
        c.put(
                "pode_incluir_produto_nao_cadastrado",
                n.path("pode_incluir_produto_nao_cadastrado").asBoolean(false));
        c.put("pode_emitir_cobrancas", n.path("pode_emitir_cobrancas").asBoolean(false));

        // Comissão: percentual com até duas casas, de 0 a 100.
        String regra = n.path("comissao_regra").asText("FIXA").trim().toUpperCase();
        if (!Set.of("FIXA", "DESCONTO").contains(regra)) erro("Regra de comissão inválida.");
        c.put("comissao_regra", regra);
        BigDecimal aliquota = decimalOpcional(n, "comissao_aliquota", 2);
        if (aliquota != null && aliquota.compareTo(new BigDecimal("100")) > 0)
            erro("A alíquota de comissão vai até 100%.");
        c.put("comissao_aliquota", aliquota == null ? BigDecimal.ZERO.setScale(2) : aliquota);
        c.put(
                "desconsiderar_comissao_linha",
                n.path("desconsiderar_comissao_linha").asBoolean(false));
        c.put("observacoes", campo(n, "observacoes", 2000, "Observações"));

        if (!faltando.isEmpty())
            erro("Preencha os campos obrigatórios: " + String.join(", ", faltando) + ".");
        return c;
    }

    /** Usuário do sistema desta empresa, ligado a no máximo um vendedor. */
    private UUID usuario(JsonNode n, UUID vendedor) {
        if (n.path("usuario_id").asText("").isBlank()) return null;
        UUID u = id(n, "usuario_id");
        Integer existe =
                db.queryForObject(
                        "select count(*) from usuario where tenant_id=? and id=?",
                        Integer.class,
                        tenant(),
                        u);
        if (existe == null || existe == 0) erro("Usuário do sistema não encontrado.");
        var outro =
                db.queryForList(
                        "select nome from radar_vendedor where tenant_id=? and usuario_id=?"
                                + " and id<>?",
                        String.class,
                        tenant(),
                        u,
                        vendedor);
        if (!outro.isEmpty())
            erro("Este usuário já está ligado ao vendedor " + outro.getFirst() + ".");
        return u;
    }

    private boolean existe(String coluna, String valor, UUID id) {
        // coluna vem só de chamadas fixas deste arquivo ("codigo", "documento").
        Integer n =
                db.queryForObject(
                        "select count(*) from radar_vendedor where tenant_id=? and "
                                + coluna
                                + "=? and id<>?",
                        Integer.class,
                        tenant(),
                        valor,
                        id);
        return n != null && n > 0;
    }

    private String proximoCodigo() {
        Integer ultimo =
                db.queryForObject(
                        "select coalesce(max(substring(codigo from 2)::int),0) from radar_vendedor"
                                + " where tenant_id=? and codigo ~ '^V[0-9]{1,9}$'",
                        Integer.class,
                        tenant());
        return "V%05d".formatted((ultimo == null ? 0 : ultimo) + 1);
    }

    private static LocalTime hora(String s) {
        if (s == null) return null;
        try {
            return LocalTime.parse(s.length() == 4 ? "0" + s : s);
        } catch (DateTimeParseException e) {
            erro("Horário inválido: use HH:MM.");
            return null;
        }
    }

    private String conjunto(JsonNode lista, Set<String> validos, String mensagem) {
        List<String> out = new ArrayList<>();
        for (JsonNode i : lista) {
            String v = i.asText("").trim().toUpperCase();
            if (!validos.contains(v)) erro(mensagem);
            if (!out.contains(v)) out.add(v);
        }
        return escrever(out);
    }

    private String ips(JsonNode lista) {
        List<String> out = new ArrayList<>();
        for (JsonNode i : lista) {
            String v = i.asText("").trim();
            if (v.isEmpty()) continue;
            if (!v.matches(IP)) erro("IP inválido: " + v);
            if (!out.contains(v)) out.add(v);
        }
        if (out.size() > 20) erro("Informe até 20 IPs.");
        return escrever(out);
    }

    private String escrever(Object valor) {
        try {
            return json.writeValueAsString(valor);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private List<Object> lista(Object valor) {
        if (valor == null) return List.of();
        try {
            return json.readValue(
                    valor.toString(),
                    new com.fasterxml.jackson.core.type.TypeReference<List<Object>>() {});
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String marcador(String coluna) {
        return COLUNAS_JSON.contains(coluna) ? "?::jsonb" : "?";
    }

    private static UUID tenant() {
        return ContextoTenant.atual();
    }
}
