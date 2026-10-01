package com.plataforma.radar;

import static com.plataforma.radar.RadarEntrada.erro;
import static com.plataforma.radar.RadarEntrada.permitir;

import com.plataforma.comum.tenant.ContextoTenant;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Relatórios do Radar, calculados no servidor sobre o razão local (radar_lancamento).
 *
 * <p>Cada bloco traz em {@code fonte} de onde o número saiu, para ser conferido (regra 3). Valores
 * monetários saem como texto decimal, nunca como número de ponto flutuante (regra 2).
 */
@Service
public class RadarRelatorios {

    private static final BigDecimal CEM = new BigDecimal("100");
    private static final int DIAS_MAXIMOS = 366;

    private final JdbcTemplate db;

    public RadarRelatorios(JdbcTemplate db) {
        this.db = db;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> gerar(String papel, String deTexto, String ateTexto) {
        permitir(papel, "DONO", "GESTOR", "FINANCEIRO");
        LocalDate ate = ateTexto == null || ateTexto.isBlank() ? LocalDate.now() : data(ateTexto);
        LocalDate de = deTexto == null || deTexto.isBlank() ? ate.minusDays(29) : data(deTexto);
        if (de.isAfter(ate)) erro("A data inicial é posterior à final.");
        if (ChronoUnit.DAYS.between(de, ate) >= DIAS_MAXIMOS)
            erro("Escolha um período de até um ano.");
        // Intervalo semiaberto [de, ate + 1 dia), em horário do banco.
        Object[] periodo = {tenant(), de, ate.plusDays(1)};

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("periodo", Map.of("de", de.toString(), "ate", ate.toString()));
        out.put("resumo", resumo(periodo));
        out.put("porDia", porDia(periodo));
        out.put("porCanal", porCanal(periodo));
        out.put("porCategoria", porCategoria(periodo));
        out.put("curvaAbc", curvaAbc(periodo));
        out.put("porCliente", porCliente(periodo));
        out.put("estoque", estoque());
        return out;
    }

    private Map<String, Object> resumo(Object[] periodo) {
        var pedidos =
                db.queryForMap(
                        "select count(*) pedidos, coalesce(sum(quantidade),0) unidades"
                                + " from radar_pedido where tenant_id=? and estado<>'CANCELADO'"
                                + " and criado_em>=? and criado_em<?",
                        periodo);
        var razao =
                db.queryForMap(
                        "select coalesce(sum(valor) filter (where tipo='RECEITA'),0) receita,"
                                + " coalesce(sum(valor),0) resultado from radar_lancamento"
                                + " where tenant_id=? and criado_em>=? and criado_em<?",
                        periodo);
        long quantidade = ((Number) pedidos.get("pedidos")).longValue();
        BigDecimal receita = (BigDecimal) razao.get("receita");
        BigDecimal resultado = (BigDecimal) razao.get("resultado");
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("pedidos", quantidade);
        r.put("unidades", ((Number) pedidos.get("unidades")).longValue());
        r.put("receita", texto(receita));
        r.put("resultado", texto(resultado));
        r.put(
                "ticketMedio",
                quantidade == 0
                        ? null
                        : texto(
                                receita.divide(
                                        BigDecimal.valueOf(quantidade), 2, RoundingMode.HALF_UP)));
        r.put("margem", percentual(resultado, receita));
        r.put(
                "fonte",
                "Pedidos não cancelados criados no período; receita e resultado somam os"
                    + " lançamentos do razão local no período (inclui estornos e devoluções).");
        return r;
    }

    private List<Map<String, Object>> porDia(Object[] periodo) {
        return dinheiroComoTexto(
                db.queryForList(
                        "select to_char(l.criado_em::date,'YYYY-MM-DD') dia, count(distinct"
                            + " l.pedido_id) filter (where l.tipo='RECEITA') pedidos,"
                            + " coalesce(sum(l.valor) filter (where l.tipo='RECEITA'),0) receita,"
                            + " sum(l.valor) resultado from radar_lancamento l where l.tenant_id=?"
                            + " and l.criado_em>=? and l.criado_em<? group by 1 order by 1",
                        periodo));
    }

    private List<Map<String, Object>> porCanal(Object[] periodo) {
        return comMargem(
                db.queryForList(
                        "select p.canal nome, count(distinct p.id) pedidos, coalesce(sum(l.valor)"
                            + " filter (where l.tipo='RECEITA'),0) receita, sum(l.valor) resultado"
                            + " from radar_lancamento l join radar_pedido p on"
                            + " p.tenant_id=l.tenant_id and p.id=l.pedido_id where l.tenant_id=?"
                            + " and l.criado_em>=? and l.criado_em<? group by p.canal order by"
                            + " receita desc",
                        periodo));
    }

    private List<Map<String, Object>> porCategoria(Object[] periodo) {
        return comMargem(
                db.queryForList(
                        "select coalesce(c.nome,'Sem categoria') nome, coalesce(sum(l.valor) filter"
                            + " (where l.tipo='RECEITA'),0) receita, sum(l.valor) resultado from"
                            + " radar_lancamento l join radar_pedido p on p.tenant_id=l.tenant_id"
                            + " and p.id=l.pedido_id join radar_produto pr on"
                            + " pr.tenant_id=p.tenant_id and pr.id=p.produto_id left join"
                            + " radar_categoria c on c.tenant_id=pr.tenant_id and"
                            + " c.id=pr.categoria_id where l.tenant_id=? and l.criado_em>=? and"
                            + " l.criado_em<? group by 1 order by receita desc",
                        periodo));
    }

    /**
     * Curva ABC por receita: classe A até 80% da receita acumulada, B até 95%, C o restante. Só
     * entram produtos com receita positiva no período.
     */
    private List<Map<String, Object>> curvaAbc(Object[] periodo) {
        var linhas =
                db.queryForList(
                        "select pr.sku, pr.nome, coalesce(sum(l.valor) filter (where"
                            + " l.tipo='RECEITA'),0) receita, sum(l.valor) resultado from"
                            + " radar_lancamento l join radar_pedido p on p.tenant_id=l.tenant_id"
                            + " and p.id=l.pedido_id join radar_produto pr on"
                            + " pr.tenant_id=p.tenant_id and pr.id=p.produto_id where l.tenant_id=?"
                            + " and l.criado_em>=? and l.criado_em<? group by pr.sku, pr.nome order"
                            + " by receita desc, pr.sku",
                        periodo);
        BigDecimal total =
                linhas.stream()
                        .map(l -> (BigDecimal) l.get("receita"))
                        .filter(v -> v.signum() > 0)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
        List<Map<String, Object>> out = new ArrayList<>();
        BigDecimal acumulado = BigDecimal.ZERO;
        for (var l : linhas) {
            BigDecimal receita = (BigDecimal) l.get("receita");
            if (receita.signum() <= 0) continue;
            acumulado = acumulado.add(receita);
            BigDecimal fatiaAcumulada =
                    acumulado.multiply(CEM).divide(total, 2, RoundingMode.HALF_UP);
            // A classe usa a participação acumulada ANTES deste produto: o primeiro item é A
            // mesmo que sozinho passe de 80%.
            BigDecimal antes =
                    acumulado
                            .subtract(receita)
                            .multiply(CEM)
                            .divide(total, 2, RoundingMode.HALF_UP);
            String classe =
                    antes.compareTo(new BigDecimal("80")) < 0
                            ? "A"
                            : antes.compareTo(new BigDecimal("95")) < 0 ? "B" : "C";
            Map<String, Object> m = new LinkedHashMap<>(l);
            m.put(
                    "participacao",
                    texto(receita.multiply(CEM).divide(total, 2, RoundingMode.HALF_UP)));
            m.put("acumulado", texto(fatiaAcumulada));
            m.put("classe", classe);
            m.put("margem", percentual((BigDecimal) l.get("resultado"), receita));
            out.add(m);
        }
        return dinheiroComoTexto(out);
    }

    private List<Map<String, Object>> porCliente(Object[] periodo) {
        return dinheiroComoTexto(
                db.queryForList(
                        "select p.cliente nome, count(distinct p.id) pedidos, coalesce(sum(l.valor)"
                            + " filter (where l.tipo='RECEITA'),0) receita from radar_lancamento l"
                            + " join radar_pedido p on p.tenant_id=l.tenant_id and p.id=l.pedido_id"
                            + " where l.tenant_id=? and l.criado_em>=? and l.criado_em<? group by"
                            + " p.cliente order by receita desc limit 20",
                        periodo));
    }

    private List<Map<String, Object>> estoque() {
        return dinheiroComoTexto(
                db.queryForList(
                        "select coalesce(c.nome,'Sem categoria') nome, count(*) skus,"
                                + " sum(pr.fisico) unidades, sum(pr.custo*pr.fisico) valor_custo"
                                + " from radar_produto pr left join radar_categoria c"
                                + " on c.tenant_id=pr.tenant_id and c.id=pr.categoria_id"
                                + " where pr.tenant_id=? group by 1 order by valor_custo desc",
                        tenant()));
    }

    private List<Map<String, Object>> comMargem(List<Map<String, Object>> linhas) {
        for (var l : linhas)
            l.put(
                    "margem",
                    percentual((BigDecimal) l.get("resultado"), (BigDecimal) l.get("receita")));
        return dinheiroComoTexto(linhas);
    }

    /** Resultado sobre receita, em %, com duas casas. Null quando não houve receita. */
    static String percentual(BigDecimal resultado, BigDecimal receita) {
        if (receita == null || receita.signum() <= 0) return null;
        return texto(resultado.multiply(CEM).divide(receita, 2, RoundingMode.HALF_UP));
    }

    private static List<Map<String, Object>> dinheiroComoTexto(List<Map<String, Object>> linhas) {
        for (var l : linhas) l.replaceAll((k, v) -> v instanceof BigDecimal b ? texto(b) : v);
        return linhas;
    }

    private static String texto(BigDecimal v) {
        return v.toPlainString();
    }

    private static LocalDate data(String texto) {
        try {
            return LocalDate.parse(texto);
        } catch (DateTimeParseException e) {
            erro("Data inválida: " + texto);
            return null;
        }
    }

    private static UUID tenant() {
        return ContextoTenant.atual();
    }
}
