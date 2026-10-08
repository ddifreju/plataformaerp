package com.plataforma.radar;

import static com.plataforma.radar.RadarEntrada.erro;
import static com.plataforma.radar.RadarEntrada.gtinValido;
import static com.plataforma.radar.RadarEntrada.id;
import static com.plataforma.radar.RadarEntrada.inteiroOpcional;
import static com.plataforma.radar.RadarEntrada.opcional;
import static com.plataforma.radar.RadarEntrada.permitir;
import static com.plataforma.radar.RadarEntrada.texto;
import static com.plataforma.radar.RadarEntrada.valor;

import com.fasterxml.jackson.databind.JsonNode;
import com.plataforma.comum.tenant.ContextoTenant;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Anúncios por loja (V023): vínculo com produto, preços em lote e importação.
 *
 * <p>Todo anúncio aponta para um produto (produto_id é NOT NULL). Na importação, anúncio sem
 * produto correspondente (por SKU ou GTIN) ganha um produto criado a partir dele, marcado como
 * incompleto até a lojista completar o cadastro.
 */
@Service
public class RadarAnuncios {

    static final Set<String> OPERACOES =
            Set.of(
                    "anuncio_relacionar",
                    "anuncios_precos",
                    "anuncios_acao_lote",
                    "loja_salvar",
                    "loja_remover",
                    "anunciar");

    /** Marketplaces em que o Radar sabe anunciar. Lista única: as outras classes usam esta. */
    static final Set<String> CANAIS = Set.of("Mercado Livre", "Shopee", "TikTok Shop", "AliExpress");

    /**
     * Regras de anúncio de cada marketplace, conferidas nas fontes oficiais em 07/10/2026
     * (docs/integracoes/regras-de-anuncio.md). Nulo = sem número oficial: não se inventa limite.
     * As mesmas regras estão em frontend/src/app/radar/canais.ts (a tela confere antes; aqui é a
     * trava). Limites que mudam por categoria ou loja são conferidos com o marketplace quando a
     * loja estiver conectada.
     */
    record Regra(
            int tituloMin,
            Integer tituloMax,
            int imagensMin,
            Integer descricaoMinPalavras,
            Integer descricaoMax,
            BigDecimal precoMin,
            BigDecimal precoMax,
            Integer estoqueMin,
            Integer estoqueMax,
            Integer fotoMaiorLadoMin,
            Integer fotoMenorLadoMin) {}

    private static final Regra LIVRE =
            new Regra(1, null, 1, null, null, null, null, null, null, null, null);

    /** Teto do próprio Radar (tamanho do campo), para marketplace sem limite oficial. */
    static final int TITULO_MAX_RADAR = 250;

    static final Map<String, Regra> REGRAS =
            Map.of(
                    // Estoque 0 só é aceito no Fulfillment: anúncio comum precisa de pelo menos 1.
                    "Mercado Livre",
                            new Regra(1, 60, 1, null, 50000, null, null, 1, null, 500, null),
                    // Os limites da Shopee são por loja: sem número público.
                    "Shopee", LIVRE,
                    // Política BR: 25 a 200 letras (a API aceita 300); vale a mais restrita.
                    "TikTok Shop",
                            new Regra(
                                    25,
                                    200,
                                    1,
                                    30,
                                    10000,
                                    new BigDecimal("0.50"),
                                    new BigDecimal("10000.00"),
                                    1,
                                    99999,
                                    null,
                                    300),
                    "AliExpress", new Regra(1, 128, 1, null, null, null, null, null, null, null, null));

    private static final Set<String> SITUACOES =
            Set.of("NAO_PUBLICADO", "ATIVO", "PAUSADO", "REJEITADO", "ENCERRADO");
    private static final int MAX_LOTE = 1000;
    private static final int MAX_IMPORTACAO = 5000;

    private final JdbcTemplate db;

    public RadarAnuncios(JdbcTemplate db) {
        this.db = db;
    }

