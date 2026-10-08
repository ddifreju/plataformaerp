package com.plataforma.radar;

import static com.plataforma.radar.RadarEntrada.erro;
import static com.plataforma.radar.RadarEntrada.inteiroOpcional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.plataforma.comum.tenant.ContextoTenant;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Consumer;

import javax.imageio.ImageIO;

/**
 * Dados de exemplo para a empresa de demonstração: lojas, cadastros, anúncios, promoções,
 * atendimento, compras, financeiro e uns 60 pedidos dos últimos 90 dias. Tudo passa pelas mesmas
 * operações da tela (o {@code executar} do {@link RadarService}), então estoque, custo médio,
 * lançamentos e contas ficam coerentes como numa operação de verdade.
 *
 * <p>Só roda em empresa de demonstração (slug "demo-…", como o infra/dados-demo.sql): numa empresa
 * de cliente, dado inventado misturado ao real quebraria a regra 5. É dividido em etapas curtas
 * porque o servidor gratuito fica longe do banco; a tela chama uma etapa por vez. Determinístico:
 * nada de sorteio, carregar de novo noutra empresa dá o mesmo conjunto.
 *
 * <p>Nada aqui finge integração: lojas ficam "aguardando conexão", anúncios ficam prontos e não
 * publicados, e não há nota fiscal (uma nota fictícia poderia ser confundida com uma real).
 */
@Service
public class RadarExemplo {

    /** SKU do primeiro produto de exemplo (também usado no kit e nas referências). */
    private static final String MARCA = "EX-CORT-LINHO";
    private static final int TOTAL_PEDIDOS = 60;
    private static final int PEDIDOS_POR_PASSO = 2;
    private static final int PRODUTOS_POR_PASSO = 2;

    private static final String DESCRICAO =
            " Produto fictício dos dados de exemplo do Radar, criado só para testes. Acabamento"
                    + " caprichado, fácil de instalar e de limpar, combina com sala, quarto e"
                    + " escritório. Acompanha manual simples de instalação e garantia de noventa"
                    + " dias contra defeito de fabricação.";

    /** Produto simples: sku, nome, categoria, NCM, custo, preço, estoque, peso (kg), medidas. */
    private record Produto(
            String sku,
            String nome,
            String categoria,
            String ncm,
            String custo,
            String preco,
            int saldo,
            String peso,
            int largura,
            int altura,
            int comprimento) {}

    private static final List<Produto> PRODUTOS =
            List.of(
                    new Produto(
                            MARCA,
                            "Cortina de linho natural 2,80 x 2,30 m",
                            "Cortinas",
                            "63039200",
                            "85.00",
                            "189.90",
                            40,
                            "1.200",
                            30,
                            10,
                            40),
                    new Produto(
                            "EX-CORT-VOIL",
                            "Cortina voil branca 3,00 x 2,50 m",
                            "Cortinas",
                            "63039200",
                            "39.00",
                            "99.90",
                            60,
                            "0.800",
                            30,
                            8,
                            35),
                    new Produto(
                            "EX-PERS-ROLO",
                            "Persiana rolô blackout 1,20 m",
                            "Persianas",
                            "39253000",
                            "62.00",
                            "159.90",
                            30,
                            "2.100",
                            130,
                            10,
                            10),
                    new Produto(
                            "EX-PERS-ROMA",
                            "Persiana romana cinza 1,40 m",
                            "Persianas",
                            "63039200",
                            "78.00",
                            "189.90",
                            25,
                            "1.800",
                            145,
                            10,
                            12),
                    new Produto(
                            "EX-TRIL-2M",
                            "Trilho suíço duplo de 2 metros",
                            "Trilhos e varões",
                            "83024200",
                            "22.00",
                            "59.90",
                            80,
                            "0.900",
                            205,
                            6,
                            6),
                    new Produto(
                            "EX-VARAO-INOX",
                            "Varão de inox 1,5 m com ponteiras",
                            "Trilhos e varões",
                            "83024200",
                            "35.00",
                            "89.90",
                            25,
                            "1.100",
                            155,
                            6,
                            6),
                    new Produto(
                            "EX-ALMO-VEL",
                            "Capa de almofada de veludo 45 x 45 cm",
                            "Almofadas",
                            "63049900",
                            "12.00",
                            "39.90",
                            120,
                            "0.200",
                            25,
                            3,
                            25),
                    // Estoque baixo de propósito: aparece em "estoque mínimo".
                    new Produto(
                            "EX-ALMO-LINHO",
                            "Capa de almofada de linho 50 x 50 cm",
                            "Almofadas",
                            "63049900",
                            "15.00",
                            "44.90",
                            4,
                            "0.250",
                            28,
                            3,
                            28));

