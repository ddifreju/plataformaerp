package com.plataforma.radar;

import static com.plataforma.radar.RadarEntrada.decimalOpcional;
import static com.plataforma.radar.RadarEntrada.erro;
import static com.plataforma.radar.RadarEntrada.gtinValido;
import static com.plataforma.radar.RadarEntrada.id;
import static com.plataforma.radar.RadarEntrada.inteiroOpcional;
import static com.plataforma.radar.RadarEntrada.medidaOpcional;
import static com.plataforma.radar.RadarEntrada.opcional;
import static com.plataforma.radar.RadarEntrada.permitir;
import static com.plataforma.radar.RadarEntrada.texto;
import static com.plataforma.radar.RadarEntrada.valor;
import static com.plataforma.radar.RadarEntrada.valorOpcional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.plataforma.comum.tenant.ContextoTenant;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Cadastro completo de produto (V020): dados comerciais, fiscais, logísticos e de marketplace,
 * variações, kits, fornecedores e imagens.
 *
 * <p>Chamado pelo {@link RadarService}, que cuida da trava por tenant, idempotência e auditoria.
 * Estoque só é informado na criação; depois muda pela tela de Estoque ou por compras.
 */
@Service
public class RadarProdutos {

    static final Set<String> OPERACOES = Set.of("produto_salvar", "produto_clonar");

    private static final int MAX_LOTE = 500;

    /** Campos que "editar dados em massa" pode mudar, com o tipo de valor de cada um. */
    private static final Map<String, String> CAMPOS_LOTE =
            Map.ofEntries(
                    Map.entry("preco", "DINHEIRO"),
                    Map.entry("custo", "DINHEIRO"),
                    Map.entry("preco_promocional", "DINHEIRO"),
                    Map.entry("marca", "TEXTO"),
                    Map.entry("categoria_id", "CATEGORIA"),
                    Map.entry("embalagem_id", "EMBALAGEM"),
                    Map.entry("ncm", "NCM"),
                    Map.entry("cest", "CEST"),
                    Map.entry("origem", "ORIGEM"),
                    Map.entry("unidade", "UNIDADE"),
                    Map.entry("condicao", "CONDICAO"),
                    Map.entry("minimo", "INTEIRO"),
                    Map.entry("maximo", "INTEIRO"),
                    Map.entry("dias_preparacao", "INTEIRO"),
                    Map.entry("garantia_meses", "INTEIRO"),
                    Map.entry("garantia_tipo", "GARANTIA"),
                    Map.entry("peso_bruto_kg", "PESO"),
                    Map.entry("peso_liquido_kg", "PESO"),
                    Map.entry("largura_cm", "MEDIDA"),
                    Map.entry("altura_cm", "MEDIDA"),
                    Map.entry("comprimento_cm", "MEDIDA"),
                    Map.entry("controla_estoque", "SIM_NAO"),
                    Map.entry("permite_venda", "SIM_NAO"));

    static final int MAX_TIPOS_VARIACAO = 3;
    static final int MAX_IMAGENS = 12;
    static final int MAX_BYTES_IMAGEM = 2 * 1024 * 1024;
    private static final Set<String> TIPOS_IMAGEM = Set.of("image/jpeg", "image/png", "image/webp");
    static final Set<String> UNIDADES =
            Set.of(
                    "UN", "PC", "KG", "G", "L", "ML", "M", "M2", "M3", "CX", "PAR", "KIT", "JG",
                    "RL");
    private static final Set<String> MOTIVOS_SEM_GTIN =
            Set.of("PRODUTO_ARTESANAL", "KIT_DA_LOJA", "SEM_CODIGO_DO_FABRICANTE", "OUTRO");

    private final JdbcTemplate db;
    private final ObjectMapper json;

    public RadarProdutos(JdbcTemplate db, ObjectMapper json) {
        this.db = db;
        this.json = json;
    }