    Map<String, Object> executar(String op, JsonNode n, String papel) {
        permitir(papel, "DONO", "GESTOR", "MARKETING");
        return switch (op) {
            case "loja_salvar" -> {
                permitir(papel, "DONO", "GESTOR");
                yield salvarLoja(n);
            }
            case "loja_remover" -> {
                permitir(papel, "DONO", "GESTOR");
                yield removerLoja(n);
            }
            case "anunciar" -> anunciar(n);
            case "anuncio_relacionar" -> relacionar(n);
            case "anuncios_precos" -> proporPrecos(n);
            case "anuncios_acao_lote" -> lote(n);
            default -> {
                erro("Operação não reconhecida.");
                yield Map.of();
            }
        };
    }

    /** Troca o produto vinculado de um ou mais anúncios. */
    private Map<String, Object> relacionar(JsonNode n) {
        UUID produto = id(n, "produto_id");
        var p = produto(produto);
        if (p.get("excluido_em") != null) erro("Este produto está na lixeira.");
        if ("VARIACAO".equals(p.get("tipo")))
            erro("Vincule à variação vendida (cor, tamanho…), não ao produto pai.");
        List<UUID> ids = ids(n.path("ids"));
        int alterados = 0;
        for (UUID a : ids)
            alterados +=
                    db.update(
                            "update radar_anuncio set produto_id=?,versao=versao+1,"
                                    + "atualizado_em=now() where tenant_id=? and id=?",
                            produto,
                            tenant(),
                            a);
        if (alterados != ids.size())
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Anúncio não encontrado.");
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("id", produto);
        r.put("mensagem", alterados + " anúncio(s) vinculado(s) a " + p.get("sku") + ".");
        return r;
    }

    /**
     * Ações em lote da lista de anúncios de uma loja. EXCLUIR apaga só o que não está no ar no
     * marketplace (sem loja conectada, apagar no Radar não tiraria o anúncio de lá) e não tem
     * histórico de preço na Central de ações. CRIAR_PRODUTOS cria um produto novo, incompleto, a
     * partir de cada anúncio e passa o anúncio para ele (o vínculo continua obrigatório).
     */
    private Map<String, Object> lote(JsonNode n) {
        String acao = n.path("acao").asText("");
        List<UUID> ids = ids(n.path("ids"));
        var linhas =
                db.queryForList(
                        "select a.id, a.canal, a.titulo, a.preco, a.id_externo, a.sku_externo,"
                                + " a.situacao_ecommerce, exists (select 1 from radar_acao x"
                                + " where x.tenant_id=a.tenant_id and x.anuncio_id=a.id)"
                                + " as tem_historico from radar_anuncio a where a.tenant_id=?"
                                + " and a.id = any(?)",
                        tenant(),
                        ids.toArray(new UUID[0]));
        if (linhas.size() != ids.size())
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Anúncio não encontrado.");
        return switch (acao) {
            case "EXCLUIR" -> excluir(linhas);
            case "CRIAR_PRODUTOS" -> criarProdutos(linhas);
            default -> {
                erro("Ação em lote não reconhecida.");
                yield Map.of();
            }
        };
    }

    private Map<String, Object> excluir(List<Map<String, Object>> linhas) {
        int excluidos = 0, noAr = 0, comHistorico = 0;
        for (var a : linhas) {
            String situacao = (String) a.get("situacao_ecommerce");
            if (situacao.equals("ATIVO") || situacao.equals("PAUSADO")) noAr++;
            else if (Boolean.TRUE.equals(a.get("tem_historico"))) comHistorico++;
            else
                excluidos +=
                        db.update(
                                "delete from radar_anuncio where tenant_id=? and id=?",
                                tenant(),
                                a.get("id"));
        }
        StringBuilder msg = new StringBuilder(excluidos + " anúncio(s) excluído(s).");
        if (noAr > 0)
            msg.append(" ")
                    .append(noAr)
                    .append(
                            " mantido(s) porque está(ão) no ar no marketplace: encerre lá"
                                    + " primeiro.");
        if (comHistorico > 0)
            msg.append(" ")
                    .append(comHistorico)
                    .append(" mantido(s) porque tem(têm) histórico de preço na Central de ações.");
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("excluidos", excluidos);
        r.put("mantidosNoAr", noAr);
        r.put("mantidosComHistorico", comHistorico);
        r.put("mensagem", msg.toString());
        return r;
    }