    private static final String[] CLIENTES = {
        "Ana Paula Ribeiro (exemplo)",
        "Bruno Carvalho (exemplo)",
        "Carla Mendes (exemplo)",
        "Diego Fernandes (exemplo)",
        "Eduarda Lima (exemplo)",
        "Felipe Moraes (exemplo)",
        "Gabriela Rocha (exemplo)",
        "Henrique Alves (exemplo)",
        "Isabela Duarte (exemplo)",
        "João Pedro Nunes (exemplo)",
        "Larissa Teixeira (exemplo)",
        "Marcos Vieira (exemplo)"
    };
    private static final String[] FORNECEDORES = {
        "Tecidos Aurora (exemplo)", "Metalúrgica Trilhar (exemplo)"
    };

    /** Lojas de exemplo: nome e marketplace. Duas no Mercado Livre, como pode acontecer. */
    private static final String[][] LOJAS = {
        {"Exemplo · Mercado Livre", "Mercado Livre"},
        {"Exemplo · ML Outlet", "Mercado Livre"},
        {"Exemplo · Shopee", "Shopee"},
        {"Exemplo · TikTok Shop", "TikTok Shop"},
        {"Exemplo · AliExpress", "AliExpress"}
    };

    /** Taxa de comissão e frete pago pelo vendedor, por marketplace (números de exemplo). */
    private static final Map<String, String[]> TAXAS =
            Map.of(
                    "Mercado Livre", new String[] {"0.16", "18.90"},
                    "Shopee", new String[] {"0.20", "0.00"},
                    "TikTok Shop", new String[] {"0.12", "9.90"},
                    "AliExpress", new String[] {"0.10", "12.00"});

    /** Ordem dos canais dos pedidos: mais Mercado Livre e Shopee, como costuma ser. */
    private static final List<String> MARKETPLACES =
            List.of(
                    "Mercado Livre",
                    "Mercado Livre",
                    "Shopee",
                    "TikTok Shop",
                    "Mercado Livre",
                    "Shopee",
                    "AliExpress");

    /** Um passo curto do carregamento (o servidor gratuito fica longe do banco). */
    private record Passo(String descricao, Consumer<Op> rodar) {}

    /** Todos os passos, em ordem. Cada um cabe numa chamada de poucos segundos. */
    private List<Passo> plano() {
        List<Passo> p = new ArrayList<>();
        p.add(new Passo("lojas e embalagens", this::lojas));
        p.add(new Passo("clientes e fornecedores", this::contatos));
        for (int i = 0; i < PRODUTOS.size(); i += PRODUTOS_POR_PASSO) {
            int de = i;
            p.add(new Passo("produtos", op -> produtos(op, de, de + PRODUTOS_POR_PASSO)));
        }
        p.add(new Passo("produto com variação", this::variacao));
        p.add(new Passo("kit e produtos incompletos", this::kitEIncompletos));
        p.add(new Passo("categorias ligadas aos marketplaces", this::vinculos));
        // 13 produtos prontos para anunciar (8 simples, 4 variações e o kit).
        for (int i = 0; i < 13; i += 2) {
            int de = i;
            p.add(new Passo("anúncios", op -> anuncios(op, de, de + 2)));
        }
        p.add(new Passo("promoções e proposta de preço", this::promocoes));
        p.add(new Passo("atendimento e concorrentes", this::atendimentoEMercado));
        p.add(new Passo("compras", this::compras));
        p.add(new Passo("contas a pagar e despesas", this::financeiro));
        for (int leva = 0; leva < TOTAL_PEDIDOS / PEDIDOS_POR_PASSO; leva++) {
            int l = leva;
            p.add(new Passo("pedidos", op -> pedidos(op, l)));
        }
        return p;
    }

    /** Quantos passos esta versão tem (a tela mostra o progresso). */
    int etapas() {
        return plano().size();
    }

