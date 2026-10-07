package com.plataforma.radar;

import static com.plataforma.radar.RadarEntrada.erro;
import static com.plataforma.radar.RadarEntrada.permitir;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.plataforma.comum.tenant.ContextoTenant;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Configurações por empresa (V032, tabela radar_configuracao), uma área por chave. Cada área tem
 * os seus campos validados aqui; o que não foi configurado vale o padrão.
 *
 * <p>Por enquanto só a área "produtos" (Configurações → cadastros → Configurações do cadastro de
 * produtos). As próximas áreas entram como novos casos em {@link #validar}.
 */
@Service
public class RadarConfiguracao {

    static final Set<String> OPERACOES = Set.of("configuracao_salvar");

    static final Set<String> MODOS_SKU = Set.of("MANUAL", "SEQUENCIAL", "PREFIXO");

    private final JdbcTemplate db;
    private final ObjectMapper json;

    public RadarConfiguracao(JdbcTemplate db, ObjectMapper json) {
        this.db = db;
        this.json = json;
    }

    /** Todas as áreas, já com os padrões, para as telas. */
    Map<String, Object> dados() {
        return Map.of("configuracoes", Map.of("produtos", produtos(db, json)));
    }

    /** Configuração de produtos da empresa do contexto, com os padrões no que faltar. */
    static Map<String, Object> produtos(JdbcTemplate db, ObjectMapper json) {
        Map<String, Object> c = new LinkedHashMap<>();
        c.put("sku_modo", "MANUAL");
        c.put("sku_prefixo", "");
        c.put("sku_digitos", 5);
        c.put("unidade_padrao", "UN");
        c.put("ncm_padrao", "");
        c.put("origem_padrao", "");
        var linhas =
                db.queryForList(
                        "select valor::text from radar_configuracao where tenant_id=? and"
                                + " chave='produtos'",
                        String.class,
                        ContextoTenant.atual());
        if (!linhas.isEmpty()) {
            try {
                json.readTree(linhas.getFirst())
                        .fields()
                        .forEachRemaining(
                                e -> {
                                    if (c.containsKey(e.getKey()))
                                        c.put(
                                                e.getKey(),
                                                e.getValue().isInt()
                                                        ? e.getValue().asInt()
                                                        : e.getValue().asText());
                                });
            } catch (JsonProcessingException e) {
                throw new IllegalStateException(e);
            }
        }
        // Revalida na leitura: o prefixo entra numa expressão regular e os dígitos num formato.
        if (!c.get("sku_prefixo").toString().matches("[A-Z0-9-]{0,12}")) c.put("sku_prefixo", "");
        if (!(c.get("sku_digitos") instanceof Integer d) || d < 1 || d > 10) c.put("sku_digitos", 5);
        if (!MODOS_SKU.contains(c.get("sku_modo").toString())) c.put("sku_modo", "MANUAL");
        if (c.get("sku_modo").equals("PREFIXO") && c.get("sku_prefixo").toString().isEmpty())
            c.put("sku_modo", "MANUAL");
        return c;
    }

    Map<String, Object> salvar(JsonNode n, String papel) {
        permitir(papel, "DONO", "GESTOR");
        String chave = n.path("chave").asText("");
        Map<String, Object> valor = validar(chave, n.path("valor"));
        try {
            db.update(
                    "insert into radar_configuracao(tenant_id,chave,valor) values(?,?,?::jsonb)"
                            + " on conflict (tenant_id,chave) do update set valor=excluded.valor,"
                            + "atualizado_em=now()",
                    tenant(),
                    chave,
                    json.writeValueAsString(valor));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
        return Map.of("id", chave, "mensagem", "Configurações salvas.");
    }

    private Map<String, Object> validar(String chave, JsonNode v) {
        if (!chave.equals("produtos")) erro("Área de configuração desconhecida.");
        if (!v.isObject()) erro("Configuração inválida.");
        Map<String, Object> c = new LinkedHashMap<>();
        String modo = v.path("sku_modo").asText("MANUAL");
        if (!MODOS_SKU.contains(modo)) erro("Modo de SKU inválido.");
        c.put("sku_modo", modo);
        String prefixo = v.path("sku_prefixo").asText("").trim().toUpperCase();
        // Só letras, números e hífen: o prefixo entra no SKU e na busca da próxima sequência.
        if (!prefixo.matches("[A-Z0-9-]{0,12}"))
            erro("O prefixo do SKU aceita até 12 letras, números ou hífen.");
        if (modo.equals("PREFIXO") && prefixo.isEmpty()) erro("Informe o prefixo do SKU.");
        c.put("sku_prefixo", prefixo);
        int digitos = v.path("sku_digitos").asInt(5);
        if (digitos < 1 || digitos > 10) erro("O número do SKU tem de 1 a 10 dígitos.");
        c.put("sku_digitos", digitos);
        String unidade = v.path("unidade_padrao").asText("UN").trim().toUpperCase();
        if (!RadarProdutos.UNIDADES.contains(unidade)) erro("Unidade padrão inválida.");
        c.put("unidade_padrao", unidade);
        String ncm = v.path("ncm_padrao").asText("").replaceAll("[^0-9]", "");
        if (!ncm.isEmpty() && ncm.length() != 8) erro("NCM padrão deve ter 8 dígitos.");
        c.put("ncm_padrao", ncm);
        String origem = v.path("origem_padrao").asText("").trim();
        if (!origem.isEmpty() && !origem.matches("[0-8]")) erro("Origem padrão inválida.");
        c.put("origem_padrao", origem);
        return c;
    }

    private UUID tenant() {
        return ContextoTenant.atual();
    }
}