    private Map<String, Object> criarProdutos(List<Map<String, Object>> linhas) {
        for (var a : linhas) {
            String canal = (String) a.get("canal");
            String externo = (String) a.get("id_externo");
            // Anúncio criado no Radar não tem código lá fora: usa o começo do id dele.
            if (externo == null) externo = a.get("id").toString().substring(0, 8).toUpperCase();
            UUID produto =
                    criarProduto(
                            canal,
                            externo,
                            (String) a.get("titulo"),
                            (String) a.get("sku_externo"),
                            null,
                            (BigDecimal) a.get("preco"),
                            null);
            db.update(
                    "update radar_anuncio set produto_id=?,versao=versao+1,atualizado_em=now()"
                            + " where tenant_id=? and id=?",
                    produto,
                    tenant(),
                    a.get("id"));
        }
        return Map.of(
                "criados",
                linhas.size(),
                "mensagem",
                linhas.size()
                        + " produto(s) criado(s) com cadastro incompleto e vinculado(s) aos"
                        + " anúncios. Complete o cadastro em Produtos.");
    }

    /**
     * Preços em lote viram propostas, como o preço de um anúncio só: alguém com cargo de aprovação
     * confirma na Central de ações, e só então o preço muda.
     */
    private Map<String, Object> proporPrecos(JsonNode n) {
        JsonNode itens = n.path("itens");
        if (!itens.isArray() || itens.isEmpty() || itens.size() > MAX_LOTE)
            erro("Escolha entre 1 e " + MAX_LOTE + " anúncios.");
        String motivo = texto(n, "motivo", 500);
        int propostas = 0;
        for (JsonNode item : itens) {
            UUID a = id(item, "id");
            BigDecimal preco = valor(item, "preco");
            if (preco.signum() == 0) erro("Preço deve ser maior que zero.");
            var linhas =
                    db.queryForList(
                            "select preco, versao from radar_anuncio where tenant_id=? and id=?",
                            tenant(),
                            a);
            if (linhas.isEmpty())
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Anúncio não encontrado.");
            var atual = linhas.getFirst();
            if (((BigDecimal) atual.get("preco")).compareTo(preco) == 0) continue;
            db.update(
                    "insert into radar_acao(id,tenant_id,anuncio_id,tipo,antes,depois,versao,"
                            + "motivo,proposto_por) values(?,?,?,'PRECO',?,?,?,?,?)",
                    UUID.randomUUID(),
                    tenant(),
                    a,
                    atual.get("preco"),
                    preco,
                    atual.get("versao"),
                    motivo,
                    RadarService.usuarioAtual());
            propostas++;
        }
        return Map.of(
                "mensagem",
                propostas == 0
                        ? "Nenhum preço mudou."
                        : propostas
                                + " proposta(s) de preço enviada(s) para aprovação na Central de"
                                + " ações. Os preços ainda não mudaram.");
    }

