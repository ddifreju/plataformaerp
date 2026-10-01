package com.plataforma.radar;

import static com.plataforma.radar.RadarEntrada.confirmar;
import static com.plataforma.radar.RadarEntrada.erro;
import static com.plataforma.radar.RadarEntrada.id;
import static com.plataforma.radar.RadarEntrada.opcional;
import static com.plataforma.radar.RadarEntrada.permitir;
import static com.plataforma.radar.RadarEntrada.texto;
import static com.plataforma.radar.RadarEntrada.valor;

import com.fasterxml.jackson.databind.JsonNode;
import com.plataforma.comum.tenant.ContextoTenant;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Promoções do Radar (V019). Uma promoção ativa vira desconto no pedido quando o usuário a escolhe;
 * o pedido guarda {@code promocao_id} para a conta ser rastreável.
 */
@Service
public class RadarPromocoes {

    static final Set<String> OPERACOES =
            Set.of("promocao", "promocao_atualizar", "promocao_encerrar");

    private static final Set<String> CANAIS =
            Set.of("Mercado Livre", "Shopee", "TikTok Shop", "SHEIN");

    private final JdbcTemplate db;

    public RadarPromocoes(JdbcTemplate db) {
        this.db = db;
    }

    Map<String, Object> dados() {
        return Map.of(
                "promocoes",
                db.queryForList(
                        "select * from radar_promocao where tenant_id=?"
                                + " order by ativo desc, inicio desc limit 1000",
                        tenant()));
    }

    Map<String, Object> executar(String op, JsonNode n, String papel) {
        permitir(papel, "DONO", "GESTOR", "MARKETING");
        Map<String, Object> r = new LinkedHashMap<>();
        switch (op) {
            case "promocao" -> {
                UUID id = UUID.randomUUID();
                db.update(
                        "insert into radar_promocao(nome,tipo,valor,produto_id,canal,inicio,fim,id,"
                                + "tenant_id) values(?,?,?,?,?,?,?,?,?)",
                        concat(campos(n), id, tenant()));
                r.put("id", id);
                r.put("mensagem", "Promoção criada. Ela aparece como opção ao lançar pedidos.");
            }
            case "promocao_atualizar" -> {
                UUID id = id(n, "id");
                confirmar(
                        db.update(
                                "update radar_promocao set nome=?,tipo=?,valor=?,produto_id=?,"
                                        + "canal=?,inicio=?,fim=? where id=? and tenant_id=?",
                                concat(campos(n), id, tenant())));
                r.put("id", id);
                r.put("mensagem", "Promoção atualizada. Pedidos já lançados não mudam.");
            }
            case "promocao_encerrar" -> {
                UUID id = id(n, "id");
                confirmar(
                        db.update(
                                "update radar_promocao set ativo=false where id=? and tenant_id=?",
                                id,
                                tenant()));
                r.put("id", id);
                r.put("mensagem", "Promoção encerrada.");
            }
            default -> erro("Operação não reconhecida.");
        }
        return r;
    }

    /**
     * Desconto total que a promoção dá a um pedido. Recusa promoção encerrada, fora do período, de
     * outro produto ou de outro canal: o usuário escolheu uma promoção que não vale para este
     * pedido, e aplicar outra coisa em silêncio esconderia o erro.
     */
    BigDecimal desconto(
            UUID promocaoId, UUID produtoId, String canal, BigDecimal preco, int quantidade) {
        var linhas =
                db.queryForList(
                        "select * from radar_promocao where tenant_id=? and id=?",
                        tenant(),
                        promocaoId);
        if (linhas.isEmpty()) erro("Promoção não encontrada.");
        var p = linhas.getFirst();
        LocalDate hoje = LocalDate.now();
        LocalDate inicio = ((java.sql.Date) p.get("inicio")).toLocalDate();
        LocalDate fim = ((java.sql.Date) p.get("fim")).toLocalDate();
        if (!Boolean.TRUE.equals(p.get("ativo"))) erro("Esta promoção foi encerrada.");
        if (hoje.isBefore(inicio) || hoje.isAfter(fim)) erro("Esta promoção está fora do período.");
        if (p.get("produto_id") != null && !p.get("produto_id").equals(produtoId))
            erro("Esta promoção é de outro produto.");
        if (p.get("canal") != null && !p.get("canal").equals(canal))
            erro("Esta promoção é de outro canal.");

        BigDecimal bruto = preco.multiply(BigDecimal.valueOf(quantidade));
        BigDecimal valor = (BigDecimal) p.get("valor");
        BigDecimal desconto =
                "PERCENTUAL".equals(p.get("tipo"))
                        ? bruto.multiply(valor)
                                .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP)
                        : valor.multiply(BigDecimal.valueOf(quantidade))
                                .setScale(2, RoundingMode.UNNECESSARY);
        if (desconto.compareTo(bruto) > 0) erro("O desconto da promoção é maior que a venda.");
        return desconto;
    }

    private Object[] campos(JsonNode n) {
        String tipo = texto(n, "tipo", 20);
        if (!Set.of("PERCENTUAL", "VALOR_FIXO").contains(tipo)) erro("Tipo de promoção inválido.");
        BigDecimal valor = valor(n, "valor");
        if (valor.signum() == 0) erro("Informe o valor do desconto.");
        if (tipo.equals("PERCENTUAL") && valor.compareTo(new BigDecimal("100")) > 0)
            erro("Percentual acima de 100%.");
        UUID produto = null;
        if (!n.path("produto_id").asText("").isBlank()) {
            produto = id(n, "produto_id");
            Integer existe =
                    db.queryForObject(
                            "select count(*) from radar_produto where tenant_id=? and id=?",
                            Integer.class,
                            tenant(),
                            produto);
            if (existe == null || existe == 0) erro("Produto não encontrado.");
        }
        String canal = opcional(n, "canal", 40);
        if (canal != null && !CANAIS.contains(canal)) erro("Canal não suportado.");
        LocalDate inicio = data(n, "inicio"), fim = data(n, "fim");
        if (fim.isBefore(inicio)) erro("A data final é anterior à inicial.");
        return new Object[] {texto(n, "nome", 160), tipo, valor, produto, canal, inicio, fim};
    }

    private static LocalDate data(JsonNode n, String campo) {
        try {
            return LocalDate.parse(n.path(campo).asText());
        } catch (DateTimeParseException e) {
            erro("Data inválida: " + campo);
            return null;
        }
    }

    private static UUID tenant() {
        return ContextoTenant.atual();
    }

    private static Object[] concat(Object[] valores, Object... extras) {
        Object[] todos = new Object[valores.length + extras.length];
        System.arraycopy(valores, 0, todos, 0, valores.length);
        System.arraycopy(extras, 0, todos, valores.length, extras.length);
        return todos;
    }
}