    private final JdbcTemplate db;
    private final ObjectMapper json;
    private final RadarClientes clientes;

    public RadarExemplo(JdbcTemplate db, ObjectMapper json, RadarClientes clientes) {
        this.db = db;
        this.json = json;
        this.clientes = clientes;
    }

    /** Para a tela: se esta empresa pode receber os dados de exemplo e até onde já foi. */
    Map<String, Object> estado() {
        boolean permitido = empresaDeDemonstracao();
        return Map.of(
                "permitido", permitido, "feitas", permitido ? feitas() : 0, "total", etapas());
    }

    /**
     * Roda o próximo passo. {@code executar} é a mesma execução de operação dos comandos da tela;
     * cada passo é um comando só (uma transação e uma linha na auditoria). O progresso fica salvo:
     * se a tela fechar no meio, continua de onde parou, e um passo nunca roda duas vezes.
     */
    Map<String, Object> etapa(JsonNode n, BiFunction<String, JsonNode, Map<String, Object>> executar) {
        if (!empresaDeDemonstracao())
            erro("Os dados de exemplo só podem ser carregados na empresa de demonstração.");
        var plano = plano();
        int feitas = feitas();
        if (feitas >= plano.size()) erro("Os dados de exemplo já foram carregados nesta empresa.");
        Integer etapa = inteiroOpcional(n, "etapa", 1, plano.size());
        if (etapa == null || etapa != feitas + 1)
            erro("O próximo passo dos dados de exemplo é o " + (feitas + 1) + ".");
        // Segunda trava, além do slug: empresa que já tem venda ou lançamento é operação de
        // verdade, e dado inventado ali não teria volta (lançamentos não se apagam).
        if (etapa == 1 && temOperacao())
            erro(
                    "Esta empresa já tem pedidos ou lançamentos: os dados de exemplo só entram"
                            + " numa demonstração vazia.");
        Op op = (nome, campos) -> executar.apply(nome, json.valueToTree(campos));
        Passo passo = plano.get(etapa - 1);
        passo.rodar().accept(op);
        db.update(
                "insert into radar_configuracao(tenant_id,chave,valor) values(?,'exemplo',?::jsonb)"
                        + " on conflict (tenant_id,chave) do update set valor=excluded.valor,"
                        + "atualizado_em=now()",
                tenant(),
                "{\"feitas\":" + etapa + "}");
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("etapa", etapa);
        r.put("total", plano.size());
        r.put(
                "mensagem",
                etapa == plano.size()
                        ? "Dados de exemplo carregados: produtos, lojas, anúncios, pedidos, compras,"
                                + " financeiro, promoções e atendimento."
                        : "Passo " + etapa + " de " + plano.size() + ": " + passo.descricao() + ".");
        return r;
    }

    @FunctionalInterface
    private interface Op {
        Map<String, Object> rodar(String nome, Map<String, Object> campos);
    }

    // ------------------------------------------------------------------ cadastros --

    private void lojas(Op op) {
        for (String[] l : LOJAS) op.rodar("loja_salvar", m("marketplace", l[1], "nome", l[0]));
        op.rodar("embalagens_sugeridas", m());
    }

    private void contatos(Op op) {
        // Clientes e fornecedores entram como cadastro incompleto (o mesmo caminho do cliente
        // criado pelo pedido): sem CPF/CNPJ de propósito, porque um documento válido inventado
        // pode ser de alguém de verdade.
        for (String c : CLIENTES) clientes.clienteDoPedido(c, "");
        List<String> fornecedores = new ArrayList<>();
        for (String f : FORNECEDORES) fornecedores.add(clientes.clienteDoPedido(f, "").toString());
        op.rodar(
                "clientes_lote",
                m("acao", "TIPO_CONTATO", "ids", fornecedores, "tipos_contato", List.of("FORNECEDOR")));
    }