    /**
     * Grava os anúncios que um conector trouxe do marketplace. Anúncio já conhecido (mesmo canal e
     * id_externo) é atualizado; novo é vinculado ao produto de mesmo SKU ou GTIN, ou a um produto
     * criado a partir dele, marcado como incompleto. Roda dentro da trava por empresa do {@link
     * RadarService}; sem conector ligado, nada chama este método.
     */
    Map<String, Object> importar(String canal, JsonNode itens) {
        if (!CANAIS.contains(canal)) erro("Canal inválido.");
        if (!itens.isArray() || itens.isEmpty() || itens.size() > MAX_IMPORTACAO)
            erro("Importe entre 1 e " + MAX_IMPORTACAO + " anúncios por vez.");
        int novos = 0, atualizados = 0, vinculados = 0, produtosCriados = 0;
        int categoriasAntes = contarCategorias();
        for (JsonNode item : itens) {
            String externo = texto(item, "id_externo", 60);
            String titulo = texto(item, "titulo", 250);
            BigDecimal preco = valor(item, "preco");
            if (preco.signum() == 0) erro("Anúncio " + externo + " sem preço.");
            String situacao = item.path("situacao").asText("ATIVO").toUpperCase();
            if (!SITUACOES.contains(situacao))
                erro("Situação inválida no anúncio " + externo + ".");
            String sku = opcional(item, "sku", 80);
            String gtin = opcional(item, "gtin", 14);
            if (gtin != null && !gtinValido(gtin)) gtin = null;
            String motivo = opcional(item, "motivo_rejeicao", 500);
            UUID categoria =
                    categoriaDoMarketplace(
                            canal,
                            opcional(item, "categoria_codigo", 60),
                            opcional(item, "categoria_nome", 300));

            int mudou =
                    db.update(
                            "update radar_anuncio set titulo=?,preco=?,situacao_ecommerce=?,"
                                    + "motivo_rejeicao=?,sku_externo=?,erro_integracao=null,"
                                    + "atualizado_em=now() where tenant_id=? and canal=?"
                                    + " and id_externo=?",
                            titulo,
                            preco,
                            situacao,
                            motivo,
                            sku,
                            tenant(),
                            canal,
                            externo);
            if (mudou > 0) {
                atualizados++;
                continue;
            }
            UUID produto = produtoPorSkuOuGtin(sku, gtin);
            if (produto != null && categoria != null)
                db.update(
                        "update radar_produto set categoria_id=? where tenant_id=? and id=?"
                                + " and categoria_id is null",
                        categoria,
                        tenant(),
                        produto);
            if (produto == null) {
                produto = criarProduto(canal, externo, titulo, sku, gtin, preco, categoria);
                produtosCriados++;
            } else vinculados++;
            db.update(
                    "insert into radar_anuncio(id,tenant_id,produto_id,canal,titulo,preco,"
                            + "id_externo,sku_externo,situacao_ecommerce,motivo_rejeicao,origem)"
                            + " values(?,?,?,?,?,?,?,?,?,?,'IMPORTACAO')",
                    UUID.randomUUID(),
                    tenant(),
                    produto,
                    canal,
                    titulo,
                    preco,
                    externo,
                    sku,
                    situacao,
                    motivo);
            novos++;
        }
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("novos", novos);
        r.put("atualizados", atualizados);
        r.put("vinculadosAProdutoExistente", vinculados);
        r.put("produtosCriados", produtosCriados);
        r.put("categoriasCriadas", contarCategorias() - categoriasAntes);
        r.put(
                "mensagem",
                novos
                        + " anúncio(s) novo(s), "
                        + atualizados
                        + " atualizado(s). "
                        + produtosCriados
                        + " produto(s) criado(s) com cadastro incompleto.");
        return r;
    }

    private UUID produtoPorSkuOuGtin(String sku, String gtin) {
        if (sku != null) {
            var porSku =
                    db.queryForList(
                            "select id from radar_produto where tenant_id=? and"
                                    + " lower(sku)=lower(?)",
                            UUID.class,
                            tenant(),
                            sku);
            if (!porSku.isEmpty()) return porSku.getFirst();
        }
        if (gtin != null) {
            var porGtin =
                    db.queryForList(
                            "select id from radar_produto where tenant_id=? and gtin=?"
                                    + " order by criado_em limit 1",
                            UUID.class,
                            tenant(),
                            gtin);
            if (!porGtin.isEmpty()) return porGtin.getFirst();
        }
        return null;
    }

