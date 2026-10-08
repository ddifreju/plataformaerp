package com.plataforma.radar;

import static com.plataforma.radar.RadarEntrada.erro;
import static com.plataforma.radar.RadarEntrada.id;
import static com.plataforma.radar.RadarEntrada.texto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.plataforma.comum.tenant.ContextoTenant;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Fontes de dados do "Montar relatório": cada fonte é uma consulta fixa (com o tenant e o
 * período), já cruzando as áreas (pedido com produto, categoria, cliente, loja...). A tela escolhe
 * colunas, filtra, agrupa e soma; os números vêm daqui, sem o limite de 1.000 das listas da tela.
 *
 * <p>Quem vê o quê segue o {@code dados()} do {@link RadarService}: colunas de custo e resultado
 * só para quem vê o financeiro; contas e lançamentos só para ele; atendimento e compras só para os
 * cargos que já veem essas áreas. Dinheiro sai como texto (decisão 0026).
 *
 * <p>Também guarda os relatórios salvos da empresa (radar_configuracao, chave "relatorios").
 */
@Service
public class RadarFontes {

    static final Set<String> OPERACOES = Set.of("relatorio_salvar", "relatorio_excluir");

    /** Acima disso o relatório avisa que foi cortado, em vez de esconder linhas (regra 5). */
    static final int LIMITE = 20000;

    private static final int DIAS_MAXIMOS = 366;
    private static final int MAX_MODELOS = 200;
    private static final int MAX_POR_PESSOA = 30;
    private static final Set<String> FINANCEIRO = Set.of("DONO", "GESTOR", "FINANCEIRO");
    private static final Set<String> TODOS =
            Set.of(
                    "DONO",
                    "GESTOR",
                    "ANALISTA",
                    "FINANCEIRO",
                    "ATENDIMENTO",
                    "ESTOQUE",
                    "MARKETING");

    /**
     * Tipo diz como a tela mostra e soma: texto, inteiro, dinheiro, data, percentual. {@code papeis}
     * nulo = todo cargo que usa a fonte vê a coluna.
     */
    record Coluna(String id, String rotulo, String tipo, Set<String> papeis) {}

    /**
     * {@code ordem} diz a ordem dos parâmetros do SQL: T = empresa, D = início, A = fim (o limite
     * de linhas vai sempre no fim). Fonte com D usa período.
     */
    private record Fonte(
            String nome,
            String rotulo,
            Set<String> papeis,
            boolean usaPeriodo,
            String sql,
            String ordem,
            List<Coluna> colunas) {}

    private static Coluna c(String id, String rotulo, String tipo) {
        return new Coluna(id, rotulo, tipo, null);
    }

    /** Coluna de custo ou resultado: só para quem vê o financeiro. */
    private static Coluna f(String id, String rotulo, String tipo) {
        return new Coluna(id, rotulo, tipo, FINANCEIRO);
    }

    /** Dado do cadastro do cliente: só para quem já vê o cadastro de clientes. */
    private static Coluna dc(String id, String rotulo, String tipo) {
        return new Coluna(id, rotulo, tipo, RadarClientes.VEEM);
    }

    private static final String CATEGORIA =
            " left join radar_categoria cat on cat.tenant_id=pr.tenant_id and"
                    + " cat.id=pr.categoria_id";