    /** Listas auxiliares do cadastro, para GET /api/radar. Imagens vão sem os bytes. */
    Map<String, Object> dados() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put(
                "kitItens",
                db.queryForList(
                        "select kit_id, componente_id, quantidade from radar_kit_item"
                                + " where tenant_id=?",
                        tenant()));
        out.put(
                "produtoFornecedores",
                db.queryForList(
                        "select produto_id, fornecedor_id, codigo_no_fornecedor"
                                + " from radar_produto_fornecedor where tenant_id=?",
                        tenant()));
        out.put(
                "imagens",
                db.queryForList(
                        "select id, produto_id, ordem from radar_produto_imagem where tenant_id=?"
                                + " order by produto_id, ordem, criado_em",
                        tenant()));
        return out;
    }

    /** Cria ou atualiza o produto com tudo o que o formulário envia. */
    Map<String, Object> salvar(JsonNode n, String papel) {
        permitir(papel, "DONO", "GESTOR");
        boolean novo = n.path("id").asText("").isBlank();
        UUID id = novo ? UUID.randomUUID() : id(n, "id");
        String tipo = n.path("tipo").asText("SIMPLES");
        if (!Set.of("SIMPLES", "KIT", "VARIACAO").contains(tipo)) erro("Tipo de produto inválido.");

        Map<String, Object> antes = null;
        if (!novo) {
            antes = linhaParaAtualizar(id);
            if (antes.get("excluido_em") != null)
                erro("Este produto está na lixeira. Restaure antes de editar.");
            if (antes.get("pai_id") != null) erro("Edite a variação pelo produto principal.");
            if (!antes.get("tipo").equals(tipo))
                erro("O tipo do produto não muda depois de criado. Cadastre um novo produto.");
        }

        Map<String, Object> c = colunasComuns(n);
        c.put("tipo", tipo);
        if (novo) aplicarPadroes(c, n);
        // Rascunho: para salvar bastam nome e SKU. O que a nota e os marketplaces exigem vira
        // pendência (coluna incompleto) e só bloqueia na hora de enviar ou faturar.
        if (n.path("nome").asText("").isBlank()) erro("Dê um nome ao produto.");
        c.put("nome", texto(n, "nome", 250));
        String sku = opcional(n, "sku", 80);
        if (sku == null && !novo) sku = (String) antes.get("sku");
        c.put("sku", sku == null ? proximoSku() : sku);
        c.put("preco", valorOuZero(n, "preco"));
        c.put("categoria_id", categoria(n));
        c.put("embalagem_id", embalagem(n));
        c.put("tipos_variacao", tipo.equals("VARIACAO") ? tiposVariacao(n) : "[]");

        List<Object[]> componentes = tipo.equals("KIT") ? componentes(n, id) : List.of();
        c.put("custo", tipo.equals("KIT") ? custoDoKit(componentes) : valorOuZero(n, "custo"));
        skuLivre((String) c.get("sku"), id);

        if (novo) {
            // Kit e produto com variações não têm saldo próprio: o estoque mora nos componentes
            // e nas variações.
            int saldo =
                    tipo.equals("SIMPLES") && n.has("saldo")
                            ? valorInteiro(n, "saldo", 0, 1_000_000)
                            : 0;
            c.put("fisico", saldo);
            inserir(id, c);
            if (saldo > 0) movimento(id, "ABERTURA", saldo, "Saldo inicial informado");
        } else {
            atualizar(id, c);
        }

        if (tipo.equals("KIT")) gravarKit(id, componentes);
        if (tipo.equals("VARIACAO")) salvarVariacoes(id, c, n);
        gravarFornecedores(id, n);
        List<String> faltando = marcarPendencias(id);

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("id", id);
        r.put("sku", c.get("sku"));
        r.put("pendencias", faltando);
        r.put(
                "mensagem",
                (novo ? "Produto cadastrado" : "Produto atualizado")
                        + (faltando.isEmpty()
                                ? "."
                                : " como rascunho. Para anunciar e emitir nota, falta: "
                                        + String.join(", ", faltando)
                                        + "."));
        if (antes != null)
            r.put(
                    "antes",
                    Map.of(
                            "nome", antes.get("nome"),
                            "custo", antes.get("custo").toString(),
                            "preco", antes.get("preco").toString()));
        return r;
    }

    /**
     * O que a NF-e (código, descrição, NCM, origem, unidade, valor, GTIN ou "SEM GTIN") e os
     * marketplaces (título, descrição, marca, categoria, condição, preço, peso e medidas do pacote)
     * exigem, a partir da linha gravada. Lista vazia = pronto. Quem envia para marketplace ou emite
     * nota chama isto e recusa se faltar algo; salvar o cadastro não recusa (rascunho). Imagem não
     * entra: cada canal tem a sua regra, conferida no "Pronto para anunciar?".
     */
    static List<String> pendencias(Map<String, Object> p) {
        List<String> faltando = new ArrayList<>();
        if (vazio(p.get("sku"))) faltando.add("Código (SKU)");
        if (p.get("origem") == null) faltando.add("Origem (ICMS)");
        if (vazio(p.get("ncm"))) faltando.add("NCM");
        if (vazio(p.get("gtin")) && vazio(p.get("motivo_sem_gtin")))
            faltando.add("Código de barras ou motivo de não ter");
        if (!(p.get("preco") instanceof BigDecimal preco) || preco.signum() <= 0)
            faltando.add("Preço de venda");
        if (vazio(p.get("marca"))) faltando.add("Marca");
        if (p.get("categoria_id") == null) faltando.add("Categoria");
        if (vazio(p.get("descricao"))) faltando.add("Descrição");
        if (p.get("peso_bruto_kg") == null) faltando.add("Peso bruto");
        boolean medidas =
                p.get("largura_cm") != null
                        && p.get("altura_cm") != null
                        && p.get("comprimento_cm") != null;
        if (!medidas && p.get("embalagem_id") == null)
            faltando.add("Medidas (largura, altura e comprimento) ou embalagem");
        return faltando;
    }

    private static boolean vazio(Object v) {
        return v == null || v.toString().isBlank();
    }

    /**
     * Grava em incompleto se o produto e cada variação estão prontos para nota e marketplace.
     * Devolve o que falta no produto principal ou, se ele estiver pronto, na primeira variação
     * com falta.
     */
    private List<String> marcarPendencias(UUID id) {
        List<String> doProduto = new ArrayList<>();
        for (var linha :
                db.queryForList(
                        "select * from radar_produto where tenant_id=? and (id=? or pai_id=?)"
                                + " order by pai_id nulls first",
                        tenant(),
                        id,
                        id)) {
            List<String> f = pendencias(linha);
            db.update(
                    "update radar_produto set incompleto=? where tenant_id=? and id=?",
                    !f.isEmpty(),
                    tenant(),
                    linha.get("id"));
            if (doProduto.isEmpty() && !f.isEmpty())
                doProduto =
                        linha.get("pai_id") == null
                                ? f
                                : f.stream().map(x -> x + " (variação " + linha.get("sku") + ")").toList();
        }
        return doProduto;
    }

    /** Recalcula a coluna incompleto depois de mudanças fora do formulário (lote, importação). */
    void recalcularPendencias(List<UUID> ids) {
        if (ids.isEmpty()) return;
        for (var linha :
                db.queryForList(
                        "select * from radar_produto where tenant_id=? and id = any(?)",
                        tenant(),
                        ids.toArray(UUID[]::new)))
            db.update(
                    "update radar_produto set incompleto=? where tenant_id=? and id=?",
                    !pendencias(linha).isEmpty(),
                    tenant(),
                    linha.get("id"));
    }

    /** SKU automático conforme Configurações do cadastro de produtos. Manual: SKU obrigatório. */
    private String proximoSku() {
        var cfg = RadarConfiguracao.produtos(db, json);
        String modo = (String) cfg.get("sku_modo");
        if (modo.equals("MANUAL"))
            erro(
                    "Informe o código (SKU). Para o Radar gerar sozinho, ligue o SKU automático em"
                            + " Configurações → cadastros.");
        String prefixo = modo.equals("PREFIXO") ? (String) cfg.get("sku_prefixo") : "";
        int digitos = (Integer) cfg.get("sku_digitos");
        // O prefixo só tem letras, números e hífen (validado ao salvar a configuração).
        // Só conta o que tem o formato da sequência (os dígitos configurados, até 12): um código
        // de barras digitado no campo SKU (13 ou 14 dígitos) não puxa a numeração nem a trava.
        String formato = "^" + prefixo + "[0-9]{" + digitos + "," + Math.max(12, digitos) + "}$";
        Long ultimo =
                db.queryForObject(
                        "select max(substring(sku from ?)::numeric) from radar_produto where"
                                + " tenant_id=? and sku ~ ?",
                        Long.class,
                        "^" + prefixo + "([0-9]+)$",
                        tenant(),
                        formato);
        long proximo = (ultimo == null ? 0 : ultimo) + 1;
        // Se o próximo já existir (digitado à mão), segue para o seguinte livre.
        for (int tentativa = 0; tentativa < 1000; tentativa++, proximo++) {
            String sku = prefixo + String.format("%0" + digitos + "d", proximo);
            if (!skuExiste(sku)) return sku;
        }
        erro("Não achei um SKU livre na sequência. Informe o SKU à mão.");
        return null;
    }

    /**
     * Valores padrão de Configurações → cadastros (unidade, NCM, origem) para produto novo que
     * não trouxe o campo: valem também fora da tela (importação, integração).
     */
    private void aplicarPadroes(Map<String, Object> c, JsonNode n) {
        var cfg = RadarConfiguracao.produtos(db, json);
        String unidade = (String) cfg.get("unidade_padrao");
        if (!n.has("unidade") && unidade != null && !unidade.isBlank()) c.put("unidade", unidade);
        String ncm = (String) cfg.get("ncm_padrao");
        if (!n.has("ncm") && ncm != null && !ncm.isBlank()) c.put("ncm", ncm);
        String origem = String.valueOf(cfg.get("origem_padrao"));
        if (!n.has("origem") && origem.matches("[0-8]")) c.put("origem", Integer.parseInt(origem));
    }

    private boolean skuExiste(String sku) {
        Integer n =
                db.queryForObject(
                        "select count(*) from radar_produto where tenant_id=? and sku=?",
                        Integer.class,
                        tenant(),
                        sku);
        return n != null && n > 0;
    }

    private static BigDecimal valorOuZero(JsonNode n, String campo) {
        BigDecimal v = valorOpcional(n, campo);
        return v == null ? BigDecimal.ZERO.setScale(2) : v;
    }

    /** Imagem enviada pelo formulário. Recusa formato, tamanho ou quantidade fora do limite. */
    UUID adicionarImagem(String papel, UUID produtoId, byte[] dados, String tipoConteudo) {
        permitir(papel, "DONO", "GESTOR", "MARKETING");
        linhaParaAtualizar(produtoId);
        if (!TIPOS_IMAGEM.contains(tipoConteudo)) erro("Envie imagens JPG, PNG ou WEBP.");
        if (dados.length == 0 || dados.length > MAX_BYTES_IMAGEM)
            erro("Cada imagem pode ter no máximo 2 MB.");
        Integer existentes =
                db.queryForObject(
                        "select count(*) from radar_produto_imagem where tenant_id=? and"
                                + " produto_id=?",
                        Integer.class,
                        tenant(),
                        produtoId);
        if (existentes != null && existentes >= MAX_IMAGENS)
            erro("Cada produto aceita até " + MAX_IMAGENS + " imagens.");
        UUID id = UUID.randomUUID();
        db.update(
                "insert into"
                        + " radar_produto_imagem(id,tenant_id,produto_id,ordem,tipo_conteudo,dados)"
                        + " values(?,?,?,?,?,?)",
                id,
                tenant(),
                produtoId,
                existentes == null ? 0 : existentes,
                tipoConteudo,
                dados);
        return id;
    }

    void removerImagem(String papel, UUID imagemId) {
        permitir(papel, "DONO", "GESTOR", "MARKETING");
        if (db.update(
                        "delete from radar_produto_imagem where tenant_id=? and id=?",
                        tenant(),
                        imagemId)
                == 0)
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Imagem não encontrada.");
    }

    /** Passa a imagem para a primeira posição (a principal, usada nos anúncios). */
    void tornarPrincipal(String papel, UUID imagemId) {
        permitir(papel, "DONO", "GESTOR", "MARKETING");
        var linhas =
                db.queryForList(
                        "select produto_id from radar_produto_imagem where tenant_id=? and id=?",
                        tenant(),
                        imagemId);
        if (linhas.isEmpty())
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Imagem não encontrada.");
        db.update(
                "update radar_produto_imagem set ordem=ordem+1 where tenant_id=? and produto_id=?",
                tenant(),
                linhas.getFirst().get("produto_id"));
        db.update(
                "update radar_produto_imagem set ordem=0 where tenant_id=? and id=?",
                tenant(),
                imagemId);
    }

    /** Bytes e tipo da imagem desta empresa; 404 se for de outra. */
    Map<String, Object> imagem(UUID imagemId) {
        var linhas =
                db.queryForList(
                        "select tipo_conteudo, dados from radar_produto_imagem"
                                + " where tenant_id=? and id=?",
                        tenant(),
                        imagemId);
        if (linhas.isEmpty())
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Imagem não encontrada.");
        return linhas.getFirst();
    }

    // ---- campos ------------------------------------------------------------------------------

    /** Colunas que produto principal e variações compartilham, já validadas. */
    private Map<String, Object> colunasComuns(JsonNode n) {
        Map<String, Object> c = new LinkedHashMap<>();
        String gtin = opcional(n, "gtin", 14);
        if (gtin != null && !gtinValido(gtin))
            erro("Código de barras (GTIN) inválido: confira os dígitos.");
        c.put("gtin", gtin);
        String motivo = opcional(n, "motivo_sem_gtin", 30);
        if (motivo != null && !MOTIVOS_SEM_GTIN.contains(motivo)) erro("Motivo sem GTIN inválido.");
        c.put("motivo_sem_gtin", gtin == null ? motivo : null);
        c.put("origem", inteiroOpcional(n, "origem", 0, 8));
        String unidade = opcional(n, "unidade", 6);
        c.put("unidade", unidade == null ? "UN" : unidade.toUpperCase());
        if (!UNIDADES.contains((String) c.get("unidade"))) erro("Unidade inválida.");
        String ncm = digitos(n, "ncm", 8, "NCM deve ter 8 dígitos.");
        c.put("ncm", ncm == null ? "" : ncm);
        c.put("cest", digitos(n, "cest", 7, "CEST deve ter 7 dígitos."));
        c.put("marca", textoOuVazio(n, "marca", 120));
        c.put("modelo", opcional(n, "modelo", 120));
        c.put(
                "condicao",
                escolha(n, "condicao", Set.of("NOVO", "USADO", "RECONDICIONADO"), "NOVO"));
        c.put("descricao", textoOuVazio(n, "descricao", 20000));
        BigDecimal promocional = valorOpcional(n, "preco_promocional");
        if (promocional != null && promocional.signum() == 0) promocional = null;
        BigDecimal precoVenda = valorOuZero(n, "preco");
        if (promocional != null
                && precoVenda.signum() > 0
                && promocional.compareTo(precoVenda) >= 0)
            erro("Preço promocional deve ser menor que o preço de venda.");
        c.put("preco_promocional", promocional);
        c.put("peso_liquido_kg", decimalOpcional(n, "peso_liquido_kg", 3));
        c.put("peso_bruto_kg", decimalOpcional(n, "peso_bruto_kg", 3));
        c.put("largura_cm", medidaOpcional(n, "largura_cm"));
        c.put("altura_cm", medidaOpcional(n, "altura_cm"));
        c.put("comprimento_cm", medidaOpcional(n, "comprimento_cm"));
        Integer volumes = inteiroOpcional(n, "volumes", 1, 999);
        c.put("volumes", volumes == null ? 1 : volumes);
        c.put(
                "formato_embalagem",
                escolha(
                        n,
                        "formato_embalagem",
                        Set.of("PACOTE_CAIXA", "ROLO_CILINDRO", "ENVELOPE"),
                        "PACOTE_CAIXA"));
        c.put("controla_estoque", n.path("controla_estoque").asBoolean(true));
        Integer minimo = inteiroOpcional(n, "minimo", 0, 1_000_000);
        c.put("minimo", minimo == null ? 0 : minimo);
        Integer maximo = inteiroOpcional(n, "maximo", 0, 1_000_000);
        if (maximo != null && maximo < (Integer) c.get("minimo"))
            erro("O estoque máximo é menor que o mínimo.");
        c.put("maximo", maximo);
        c.put("sob_encomenda", n.path("sob_encomenda").asBoolean(false));
        c.put("dias_preparacao", inteiroOpcional(n, "dias_preparacao", 0, 90));
        c.put(
                "garantia_tipo",
                escolhaOpcional(
                        n, "garantia_tipo", Set.of("VENDEDOR", "FABRICANTE", "SEM_GARANTIA")));
        c.put("garantia_meses", inteiroOpcional(n, "garantia_meses", 0, 120));
        String video = opcional(n, "video_url", 500);
        if (video != null && !video.startsWith("https://"))
            erro("O link do vídeo precisa começar com https://");
        c.put("video_url", video);
        c.put("keywords", opcional(n, "keywords", 500));
        c.put("descricao_seo", opcional(n, "descricao_seo", 320));
        c.put("tags", listaDeTextos(n, "tags", 30, 60));
        c.put("atributos", paresNomeValor(n, "atributos"));
        c.put("campos_adicionais", paresNomeValor(n, "campos_adicionais"));
        c.put("unidades_por_caixa", inteiroOpcional(n, "unidades_por_caixa", 1, 100000));
        c.put("linha_produto", opcional(n, "linha_produto", 120));
        c.put("permite_venda", n.path("permite_venda").asBoolean(true));
        // Fiscal
        String gtinTrib = opcional(n, "gtin_tributavel", 14);
        if (gtinTrib != null && !gtinValido(gtinTrib)) erro("GTIN tributável inválido.");
        c.put("gtin_tributavel", gtinTrib);
        String unidadeTrib = opcional(n, "unidade_tributavel", 6);
        c.put("unidade_tributavel", unidadeTrib == null ? null : unidadeTrib.toUpperCase());
        c.put("fator_conversao", decimalOpcional(n, "fator_conversao", 4));
        c.put("ipi_codigo_enquadramento", opcional(n, "ipi_codigo_enquadramento", 5));
        c.put(
                "ipi_enquadramento_legal",
                digitos(
                        n,
                        "ipi_enquadramento_legal",
                        3,
                        "Enquadramento legal do IPI tem 3 dígitos."));
        c.put("ipi_valor_fixo", valorOpcional(n, "ipi_valor_fixo"));
        String exTipi = opcional(n, "ex_tipi", 3);
        if (exTipi != null && !exTipi.matches("[0-9]{1,3}")) erro("EX TIPI tem até 3 dígitos.");
        c.put("ex_tipi", exTipi);
        c.put("is_aliquota_especifica", decimalOpcional(n, "is_aliquota_especifica", 4));
        c.put("qtd_monofasia", decimalOpcional(n, "qtd_monofasia", 4));
        c.put("qtd_monofasia_retencao", decimalOpcional(n, "qtd_monofasia_retencao", 4));
        c.put("observacoes_internas", opcional(n, "observacoes_internas", 5000));
        return c;
    }

    /** Categoria escolhida ou digitada; categoria nova é criada no cadastro de categorias. */
    private UUID categoria(JsonNode n) {
        if (!n.path("categoria_id").asText("").isBlank()) {
            UUID id = id(n, "categoria_id");
            existe("radar_categoria", id, "Categoria não encontrada.");
            return id;
        }
        String nome = opcional(n, "categoria_nome", 120);
        if (nome == null) return null;
        var existente =
                db.queryForList(
                        "select id from radar_categoria where tenant_id=? and lower(nome)=lower(?)",
                        tenant(),
                        nome);
        if (!existente.isEmpty()) return (UUID) existente.getFirst().get("id");
        UUID id = UUID.randomUUID();
        db.update(
                "insert into radar_categoria(id,tenant_id,nome) values(?,?,?)", id, tenant(), nome);
        return id;
    }

    /**
     * Embalagem escolhida no cadastro, ou "embalagem customizada": cria uma embalagem nova com as
     * medidas informadas, que passa a aparecer no cadastro de embalagens.
     */
    private UUID embalagem(JsonNode n) {
        if (!n.path("embalagem_id").asText("").isBlank()) {
            UUID id = id(n, "embalagem_id");
            existe("radar_embalagem", id, "Embalagem não encontrada.");
            return id;
        }
        JsonNode nova = n.path("embalagem_nova");
        if (!nova.isObject() || nova.path("nome").asText("").isBlank()) return null;
        String nome = texto(nova, "nome", 120);
        var existente =
                db.queryForList(
                        "select id from radar_embalagem where tenant_id=? and lower(nome)=lower(?)",
                        tenant(),
                        nome);
        if (!existente.isEmpty()) return (UUID) existente.getFirst().get("id");
        UUID id = UUID.randomUUID();
        db.update(
                "insert into radar_embalagem(id,tenant_id,nome,custo,comprimento_cm,largura_cm,"
                        + "altura_cm,peso_g) values(?,?,?,?,?,?,?,?)",
                id,
                tenant(),
                nome,
                nova.path("custo").asText("").isBlank()
                        ? BigDecimal.ZERO.setScale(2)
                        : valor(nova, "custo"),
                medidaOpcional(nova, "comprimento_cm"),
                medidaOpcional(nova, "largura_cm"),
                medidaOpcional(nova, "altura_cm"),
                inteiroOpcional(nova, "peso_g", 0, 1_000_000));
        return id;
    }

    private String tiposVariacao(JsonNode n) {
        JsonNode tipos = n.path("tipos_variacao");
        if (!tipos.isArray() || tipos.isEmpty())
            erro("Informe ao menos um tipo de variação (ex.: Cor).");
        if (tipos.size() > MAX_TIPOS_VARIACAO)
            erro("Use no máximo " + MAX_TIPOS_VARIACAO + " tipos de variação.");
        Set<String> vistos = new HashSet<>();
        List<String> lista = new ArrayList<>();
        for (JsonNode t : tipos) {
            String tipo = t.asText("").trim();
            if (tipo.isBlank() || tipo.length() > 40) erro("Tipo de variação inválido.");
            if (!vistos.add(tipo.toLowerCase())) erro("Tipo de variação repetido: " + tipo);
            lista.add(tipo);
        }
        return paraJson(lista);
    }

    // ---- variações ---------------------------------------------------------------------------

    /**
     * Cada linha da grade vira (ou atualiza) um produto com pai_id. Variação que sai da grade não é
     * apagada, porque pode ter pedidos: fica fora de venda.
     */
    private void salvarVariacoes(UUID paiId, Map<String, Object> pai, JsonNode n) {
        JsonNode grade = n.path("variacoes");
        if (!grade.isArray() || grade.isEmpty()) erro("Adicione ao menos uma variação na grade.");
        if (grade.size() > 300) erro("A grade aceita até 300 variações.");
        List<String> tipos = lerLista((String) pai.get("tipos_variacao"));
        Set<String> combinacoes = new HashSet<>();
        Set<UUID> mantidas = new HashSet<>();
        for (JsonNode v : grade) {
            Map<String, String> atributos = new LinkedHashMap<>();
            for (String tipo : tipos) {
                String valorAtributo = v.path("atributos").path(tipo).asText("").trim();
                if (valorAtributo.isBlank() || valorAtributo.length() > 60)
                    erro("Preencha " + tipo + " em todas as variações.");
                atributos.put(tipo, valorAtributo);
            }
            if (!combinacoes.add(atributos.toString().toLowerCase()))
                erro("Combinação repetida na grade: " + String.join(" / ", atributos.values()));

            boolean nova = v.path("id").asText("").isBlank();
            UUID id = nova ? UUID.randomUUID() : id(v, "id");
            if (!nova) {
                var linha = linhaParaAtualizar(id);
                if (!paiId.equals(linha.get("pai_id")))
                    erro("Variação não pertence a este produto.");
            }
            Map<String, Object> c = new LinkedHashMap<>(pai);
            c.put("tipo", "SIMPLES");
            c.put("tipos_variacao", "[]");
            c.put("pai_id", paiId);
            c.put("atributos_variacao", paraJson(atributos));
            c.put("nome", pai.get("nome") + " - " + String.join(" / ", atributos.values()));
            String skuVariacao = opcional(v, "sku", 80);
            c.put("sku", skuVariacao == null ? skuDaVariacao(pai, atributos, id) : skuVariacao);
            c.put(
                    "preco",
                    v.path("preco").asText("").isBlank() ? pai.get("preco") : valor(v, "preco"));
            c.put(
                    "custo",
                    v.path("custo").asText("").isBlank() ? pai.get("custo") : valor(v, "custo"));
            // A grade não tem promocional próprio: sem o campo, vale o do produto principal.
            c.put(
                    "preco_promocional",
                    v.has("preco_promocional")
                            ? valorOpcional(v, "preco_promocional")
                            : pai.get("preco_promocional"));
            if (c.get("preco_promocional") instanceof BigDecimal promo
                    && c.get("preco") instanceof BigDecimal precoVar
                    && precoVar.signum() > 0
                    && promo.compareTo(precoVar) >= 0)
                erro(
                        "Na variação "
                                + c.get("sku")
                                + ", o preço promocional deve ser menor que o preço de venda.");
            String gtin = opcional(v, "gtin", 14);
            if (gtin != null && !gtinValido(gtin))
                erro("GTIN inválido na variação " + c.get("sku") + ".");
            c.put("gtin", gtin);
            c.put("motivo_sem_gtin", gtin == null ? pai.get("motivo_sem_gtin") : null);
            c.put("permite_venda", v.path("permite_venda").asBoolean(true));
            skuLivre((String) c.get("sku"), id);
            if (nova) {
                int saldo = v.has("saldo") ? valorInteiro(v, "saldo", 0, 1_000_000) : 0;
                c.put("fisico", saldo);
                inserir(id, c);
                if (saldo > 0) movimento(id, "ABERTURA", saldo, "Saldo inicial da variação");
            } else {
                atualizar(id, c);
            }
            mantidas.add(id);
        }
        for (var antiga :
                db.queryForList(
                        "select id from radar_produto where tenant_id=? and pai_id=?",
                        tenant(),
                        paiId)) {
            UUID id = (UUID) antiga.get("id");
            if (!mantidas.contains(id))
                db.update(
                        "update radar_produto set permite_venda=false where tenant_id=? and id=?",
                        tenant(),
                        id);
        }
    }

    /** SKU da variação sem SKU informado: SKU do pai + valores (ex.: CAM-01-AZUL-P). */
    private String skuDaVariacao(Map<String, Object> pai, Map<String, String> atributos, UUID id) {
        StringBuilder b = new StringBuilder((String) pai.get("sku"));
        for (String valor : atributos.values()) {
            String limpo =
                    java.text.Normalizer.normalize(valor, java.text.Normalizer.Form.NFD)
                            .replaceAll("[^A-Za-z0-9]", "")
                            .toUpperCase();
            b.append('-').append(limpo, 0, Math.min(12, limpo.length()));
        }
        String base = b.length() > 76 ? b.substring(0, 76) : b.toString();
        String sku = base;
        for (int i = 2; skuEmUso(sku, id); i++) sku = base + "-" + i;
        return sku;
    }

    private boolean skuEmUso(String sku, UUID id) {
        Integer k =
                db.queryForObject(
                        "select count(*) from radar_produto where tenant_id=? and sku=? and id<>?",
                        Integer.class,
                        tenant(),
                        sku,
                        id);
        return k != null && k > 0;
    }

    // ---- kit ---------------------------------------------------------------------------------

    private List<Object[]> componentes(JsonNode n, UUID kitId) {
        JsonNode itens = n.path("kit");
        if (!itens.isArray() || itens.isEmpty()) erro("Adicione ao menos um produto ao kit.");
        if (itens.size() > 50) erro("Um kit aceita até 50 itens.");
        List<Object[]> out = new ArrayList<>();
        Set<UUID> vistos = new HashSet<>();
        for (JsonNode item : itens) {
            UUID componente = id(item, "componente_id");
            if (componente.equals(kitId)) erro("Um kit não pode conter ele mesmo.");
            if (!vistos.add(componente)) erro("Produto repetido no kit.");
            var linhas =
                    db.queryForList(
                            "select tipo, custo from radar_produto where tenant_id=? and id=?"
                                    + " and excluido_em is null",
                            tenant(),
                            componente);
            if (linhas.isEmpty()) erro("Produto do kit não encontrado.");
            if (!"SIMPLES".equals(linhas.getFirst().get("tipo")))
                erro("O kit só aceita produtos simples ou variações, não outros kits.");
            int quantidade = valorInteiro(item, "quantidade", 1, 1000);
            out.add(new Object[] {componente, quantidade, linhas.getFirst().get("custo")});
        }
        return out;
    }

    // Custo do kit = soma do custo dos componentes × quantidade, em escala 2 (sem arredondar).
    private static BigDecimal custoDoKit(List<Object[]> componentes) {
        return componentes.stream()
                .map(c -> ((BigDecimal) c[2]).multiply(BigDecimal.valueOf((Integer) c[1])))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2);
    }

    private void gravarKit(UUID kitId, List<Object[]> componentes) {
        db.update("delete from radar_kit_item where tenant_id=? and kit_id=?", tenant(), kitId);
        for (Object[] c : componentes)
            db.update(
                    "insert into radar_kit_item(tenant_id,kit_id,componente_id,quantidade)"
                            + " values(?,?,?,?)",
                    tenant(),
                    kitId,
                    c[0],
                    c[1]);
    }

    // ---- fornecedores ------------------------------------------------------------------------

    private void gravarFornecedores(UUID produtoId, JsonNode n) {
        JsonNode lista = n.path("fornecedores");
        if (lista.isMissingNode()) return;
        db.update(
                "delete from radar_produto_fornecedor where tenant_id=? and produto_id=?",
                tenant(),
                produtoId);
        Set<UUID> vistos = new HashSet<>();
        for (JsonNode f : lista) {
            UUID fornecedor = id(f, "fornecedor_id");
            if (!vistos.add(fornecedor)) erro("Fornecedor repetido no produto.");
            Integer ehFornecedor =
                    db.queryForObject(
                            "select count(*) from radar_cliente where tenant_id=? and id=?"
                                    + " and tipos_contato @> '[\"FORNECEDOR\"]'",
                            Integer.class,
                            tenant(),
                            fornecedor);
            if (ehFornecedor == null || ehFornecedor == 0) erro("Fornecedor não encontrado.");
            db.update(
                    "insert into radar_produto_fornecedor(tenant_id,produto_id,fornecedor_id,"
                            + "codigo_no_fornecedor) values(?,?,?,?)",
                    tenant(),
                    produtoId,
                    fornecedor,
                    opcional(f, "codigo", 60));
        }
    }

    // ---- persistência ------------------------------------------------------------------------

    private static final Set<String> JSONB =
            Set.of(
                    "tags",
                    "atributos",
                    "campos_adicionais",
                    "tipos_variacao",
                    "atributos_variacao");

    // Nomes de coluna vêm só de chaves fixas deste arquivo, nunca da requisição.
    private void inserir(UUID id, Map<String, Object> c) {
        List<String> colunas = new ArrayList<>(c.keySet());
        String marcadores =
                colunas.stream()
                        .map(k -> JSONB.contains(k) ? "?::jsonb" : "?")
                        .collect(Collectors.joining(","));
        List<Object> valores = new ArrayList<>(c.values());
        valores.add(id);
        valores.add(tenant());
        db.update(
                "insert into radar_produto("
                        + String.join(",", colunas)
                        + ",id,tenant_id) values("
                        + marcadores
                        + ",?,?)",
                valores.toArray());
    }

    private void atualizar(UUID id, Map<String, Object> c) {
        Map<String, Object> semEstoque = new LinkedHashMap<>(c);
        semEstoque.remove("fisico");
        String sets =
                semEstoque.keySet().stream()
                        .map(k -> k + (JSONB.contains(k) ? "=?::jsonb" : "=?"))
                        .collect(Collectors.joining(","));
        List<Object> valores = new ArrayList<>(semEstoque.values());
        valores.add(tenant());
        valores.add(id);
        db.update(
                "update radar_produto set "
                        + sets
                        + ",atualizado_em=now() where tenant_id=? and id=?",
                valores.toArray());
    }

    // ---- clonar e histórico -------------------------------------------------------------------

    /** Colunas que não passam para a cópia: identidade, saldo e datas. */
    private static final Set<String> NAO_CLONA =
            Set.of(
                    "id", "tenant_id", "criado_em", "atualizado_em", "excluido_em", "fisico",
                    "reservado", "incompleto", "pai_id");

    /**
     * Copia o produto (e as variações, a composição do kit e os fornecedores) com SKU novo e
     * estoque zero. Imagens só se pedir. Pedidos, anúncios e histórico ficam no original.
     */
    /**
     * "Iniciar histórico de custos": grava o custo de hoje como ponto de partida dos produtos (e
     * variações) que ainda não têm histórico. Daí em diante o gatilho da V035 registra cada
     * mudança. Produto sem custo fica de fora (não há o que registrar).
     */
    Map<String, Object> iniciarCustos(JsonNode n, String papel) {
        permitir(papel, "DONO", "GESTOR", "FINANCEIRO");
        JsonNode lista = n.path("ids");
        if (!lista.isArray() || lista.isEmpty() || lista.size() > 5000)
            erro("Escolha entre 1 e 5000 produtos.");
        List<UUID> ids = new ArrayList<>();
        for (JsonNode i : lista) {
            try {
                ids.add(UUID.fromString(i.asText()));
            } catch (IllegalArgumentException e) {
                erro("Identificador inválido.");
            }
        }
        UUID[] arr = ids.toArray(UUID[]::new);
        int iniciados =
                db.update(
                        "insert into radar_custo_historico(tenant_id,produto_id,depois,motivo,"
                                + "usuario_id) select p.tenant_id,p.id,p.custo,'Início do"
                                + " histórico',? from radar_produto p where p.tenant_id=? and"
                                + " (p.id = any(?) or p.pai_id = any(?)) and p.custo is not null"
                                + " and p.excluido_em is null and not exists (select 1 from"
                                + " radar_custo_historico h where h.tenant_id=p.tenant_id and"
                                + " h.produto_id=p.id)",
                        RadarService.usuarioAtual(),
                        tenant(),
                        arr,
                        arr);
        return Map.of(
                "nada",
                iniciados == 0,
                "iniciados",
                iniciados,
                "mensagem",
                iniciados == 0
                        ? "Nada a iniciar: esses produtos já têm histórico de custos (ele começa"
                                + " sozinho no cadastro e a cada mudança de custo) ou estão sem custo."
                        : iniciados
                                + " produto(s) com o histórico de custos iniciado. Cada mudança de"
                                + " custo fica registrada daqui em diante.");
    }

    Map<String, Object> clonar(JsonNode n, String papel) {
        permitir(papel, "DONO", "GESTOR");
        UUID origem = id(n, "id");
        var p = linhaParaAtualizar(origem);
        if (p.get("excluido_em") != null) erro("Este produto está na lixeira. Restaure antes.");
        if (p.get("pai_id") != null) erro("Clone pelo produto principal.");
        UUID novo = UUID.randomUUID();
        String sku = opcional(n, "sku", 80);
        if (sku == null) {
            String modo = (String) RadarConfiguracao.produtos(db, json).get("sku_modo");
            sku = modo.equals("MANUAL") ? livre(p.get("sku") + "-COPIA", novo) : proximoSku();
        } else skuLivre(sku, novo);
        Map<String, Object> c = copia(p);
        c.put("sku", sku);
        String nome = p.get("nome") + " (cópia)";
        c.put("nome", nome.length() > 250 ? nome.substring(0, 250) : nome);
        c.put("origem_cadastro", "MANUAL");
        c.put("fisico", 0);
        inserir(novo, c);

        String skuAntigo = (String) p.get("sku");
        for (var v :
                db.queryForList(
                        "select * from radar_produto where tenant_id=? and pai_id=? order by sku",
                        tenant(),
                        origem)) {
            UUID idVariacao = UUID.randomUUID();
            String skuV = (String) v.get("sku");
            skuV =
                    skuV.startsWith(skuAntigo)
                            ? sku + skuV.substring(skuAntigo.length())
                            : skuV + "-COPIA";
            if (skuV.length() > 80) skuV = skuV.substring(0, 80);
            Map<String, Object> cv = copia(v);
            cv.put("sku", livre(skuV, idVariacao));
            cv.put("pai_id", novo);
            cv.put("nome", c.get("nome") + v.get("nome").toString().substring(
                    Math.min(v.get("nome").toString().length(), p.get("nome").toString().length())));
            cv.put("origem_cadastro", "MANUAL");
            cv.put("fisico", 0);
            inserir(idVariacao, cv);
            if (n.path("imagens").asBoolean(false)) copiarImagens((UUID) v.get("id"), idVariacao);
        }
        db.update(
                "insert into radar_kit_item(tenant_id,kit_id,componente_id,quantidade) select"
                        + " tenant_id,?,componente_id,quantidade from radar_kit_item where"
                        + " tenant_id=? and kit_id=?",
                novo,
                tenant(),
                origem);
        db.update(
                "insert into radar_produto_fornecedor(tenant_id,produto_id,fornecedor_id,"
                        + "codigo_no_fornecedor) select tenant_id,?,fornecedor_id,"
                        + "codigo_no_fornecedor from radar_produto_fornecedor where tenant_id=?"
                        + " and produto_id=?",
                novo,
                tenant(),
                origem);
        if (n.path("imagens").asBoolean(false)) copiarImagens(origem, novo);
        marcarPendencias(novo);
        return Map.of(
                "id", novo, "sku", sku, "mensagem", "Cópia criada com o SKU " + sku + ", estoque zero.");
    }

    private Map<String, Object> copia(Map<String, Object> linha) {
        Map<String, Object> c = new LinkedHashMap<>();
        // As chaves vêm das colunas da própria tabela (select *), não do pedido.
        linha.forEach(
                (k, v) -> {
                    if (!NAO_CLONA.contains(k))
                        c.put(k, JSONB.contains(k) && v != null ? v.toString() : v);
                });
        return c;
    }

    private String livre(String base, UUID id) {
        String sku = base.length() > 76 ? base.substring(0, 76) : base;
        String tentativa = sku;
        for (int i = 2; skuEmUso(tentativa, id); i++) tentativa = sku + "-" + i;
        return tentativa;
    }

    private void copiarImagens(UUID de, UUID para) {
        db.update(
                "insert into radar_produto_imagem(id,tenant_id,produto_id,ordem,tipo_conteudo,"
                        + "dados) select gen_random_uuid(),tenant_id,?,ordem,tipo_conteudo,dados"
                        + " from radar_produto_imagem where tenant_id=? and produto_id=?",
                para,
                tenant(),
                de);
    }

    /**
     * Linha do tempo do produto (e das variações): estoque, pedidos, compras planejadas e
     * alterações do cadastro, com quem fez. Custo só para quem vê o financeiro.
     */
    Map<String, Object> historico(UUID id, String papel, boolean veCusto) {
        // Mesma visibilidade de GET /api/radar: compras só para quem cuida de compra; quem alterou
        // o cadastro (auditoria), só para quem gerencia.
        boolean veCompras = !Set.of("ATENDIMENTO", "ANALISTA", "MARKETING").contains(papel);
        boolean veAlteracoes = Set.of("DONO", "GESTOR").contains(papel);
        var linhas =
                db.queryForList(
                        "select id from radar_produto where tenant_id=? and id=?", tenant(), id);
        if (linhas.isEmpty())
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Produto não encontrado.");
        List<Map<String, Object>> eventos = new ArrayList<>();
        for (var m :
                db.queryForList(
                        "select m.criado_em, m.tipo, m.fisico_delta, m.reserva_delta, m.motivo,"
                                + " p.sku, u.nome ator from radar_movimento m join radar_produto"
                                + " p on p.tenant_id=m.tenant_id and p.id=m.produto_id left join"
                                + " usuario u on u.tenant_id=m.tenant_id and u.id=m.ator where"
                                + " m.tenant_id=? and (p.id=? or p.pai_id=?) order by m.criado_em"
                                + " desc limit 300",
                        tenant(),
                        id,
                        id)) {
            int fisico = ((Number) m.get("fisico_delta")).intValue();
            int reserva = ((Number) m.get("reserva_delta")).intValue();
            String qtd =
                    fisico != 0
                            ? (fisico > 0 ? "+" : "") + fisico + " no estoque"
                            : (reserva > 0 ? "+" : "") + reserva + " reservado";
            eventos.add(
                    evento(
                            m.get("criado_em"),
                            "ESTOQUE",
                            rotuloMovimento((String) m.get("tipo")) + " · " + qtd,
                            m.get("motivo") + " (" + m.get("sku") + ")",
                            m.get("ator")));
        }
        for (var v :
                db.queryForList(
                        "select v.criado_em, v.numero, v.canal, v.quantidade, v.preco, v.estado,"
                                + " p.sku from radar_pedido v join radar_produto p on"
                                + " p.tenant_id=v.tenant_id and p.id=v.produto_id where"
                                + " v.tenant_id=? and (p.id=? or p.pai_id=?) order by v.criado_em"
                                + " desc limit 300",
                        tenant(),
                        id,
                        id))
            eventos.add(
                    evento(
                            v.get("criado_em"),
                            "VENDA",
                            "Pedido " + v.get("numero") + " · " + v.get("canal"),
                            v.get("quantidade")
                                    + " × R$ "
                                    + v.get("preco")
                                    + " · "
                                    + v.get("estado").toString().toLowerCase()
                                    + " ("
                                    + v.get("sku")
                                    + ")",
                            null));
        for (var r :
                !veCompras
                        ? List.<Map<String, Object>>of()
                        : db.queryForList(
                        "select r.criado_em, r.dados->>'quantidade' quantidade,"
                                + " r.dados->>'custo_unitario' custo, r.dados->>'recebida_em'"
                                + " recebida from radar_registro r where r.tenant_id=? and"
                                + " r.tipo='COMPRA' and r.dados->>'produto_id' in (select"
                                + " id::text from radar_produto where tenant_id=? and (id=? or"
                                + " pai_id=?)) order by r.criado_em desc limit 100",
                        tenant(),
                        tenant(),
                        id,
                        id))
            eventos.add(
                    evento(
                            r.get("criado_em"),
                            "COMPRA",
                            r.get("recebida") == null ? "Compra planejada" : "Compra recebida",
                            r.get("quantidade")
                                    + " un."
                                    + (veCusto ? " a R$ " + r.get("custo") + " cada" : ""),
                            null));
        for (var c :
                !veCusto
                        ? List.<Map<String, Object>>of()
                        : db.queryForList(
                                "select h.criado_em, h.antes, h.depois, h.motivo, p.sku, u.nome"
                                        + " ator from radar_custo_historico h join radar_produto p"
                                        + " on p.tenant_id=h.tenant_id and p.id=h.produto_id left"
                                        + " join usuario u on u.tenant_id=h.tenant_id and"
                                        + " u.id=h.usuario_id where h.tenant_id=? and (p.id=? or"
                                        + " p.pai_id=?) order by h.criado_em desc limit 300",
                                tenant(),
                                id,
                                id))
            eventos.add(
                    evento(
                            c.get("criado_em"),
                            "CUSTO",
                            (c.get("antes") == null ? "" : reais(c.get("antes")) + " → ")
                                    + (c.get("depois") == null ? "sem custo" : reais(c.get("depois"))),
                            c.get("motivo") + " (" + c.get("sku") + ")",
                            c.get("ator")));
        for (var a :
                !veAlteracoes
                        ? List.<Map<String, Object>>of()
                        : db.queryForList(
                        "select a.criado_em, a.operacao, a.detalhes->'resultado'->'antes' antes,"
                                + " a.detalhes->'parametros'->>'acao' acao, u.nome ator from"
                                + " radar_auditoria a left join usuario u on"
                                + " u.tenant_id=a.tenant_id and u.id=a.ator where a.tenant_id=?"
                                + " and (a.recurso=? or a.detalhes->'parametros'->'ids' @>"
                                + " jsonb_build_array(?::text))"
                                + " order by a.criado_em desc limit 300",
                        tenant(),
                        id.toString(),
                        id.toString())) {
            String detalhe = "";
            if (a.get("antes") != null) {
                try {
                    JsonNode antes = json.readTree(a.get("antes").toString());
                    detalhe =
                            "Antes: "
                                    + antes.path("nome").asText()
                                    + " · preço R$ "
                                    + antes.path("preco").asText()
                                    + (veCusto ? " · custo R$ " + antes.path("custo").asText() : "");
                } catch (Exception e) {
                    detalhe = "";
                }
            }
            eventos.add(
                    evento(
                            a.get("criado_em"),
                            "CADASTRO",
                            rotuloOperacao((String) a.get("operacao"), (String) a.get("acao")),
                            detalhe,
                            a.get("ator")));
        }
        eventos.sort(
                (x, y) -> y.get("quando").toString().compareTo(x.get("quando").toString()));
        return Map.of("eventos", eventos.size() > 500 ? eventos.subList(0, 500) : eventos);
    }

    private static Map<String, Object> evento(
            Object quando, String tipo, String titulo, String detalhe, Object quem) {
        Map<String, Object> e = new LinkedHashMap<>();
        e.put("quando", quando instanceof java.sql.Timestamp t ? t.toInstant().toString() : quando.toString());
        e.put("tipo", tipo);
        e.put("titulo", titulo);
        e.put("detalhe", detalhe);
        e.put("quem", quem);
        return e;
    }

    private static String rotuloMovimento(String tipo) {
        return switch (tipo) {
            case "ABERTURA" -> "Saldo inicial";
            case "AJUSTE" -> "Ajuste de estoque";
            case "COMPRA" -> "Entrada de compra";
            case "RESERVA" -> "Reserva de pedido";
            case "SAIDA", "EXPEDICAO" -> "Saída de pedido";
            default -> tipo.charAt(0) + tipo.substring(1).toLowerCase().replace('_', ' ');
        };
    }

    /** R$ no formato brasileiro (1.234,56). */
    private static String reais(Object v) {
        return String.format(Locale.forLanguageTag("pt-BR"), "R$ %,.2f", (BigDecimal) v);
    }

    private static String rotuloOperacao(String op, String acao) {
        return switch (op) {
            case "produto_salvar" -> "Cadastro salvo";
            case "produto_clonar" -> "Produto clonado";
            case "custos_iniciar" -> "Histórico de custos iniciado";
            case "imagem_adicionar" -> "Imagem adicionada";
            case "imagem_remover" -> "Imagem removida";
            case "imagem_principal" -> "Imagem principal trocada";
            case "produtos_lote" -> "Ação em lote: " + (acao == null ? "" : acao.toLowerCase().replace('_', ' '));
            default -> op.replace('_', ' ');
        };
    }

    // ---- lote --------------------------------------------------------------------------------

    /**
     * Ações em lote sobre os produtos marcados. Valem também para as variações dos produtos
     * marcados (é a variação que é vendida e estocada).
     */
    Map<String, Object> lote(JsonNode n, String papel) {
        permitir(papel, "DONO", "GESTOR");
        List<UUID> marcados = new ArrayList<>();
        JsonNode lista = n.path("ids");
        if (!lista.isArray() || lista.isEmpty() || lista.size() > MAX_LOTE)
            erro("Escolha entre 1 e " + MAX_LOTE + " produtos.");
        for (JsonNode i : lista) {
            try {
                UUID u = UUID.fromString(i.asText());
                if (!marcados.contains(u)) marcados.add(u);
            } catch (IllegalArgumentException e) {
                erro("Identificador inválido.");
            }
        }
        UUID[] arr = marcados.toArray(UUID[]::new);
        Integer existentes =
                db.queryForObject(
                        "select count(*) from radar_produto where tenant_id=? and id = any(?)",
                        Integer.class,
                        tenant(),
                        arr);
        if (existentes == null || existentes != marcados.size())
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Produto não encontrado.");
        String acao = n.path("acao").asText("");
        // Restaurar e apagar de vez valem para o que está na lixeira; o resto, para o que não está.
        boolean daLixeira = Set.of("RESTAURAR", "EXCLUIR_DEFINITIVO").contains(acao);
        List<UUID> alvo =
                db.queryForList(
                        "select id from radar_produto where tenant_id=? and (id = any(?) or pai_id"
                                + " = any(?)) and (excluido_em is not null) = ?",
                        UUID.class,
                        tenant(),
                        arr,
                        arr,
                        daLixeira);
        // Trava as linhas: ninguém cria pedido ou anúncio para elas entre a checagem e a mudança.
        db.queryForList(
                "select id from radar_produto where tenant_id=? and id = any(?) for update",
                tenant(),
                alvo.toArray(UUID[]::new));
        if (alvo.isEmpty())
            erro(
                    daLixeira
                            ? "Nenhum dos produtos escolhidos está na lixeira."
                            : "Os produtos escolhidos estão na lixeira. Restaure antes.");
        return switch (acao) {
            case "EDITAR" -> {
                var r = editarEmMassa(n, alvo);
                recalcularPendencias(alvo);
                yield r;
            }
            case "PREENCHER" -> {
                if (marcados.size() != 1) erro("Preencha um produto por vez.");
                var r = preencher(n, marcados.getFirst(), alvo);
                recalcularPendencias(alvo);
                yield r;
            }
            case "TAGS" -> alterarTags(n, alvo);
            case "INATIVAR", "ATIVAR" -> {
                int k =
                        db.update(
                                "update radar_produto set permite_venda=?,atualizado_em=now()"
                                        + " where tenant_id=? and id = any(?)",
                                acao.equals("ATIVAR"),
                                tenant(),
                                alvo.toArray(UUID[]::new));
                yield Map.of(
                        "mensagem",
                        k
                                + (acao.equals("ATIVAR")
                                        ? " produto(s) liberados para venda."
                                        : " produto(s) inativados: saem da venda, o histórico"
                                                + " continua."));
            }
            case "EXCLUIR_ANEXOS" -> {
                int k =
                        db.update(
                                "delete from radar_produto_imagem where tenant_id=? and produto_id"
                                        + " = any(?)",
                                tenant(),
                                alvo.toArray(UUID[]::new));
                yield Map.of("mensagem", k + " imagem(ns) removida(s) dos produtos.");
            }
            case "EXCLUIR" -> paraLixeira(alvo);
            case "RESTAURAR" -> {
                int k =
                        db.update(
                                "update radar_produto set excluido_em=null,atualizado_em=now()"
                                        + " where tenant_id=? and id = any(?)",
                                tenant(),
                                alvo.toArray(UUID[]::new));
                yield Map.of(
                        "alterados",
                        k,
                        "mensagem",
                        k
                                + " produto(s) restaurado(s). Voltam inativos: libere para venda"
                                + " quando quiser.");
            }
            case "EXCLUIR_DEFINITIVO" -> excluirDeVez(alvo);
            default -> {
                erro("Ação em lote não reconhecida.");
                yield Map.of();
            }
        };
    }

    /**
     * Preenchimento rápido de pendência (um produto por vez): além dos campos de "editar em massa",
     * aceita o que é único de cada produto (código de barras, descrição) e o fornecedor.
     */
    private Map<String, Object> preencher(JsonNode n, UUID produto, List<UUID> alvo) {
        String campo = n.path("campo").asText("");
        switch (campo) {
            case "gtin" -> {
                String gtin = opcional(n, "valor", 14);
                if (gtin == null || !gtinValido(gtin))
                    erro("Código de barras (GTIN) inválido: confira os dígitos.");
                db.update(
                        "update radar_produto set gtin=?, motivo_sem_gtin=null, atualizado_em=now()"
                                + " where tenant_id=? and id=?",
                        gtin,
                        tenant(),
                        produto);
            }
            case "motivo_sem_gtin" -> {
                String motivo = opcional(n, "valor", 30);
                if (motivo == null || !MOTIVOS_SEM_GTIN.contains(motivo))
                    erro("Escolha o motivo de não ter código de barras.");
                db.update(
                        "update radar_produto set motivo_sem_gtin=?, atualizado_em=now() where"
                                + " tenant_id=? and (id=? or pai_id=?) and gtin is null",
                        motivo,
                        tenant(),
                        produto,
                        produto);
            }
            case "descricao" -> {
                String descricao = textoOuVazio(n, "valor", 20000);
                if (descricao.isBlank()) erro("A descrição é obrigatória.");
                db.update(
                        "update radar_produto set descricao=?, atualizado_em=now() where"
                                + " tenant_id=? and id=?",
                        descricao,
                        tenant(),
                        produto);
            }
            case "fornecedor_id" -> {
                UUID fornecedor = id(n, "valor");
                Integer eh =
                        db.queryForObject(
                                "select count(*) from radar_cliente where tenant_id=? and id=?"
                                        + " and tipos_contato @> '[\"FORNECEDOR\"]'",
                                Integer.class,
                                tenant(),
                                fornecedor);
                if (eh == null || eh == 0) erro("Fornecedor não encontrado.");
                db.update(
                        "insert into radar_produto_fornecedor(tenant_id,produto_id,fornecedor_id)"
                                + " select ?,?,? where not exists (select 1 from"
                                + " radar_produto_fornecedor where tenant_id=? and produto_id=?"
                                + " and fornecedor_id=?)",
                        tenant(),
                        produto,
                        fornecedor,
                        tenant(),
                        produto,
                        fornecedor);
            }
            default -> {
                return editarEmMassa(n, alvo);
            }
        }
        return Map.of("mensagem", "Produto atualizado.");
    }

    private Map<String, Object> editarEmMassa(JsonNode n, List<UUID> alvo) {
        String campo = n.path("campo").asText("");
        String tipo = CAMPOS_LOTE.get(campo);
        if (tipo == null) erro("Campo não pode ser editado em massa.");
        UUID[] ids = alvo.toArray(UUID[]::new);
        String modo = n.path("modo").asText("DEFINIR");
        int k;
        if (tipo.equals("DINHEIRO") && !modo.equals("DEFINIR")) {
            // Reajuste: percentual com até duas casas, ou valor somado; resultado com 2 casas,
            // arredondado meio para cima (regra 2).
            BigDecimal fator = decimalOpcional(n, "valor", 2);
            if (fator == null) erro("Informe o percentual ou o valor do reajuste.");
            String expressao =
                    switch (modo) {
                        case "AUMENTAR_PCT" -> campo + " * (1 + ?/100)";
                        case "REDUZIR_PCT" -> campo + " * (1 - ?/100)";
                        case "SOMAR" -> campo + " + ?";
                        case "SUBTRAIR" -> campo + " - ?";
                        default -> {
                            erro("Modo de reajuste inválido.");
                            yield "";
                        }
                    };
            if (modo.equals("REDUZIR_PCT") && fator.compareTo(new BigDecimal("100")) >= 0)
                erro("A redução precisa ser menor que 100%.");
            String filtroKit = campo.equals("custo") ? " and tipo<>'KIT'" : "";
            // Preço e custo novos não podem ficar negativos nem zerar o preço.
            Integer invalidos =
                    db.queryForObject(
                            "select count(*) from radar_produto where tenant_id=? and id = any(?)"
                                    + " and "
                                    + campo
                                    + " is not null and round("
                                    + expressao
                                    + ", 2) "
                                    + (campo.equals("preco") ? "<= 0" : "< 0")
                                    + filtroKit,
                            Integer.class,
                            tenant(),
                            ids,
                            fator);
            if (invalidos != null && invalidos > 0)
                erro("O reajuste deixaria " + invalidos + " produto(s) com valor inválido.");
            promocionalAcima(campo, "round(" + expressao + ", 2)", fator, ids);
            k =
                    db.update(
                            "update radar_produto set "
                                    + campo
                                    + " = round("
                                    + expressao
                                    + ", 2), atualizado_em=now() where tenant_id=? and id = any(?)"
                                    + " and "
                                    + campo
                                    + " is not null"
                                    + filtroKit,
                            fator,
                            tenant(),
                            ids);
        } else {
            Object valor = valorDoLote(n, campo, tipo);
            if (valor != null) promocionalAcima(campo, "?", valor, ids);
            String filtroKit = campo.equals("custo") ? " and tipo<>'KIT'" : "";
            // campo vem só de CAMPOS_LOTE (chaves fixas), nunca do texto da requisição.
            k =
                    db.update(
                            "update radar_produto set "
                                    + campo
                                    + " = ?, atualizado_em=now() where tenant_id=? and id = any(?)"
                                    + filtroKit,
                            valor,
                            tenant(),
                            ids);
        }
        return Map.of(
                "mensagem",
                k
                        + " produto(s) atualizado(s)."
                        + (campo.equals("custo") && temKit(ids)
                                ? " O custo de kit vem dos componentes e não muda."
                                : ""));
    }

    private boolean temKit(UUID[] ids) {
        Integer kits =
                db.queryForObject(
                        "select count(*) from radar_produto where tenant_id=? and id = any(?) and"
                                + " tipo='KIT'",
                        Integer.class,
                        tenant(),
                        ids);
        return kits != null && kits > 0;
    }

    /** Valor validado do campo; vazio limpa o campo quando ele é opcional. */
    private Object valorDoLote(JsonNode n, String campo, String tipo) {
        String texto = n.path("valor").asText("").trim();
        boolean vazio = texto.isEmpty();
        Set<String> obrigatorios =
                Set.of(
                        "preco",
                        "custo",
                        "marca",
                        "categoria_id",
                        "ncm",
                        "origem",
                        "unidade",
                        "condicao",
                        "peso_bruto_kg",
                        "controla_estoque",
                        "permite_venda");
        if (vazio && obrigatorios.contains(campo))
            erro("Esse campo é obrigatório no produto: informe um valor.");
        return switch (tipo) {
            case "DINHEIRO" -> {
                if (vazio) yield null;
                BigDecimal v = valor(n, "valor");
                if (campo.equals("preco") && v.signum() == 0)
                    erro("Preço deve ser maior que zero.");
                yield v;
            }
            case "TEXTO" -> textoOuVazio(n, "valor", 120);
            case "CATEGORIA" -> {
                if (vazio) yield null;
                UUID id = id(n, "valor");
                existe("radar_categoria", id, "Categoria não encontrada.");
                yield id;
            }
            case "EMBALAGEM" -> {
                if (vazio) yield null;
                UUID id = id(n, "valor");
                existe("radar_embalagem", id, "Embalagem não encontrada.");
                yield id;
            }
            case "NCM" -> digitos(n, "valor", 8, "NCM deve ter 8 dígitos.");
            case "CEST" -> digitos(n, "valor", 7, "CEST deve ter 7 dígitos.");
            case "ORIGEM" -> inteiroOpcional(n, "valor", 0, 8);
            case "UNIDADE" -> {
                String u = texto.toUpperCase();
                if (!UNIDADES.contains(u)) erro("Unidade inválida.");
                yield u;
            }
            case "CONDICAO" ->
                    escolha(n, "valor", Set.of("NOVO", "USADO", "RECONDICIONADO"), "NOVO");
            case "GARANTIA" ->
                    escolhaOpcional(n, "valor", Set.of("VENDEDOR", "FABRICANTE", "SEM_GARANTIA"));
            case "INTEIRO" -> {
                Integer v = inteiroOpcional(n, "valor", 0, 1_000_000);
                if (v == null && (campo.equals("minimo"))) yield 0;
                yield v;
            }
            case "PESO" -> decimalOpcional(n, "valor", 3);
            case "MEDIDA" -> medidaOpcional(n, "valor");
            case "SIM_NAO" -> {
                if (!Set.of("true", "false").contains(texto)) erro("Escolha Sim ou Não.");
                yield Boolean.parseBoolean(texto);
            }
            default -> {
                erro("Campo não pode ser editado em massa.");
                yield null;
            }
        };
    }

    /**
     * Antes de gravar preço ou promocional em lote: promoção igual ou acima do preço confunde o
     * cliente e o marketplace recusa. {@code novo} é a expressão SQL do valor novo (com um "?").
     */
    private void promocionalAcima(String campo, String novo, Object parametro, UUID[] ids) {
        String condicao =
                switch (campo) {
                    case "preco" ->
                            "preco_promocional is not null and preco_promocional >= " + novo;
                    case "preco_promocional" -> "preco > 0 and " + novo + " >= preco";
                    default -> null;
                };
        if (condicao == null) return;
        Integer acima =
                db.queryForObject(
                        "select count(*) from radar_produto where tenant_id=? and id = any(?) and "
                                + condicao,
                        Integer.class,
                        tenant(),
                        ids,
                        parametro);
        if (acima != null && acima > 0)
            erro(
                    acima
                            + " produto(s) ficariam com o preço promocional igual ou maior que o preço"
                            + " de venda. Ajuste o promocional antes.");
    }

    private Map<String, Object> alterarTags(JsonNode n, List<UUID> alvo) {
        String modo = n.path("modo").asText("ADICIONAR");
        String tags = listaDeTextos(n, "tags", 30, 60);
        UUID[] ids = alvo.toArray(UUID[]::new);
        String sql =
                switch (modo) {
                    case "ADICIONAR" ->
                            "update radar_produto set tags = (select coalesce(jsonb_agg(distinct"
                                    + " t), '[]'::jsonb) from jsonb_array_elements_text(tags ||"
                                    + " ?::jsonb) t), atualizado_em=now() where tenant_id=? and id"
                                    + " = any(?)";
                    case "REMOVER" ->
                            "update radar_produto set tags = (select coalesce(jsonb_agg(t),"
                                    + " '[]'::jsonb) from jsonb_array_elements_text(tags) t where"
                                    + " not (to_jsonb(t) <@ ?::jsonb)), atualizado_em=now() where"
                                    + " tenant_id=? and id = any(?)";
                    case "SUBSTITUIR" ->
                            "update radar_produto set tags = ?::jsonb, atualizado_em=now() where"
                                    + " tenant_id=? and id = any(?)";
                    default -> {
                        erro("Modo de tags inválido.");
                        yield "";
                    }
                };
        int k = db.update(sql, tags, tenant(), ids);
        return Map.of("mensagem", "Tags alteradas em " + k + " produto(s).");
    }

    /**
     * Exclui só produto sem histórico: pedido, anúncio, movimento de estoque ou uso como componente
     * de kit mantêm o produto (o número precisa continuar rastreável). Para esses, a saída é
     * inativar.
     */
    /**
     * Manda para a lixeira: sai da venda e das listas, mas pedidos, estoque e histórico continuam
     * apontando para ele. Não exclui componente de kit ativo (o kit ficaria sem estoque) nem
     * produto com anúncio no ar.
     */
    private Map<String, Object> paraLixeira(List<UUID> alvo) {
        UUID[] ids = alvo.toArray(UUID[]::new);
        List<String> emKit =
                db.queryForList(
                        "select distinct p.sku from radar_produto p join radar_kit_item k on"
                                + " k.tenant_id=p.tenant_id and k.componente_id=p.id join"
                                + " radar_produto kit on kit.tenant_id=k.tenant_id and"
                                + " kit.id=k.kit_id where p.tenant_id=? and p.id = any(?) and"
                                + " kit.excluido_em is null and not (kit.id = any(?)) order by"
                                + " p.sku",
                        String.class,
                        tenant(),
                        ids,
                        ids);
        if (!emKit.isEmpty())
            erro(
                    "Estes produtos fazem parte de um kit ativo: "
                            + String.join(", ", emKit)
                            + ". Tire do kit ou exclua o kit junto.");
        List<String> noAr =
                db.queryForList(
                        "select distinct p.sku from radar_produto p join radar_anuncio a on"
                                + " a.tenant_id=p.tenant_id and a.produto_id=p.id where"
                                + " p.tenant_id=? and p.id = any(?) and a.situacao_ecommerce"
                                + " in ('ATIVO','PAUSADO') order by p.sku",
                        String.class,
                        tenant(),
                        ids);
        if (!noAr.isEmpty())
            erro(
                    "Estes produtos têm anúncio no marketplace: "
                            + String.join(", ", noAr)
                            + ". Encerre os anúncios antes de excluir.");
        int k =
                db.update(
                        "update radar_produto set excluido_em=now(),permite_venda=false,"
                                + "atualizado_em=now() where tenant_id=? and id = any(?)",
                        tenant(),
                        ids);
        return Map.of(
                "alterados",
                k,
                "mensagem",
                k + " produto(s) na lixeira. Dá para restaurar quando quiser.");
    }

    /** Apaga de vez, da lixeira, só o que não tem histórico nenhum. */
    private Map<String, Object> excluirDeVez(List<UUID> alvo) {
        UUID[] ids = alvo.toArray(UUID[]::new);
        List<String> presos =
                db.queryForList(
                        "select p.sku from radar_produto p where p.tenant_id=? and p.id = any(?)"
                                + " and (exists(select 1 from radar_pedido x where"
                                + " x.tenant_id=p.tenant_id and x.produto_id=p.id) or exists(select"
                                + " 1 from radar_anuncio x where x.tenant_id=p.tenant_id and"
                                + " x.produto_id=p.id) or exists(select 1 from radar_movimento x"
                                + " where x.tenant_id=p.tenant_id and x.produto_id=p.id) or"
                                + " exists(select 1 from radar_kit_item x where"
                                + " x.tenant_id=p.tenant_id and x.componente_id=p.id and not"
                                + " (x.kit_id = any(?))) or exists(select 1 from radar_promocao x"
                                + " where x.tenant_id=p.tenant_id and x.produto_id=p.id) or"
                                + " exists(select 1 from radar_registro x where"
                                + " x.tenant_id=p.tenant_id and x.tipo='COMPRA' and"
                                + " x.dados->>'produto_id'=p.id::text)) order by p.sku",
                        String.class,
                        tenant(),
                        ids,
                        ids);
        if (!presos.isEmpty())
            erro(
                    "Não dá para apagar de vez produto com histórico (pedido, anúncio, estoque,"
                            + " kit, promoção ou compra): "
                            + String.join(", ", presos)
                            + ". Ele pode ficar na lixeira para sempre, sem atrapalhar.");
        for (String tabela : List.of("radar_produto_imagem", "radar_produto_fornecedor"))
            db.update(
                    "delete from " + tabela + " where tenant_id=? and produto_id = any(?)",
                    tenant(),
                    ids);
        db.update(
                "delete from radar_kit_item where tenant_id=? and kit_id = any(?)", tenant(), ids);
        // Variações antes do produto principal (pai_id aponta para ele).
        db.update(
                "delete from radar_produto where tenant_id=? and id = any(?) and pai_id is not"
                        + " null",
                tenant(),
                ids);
        db.update("delete from radar_produto where tenant_id=? and id = any(?)", tenant(), ids);
        return Map.of("mensagem", alvo.size() + " produto(s) apagado(s) de vez.");
    }

    private Map<String, Object> linhaParaAtualizar(UUID id) {
        var linhas =
                db.queryForList(
                        "select * from radar_produto where tenant_id=? and id=? for update",
                        tenant(),
                        id);
        if (linhas.isEmpty())
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Produto não encontrado.");
        return linhas.getFirst();
    }

    private void skuLivre(String sku, UUID id) {
        Integer outros =
                db.queryForObject(
                        "select count(*) from radar_produto where tenant_id=? and sku=? and id<>?",
                        Integer.class,
                        tenant(),
                        sku,
                        id);
        if (outros != null && outros > 0) erro("SKU já cadastrado: " + sku);
    }

    private void existe(String tabela, UUID id, String mensagem) {
        Integer n =
                db.queryForObject(
                        "select count(*) from " + tabela + " where tenant_id=? and id=?",
                        Integer.class,
                        tenant(),
                        id);
        if (n == null || n == 0) erro(mensagem);
    }

    private void movimento(UUID produtoId, String tipo, int quantidade, String motivo) {
        db.update(
                "insert into"
                    + " radar_movimento(id,tenant_id,produto_id,tipo,fisico_delta,reserva_delta,motivo,ator)"
                    + " values(?,?,?,?,?,0,?,?)",
                UUID.randomUUID(),
                tenant(),
                produtoId,
                tipo,
                quantidade,
                motivo,
                RadarService.usuarioAtual());
    }

    // ---- utilitários de entrada --------------------------------------------------------------

    private static int valorInteiro(JsonNode n, String campo, int min, int max) {
        Integer v = inteiroOpcional(n, campo, min, max);
        return v == null ? 0 : v;
    }

    private static String textoOuVazio(JsonNode n, String campo, int max) {
        String s = opcional(n, campo, max);
        return s == null ? "" : s;
    }

    private static String digitos(JsonNode n, String campo, int tamanho, String mensagem) {
        String s = n.path(campo).asText("").replaceAll("[.\\s-]", "");
        if (s.isBlank()) return null;
        if (!s.matches("[0-9]{" + tamanho + "}")) erro(mensagem);
        return s;
    }

    private static String escolha(JsonNode n, String campo, Set<String> opcoes, String padrao) {
        String s = n.path(campo).asText("").trim();
        if (s.isBlank()) return padrao;
        if (!opcoes.contains(s)) erro("Opção inválida: " + campo);
        return s;
    }

    private static String escolhaOpcional(JsonNode n, String campo, Set<String> opcoes) {
        String s = n.path(campo).asText("").trim();
        if (s.isBlank()) return null;
        if (!opcoes.contains(s)) erro("Opção inválida: " + campo);
        return s;
    }

    private String listaDeTextos(JsonNode n, String campo, int maxItens, int maxTamanho) {
        JsonNode lista = n.path(campo);
        if (lista.isMissingNode() || lista.isNull()) return "[]";
        if (!lista.isArray() || lista.size() > maxItens) erro("Lista inválida: " + campo);
        List<String> out = new ArrayList<>();
        for (JsonNode item : lista) {
            String s = item.asText("").trim();
            if (s.isBlank()) continue;
            if (s.length() > maxTamanho)
                erro(
                        "Cada item de "
                                + RadarEntrada.nomeDoCampo(campo)
                                + " aceita até "
                                + maxTamanho
                                + " caracteres.");
            if (!out.contains(s)) out.add(s);
        }
        return paraJson(out);
    }

    private String paresNomeValor(JsonNode n, String campo) {
        JsonNode lista = n.path(campo);
        if (lista.isMissingNode() || lista.isNull()) return "[]";
        if (!lista.isArray() || lista.size() > 50) erro("Lista inválida: " + campo);
        List<Map<String, String>> out = new ArrayList<>();
        for (JsonNode item : lista) {
            String nome = item.path("nome").asText("").trim();
            String valorItem = item.path("valor").asText("").trim();
            if (nome.isBlank() && valorItem.isBlank()) continue;
            if (nome.isBlank() || nome.length() > 80 || valorItem.length() > 500)
                erro("Confira os itens de " + campo + ".");
            out.add(Map.of("nome", nome, "valor", valorItem));
        }
        return paraJson(out);
    }

    private String paraJson(Object valor) {
        try {
            return json.writeValueAsString(valor);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @SuppressWarnings("unchecked")
    private List<String> lerLista(String texto) {
        try {
            return json.readValue(texto, List.class);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static UUID tenant() {
        return ContextoTenant.atual();
    }
}