    /**
     * Categoria da loja para a categoria do marketplace. Já vinculada: usa o vínculo. Senão, usa a
     * categoria de mesmo nome (sem vínculo com este canal) ou cria uma, e grava o vínculo. A
     * lojista pode renomear depois; o vínculo continua.
     */
    private UUID categoriaDoMarketplace(String canal, String codigo, String nomeExterno) {
        if (codigo == null || nomeExterno == null) return null;
        var vinculada =
                db.queryForList(
                        "select categoria_id from radar_categoria_canal where tenant_id=?"
                                + " and canal=? and codigo_externo=? limit 1",
                        UUID.class,
                        tenant(),
                        canal,
                        codigo);
        if (!vinculada.isEmpty()) return vinculada.getFirst();
        // "Casa > Cortinas > Blackout" vira "Blackout" como nome da categoria da loja.
        String[] partes = nomeExterno.split(">");
        String nome = partes[partes.length - 1].trim();
        if (nome.isEmpty()) nome = nomeExterno.trim();
        if (nome.length() > 120) nome = nome.substring(0, 120);
        var mesmoNome =
                db.queryForList(
                        "select c.id from radar_categoria c where c.tenant_id=?"
                                + " and lower(c.nome)=lower(?) and not exists (select 1 from"
                                + " radar_categoria_canal v where v.tenant_id=c.tenant_id and"
                                + " v.categoria_id=c.id and v.canal=?)",
                        UUID.class,
                        tenant(),
                        nome,
                        canal);
        UUID id;
        if (!mesmoNome.isEmpty()) id = mesmoNome.getFirst();
        else {
            id = UUID.randomUUID();
            String livre = nome;
            for (int n = 2; existeCategoria(livre); n++)
                livre = nome + " (" + canal + " " + n + ")";
            db.update(
                    "insert into radar_categoria(id,tenant_id,nome,origem) values(?,?,?,"
                            + "'IMPORTACAO')",
                    id,
                    tenant(),
                    livre);
        }
        db.update(
                "insert into radar_categoria_canal(tenant_id,categoria_id,canal,codigo_externo,"
                        + "nome_externo) values(?,?,?,?,?)",
                tenant(),
                id,
                canal,
                codigo,
                nomeExterno);
        return id;
    }

    private boolean existeCategoria(String nome) {
        Integer n =
                db.queryForObject(
                        "select count(*) from radar_categoria where tenant_id=?"
                                + " and lower(nome)=lower(?)",
                        Integer.class,
                        tenant(),
                        nome);
        return n != null && n > 0;
    }

    private int contarCategorias() {
        Integer n =
                db.queryForObject(
                        "select count(*) from radar_categoria where tenant_id=?",
                        Integer.class,
                        tenant());
        return n == null ? 0 : n;
    }

    /** Produto mínimo a partir do anúncio. Custo zero: a lojista completa no cadastro. */
    private UUID criarProduto(
            String canal,
            String externo,
            String titulo,
            String sku,
            String gtin,
            BigDecimal preco,
            UUID categoria) {
        UUID id = UUID.randomUUID();
        String base = sku != null ? sku : prefixo(canal) + "-" + externo;
        String codigo = base.length() > 80 ? base.substring(0, 80) : base;
        for (int n = 2; existeSku(codigo); n++) {
            String sufixo = "-" + n;
            codigo = base.substring(0, Math.min(base.length(), 80 - sufixo.length())) + sufixo;
        }
        db.update(
                "insert into radar_produto(id,tenant_id,sku,nome,custo,preco,gtin,categoria_id,"
                        + "incompleto,origem_cadastro) values(?,?,?,?,0,?,?,?,true,'ANUNCIO')",
                id,
                tenant(),
                codigo,
                titulo,
                preco,
                gtin,
                categoria);
        return id;
    }

    private boolean existeSku(String sku) {
        Integer n =
                db.queryForObject(
                        "select count(*) from radar_produto where tenant_id=? and sku=?",
                        Integer.class,
                        tenant(),
                        sku);
        return n != null && n > 0;
    }

    private static String prefixo(String canal) {
        return switch (canal) {
            case "Mercado Livre" -> "ML";
            case "Shopee" -> "SHP";
            case "TikTok Shop" -> "TTS";
            case "AliExpress" -> "ALI";
            default -> "MKT";
        };
    }

    /** Lojas ativas da empresa, para a tela: Integrações e o passo a passo de anunciar. */
    List<Map<String, Object>> lojas() {
        return db.queryForList(
                "select id, marketplace, nome, conectada_em from radar_loja where tenant_id=? and"
                        + " excluida_em is null order by marketplace, lower(nome)",
                tenant());
    }