    private void produtos(Op op, int de, int ate) {
        for (int i = de; i < Math.min(ate, PRODUTOS.size()); i++) {
            Produto p = PRODUTOS.get(i);
            var campos = completo(p.sku(), p.nome(), p.categoria(), p.ncm(), p.custo(), p.preco());
            campos.put("saldo", String.valueOf(p.saldo()));
            campos.put("peso_bruto_kg", p.peso());
            campos.put("largura_cm", String.valueOf(p.largura()));
            campos.put("altura_cm", String.valueOf(p.altura()));
            campos.put("comprimento_cm", String.valueOf(p.comprimento()));
            campos.put("minimo", "10");
            imagem((UUID) op.rodar("produto_salvar", campos).get("id"), i);
        }
    }

    private void variacao(Op op) {
        // Com variação: cada combinação de cor e tamanho é vendida e estocada separada.
        var pai =
                completo(
                        "EX-CORT-BLACK",
                        "Cortina blackout com ilhós",
                        "Cortinas",
                        "63039200",
                        "70.00",
                        "169.90");
        pai.put("tipo", "VARIACAO");
        pai.put("peso_bruto_kg", "1.500");
        pai.put("largura_cm", "30");
        pai.put("altura_cm", "12");
        pai.put("comprimento_cm", "40");
        pai.put("tipos_variacao", List.of("Cor", "Tamanho"));
        List<Object> grade = new ArrayList<>();
        for (String[] cor : new String[][] {{"Cinza", "CZ"}, {"Bege", "BG"}})
            for (String[] tam : new String[][] {{"2,80 m", "280", "169.90"}, {"4,00 m", "400", "219.90"}})
                grade.add(
                        m(
                                "sku", "EX-CORT-BLACK-" + cor[1] + "-" + tam[1],
                                "atributos", m("Cor", cor[0], "Tamanho", tam[0]),
                                "saldo", "20",
                                "preco", tam[2]));
        pai.put("variacoes", grade);
        imagem((UUID) op.rodar("produto_salvar", pai).get("id"), 8);
    }

    private void kitEIncompletos(Op op) {
        // Kit: o custo é a soma dos componentes; o estoque vem deles.
        var kit =
                completo(
                        "EX-KIT-SALA",
                        "Kit sala: cortina de linho + trilho duplo",
                        "Kits de decoração",
                        "63039200",
                        "0",
                        "229.90");
        kit.put("tipo", "KIT");
        kit.put("peso_bruto_kg", "2.100");
        kit.put("largura_cm", "30");
        kit.put("altura_cm", "15");
        kit.put("comprimento_cm", "210");
        kit.put(
                "kit",
                List.of(
                        m("componente_id", produto(MARCA).toString(), "quantidade", "1"),
                        m("componente_id", produto("EX-TRIL-2M").toString(), "quantidade", "1")));
        imagem((UUID) op.rodar("produto_salvar", kit).get("id"), 9);

        // Incompletos de propósito: aparecem em "Pendências do cadastro" e na coluna Cadastro.
        var semFiscal = m("tipo", "SIMPLES", "sku", "EX-CORT-INF", "nome", "Cortina infantil estrelas");
        semFiscal.put("preco", "119.90");
        semFiscal.put("custo", "48.00");
        semFiscal.put("saldo", "15");
        semFiscal.put("marca", "Casa Clara");
        semFiscal.put("categoria_nome", "Cortinas");
        op.rodar("produto_salvar", semFiscal);
        var semDescricao = m("tipo", "SIMPLES", "sku", "EX-PERS-PVC", "nome", "Persiana de PVC 1,00 m");
        semDescricao.put("preco", "79.90");
        semDescricao.put("custo", "31.00");
        semDescricao.put("saldo", "18");
        semDescricao.put("categoria_nome", "Persianas");
        op.rodar("produto_salvar", semDescricao);
    }

    private void vinculos(Op op) {
        // A categoria de cada produto ligada à de cada marketplace (códigos de exemplo).
        int k = 1;
        for (var c :
                db.queryForList(
                        "select distinct c.id, c.nome from radar_categoria c join radar_produto p"
                                + " on p.categoria_id=c.id and p.tenant_id=c.tenant_id where"
                                + " c.tenant_id=? and p.sku like 'EX-%' order by c.nome",
                        tenant())) {
            for (String canal : List.of("Mercado Livre", "Shopee", "TikTok Shop", "AliExpress"))
                op.rodar(
                        "categoria_vinculo",
                        m(
                                "categoria_id", c.get("id").toString(),
                                "canal", canal,
                                "codigo_externo", "EXEMPLO-" + k++,
                                "nome_externo", "Casa > " + c.get("nome") + " (exemplo)"));
        }
    }