    private static final List<Fonte> FONTES =
            List.of(
                    new Fonte(
                            "pedidos",
                            "Pedidos",
                            TODOS,
                            true,
                            "select p.numero, p.numero_externo, to_char(p.criado_em,'YYYY-MM-DD')"
                                    + " data, p.canal marketplace, initcap(lower(p.estado))"
                                    + " situacao, p.cliente cliente, cl.cidade,"
                                    + " cl.uf, pr.sku, pr.nome produto, cat.nome categoria,"
                                    + " pr.marca, p.quantidade, p.preco preco_unitario,"
                                    + " p.preco*p.quantidade receita_bruta, p.desconto,"
                                    + " p.comissao, p.frete, p.imposto, p.ads, p.embalagem,"
                                    + " p.custo_unitario*p.quantidade custo_produtos,"
                                    + " coalesce(rs.resultado,0) resultado,"
                                    + " pm.nome promocao, p.nota_fiscal_numero,"
                                    + " to_char(p.data_faturamento,'YYYY-MM-DD') data_faturamento,"
                                    + " case when p.conciliado then 'Sim' else 'Não' end conciliado"
                                    + " from radar_pedido p join radar_produto pr on"
                                    + " pr.tenant_id=p.tenant_id and pr.id=p.produto_id"
                                    + CATEGORIA
                                    + " left join radar_cliente cl on cl.tenant_id=p.tenant_id"
                                    + " and cl.id=p.cliente_id left join radar_promocao pm on"
                                    + " pm.tenant_id=p.tenant_id and pm.id=p.promocao_id"
                                    + " left join (select pedido_id, sum(valor) resultado from"
                                    + " radar_lancamento where tenant_id=? and pedido_id is not"
                                    + " null group by pedido_id) rs on rs.pedido_id=p.id where"
                                    + " p.tenant_id=? and p.criado_em>=? and p.criado_em<? order"
                                    + " by p.criado_em desc limit ?",
                            "TTDA",
                            List.of(
                                    c("numero", "Pedido", "texto"),
                                    c("numero_externo", "Nº no marketplace", "texto"),
                                    c("data", "Data", "data"),
                                    c("marketplace", "Marketplace", "texto"),
                                    c("situacao", "Situação", "texto"),
                                    c("cliente", "Cliente", "texto"),
                                    dc("cidade", "Cidade", "texto"),
                                    dc("uf", "UF", "texto"),
                                    c("sku", "SKU", "texto"),
                                    c("produto", "Produto", "texto"),
                                    c("categoria", "Categoria", "texto"),
                                    c("marca", "Marca", "texto"),
                                    c("quantidade", "Quantidade", "inteiro"),
                                    c("preco_unitario", "Preço unitário", "dinheiro"),
                                    c("receita_bruta", "Receita bruta", "dinheiro"),
                                    f("desconto", "Desconto", "dinheiro"),
                                    f("comissao", "Comissão", "dinheiro"),
                                    f("frete", "Frete", "dinheiro"),
                                    f("imposto", "Imposto", "dinheiro"),
                                    f("ads", "Anúncios pagos", "dinheiro"),
                                    f("embalagem", "Embalagem", "dinheiro"),
                                    f("custo_produtos", "Custo dos produtos", "dinheiro"),
                                    f("resultado", "Lucro", "dinheiro"),
                                    f("margem", "Margem", "percentual"),
                                    c("promocao", "Promoção", "texto"),
                                    c("nota_fiscal_numero", "Nota fiscal", "texto"),
                                    c("data_faturamento", "Data de faturamento", "data"),
                                    c("conciliado", "Conciliado", "texto"))),
                    new Fonte(
                            "produtos",
                            "Produtos e estoque",
                            TODOS,
                            true,
                            "select pr.sku, pr.nome produto, case pr.tipo when 'VARIACAO' then"
                                    + " 'Com variação' when 'KIT' then 'Kit' else 'Simples' end"
                                    + " tipo, cat.nome categoria, pr.marca, case when"
                                    + " pr.permite_venda then 'Ativo' else 'Inativo' end situacao,"
                                    + " case when pr.incompleto then 'Incompleto' else 'Completo'"
                                    + " end cadastro, pr.preco, pr.custo, pr.fisico, pr.reservado,"
                                    + " pr.fisico-pr.reservado disponivel, pr.minimo, case when"
                                    + " pr.minimo is not null and pr.fisico-pr.reservado<pr.minimo"
                                    + " then 'Sim' else 'Não' end abaixo_minimo,"
                                    + " pr.custo*pr.fisico valor_estoque, (select count(*) from"
                                    + " radar_anuncio a where a.tenant_id=pr.tenant_id and"
                                    + " a.produto_id=pr.id) anuncios, coalesce(v.unidades,0)"
                                    + " vendidos, coalesce(v.receita,0) receita_periodo, v.ultima"
                                    + " ultima_venda from radar_produto pr"
                                    + CATEGORIA
                                    + " left join (select produto_id, sum(quantidade) unidades,"
                                    + " sum(preco*quantidade) receita,"
                                    + " to_char(max(criado_em),'YYYY-MM-DD') ultima from"
                                    + " radar_pedido where tenant_id=? and estado not in"
                                    + " ('CANCELADO','DEVOLVIDO') and criado_em>=? and"
                                    + " criado_em<? group by produto_id) v on v.produto_id=pr.id"
                                    + " where pr.tenant_id=? and pr.excluido_em is null order by"
                                    + " pr.sku limit ?",
                            "TDAT",
                            List.of(
                                    c("sku", "SKU", "texto"),
                                    c("produto", "Produto", "texto"),
                                    c("tipo", "Tipo", "texto"),
                                    c("categoria", "Categoria", "texto"),
                                    c("marca", "Marca", "texto"),
                                    c("situacao", "Situação", "texto"),
                                    c("cadastro", "Cadastro", "texto"),
                                    c("preco", "Preço", "dinheiro"),
                                    f("custo", "Custo", "dinheiro"),
                                    f("margem_bruta", "Margem bruta", "percentual"),
                                    c("fisico", "Estoque físico", "inteiro"),
                                    c("reservado", "Reservado", "inteiro"),
                                    c("disponivel", "Disponível", "inteiro"),
                                    c("minimo", "Estoque mínimo", "inteiro"),
                                    c("abaixo_minimo", "Abaixo do mínimo", "texto"),
                                    f("valor_estoque", "Valor em estoque (custo)", "dinheiro"),
                                    c("anuncios", "Anúncios", "inteiro"),
                                    c("vendidos", "Vendidos no período", "inteiro"),
                                    c("receita_periodo", "Receita no período", "dinheiro"),
                                    c("ultima_venda", "Última venda", "data"))),
                    new Fonte(
                            "movimentos",
                            "Movimentos de estoque",
                            TODOS,
                            true,
                            "select to_char(m.criado_em,'YYYY-MM-DD') data, pr.sku, pr.nome"
                                    + " produto, initcap(lower(m.tipo)) tipo, m.fisico_delta"
                                    + " quantidade, m.reserva_delta reserva, m.motivo, p.numero"
                                    + " pedido from radar_movimento m join radar_produto pr on"
                                    + " pr.tenant_id=m.tenant_id and pr.id=m.produto_id left join"
                                    + " radar_pedido p on p.tenant_id=m.tenant_id and"
                                    + " p.id=m.pedido_id where m.tenant_id=? and m.criado_em>=?"
                                    + " and m.criado_em<? order by m.criado_em desc limit ?",
                            "TDA",
                            List.of(
                                    c("data", "Data", "data"),
                                    c("sku", "SKU", "texto"),
                                    c("produto", "Produto", "texto"),
                                    c("tipo", "Movimento", "texto"),
                                    c("quantidade", "Estoque físico (+/−)", "inteiro"),
                                    c("reserva", "Reserva (+/−)", "inteiro"),
                                    c("motivo", "Motivo", "texto"),
                                    c("pedido", "Pedido", "texto"))),
                    new Fonte(
                            "compras",
                            "Compras",
                            Set.of("DONO", "GESTOR", "ESTOQUE", "FINANCEIRO"),
                            true,
                            "select to_char(r.criado_em,'YYYY-MM-DD') data, pr.sku, pr.nome"
                                    + " produto, r.dados->>'fornecedor' fornecedor,"
                                    + " case when r.dados->>'quantidade' ~ '^[0-9]{1,9}$' then"
                                    + " (r.dados->>'quantidade')::int end quantidade, case when"
                                    + " r.dados->>'custo_unitario' ~ '^[0-9]{1,9}([.][0-9]{1,2})?$'"
                                    + " then (r.dados->>'custo_unitario')::numeric(18,2) end"
                                    + " custo_unitario, case when r.dados->>'quantidade' ~"
                                    + " '^[0-9]{1,9}$' and r.dados->>'custo_unitario' ~"
                                    + " '^[0-9]{1,9}([.][0-9]{1,2})?$' then"
                                    + " ((r.dados->>'quantidade')::numeric *"
                                    + " (r.dados->>'custo_unitario')::numeric)::numeric(18,2) end"
                                    + " total, case when r.dados->>'recebida_em' is null then"
                                    + " 'Pendente' else 'Recebida' end situacao,"
                                    + " left(r.dados->>'recebida_em',10) recebida_em from"
                                    + " radar_registro r left join radar_produto pr on"
                                    + " pr.tenant_id=r.tenant_id and"
                                    + " pr.id=case when r.dados->>'produto_id' ~"
                                    + " '^[0-9a-f-]{36}$' then (r.dados->>'produto_id')::uuid end"
                                    + " where r.tenant_id=?"
                                    + " and r.tipo='COMPRA' and r.criado_em>=? and r.criado_em<?"
                                    + " order by r.criado_em desc limit ?",
                            "TDA",
                            List.of(
                                    c("data", "Data", "data"),
                                    c("sku", "SKU", "texto"),
                                    c("produto", "Produto", "texto"),
                                    c("fornecedor", "Fornecedor", "texto"),
                                    c("quantidade", "Quantidade", "inteiro"),
                                    c("custo_unitario", "Custo unitário", "dinheiro"),
                                    c("total", "Total", "dinheiro"),
                                    c("situacao", "Situação", "texto"),
                                    c("recebida_em", "Recebida em", "data"))),
                    new Fonte(
                            "contas",
                            "Contas a pagar e a receber",
                            FINANCEIRO,
                            true,
                            "select t.descricao, case t.tipo when 'PAGAR' then 'A pagar' else"
                                    + " 'A receber' end tipo, t.valor,"
                                    + " to_char(t.vencimento,'YYYY-MM-DD') vencimento, case when"
                                    + " t.estado='BAIXADO' then 'Pago/recebido' when"
                                    + " t.vencimento<current_date then 'Atrasado' else 'Em"
                                    + " aberto' end situacao, p.numero pedido,"
                                    + " to_char(t.criado_em,'YYYY-MM-DD') lancado_em from"
                                    + " radar_titulo t left join radar_pedido p on"
                                    + " p.tenant_id=t.tenant_id and p.id=t.pedido_id where"
                                    + " t.tenant_id=? and t.vencimento>=? and t.vencimento<?"
                                    + " order by t.vencimento limit ?",
                            "TDA",
                            List.of(
                                    c("descricao", "Descrição", "texto"),
                                    c("tipo", "Tipo", "texto"),
                                    c("valor", "Valor", "dinheiro"),
                                    c("vencimento", "Vencimento", "data"),
                                    c("situacao", "Situação", "texto"),
                                    c("pedido", "Pedido", "texto"),
                                    c("lancado_em", "Lançada em", "data"))),
                    new Fonte(
                            "lancamentos",
                            "Lançamentos (razão)",
                            FINANCEIRO,
                            true,
                            "select to_char(l.criado_em,'YYYY-MM-DD') data, case l.tipo when"
                                    + " 'RECEITA' then 'Receita' when 'CMV' then 'Custo dos"
                                    + " produtos' when 'COMISSAO' then 'Comissão' when 'FRETE'"
                                    + " then 'Frete' when 'IMPOSTO' then 'Imposto' when 'ADS' then"
                                    + " 'Anúncios pagos' when 'EMBALAGEM' then 'Embalagem' when"
                                    + " 'DESCONTO' then 'Desconto' when 'DESPESA' then 'Despesa'"
                                    + " else initcap(lower(l.tipo)) end tipo, l.valor, l.fonte"
                                    + " descricao, p.numero pedido, p.canal marketplace from"
                                    + " radar_lancamento l left join radar_pedido p on"
                                    + " p.tenant_id=l.tenant_id and p.id=l.pedido_id where"
                                    + " l.tenant_id=? and l.criado_em>=? and l.criado_em<? order"
                                    + " by l.criado_em desc limit ?",
                            "TDA",
                            List.of(
                                    c("data", "Data", "data"),
                                    c("tipo", "Tipo", "texto"),
                                    c("valor", "Valor", "dinheiro"),
                                    c("descricao", "Descrição", "texto"),
                                    c("pedido", "Pedido", "texto"),
                                    c("marketplace", "Marketplace", "texto"))),
                    new Fonte(
                            "anuncios",
                            "Anúncios",
                            TODOS,
                            false,
                            "select to_char(a.criado_em,'YYYY-MM-DD') criado_em,"
                                    + " coalesce(lo.nome,'Sem loja') loja, a.canal marketplace,"
                                    + " a.titulo, pr.sku, pr.nome produto, cat.nome categoria,"
                                    + " a.preco, a.estoque quantidade, case a.estado when 'PRONTO'"
                                    + " then 'Pronto para publicar' else initcap(lower(a.estado))"
                                    + " end situacao_radar, case a.situacao_ecommerce when"
                                    + " 'NAO_PUBLICADO' then 'Não publicado' else"
                                    + " initcap(lower(a.situacao_ecommerce)) end"
                                    + " situacao_marketplace from radar_anuncio a join"
                                    + " radar_produto pr on pr.tenant_id=a.tenant_id and"
                                    + " pr.id=a.produto_id"
                                    + CATEGORIA
                                    + " left join radar_loja lo on lo.tenant_id=a.tenant_id and"
                                    + " lo.id=a.loja_id where a.tenant_id=? order by a.criado_em"
                                    + " desc limit ?",
                            "T",
                            List.of(
                                    c("criado_em", "Criado em", "data"),
                                    c("loja", "Loja", "texto"),
                                    c("marketplace", "Marketplace", "texto"),
                                    c("titulo", "Título", "texto"),
                                    c("sku", "SKU", "texto"),
                                    c("produto", "Produto", "texto"),
                                    c("categoria", "Categoria", "texto"),
                                    c("preco", "Preço", "dinheiro"),
                                    c("quantidade", "Quantidade anunciada", "inteiro"),
                                    c("situacao_radar", "Situação no Radar", "texto"),
                                    c("situacao_marketplace", "Situação no marketplace", "texto"))),
                    new Fonte(
                            "atendimento",
                            "Atendimento",
                            Set.of("DONO", "GESTOR", "ATENDIMENTO", "ANALISTA"),
                            true,
                            "select to_char(r.criado_em,'YYYY-MM-DD') data, r.dados->>'cliente'"
                                    + " cliente, r.dados->>'canal' canal, left(r.dados->>'mensagem',500)"
                                    + " mensagem from radar_registro r where r.tenant_id=? and"
                                    + " r.tipo='MENSAGEM' and r.criado_em>=? and r.criado_em<?"
                                    + " order by r.criado_em desc limit ?",
                            "TDA",
                            List.of(
                                    c("data", "Data", "data"),
                                    c("cliente", "Cliente", "texto"),
                                    c("canal", "Canal", "texto"),
                                    c("mensagem", "Mensagem", "texto"))));

