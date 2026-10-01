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

    static final Set<String> OPERACOES = Set.of("produto_salvar");

    static final int MAX_TIPOS_VARIACAO = 3;
    static final int MAX_IMAGENS = 12;
    static final int MAX_BYTES_IMAGEM = 2 * 1024 * 1024;
    private static final Set<String> TIPOS_IMAGEM = Set.of("image/jpeg", "image/png", "image/webp");
    private static final Set<String> UNIDADES =
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
            if (antes.get("pai_id") != null) erro("Edite a variação pelo produto principal.");
            if (!antes.get("tipo").equals(tipo))
                erro("O tipo do produto não muda depois de criado. Cadastre um novo produto.");
        }

        Map<String, Object> c = colunasComuns(n);
        c.put("tipo", tipo);
        c.put("sku", texto(n, "sku", 80));
        c.put("nome", texto(n, "nome", 250));
        c.put("preco", valor(n, "preco"));
        c.put("categoria_id", categoria(n));
        c.put("embalagem_id", embalagem(n));
        c.put("tipos_variacao", tipo.equals("VARIACAO") ? tiposVariacao(n) : "[]");

        List<Object[]> componentes = tipo.equals("KIT") ? componentes(n, id) : List.of();
        c.put("custo", tipo.equals("KIT") ? custoDoKit(componentes) : valor(n, "custo"));
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

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("id", id);
        r.put("mensagem", novo ? "Produto cadastrado." : "Produto atualizado.");
        if (antes != null)
            r.put(
                    "antes",
                    Map.of(
                            "nome", antes.get("nome"),
                            "custo", antes.get("custo").toString(),
                            "preco", antes.get("preco").toString()));
        return r;
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
            c.put("sku", texto(v, "sku", 80));
            c.put(
                    "preco",
                    v.path("preco").asText("").isBlank() ? pai.get("preco") : valor(v, "preco"));
            c.put(
                    "custo",
                    v.path("custo").asText("").isBlank() ? pai.get("custo") : valor(v, "custo"));
            c.put("preco_promocional", valorOpcional(v, "preco_promocional"));
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
                            "select tipo, custo from radar_produto where tenant_id=? and id=?",
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
            existe("radar_fornecedor", fornecedor, "Fornecedor não encontrado.");
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
                        // Salvo pela tela, o produto criado por anúncio importado deixa de
                        // ser incompleto.
                        + ",incompleto=false,atualizado_em=now() where tenant_id=? and id=?",
                valores.toArray());
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
            if (s.length() > maxTamanho) erro("Item muito longo em " + campo);
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