    /** Produto com tudo o que a nota e os marketplaces pedem. */
    private Map<String, Object> completo(
            String sku, String nome, String categoria, String ncm, String custo, String preco) {
        return m(
                "tipo", "SIMPLES",
                "sku", sku,
                "nome", nome,
                "custo", custo,
                "preco", preco,
                "marca", "Casa Clara",
                "origem", "0",
                "ncm", ncm,
                "motivo_sem_gtin", "SEM_CODIGO_DO_FABRICANTE",
                "categoria_nome", categoria,
                "descricao", nome + "." + DESCRICAO);
    }

    // ------------------------------------------------------------- anúncios e vendas --

    /** Anúncios conferidos e prontos em várias lojas; nada é publicado (não há conexão). */
    private void anuncios(Op op, int de, int ate) {
        List<Object> itens = new ArrayList<>();
        var vendaveis =
                db.queryForList(
                        "select id, nome, preco, fisico from radar_produto where tenant_id=? and"
                                + " sku like 'EX-%' and tipo<>'VARIACAO' and not incompleto"
                                + " order by sku",
                        tenant());
        for (int i = de; i < Math.min(ate, vendaveis.size()); i++) {
            var p = vendaveis.get(i);
            for (int l = 0; l < LOJAS.length; l++) {
                // Nem todo produto em toda loja, como na vida real.
                if ((i + l) % 3 == 2) continue;
                String marketplace = LOJAS[l][1];
                String nome = (String) p.get("nome");
                String titulo =
                        marketplace.equals("TikTok Shop")
                                ? nome + " | Casa Clara"
                                : nome.length() > 60 ? nome.substring(0, 60).strip() : nome;
                itens.add(
                        m(
                                "produto_id", p.get("id").toString(),
                                "loja_id", loja(LOJAS[l][0]).toString(),
                                "titulo", titulo,
                                "preco", p.get("preco").toString(),
                                "estoque", String.valueOf(Math.max(1, ((Number) p.get("fisico")).intValue()))));
            }
        }
        if (!itens.isEmpty()) op.rodar("anunciar", m("itens", itens));
    }

    private void promocoes(Op op) {
        // Um pedido de preço esperando aprovação na Central de ações.
        UUID anuncio =
                db.queryForObject(
                        "select a.id from radar_anuncio a join radar_produto p on p.id=a.produto_id"
                                + " and p.tenant_id=a.tenant_id where a.tenant_id=? and p.sku=?"
                                + " order by a.criado_em limit 1",
                        UUID.class,
                        tenant(),
                        "EX-PERS-ROLO");
        op.rodar(
                "propor_preco",
                m("id", anuncio.toString(), "preco", "149.90", "motivo", "Concorrente baixou o preço"));

        LocalDate hoje = hoje();
        op.rodar(
                "promocao",
                m(
                        "nome", "Semana da cortina (exemplo)",
                        "tipo", "PERCENTUAL",
                        "valor", "10",
                        "produto_id", produto(MARCA).toString(),
                        "canal", "Mercado Livre",
                        "inicio", hoje.minusDays(5).toString(),
                        "fim", hoje.plusDays(10).toString()));
        op.rodar(
                "promocao",
                m(
                        "nome", "Almofadas com desconto (exemplo)",
                        "tipo", "VALOR_FIXO",
                        "valor", "5",
                        "produto_id", produto("EX-ALMO-VEL").toString(),
                        "canal", "",
                        "inicio", hoje.minusDays(20).toString(),
                        "fim", hoje.plusDays(20).toString()));
        var encerrada =
                op.rodar(
                "promocao",
                m(
                        "nome", "Liquida persianas (exemplo, encerrada)",
                        "tipo", "PERCENTUAL",
                        "valor", "15",
                        "produto_id", produto("EX-PERS-ROMA").toString(),
                        "canal", "",
                        "inicio", hoje.minusDays(60).toString(),
                        "fim", hoje.minusDays(45).toString()));
        op.rodar("promocao_encerrar", m("id", encerrada.get("id").toString()));
    }