    private final JdbcTemplate db;
    private final ObjectMapper json;

    public RadarFontes(JdbcTemplate db, ObjectMapper json) {
        this.db = db;
        this.json = json;
    }

    /** As fontes que este cargo pode usar, com as colunas que ele pode ver. */
    List<Map<String, Object>> catalogo(String papel) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Fonte fonte : FONTES) {
            if (!fonte.papeis().contains(papel)) continue;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("nome", fonte.nome());
            m.put("rotulo", fonte.rotulo());
            m.put("usaPeriodo", fonte.usaPeriodo());
            m.put("colunas", colunas(fonte, papel));
            out.add(m);
        }
        return out;
    }

    /** Linhas de uma fonte no período (de a até, inclusive), já sem o que o cargo não vê. */
    @Transactional(readOnly = true)
    public Map<String, Object> linhas(String papel, String nome, String deTexto, String ateTexto) {
        Fonte fonte =
                FONTES.stream()
                        .filter(x -> x.nome().equals(nome))
                        .findFirst()
                        .orElseThrow(
                                () ->
                                        new ResponseStatusException(
                                                HttpStatus.NOT_FOUND, "Relatório não encontrado."));
        if (!fonte.papeis().contains(papel))
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "Seu cargo não permite este relatório.");
        LocalDate ate = ateTexto == null || ateTexto.isBlank() ? LocalDate.now() : data(ateTexto);
        LocalDate de = deTexto == null || deTexto.isBlank() ? ate.minusDays(29) : data(deTexto);
        if (de.isAfter(ate)) erro("A data inicial é posterior à final.");
        if (ChronoUnit.DAYS.between(de, ate) >= DIAS_MAXIMOS)
            erro("Escolha um período de até um ano.");

        List<Object> params = new ArrayList<>();
        for (char x : fonte.ordem().toCharArray())
            params.add(x == 'T' ? tenant() : x == 'D' ? de : ate.plusDays(1));
        params.add(LIMITE + 1);
        // Relatório pesado não pode prender o banco de todas as empresas.
        db.execute("set local statement_timeout = '20s'");
        var brutas = db.queryForList(fonte.sql(), params.toArray());

        List<Coluna> visiveis = colunasVisiveis(fonte, papel);
        List<Map<String, Object>> linhas = new ArrayList<>();
        for (var b : brutas.subList(0, Math.min(LIMITE, brutas.size()))) {
            Map<String, Object> l = new LinkedHashMap<>();
            for (Coluna col : visiveis) l.put(col.id(), valor(col, b));
            linhas.add(l);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("fonte", fonte.nome());
        out.put("periodo", Map.of("de", de.toString(), "ate", ate.toString()));
        out.put("colunas", colunas(fonte, papel));
        out.put("linhas", linhas);
        out.put("cortado", brutas.size() > LIMITE);
        out.put("limite", LIMITE);
        return out;
    }

    private List<Coluna> colunasVisiveis(Fonte fonte, String papel) {
        return fonte.colunas().stream()
                .filter(x -> x.papeis() == null || x.papeis().contains(papel))
                .toList();
    }

    private List<Map<String, Object>> colunas(Fonte fonte, String papel) {
        return colunasVisiveis(fonte, papel).stream()
                .map(x -> Map.<String, Object>of("id", x.id(), "rotulo", x.rotulo(), "tipo", x.tipo()))
                .toList();
    }

    /** Valor de uma coluna; margens são calculadas aqui, a partir das colunas de dinheiro. */
    private static Object valor(Coluna col, Map<String, Object> b) {
        Object v =
                switch (col.id()) {
                    case "margem" -> percentual(b.get("resultado"), b.get("receita_bruta"));
                    case "margem_bruta" ->
                            b.get("preco") instanceof BigDecimal p && b.get("custo") instanceof BigDecimal cu
                                    ? percentual(p.subtract(cu), p)
                                    : null;
                    default -> b.get(col.id());
                };
        if (v instanceof BigDecimal d && col.tipo().equals("dinheiro"))
            return d.setScale(2, RoundingMode.HALF_UP).toPlainString();
        if (v instanceof Number n && col.tipo().equals("inteiro")) return n.longValue();
        return v;
    }

    private static String percentual(Object parte, Object todo) {
        if (!(parte instanceof BigDecimal p) || !(todo instanceof BigDecimal t) || t.signum() == 0)
            return null;
        return p.multiply(new BigDecimal("100")).divide(t, 1, RoundingMode.HALF_UP).toPlainString();
    }

    // ------------------------------------------------------------ relatórios salvos --

    /** Relatórios salvos da empresa que este cargo consegue abrir ("Meus relatórios"). */
    List<Object> modelos(String papel) {
        return lerModelos().stream()
                .filter(
                        x -> {
                            Object cfg = ((Map<?, ?>) x).get("config");
                            Object fonte = cfg instanceof Map<?, ?> m ? m.get("fonte") : null;
                            return FONTES.stream()
                                    .anyMatch(
                                            f -> f.nome().equals(fonte) && f.papeis().contains(papel));
                        })
                .toList();
    }

    Map<String, Object> executar(String op, JsonNode n, String papel, UUID usuario) {
        List<Object> modelos = new ArrayList<>(lerModelos());
        Map<String, Object> r = new LinkedHashMap<>();
        if (op.equals("relatorio_salvar")) {
            String nome = texto(n, "nome", 80);
            JsonNode config = n.path("config");
            validarConfig(config);
            String fonte = config.path("fonte").asText("");
            if (FONTES.stream().noneMatch(x -> x.nome().equals(fonte) && x.papeis().contains(papel)))
                erro("Escolha uma fonte de dados que o seu cargo pode usar.");
            if (modelos.size() >= MAX_MODELOS) erro("Limite de " + MAX_MODELOS + " relatórios salvos.");
            long meus =
                    modelos.stream()
                            .filter(x -> usuario.toString().equals(((Map<?, ?>) x).get("autor")))
                            .count();
            if (meus >= MAX_POR_PESSOA)
                erro("Cada pessoa salva até " + MAX_POR_PESSOA + " relatórios. Apague um antes.");
            Map<String, Object> m = new LinkedHashMap<>();
            String id = UUID.randomUUID().toString();
            m.put("id", id);
            m.put("nome", nome);
            m.put("config", json.convertValue(config, Map.class));
            m.put("autor", usuario.toString());
            m.put("criado_em", Instant.now().toString());
            modelos.add(m);
            r.put("id", id);
            r.put("mensagem", "Relatório \"" + nome + "\" salvo em Meus relatórios.");
        } else {
            String id = id(n, "id").toString();
            var alvo =
                    modelos.stream()
                            .filter(x -> id.equals(((Map<?, ?>) x).get("id")))
                            .findFirst()
                            .orElseThrow(
                                    () ->
                                            new ResponseStatusException(
                                                    HttpStatus.NOT_FOUND,
                                                    "Relatório salvo não encontrado."));
            boolean autor = usuario.toString().equals(((Map<?, ?>) alvo).get("autor"));
            if (!autor && !Set.of("DONO", "GESTOR").contains(papel))
                throw new ResponseStatusException(
                        HttpStatus.FORBIDDEN, "Só quem salvou, o dono ou o gestor apagam.");
            modelos.remove(alvo);
            r.put("mensagem", "Relatório salvo removido.");
        }
        try {
            db.update(
                    "insert into radar_configuracao(tenant_id,chave,valor) values(?,'relatorios',"
                            + "?::jsonb) on conflict (tenant_id,chave) do update set"
                            + " valor=excluded.valor, atualizado_em=now()",
                    tenant(),
                    json.writeValueAsString(Map.of("modelos", modelos)));
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
        return r;
    }

    /** Só o formato que a tela usa, com limites: nada de texto livre grande guardado aqui. */
    private static void validarConfig(JsonNode c) {
        boolean ok = c.isObject() && c.toString().length() <= 2000;
        if (ok) {
            JsonNode colunas = c.path("colunas"), filtros = c.path("filtros");
            ok =
                    colunas.isArray()
                            && colunas.size() <= 40
                            && (filtros.isMissingNode() || (filtros.isArray() && filtros.size() <= 20));
            for (JsonNode col : colunas) ok &= col.isTextual() && col.asText().matches("[a-z_]{1,40}");
            for (JsonNode f : filtros)
                ok &=
                        f.path("coluna").asText("").matches("[a-z_]{1,40}")
                                && f.path("op").asText("").matches("[a-z]{1,12}")
                                && f.path("valor").asText("").length() <= 200;
            JsonNode dias = c.path("dias");
            ok &= dias.isNull() || dias.isMissingNode() || (dias.isInt() && dias.asInt() >= 1 && dias.asInt() <= 366);
            JsonNode adiante = c.path("adiante");
            ok &= adiante.isMissingNode() || (adiante.isInt() && adiante.asInt() >= 0 && adiante.asInt() <= 90);
            for (String d : List.of("de", "ate"))
                ok &= c.path(d).isMissingNode() || c.path(d).asText().matches("\\d{4}-\\d{2}-\\d{2}");
        }
        if (!ok) erro("Configuração do relatório inválida.");
    }

    @SuppressWarnings("unchecked")
    private List<Object> lerModelos() {
        var linhas =
                db.queryForList(
                        "select valor::text from radar_configuracao where tenant_id=? and"
                                + " chave='relatorios'",
                        String.class,
                        tenant());
        if (linhas.isEmpty()) return List.of();
        try {
            Object m = json.readValue(linhas.getFirst(), Map.class).get("modelos");
            return m instanceof List<?> l ? (List<Object>) l : List.of();
        } catch (Exception e) {
            return List.of();
        }
    }

    private static LocalDate data(String texto) {
        try {
            return LocalDate.parse(texto);
        } catch (DateTimeParseException e) {
            erro("Data inválida.");
            return null;
        }
    }

    private static UUID tenant() {
        return ContextoTenant.atual();
    }
}