    /**
     * Cria ou renomeia uma loja. Sem conexão real (depende do CNPJ), a loja fica "aguardando
     * conexão": serve para preparar os anúncios, que sobem quando ela for conectada.
     */
    private Map<String, Object> salvarLoja(JsonNode n) {
        String nome = texto(n, "nome", 60);
        boolean nova = n.path("id").asText("").isBlank();
        UUID id = nova ? UUID.randomUUID() : id(n, "id");
        String marketplace =
                nova ? texto(n, "marketplace", 40) : (String) loja(id).get("marketplace");
        if (nova && !CANAIS.contains(marketplace)) erro("Marketplace não suportado.");
        // O nome só não pode repetir dentro do mesmo marketplace ("Casa Bonita" pode ter uma
        // loja no Mercado Livre e outra na Shopee).
        var repetidas =
                db.queryForList(
                        "select nome from radar_loja where tenant_id=? and marketplace=? and"
                                + " lower(nome)=lower(?) and excluida_em is null and id<>?",
                        String.class,
                        tenant(),
                        marketplace,
                        nome,
                        id);
        if (!repetidas.isEmpty())
            erro(
                    "Já existe a loja "
                            + repetidas.getFirst()
                            + " no "
                            + marketplace
                            + ". Escolha outro nome.");
        if (nova) {
            db.update(
                    "insert into radar_loja(id,tenant_id,marketplace,nome) values(?,?,?,?)",
                    id,
                    tenant(),
                    marketplace,
                    nome);
        } else {
            db.update(
                    "update radar_loja set nome=? where tenant_id=? and id=?", nome, tenant(), id);
        }
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("id", id);
        r.put("mensagem", "Loja " + nome + " salva.");
        return r;
    }

    /** Remove a loja da lista. Os anúncios dela continuam no histórico, apontando para ela. */
    private Map<String, Object> removerLoja(JsonNode n) {
        UUID id = id(n, "id");
        var loja = loja(id);
        db.update(
                "update radar_loja set excluida_em=now() where tenant_id=? and id=?", tenant(), id);
        // Anúncio pronto de loja removida não pode subir quando houver conector: volta a rascunho.
        int voltaram =
                db.update(
                        "update radar_anuncio set estado='RASCUNHO',versao=versao+1,"
                                + "atualizado_em=now() where tenant_id=? and loja_id=? and"
                                + " estado='PRONTO'",
                        tenant(),
                        id);
        return Map.of(
                "mensagem",
                "Loja "
                        + loja.get("nome")
                        + " removida."
                        + (voltaram > 0
                                ? " " + voltaram + " anúncio(s) pronto(s) dela voltaram a rascunho."
                                : ""));
    }

    private Map<String, Object> loja(UUID id) {
        var linhas =
                db.queryForList(
                        "select id, marketplace, nome from radar_loja where tenant_id=? and id=?"
                                + " and excluida_em is null",
                        tenant(),
                        id);
        if (linhas.isEmpty())
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Loja não encontrada.");
        return linhas.getFirst();
    }