    private void atendimentoEMercado(Op op) {
        String[][] conversas = {
            {"Mercado Livre", "Bom dia! A cortina de linho serve em janela de 2,50 m?"},
            {"Shopee", "Meu pedido já foi enviado? Comprei há três dias."},
            {"TikTok Shop", "Vocês têm a persiana rolô em branco?"},
            {"Mercado Livre", "Chegou com o trilho amassado, como faço a troca?"},
            {"AliExpress", "Qual o prazo de entrega para Curitiba?"},
            {"Shopee", "Dá para mandar com embalagem para presente?"}
        };
        for (int i = 0; i < conversas.length; i++)
            op.rodar(
                    "registro",
                    m(
                            "tipo", "MENSAGEM",
                            "dados",
                                    m(
                                            "cliente", CLIENTES[i],
                                            "canal", conversas[i][0],
                                            "mensagem", conversas[i][1])));

        // Mercado: duas referências de concorrente (links de exemplo) e um preço observado.
        var ref =
                op.rodar(
                        "registro",
                        m(
                                "tipo", "CONCORRENTE",
                                "dados",
                                        m(
                                                "nome", "Concorrente A · cortina de linho (exemplo)",
                                                "url", "https://concorrente-a.example/cortina-linho",
                                                "canal", "Mercado Livre",
                                                "produto_id", produto(MARCA).toString(),
                                                "preco", "179.90",
                                                "observacao", "Link fictício dos dados de exemplo")));
        op.rodar(
                "registro",
                m(
                        "tipo", "PRECO_REFERENCIA",
                        "dados", m("referencia_id", ref.get("id").toString(), "preco", "174.90")));
        op.rodar(
                "registro",
                m(
                        "tipo", "CONCORRENTE",
                        "dados",
                                m(
                                        "nome", "Concorrente B · persiana rolô (exemplo)",
                                        "url", "https://concorrente-b.example/persiana-rolo",
                                        "canal", "Shopee",
                                        "produto_id", produto("EX-PERS-ROLO").toString(),
                                        "preco", "149.00",
                                        "observacao", "Link fictício dos dados de exemplo")));
    }

    // -------------------------------------------------------- compras e financeiro --

    private void compras(Op op) {
        String[][] compras = {
            {MARCA, "20", "82.00", "Tecidos Aurora (exemplo)", "s"},
            {"EX-ALMO-LINHO", "30", "14.50", "Tecidos Aurora (exemplo)", "s"},
            {"EX-TRIL-2M", "50", "21.00", "Metalúrgica Trilhar (exemplo)", "s"},
            {"EX-VARAO-INOX", "20", "34.00", "Metalúrgica Trilhar (exemplo)", "n"},
            {"EX-CORT-VOIL", "40", "38.00", "Tecidos Aurora (exemplo)", "n"}
        };
        for (String[] c : compras) {
            var r =
                    op.rodar(
                            "registro",
                            m(
                                    "tipo", "COMPRA",
                                    "dados",
                                            m(
                                                    "produto_id", produto(c[0]).toString(),
                                                    "quantidade", Integer.parseInt(c[1]),
                                                    "custo_unitario", c[2],
                                                    "fornecedor", c[3],
                                                    "observacao", "Compra dos dados de exemplo")));
            if (c[4].equals("s")) op.rodar("receber_compra", m("id", r.get("id").toString()));
        }
    }

    private void financeiro(Op op) {
        LocalDate hoje = hoje();
        String[][] contas = {
            {"Aluguel do galpão (exemplo)", "2500.00", "5", "n"},
            {"Contador (exemplo)", "450.00", "10", "n"},
            {"Internet (exemplo)", "149.90", "-3", "s"},
            {"Energia (exemplo)", "386.40", "-8", "s"},
            {"Embalagens e etiquetas (exemplo)", "620.00", "15", "n"}
        };
        for (String[] c : contas) {
            op.rodar(
                    "titulo",
                    m(
                            "descricao", c[0],
                            "tipo", "PAGAR",
                            "valor", c[1],
                            "vencimento", hoje.plusDays(Long.parseLong(c[2])).toString()));
            if (c[3].equals("s"))
                op.rodar(
                        "baixar_titulo",
                        m(
                                "id",
                                db.queryForObject(
                                                "select id from radar_titulo where tenant_id=? and"
                                                        + " descricao=? order by criado_em desc"
                                                        + " limit 1",
                                                UUID.class,
                                                tenant(),
                                                c[0])
                                        .toString()));
        }
        op.rodar("despesa", m("descricao", "Anúncios patrocinados (exemplo)", "valor", "300.00"));
        op.rodar("despesa", m("descricao", "Material de embalagem (exemplo)", "valor", "180.00"));
    }

