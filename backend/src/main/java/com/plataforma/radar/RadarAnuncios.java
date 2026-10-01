package com.plataforma.radar;

import static com.plataforma.radar.RadarEntrada.erro;
import static com.plataforma.radar.RadarEntrada.gtinValido;
import static com.plataforma.radar.RadarEntrada.id;
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

    static final Set<String> OPERACOES = Set.of("anuncio_relacionar", "anuncios_precos");

    static final Set<String> CANAIS = Set.of("Mercado Livre", "Shopee", "TikTok Shop", "SHEIN");
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
            case "anuncio_relacionar" -> relacionar(n);
            case "anuncios_precos" -> proporPrecos(n);
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
            if (produto == null) {
                produto = criarProduto(canal, externo, titulo, sku, gtin, preco);
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

    /** Produto mínimo a partir do anúncio. Custo zero: a lojista completa no cadastro. */
    private UUID criarProduto(
            String canal,
            String externo,
            String titulo,
            String sku,
            String gtin,
            BigDecimal preco) {
        UUID id = UUID.randomUUID();
        String base = sku != null ? sku : prefixo(canal) + "-" + externo;
        String codigo = base.length() > 80 ? base.substring(0, 80) : base;
        for (int n = 2; existeSku(codigo); n++) {
            String sufixo = "-" + n;
            codigo = base.substring(0, Math.min(base.length(), 80 - sufixo.length())) + sufixo;
        }
        db.update(
                "insert into radar_produto(id,tenant_id,sku,nome,custo,preco,gtin,incompleto,"
                        + "origem_cadastro) values(?,?,?,?,0,?,?,true,'ANUNCIO')",
                id,
                tenant(),
                codigo,
                titulo,
                preco,
                gtin);
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
            default -> "SHN";
        };
    }

    private Map<String, Object> produto(UUID id) {
        var linhas =
                db.queryForList(
                        "select sku, tipo from radar_produto where tenant_id=? and id=?",
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