    /**
     * Anunciar pelo passo a passo da tela: um anúncio por produto e loja, já conferido, no estado
     * PRONTO. Nada sai do Radar: o anúncio sobe quando a loja for conectada. A conferência repete
     * aqui o que a tela mostrou: cadastro completo (as mesmas pendências que travam a nota), imagens
     * e título dentro das regras do marketplace e categoria ligada à do marketplace. Um erro
     * cancela o lote inteiro (o comando é uma transação só).
     */
    private Map<String, Object> anunciar(JsonNode n) {
        JsonNode itens = n.path("itens");
        if (!itens.isArray() || itens.isEmpty() || itens.size() > MAX_LOTE)
            erro("Escolha entre 1 e " + MAX_LOTE + " anúncios.");
        for (JsonNode item : itens) {
            var loja = loja(id(item, "loja_id"));
            String marketplace = (String) loja.get("marketplace");
            Regra regra = REGRAS.get(marketplace);
            UUID pid = id(item, "produto_id");
            var linhas =
                    db.queryForList(
                            "select * from radar_produto where tenant_id=? and id=?",
                            tenant(),
                            pid);
            if (linhas.isEmpty())
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Produto não encontrado.");
            var p = linhas.getFirst();
            String sku = (String) p.get("sku");
            if (p.get("excluido_em") != null) erro(sku + ": o produto está na lixeira.");
            if ("VARIACAO".equals(p.get("tipo")))
                erro(sku + ": anuncie as variações (cor, tamanho…), não o produto pai.");

            // Todos os problemas do anúncio de uma vez: corrigir um e descobrir o próximo só no
            // envio seguinte é o retrabalho que o passo a passo quer evitar (decisão 0036).
            List<String> problemas = new ArrayList<>();
            List<String> faltando = RadarProdutos.pendencias(p);
            if (!faltando.isEmpty())
                problemas.add("complete o cadastro (falta " + String.join(", ", faltando) + ")");

            String titulo = opcional(item, "titulo", 1000);
            // Conta caracteres como a pessoa vê (emoji vale 1).
            int letras = titulo == null ? 0 : titulo.codePointCount(0, titulo.length());
            int maximo = regra.tituloMax() == null ? TITULO_MAX_RADAR : regra.tituloMax();
            if (letras < regra.tituloMin())
                problemas.add(
                        "o título no "
                                + marketplace
                                + " precisa ter pelo menos "
                                + regra.tituloMin()
                                + " letras (tem "
                                + letras
                                + ")");
            else if (letras > maximo)
                problemas.add(
                        (regra.tituloMax() == null
                                        ? "o Radar guarda títulos de até "
                                        : "o título no " + marketplace + " vai até ")
                                + maximo
                                + " letras (tem "
                                + letras
                                + ")");

            BigDecimal preco = valor(item, "preco");
            if (preco.signum() <= 0) problemas.add("o preço precisa ser maior que zero");
            if ((regra.precoMin() != null && preco.compareTo(regra.precoMin()) < 0)
                    || (regra.precoMax() != null && preco.compareTo(regra.precoMax()) > 0))
                problemas.add(
                        "o preço no "
                                + marketplace
                                + " vai de R$ "
                                + regra.precoMin()
                                + " a R$ "
                                + regra.precoMax());
            // Mesma política da aprovação de preço: anúncio pronto não sobe abaixo do custo.
            if (preco.compareTo((BigDecimal) p.get("custo")) < 0)
                problemas.add("preço abaixo do custo do produto (política local: bloqueado)");

            // Quantidade livre, por decisão da lojista: não é limitada pelo estoque físico. Mas
            // anúncio com zero não está pronto para vender em marketplace nenhum.
            Integer estoque = inteiroOpcional(item, "estoque", 0, 9_999_999);
            if (estoque == null) erro(sku + ": informe a quantidade a anunciar.");
            int minimo = regra.estoqueMin() == null ? 1 : Math.max(1, regra.estoqueMin());
            if (estoque < minimo) problemas.add("a quantidade precisa ser pelo menos " + minimo);
            if (regra.estoqueMax() != null && estoque > regra.estoqueMax())
                problemas.add(
                        "o " + marketplace + " aceita quantidade de no máximo " + regra.estoqueMax());

            String descricao = p.get("descricao") == null ? "" : String.valueOf(p.get("descricao")).strip();
            if (regra.descricaoMinPalavras() != null
                    && !descricao.isEmpty()
                    && descricao.split("\\s+").length < regra.descricaoMinPalavras())
                problemas.add(
                        "o "
                                + marketplace
                                + " pede descrição com pelo menos "
                                + regra.descricaoMinPalavras()
                                + " palavras");
            if (regra.descricaoMax() != null && descricao.length() > regra.descricaoMax())
                problemas.add(
                        "o " + marketplace + " aceita descrição com até " + regra.descricaoMax() + " letras");

            // A variação mostra também as fotos do produto pai.
            UUID pai = p.get("pai_id") == null ? pid : (UUID) p.get("pai_id");
            List<byte[]> fotos =
                    db.queryForList(
                            "select dados from radar_produto_imagem where tenant_id=? and"
                                    + " produto_id in (?,?)",
                            byte[].class,
                            tenant(),
                            pid,
                            pai);
            if (fotos.size() < regra.imagensMin())
                problemas.add("o " + marketplace + " pede pelo menos " + regra.imagensMin() + " imagem(ns)");
            int pequenas = 0;
            for (byte[] foto : fotos) if (fotoPequena(foto, regra)) pequenas++;
            if (pequenas > 0)
                problemas.add(
                        pequenas
                                + " foto(s) pequena(s) para o "
                                + marketplace
                                + (regra.fotoMaiorLadoMin() != null
                                        ? " (o maior lado precisa ter " + regra.fotoMaiorLadoMin() + " pixels)"
                                        : " (os dois lados precisam ter " + regra.fotoMenorLadoMin() + " pixels)"));

            Integer ligada =
                    db.queryForObject(
                            "select count(*) from radar_categoria_canal where tenant_id=? and"
                                    + " canal=? and categoria_id=?",
                            Integer.class,
                            tenant(),
                            marketplace,
                            p.get("categoria_id"));
            if (ligada == null || ligada == 0)
                problemas.add("ligue a categoria do produto a uma categoria do " + marketplace);
            if (!problemas.isEmpty()) erro(sku + ": " + String.join("; ", problemas) + ".");
            // Quantos anúncios o lojista quiser do mesmo produto na mesma loja (decisão dela:
            // teste de título, estratégia de ads, mais catálogo).
            db.update(
                    "insert into radar_anuncio(id,tenant_id,produto_id,canal,loja_id,titulo,preco,"
                            + "estoque,estado) values(?,?,?,?,?,?,?,?,'PRONTO')",
                    UUID.randomUUID(),
                    tenant(),
                    pid,
                    marketplace,
                    loja.get("id"),
                    titulo,
                    preco,
                    estoque);
        }
        return Map.of(
                "criados",
                itens.size(),
                "mensagem",
                itens.size()
                        + " anúncio(s) conferido(s) e pronto(s) para publicar. Eles sobem quando a"
                        + " loja for conectada.");
    }