    // ------------------------------------------------------------------ pedidos --

    /**
     * Uma leva de pedidos, espalhados pelos últimos 90 dias. A maioria é expedida; alguns ficam
     * reservados ou separados (os mais recentes), um ou outro é cancelado ou devolvido.
     */
    private void pedidos(Op op, int leva) {
        List<String> skus =
                db.queryForList(
                        "select sku from radar_produto where tenant_id=? and sku like 'EX-%' and"
                                + " tipo<>'VARIACAO' and sku not in ('EX-ALMO-LINHO') order by sku",
                        String.class,
                        tenant());
        List<UUID> compradores = new ArrayList<>();
        for (String c : CLIENTES)
            compradores.add(
                    db.queryForObject(
                            "select id from radar_cliente where tenant_id=? and nome=? limit 1",
                            UUID.class,
                            tenant(),
                            c));
        List<UUID> expedidos = new ArrayList<>();
        for (int j = 0; j < PEDIDOS_POR_PASSO; j++) {
            int i = leva * PEDIDOS_POR_PASSO + j;
            // Passo 5 percorre a lista toda (14 produtos), sem repetir sempre os mesmos.
            String sku = skus.get((i * 5) % skus.size());
            var p =
                    db.queryForMap(
                            "select id, preco from radar_produto where tenant_id=? and sku=?",
                            tenant(),
                            sku);
            String canal = MARKETPLACES.get(i % MARKETPLACES.size());
            int quantidade = i % 5 == 0 ? 2 : 1;
            BigDecimal preco = (BigDecimal) p.get("preco");
            BigDecimal bruto = preco.multiply(BigDecimal.valueOf(quantidade));
            String[] taxa = TAXAS.get(canal);
            var pedido =
                    op.rodar(
                            "pedido",
                            m(
                                    "produto_id", p.get("id").toString(),
                                    "quantidade", quantidade,
                                    "preco", preco.toPlainString(),
                                    "canal", canal,
                                    "cliente_id", compradores.get(i % compradores.size()).toString(),
                                    "comissao", dinheiro(bruto.multiply(new BigDecimal(taxa[0]))),
                                    "frete", taxa[1],
                                    "imposto", dinheiro(bruto.multiply(new BigDecimal("0.06"))),
                                    "ads", i % 4 == 0 ? "6.50" : "0",
                                    "embalagem", "2.40",
                                    "desconto", "0"));
            UUID id = (UUID) pedido.get("id");
            // Do mais antigo (89 dias) ao mais recente (hoje).
            int dias = 89 - (89 * i) / (TOTAL_PEDIDOS - 1);
            if (dias <= 2 && i % 2 == 0) {
                // Recentes: uns ainda reservados, outros separados.
            } else if (i % 13 == 6) {
                op.rodar("pedido_estado", m("id", id.toString(), "estado", "CANCELADO"));
            } else {
                op.rodar("pedido_estado", m("id", id.toString(), "estado", "SEPARADO"));
                if (dias > 2) {
                    op.rodar(
                            "pedido_estado",
                            m("id", id.toString(), "estado", "EXPEDIDO", "confirmar_simulacao", true));
                    if (i % 17 == 9)
                        op.rodar(
                                "pedido_estado",
                                m(
                                        "id", id.toString(),
                                        "estado", "DEVOLVIDO",
                                        "retornar_estoque", true));
                    else expedidos.add(id);
                }
            }
            antedatar(id, dias, (i * 5) % 12);
        }
        // Contas a receber dos expedidos; as mais antigas já conciliadas.
        if (!expedidos.isEmpty())
            op.rodar(
                    "pedidos_lote",
                    m(
                            "acao", "LANCAR_CONTAS",
                            "ids", expedidos.stream().map(UUID::toString).toList(),
                            "vencimento", hoje().plusDays(30).toString()));
        // Os mais antigos já conferidos com o valor recebido.
        if (leva < 5)
            for (UUID id : expedidos) {
                var p =
                        db.queryForMap(
                                "select preco, quantidade, comissao, frete, desconto from"
                                        + " radar_pedido where tenant_id=? and id=?",
                                tenant(),
                                id);
                BigDecimal esperado =
                        ((BigDecimal) p.get("preco"))
                                .multiply(BigDecimal.valueOf(((Number) p.get("quantidade")).longValue()))
                                .subtract((BigDecimal) p.get("comissao"))
                                .subtract((BigDecimal) p.get("frete"))
                                .subtract((BigDecimal) p.get("desconto"));
                op.rodar("conciliar", m("id", id.toString(), "valor", esperado.toPlainString()));
            }
    }