    /**
     * Foto abaixo do tamanho mínimo do marketplace. Formato que o Java não lê (WEBP) não é
     * medido aqui: a tela mede no navegador.
     */
    private static boolean fotoPequena(byte[] foto, Regra regra) {
        if (regra.fotoMaiorLadoMin() == null && regra.fotoMenorLadoMin() == null) return false;
        try {
            var imagem = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(foto));
            if (imagem == null) return false;
            int maior = Math.max(imagem.getWidth(), imagem.getHeight());
            int menor = Math.min(imagem.getWidth(), imagem.getHeight());
            return (regra.fotoMaiorLadoMin() != null && maior < regra.fotoMaiorLadoMin())
                    || (regra.fotoMenorLadoMin() != null && menor < regra.fotoMenorLadoMin());
        } catch (java.io.IOException e) {
            return false;
        }
    }

    private Map<String, Object> produto(UUID id) {
        var linhas =
                db.queryForList(
                        "select sku, tipo, excluido_em from radar_produto where tenant_id=? and id=?",
                        tenant(),
                        id);
        if (linhas.isEmpty()) erro("Produto não encontrado.");
        return linhas.getFirst();
    }

    private static List<UUID> ids(JsonNode lista) {
        if (!lista.isArray() || lista.isEmpty() || lista.size() > MAX_LOTE)
            erro("Escolha entre 1 e " + MAX_LOTE + " anúncios.");
        List<UUID> out = new ArrayList<>();
        for (JsonNode i : lista) {
            try {
                out.add(UUID.fromString(i.asText()));
            } catch (IllegalArgumentException e) {
                erro("Identificador inválido.");
            }
        }
        return out;
    }

    private static UUID tenant() {
        return ContextoTenant.atual();
    }
}