    /**
     * Leva o pedido para N dias atrás. Lançamentos e movimentos de estoque ficam com a data em que
     * foram registrados: essas tabelas não aceitam alteração (são a trilha que prova cada número).
     */
    private void antedatar(UUID pedido, int dias, int horas) {
        db.update(
                "update radar_pedido set criado_em = now() - make_interval(days => ?, hours => ?)"
                        + " where tenant_id=? and id=?",
                dias,
                horas,
                tenant(),
                pedido);
    }

    // --------------------------------------------------------------- apoio --

    private boolean empresaDeDemonstracao() {
        String slug =
                db.queryForObject("select slug from tenant where id=?", String.class, tenant());
        return slug != null && slug.startsWith("demo-");
    }

    private boolean temOperacao() {
        Integer n =
                db.queryForObject(
                        "select (select count(*) from radar_pedido where tenant_id=?)"
                                + " + (select count(*) from radar_lancamento where tenant_id=?)",
                        Integer.class,
                        tenant(),
                        tenant());
        return n != null && n > 0;
    }

    /** Quantos passos dos dados de exemplo esta empresa já completou. */
    private int feitas() {
        var linhas =
                db.queryForList(
                        "select (valor->>'feitas')::int from radar_configuracao where tenant_id=?"
                                + " and chave='exemplo'",
                        Integer.class,
                        tenant());
        return linhas.isEmpty() || linhas.getFirst() == null ? 0 : linhas.getFirst();
    }

    private UUID produto(String sku) {
        return db.queryForObject(
                "select id from radar_produto where tenant_id=? and sku=?", UUID.class, tenant(), sku);
    }

    private UUID loja(String nome) {
        return db.queryForObject(
                "select id from radar_loja where tenant_id=? and nome=? and excluida_em is null",
                UUID.class,
                tenant(),
                nome);
    }

    /** Foto de exemplo: um quadrado de 800 px de uma cor, o bastante para as regras de foto. */
    private void imagem(UUID produto, int cor) {
        Color[] cores = {
            new Color(0xC9B79C), new Color(0xE8E4DA), new Color(0x2F3A4A), new Color(0x8A8D91),
            new Color(0xB8A27A), new Color(0x9AA3AD), new Color(0x7B4B5A), new Color(0xD8CBB5),
            new Color(0x55606E), new Color(0xA67C52)
        };
        BufferedImage foto = new BufferedImage(800, 800, BufferedImage.TYPE_INT_RGB);
        var g = foto.createGraphics();
        g.setColor(cores[cor % cores.length]);
        g.fillRect(0, 0, 800, 800);
        g.dispose();
        var bytes = new ByteArrayOutputStream();
        try {
            ImageIO.write(foto, "png", bytes);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        db.update(
                "insert into radar_produto_imagem(id,tenant_id,produto_id,tipo_conteudo,dados)"
                        + " values(?,?,?,'image/png',?)",
                UUID.randomUUID(),
                tenant(),
                produto,
                bytes.toByteArray());
    }

    private static String dinheiro(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static LocalDate hoje() {
        return LocalDate.now(ZoneId.of("America/Sao_Paulo"));
    }

    /** Mapa na ordem em que os pares vêm: m("a", 1, "b", 2). */
    private static Map<String, Object> m(Object... pares) {
        Map<String, Object> r = new LinkedHashMap<>();
        for (int i = 0; i < pares.length; i += 2) r.put((String) pares[i], pares[i + 1]);
        return r;
    }

    private static UUID tenant() {
        return ContextoTenant.atual();
    }
}
