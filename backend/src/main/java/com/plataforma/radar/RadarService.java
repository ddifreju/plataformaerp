package com.plataforma.radar;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.plataforma.autenticacao.UsuarioAutenticado;
import com.plataforma.comum.tenant.ContextoTenant;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.*;

/** Local operational core. External effects deliberately require a future certified connector. */
@Service
public class RadarService {
    private final JdbcTemplate db;
    private final ObjectMapper json;
    private final RadarCadastros cadastros;
    private final RadarPromocoes promocoes;
    private final RadarRelatorios relatorios;
    private final RadarProdutos produtos;
    private final RadarClientes clientes;
    private final RadarAnuncios anuncios;
    private final RadarVendedores vendedores;
    private final RadarEmpresa empresa;
    private static final Set<String> CANAIS =
            Set.of("Mercado Livre", "Shopee", "TikTok Shop", "SHEIN");

    public RadarService(
            JdbcTemplate db,
            ObjectMapper json,
            RadarCadastros cadastros,
            RadarPromocoes promocoes,
            RadarRelatorios relatorios,
            RadarProdutos produtos,
            RadarClientes clientes,
            RadarAnuncios anuncios,
            RadarVendedores vendedores,
            RadarEmpresa empresa) {
        this.db = db;
        this.json = json;
        this.cadastros = cadastros;
        this.promocoes = promocoes;
        this.relatorios = relatorios;
        this.produtos = produtos;
        this.clientes = clientes;
        this.anuncios = anuncios;
        this.vendedores = vendedores;
        this.empresa = empresa;
    }

    /** Usuário da requisição atual, para registrar quem fez cada movimento. */
    static UUID usuarioAtual() {
        return ((UsuarioAutenticado)
                        SecurityContextHolder.getContext().getAuthentication().getPrincipal())
                .usuarioId();
    }

    @Transactional
    public UUID adicionarImagem(UUID produtoId, byte[] dados, String tipoConteudo) {
        UUID id = produtos.adicionarImagem(papel(), produtoId, dados, tipoConteudo);
        auditar("imagem_adicionar", produtoId.toString(), Map.of("imagem", id));
        return id;
    }

    @Transactional
    public void removerImagem(UUID imagemId) {
        produtos.removerImagem(papel(), imagemId);
        auditar("imagem_remover", imagemId.toString(), Map.of());
    }

    @Transactional
    public void tornarImagemPrincipal(UUID imagemId) {
        produtos.tornarPrincipal(papel(), imagemId);
        auditar("imagem_principal", imagemId.toString(), Map.of());
    }

    @Transactional(readOnly = true)
    public Map<String, Object> imagem(UUID imagemId) {
        return produtos.imagem(imagemId);
    }

    /** Cliente completo. Abrir o cadastro fica na auditoria: tem CPF/CNPJ e endereço. */
    @Transactional
    public Map<String, Object> cliente(UUID clienteId) {
        var c = clientes.detalhe(papel(), clienteId);
        auditar("cliente_ver", clienteId.toString(), Map.of());
        return c;
    }

    /** Endereços para etiqueta. Fica na auditoria: sai endereço de cliente. */
    @Transactional
    public List<Map<String, Object>> etiquetas(List<UUID> ids) {
        var e = clientes.etiquetas(papel(), ids);
        auditar("etiquetas_imprimir", "clientes", Map.of("quantidade", ids.size()));
        return e;
    }

    /** Vendedor completo. Abrir fica na auditoria: tem CPF/CNPJ e comissão. */
    @Transactional
    public Map<String, Object> vendedor(UUID vendedorId) {
        var v = vendedores.detalhe(papel(), vendedorId);
        auditar("vendedor_ver", vendedorId.toString(), Map.of());
        return v;
    }

    /** Troca a senha de acesso do vendedor. A auditoria registra quem trocou, nunca a senha. */
    @Transactional
    public void alterarSenhaVendedor(UUID vendedorId, String senha, String confirmacao) {
        vendedores.alterarSenha(papel(), vendedorId, senha, confirmacao);
        auditar("vendedor_senha_alterar", vendedorId.toString(), Map.of());
    }

    /** Cria um usuário do sistema. A auditoria registra quem criou, nunca a senha. */
    @Transactional
    public UUID criarUsuario(com.fasterxml.jackson.databind.JsonNode n) {
        UUID id = empresa.criarUsuario(papel(), n);
        auditar(
                "usuario_criar",
                id.toString(),
                Map.of("papel", n.path("papel").asText(""), "nome", n.path("nome").asText("")));
        return id;
    }

    @Transactional
    public void alterarSenhaUsuario(UUID usuarioId, String senha, String confirmacao) {
        empresa.alterarSenha(papel(), usuarioId, senha, confirmacao);
        auditar("usuario_senha_alterar", usuarioId.toString(), Map.of());
    }

    @Transactional
    public void salvarLogo(byte[] dados, String tipo) {
        empresa.salvarLogo(papel(), dados, tipo);
        auditar("empresa_logo", "empresa", Map.of("bytes", dados.length));
    }

    @Transactional
    public void removerLogo() {
        empresa.removerLogo(papel());
        auditar("empresa_logo_remover", "empresa", Map.of());
    }

    @Transactional(readOnly = true)
    public Map<String, Object> logo() {
        return empresa.logo();
    }

    @Transactional
    public UUID adicionarAnexo(UUID clienteId, String nome, String tipo, byte[] dados) {
        UUID id = clientes.adicionarAnexo(papel(), clienteId, nome, tipo, dados);
        auditar("anexo_adicionar", clienteId.toString(), Map.of("anexo", id));
        return id;
    }

    @Transactional
    public void removerAnexo(UUID anexoId) {
        clientes.removerAnexo(papel(), anexoId);
        auditar("anexo_remover", anexoId.toString(), Map.of());
    }

    @Transactional
    public Map<String, Object> anexo(UUID anexoId) {
        var a = clientes.anexo(papel(), anexoId);
        auditar("anexo_baixar", anexoId.toString(), Map.of());
        return a;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> relatorios(String de, String ate) {
        return relatorios.gerar(papel(), de, ate);
    }

    private UUID tenant() {
        return ContextoTenant.atual();
    }

    private UsuarioAutenticado user() {
        return (UsuarioAutenticado)
                SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }

    private String papel() {
        return db.queryForObject(
                "select papel from usuario where tenant_id=? and id=? and ativo",
                String.class,
                tenant(),
                user().usuarioId());
    }

    private boolean financeiro() {
        return Set.of("DONO", "FINANCEIRO", "GESTOR").contains(papel());
    }

    private void permitir(String... papeis) {
        if (!Set.of(papeis).contains(papel()))
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "Seu cargo não permite esta ação.");
    }

    private String enc(Object v) {
        try {
            return json.writeValueAsString(v);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private Map<String, Object> dec(String s) {
        try {
            return json.readValue(
                    s, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private List<Map<String, Object>> rows(String table) {
        return db.queryForList(
                "select * from " + table + " where tenant_id=? order by criado_em desc limit 1000",
                tenant());
    }

    private Map<String, Object> um(String table, UUID id) {
        var r =
                db.queryForList(
                        "select * from " + table + " where tenant_id=? and id=? for update",
                        tenant(),
                        id);
        if (r.isEmpty())
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Registro não encontrado.");
        return r.getFirst();
    }

    private String texto(JsonNode n, String k, int max) {
        String s = n.path(k).asText("").trim();
        if (s.isBlank() || s.length() > max) erro("Confira o campo " + k + ".");
        return s;
    }

    private UUID id(JsonNode n, String k) {
        try {
            return UUID.fromString(n.path(k).asText());
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Identificador inválido.");
        }
    }

    private void erro(String m) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, m);
    }

    private int inteiro(JsonNode n, String k, int min, int max) {
        if (!n.path(k).canConvertToInt() || !n.path(k).isIntegralNumber())
            erro("Quantidade inválida: " + k);
        int v = n.path(k).asInt();
        if (v < min || v > max) erro("Quantidade fora do limite: " + k);
        return v;
    }

    private BigDecimal valor(JsonNode n, String k) {
        try {
            BigDecimal v = new BigDecimal(n.path(k).asText("0"));
            if (v.signum() < 0 || v.compareTo(new BigDecimal("999999999")) > 0 || v.scale() > 2)
                erro("Valor inválido: " + k);
            return v.setScale(2);
        } catch (NumberFormatException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Valor inválido: " + k);
        }
    }

    private BigDecimal bd(Object n) {
        return new BigDecimal(n.toString());
    }

    private String canal(JsonNode n) {
        String c = texto(n, "canal", 40);
        if (!CANAIS.contains(c)) erro("Canal não suportado.");
        return c;
    }

    /** Parâmetros para a auditoria com CPF/CNPJ mascarado (o completo fica só no cadastro). */
    private static JsonNode semDocumento(JsonNode n) {
        if (!(n instanceof com.fasterxml.jackson.databind.node.ObjectNode o)) return n;
        var copia = o.deepCopy();
        for (String campo : List.of("documento", "cliente_documento"))
            if (copia.hasNonNull(campo))
                copia.put(
                        campo,
                        RadarClientes.mascarar(
                                RadarEntrada.soAlfanumerico(copia.get(campo).asText())));
        return copia;
    }

    private void auditar(String op, String recurso, Object d) {
        db.update(
                "insert into radar_auditoria(id,tenant_id,ator,operacao,recurso,detalhes)"
                        + " values(?,?,?,?,?,?::jsonb)",
                UUID.randomUUID(),
                tenant(),
                user().usuarioId(),
                op,
                recurso,
                enc(d));
    }

    private void movimento(
            UUID produto, UUID pedido, String tipo, int fisico, int reserva, String motivo) {
        db.update(
                "insert into"
                    + " radar_movimento(id,tenant_id,produto_id,pedido_id,tipo,fisico_delta,reserva_delta,motivo,ator)"
                    + " values(?,?,?,?,?,?,?,?,?)",
                UUID.randomUUID(),
                tenant(),
                produto,
                pedido,
                tipo,
                fisico,
                reserva,
                motivo,
                user().usuarioId());
    }

    private void lancar(UUID pedido, String tipo, BigDecimal v, String fonte) {
        db.update(
                "insert into radar_lancamento(id,tenant_id,pedido_id,tipo,valor,fonte)"
                        + " values(?,?,?,?,?,?)",
                UUID.randomUUID(),
                tenant(),
                pedido,
                tipo,
                v,
                fonte);
    }

    private UUID registro(String tipo, Object dados) {
        UUID id = UUID.randomUUID();
        db.update(
                "insert into radar_registro(id,tenant_id,tipo,dados) values(?,?,?,?::jsonb)",
                id,
                tenant(),
                tipo,
                enc(dados));
        return id;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> dados() {
        String p = papel();
        boolean f = financeiro();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("usuario", Map.of("id", user().usuarioId(), "nome", user().nome(), "papel", p));
        out.put("modo", "LOCAL");
        out.put("financeiroPermitido", f);
        var produtos = rows("radar_produto");
        if (!f) produtos.forEach(x -> x.remove("custo"));
        out.put("produtos", produtos);
        out.put("anuncios", rows("radar_anuncio"));
        var pedidos = rows("radar_pedido");
        if (!f)
            pedidos.forEach(
                    x -> {
                        for (String k :
                                List.of(
                                        "custo_unitario",
                                        "comissao",
                                        "frete",
                                        "imposto",
                                        "ads",
                                        "embalagem",
                                        "desconto")) x.remove(k);
                    });
        out.put("pedidos", pedidos);
        out.put("movimentos", rows("radar_movimento"));
        out.put("lancamentos", f ? rows("radar_lancamento") : List.of());
        out.put("titulos", f ? rows("radar_titulo") : List.of());
        out.put(
                "acoes",
                Set.of("DONO", "GESTOR", "MARKETING").contains(p) ? rows("radar_acao") : List.of());
        out.put("auditoria", p.equals("DONO") ? rows("radar_auditoria") : List.of());
        var registros = new ArrayList<Map<String, Object>>();
        Set<String> tipos =
                switch (p) {
                    case "DONO", "GESTOR" ->
                            Set.of(
                                    "MENSAGEM",
                                    "MARCA",
                                    "CONCORRENTE",
                                    "PRECO_REFERENCIA",
                                    "POLITICA",
                                    "FORNECEDOR",
                                    "COMPRA");
                    case "ATENDIMENTO", "ANALISTA" -> Set.of("MENSAGEM");
                    case "MARKETING" -> Set.of("MARCA", "CONCORRENTE", "PRECO_REFERENCIA");
                    case "ESTOQUE" -> Set.of("FORNECEDOR", "COMPRA");
                    default -> Set.of("FORNECEDOR", "COMPRA");
                };
        for (var r : rows("radar_registro"))
            if (tipos.contains(r.get("tipo"))) {
                r.put("dados", dec(r.get("dados").toString()));
                registros.add(r);
            }
        out.put("registros", registros);
        out.put(
                "porCanal",
                f
                        ? db.queryForList(
                                "select p.canal nome,sum(l.valor) resultado from radar_lancamento l"
                                        + " join radar_pedido p on p.id=l.pedido_id and"
                                        + " p.tenant_id=l.tenant_id where l.tenant_id=? group by"
                                        + " p.canal order by p.canal",
                                tenant())
                        : List.of());
        out.put(
                "porSku",
                f
                        ? db.queryForList(
                                "select pr.sku nome,sum(l.valor) resultado from radar_lancamento l"
                                        + " join radar_pedido p on p.id=l.pedido_id and"
                                        + " p.tenant_id=l.tenant_id join radar_produto pr on"
                                        + " pr.id=p.produto_id and pr.tenant_id=p.tenant_id where"
                                        + " l.tenant_id=? group by pr.sku order by pr.sku",
                                tenant())
                        : List.of());
        out.put(
                "divergencias",
                f
                        ? db
                                .queryForList(
                                        "select dados,criado_em from radar_registro where"
                                                + " tenant_id=? and tipo='DIVERGENCIA' order by"
                                                + " criado_em desc limit 1000",
                                        tenant())
                                .stream()
                                .map(
                                        r -> {
                                            r.put("dados", dec(r.get("dados").toString()));
                                            return r;
                                        })
                                .toList()
                        : List.of());
        out.put("resumo", f ? resumo() : Map.of());
        out.put("agentes", agentes());
        out.putAll(cadastros.dados(p, f));
        out.putAll(clientes.dados(p));
        out.putAll(vendedores.dados(p));
        out.putAll(empresa.dados(p));
        out.putAll(promocoes.dados());
        out.putAll(this.produtos.dados());
        normalizarDinheiro(out);
        return out;
    }

    /**
     * Prepara a resposta: dinheiro vira texto decimal (regra 2) e coluna jsonb vira JSON de
     * verdade. O driver devolve jsonb como PGobject, que o Jackson serializaria como objeto {@code
     * {"type":"jsonb","value":"..."}}; comparado pelo nome porque o driver só existe em tempo de
     * execução.
     */
    @SuppressWarnings("unchecked")
    private void normalizarDinheiro(Object value) {
        if (value instanceof Map<?, ?> m) {
            var mutable = (Map<Object, Object>) m;
            for (var key : new ArrayList<>(m.keySet())) {
                Object v = m.get(key);
                if (v instanceof BigDecimal b) mutable.put(key, b.toPlainString());
                else if (v != null && v.getClass().getName().equals("org.postgresql.util.PGobject"))
                    mutable.put(key, jsonDoBanco(v.toString()));
                else normalizarDinheiro(v);
            }
        } else if (value instanceof List<?> l) l.forEach(this::normalizarDinheiro);
    }

    private JsonNode jsonDoBanco(String texto) {
        if (texto == null) return null;
        try {
            return json.readTree(texto);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private Map<String, Object> resumo() {
        var r =
                db.queryForMap(
                        "select coalesce(sum(case when tipo='RECEITA' then valor else 0 end),0)"
                            + " bruto,coalesce(sum(valor),0) resultado from radar_lancamento where"
                            + " tenant_id=?",
                        tenant());
        r.put(
                "estoque",
                db.queryForObject(
                        "select coalesce(sum(custo*fisico),0) from radar_produto where tenant_id=?",
                        BigDecimal.class,
                        tenant()));
        return r;
    }

    private List<Map<String, String>> agentes() {
        return List.of(
                Map.of(
                        "nome",
                        "Import Agent",
                        "estado",
                        "Disponível localmente",
                        "descricao",
                        "Mapeamento de colunas assistido por regras; confirmação antes de"
                                + " importar."),
                Map.of(
                        "nome",
                        "Product Agent",
                        "estado",
                        "Disponível localmente",
                        "descricao",
                        "Identifica NCM e descrição ausentes no cadastro."),
                Map.of(
                        "nome",
                        "Business Copilot",
                        "estado",
                        "Consulta local",
                        "descricao",
                        "Números determinísticos, com memória de cálculo."),
                Map.of(
                        "nome",
                        "Profit Guardian",
                        "estado",
                        "Alertas locais",
                        "descricao",
                        "Detecta pedidos locais com resultado negativo."),
                Map.of(
                        "nome",
                        "Pricing Agent",
                        "estado",
                        "Aprovação humana",
                        "descricao",
                        "Propostas de preço com piso e controle de versão."),
                Map.of(
                        "nome",
                        "Listing Agent",
                        "estado",
                        "Rascunho local",
                        "descricao",
                        "Prepara títulos por canal; publicação externa desconectada."),
                Map.of(
                        "nome",
                        "Support Agent",
                        "estado",
                        "Rascunho local",
                        "descricao",
                        "Sugere resposta de status sem enviar ao cliente."),
                Map.of(
                        "nome",
                        "Reconciliation Agent",
                        "estado",
                        "Conferência manual",
                        "descricao",
                        "Confere valor informado e registra divergências."),
                Map.of(
                        "nome",
                        "Inventory & Purchase Agent",
                        "estado",
                        "Regra de estoque mínimo",
                        "descricao",
                        "Sinaliza saldo baixo; previsão estatística ainda não habilitada."),
                Map.of(
                        "nome",
                        "Fiscal Agent",
                        "estado",
                        "Requer emissor",
                        "descricao",
                        "NF-e real exige provedor e homologação."),
                Map.of(
                        "nome",
                        "Ads Profit Agent",
                        "estado",
                        "Requer conexão",
                        "descricao",
                        "Custos podem ser informados nos pedidos locais."),
                Map.of(
                        "nome",
                        "Competitor Agent",
                        "estado",
                        "Cadastro manual",
                        "descricao",
                        "Salva links e referências. Coleta automática não conectada."),
                Map.of(
                        "nome",
                        "Radar Operator",
                        "estado",
                        "Desligado",
                        "descricao",
                        "Nenhuma ação autônoma externa habilitada."));
    }

    @Transactional
    public Map<String, Object> comando(UUID chave, JsonNode n) {
        // Serialize commands per tenant; protects idempotency and shared-stock transitions.
        db.queryForList("select pg_advisory_xact_lock(hashtextextended(?,0))", tenant().toString());
        String op = texto(n, "op", 80);
        String hash;
        try {
            hash =
                    HexFormat.of()
                            .formatHex(
                                    MessageDigest.getInstance("SHA-256")
                                            .digest(enc(n).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        var antigos =
                db.queryForList(
                        "select hash,ator,resultado from radar_comando where tenant_id=? and id=?",
                        tenant(),
                        chave);
        if (!antigos.isEmpty()) {
            var a = antigos.getFirst();
            if (!a.get("hash").equals(hash) || !a.get("ator").equals(user().usuarioId()))
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT, "Chave já usada por outro comando.");
            return dec(a.get("resultado").toString());
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("mensagem", "Alteração salva no ambiente local.");
        switch (op) {
            case "produto" -> {
                permitir("DONO", "GESTOR");
                result.put("id", produto(n));
            }
            case "produto_atualizar" -> {
                permitir("DONO", "GESTOR");
                UUID pid = id(n, "id");
                var antes = um("radar_produto", pid);
                String ncm = n.path("ncm").asText("");
                if (!ncm.isBlank() && !ncm.matches("[0-9]{8}")) erro("NCM deve ter 8 dígitos.");
                db.update(
                        "update radar_produto set nome=?,marca=?,ncm=?,descricao=?,custo=?,preco=?,"
                                + "categoria_id=?,embalagem_id=? where tenant_id=? and id=?",
                        texto(n, "nome", 250),
                        n.path("marca").asText(""),
                        ncm,
                        n.path("descricao").asText(""),
                        valor(n, "custo"),
                        valor(n, "preco"),
                        cadastros.vinculoOpcional(n, "categoria_id", "radar_categoria"),
                        cadastros.vinculoOpcional(n, "embalagem_id", "radar_embalagem"),
                        tenant(),
                        pid);
                result.put("id", pid);
                result.put(
                        "antes",
                        Map.of(
                                "nome",
                                antes.get("nome"),
                                "custo",
                                antes.get("custo").toString(),
                                "preco",
                                antes.get("preco").toString()));
            }
            case "anuncios_lote" -> {
                permitir("DONO", "GESTOR", "MARKETING");
                UUID pid = id(n, "produto_id");
                var p = um("radar_produto", pid);
                if (bd(p.get("preco")).signum() <= 0)
                    erro("Cadastre um preço positivo no produto.");
                int criados = 0;
                for (String c : CANAIS) {
                    criados +=
                            db.update(
                                    "insert into"
                                        + " radar_anuncio(id,tenant_id,produto_id,canal,titulo,preco)"
                                        + " select ?,?,?,?,?,? where not exists (select 1 from"
                                        + " radar_anuncio where tenant_id=? and produto_id=? and"
                                        + " canal=?)",
                                    UUID.randomUUID(),
                                    tenant(),
                                    pid,
                                    c,
                                    p.get("nome"),
                                    p.get("preco"),
                                    tenant(),
                                    pid,
                                    c);
                }
                result.put(
                        "mensagem",
                        criados + " rascunhos criados para os canais. Nenhuma publicação externa.");
            }
            case "anuncios_estado_lote" -> {
                permitir("DONO", "GESTOR", "MARKETING");
                JsonNode ids = n.path("ids");
                if (!ids.isArray() || ids.isEmpty() || ids.size() > 1000)
                    erro("Selecione até 1.000 anúncios.");
                String estado = texto(n, "estado", 30);
                if (!Set.of("PAUSADO", "SIMULADO").contains(estado)) erro("Estado inválido.");
                for (JsonNode item : ids) {
                    UUID aid;
                    try {
                        aid = UUID.fromString(item.asText());
                    } catch (Exception e) {
                        throw new ResponseStatusException(
                                HttpStatus.BAD_REQUEST, "Identificador inválido.");
                    }
                    um("radar_anuncio", aid);
                    db.update(
                            "update radar_anuncio set estado=?,versao=versao+1 where tenant_id=?"
                                    + " and id=?",
                            estado,
                            tenant(),
                            aid);
                }
                result.put("mensagem", ids.size() + " anúncios alterados somente localmente.");
            }
            case "importar" -> {
                permitir("DONO", "GESTOR");
                JsonNode itens = n.path("itens");
                if (!itens.isArray() || itens.size() == 0 || itens.size() > 3000)
                    erro("Importe entre 1 e 3.000 linhas.");
                var ids = new ArrayList<UUID>();
                for (JsonNode item : itens) ids.add(produto(item));
                result.put("importados", ids.size());
                result.put(
                        "mensagem",
                        ids.size() + " produtos importados. O lote foi validado integralmente.");
            }
            case "estoque" -> {
                permitir("DONO", "GESTOR", "ESTOQUE");
                UUID pid = id(n, "produto_id");
                var p = um("radar_produto", pid);
                exigirEstoqueProprio(p);
                int delta = inteiro(n, "quantidade", -100000, 100000);
                if (delta == 0) erro("Informe uma quantidade diferente de zero.");
                if (((Number) p.get("fisico")).intValue() + delta
                        < ((Number) p.get("reservado")).intValue())
                    erro("O ajuste consumiria estoque reservado.");
                String motivo = texto(n, "motivo", 500);
                db.update(
                        "update radar_produto set fisico=fisico+? where tenant_id=? and id=?",
                        delta,
                        tenant(),
                        pid);
                movimento(pid, null, "AJUSTE", delta, 0, motivo);
            }
            case "anuncio" -> {
                permitir("DONO", "GESTOR", "MARKETING");
                // Anúncio sem produto não existe: o produto é o primeiro campo do formulário.
                if (n.path("produto_id").asText("").isBlank())
                    erro("Escolha o produto do anúncio pelo nome ou SKU.");
                UUID pid = id(n, "produto_id");
                if ("VARIACAO".equals(um("radar_produto", pid).get("tipo")))
                    erro("Escolha a variação vendida (cor, tamanho…), não o produto pai.");
                BigDecimal preco = valor(n, "preco");
                if (preco.signum() == 0) erro("Preço deve ser maior que zero.");
                UUID aid = UUID.randomUUID();
                db.update(
                        "insert into radar_anuncio(id,tenant_id,produto_id,canal,titulo,preco)"
                                + " values(?,?,?,?,?,?)",
                        aid,
                        tenant(),
                        pid,
                        canal(n),
                        texto(n, "titulo", 250),
                        preco);
                result.put("id", aid);
                result.put(
                        "mensagem", "Rascunho salvo. Nenhum anúncio foi publicado no marketplace.");
            }
            case "anuncio_estado" -> {
                permitir("DONO", "GESTOR", "MARKETING");
                UUID aid = id(n, "id");
                um("radar_anuncio", aid);
                String estado = texto(n, "estado", 30);
                if (!Set.of("SIMULADO", "PAUSADO", "RASCUNHO").contains(estado))
                    erro("Estado inválido.");
                db.update(
                        "update radar_anuncio set estado=?,versao=versao+1 where tenant_id=? and"
                                + " id=?",
                        estado,
                        tenant(),
                        aid);
                result.put("mensagem", "Estado alterado somente na simulação local.");
            }
            case "propor_preco" -> {
                permitir("DONO", "GESTOR", "MARKETING");
                UUID aid = id(n, "id");
                var a = um("radar_anuncio", aid);
                BigDecimal depois = valor(n, "preco");
                if (depois.signum() == 0) erro("Preço inválido.");
                UUID ac = UUID.randomUUID();
                db.update(
                        "insert into"
                            + " radar_acao(id,tenant_id,anuncio_id,tipo,antes,depois,versao,motivo,proposto_por)"
                            + " values(?,?,?,'PRECO',?,?,?,?,?)",
                        ac,
                        tenant(),
                        aid,
                        a.get("preco"),
                        depois,
                        a.get("versao"),
                        texto(n, "motivo", 500),
                        user().usuarioId());
                result.put("id", ac);
                result.put("mensagem", "Proposta enviada para aprovação. O preço ainda não mudou.");
            }
            case "aprovar", "rejeitar" -> {
                permitir("DONO", "GESTOR");
                UUID ac = id(n, "id");
                var a = um("radar_acao", ac);
                if (!a.get("estado").equals("PENDENTE")) erro("Esta ação já foi resolvida.");
                if (op.equals("aprovar")) {
                    var ad = um("radar_anuncio", (UUID) a.get("anuncio_id"));
                    if (!ad.get("versao").equals(a.get("versao")))
                        throw new ResponseStatusException(
                                HttpStatus.CONFLICT, "O anúncio mudou. Refaça a proposta.");
                    var pr = um("radar_produto", (UUID) ad.get("produto_id"));
                    if (bd(a.get("depois")).compareTo(bd(pr.get("custo"))) < 0)
                        erro("Política local: preço abaixo do custo bloqueado.");
                    db.update(
                            "update radar_anuncio set preco=?,versao=versao+1 where tenant_id=? and"
                                    + " id=?",
                            a.get("depois"),
                            tenant(),
                            a.get("anuncio_id"));
                }
                db.update(
                        "update radar_acao set estado=?,aprovado_por=? where tenant_id=? and id=?",
                        op.equals("aprovar") ? "EXECUTADA" : "REJEITADA",
                        user().usuarioId(),
                        tenant(),
                        ac);
                result.put(
                        "mensagem",
                        op.equals("aprovar")
                                ? "Aprovado e aplicado ao anúncio local. Canal externo"
                                        + " desconectado."
                                : "Proposta rejeitada.");
            }
            case "pedido" -> {
                permitir("DONO", "GESTOR");
                result.put("id", pedido(n));
            }
            case "pedido_estado" -> {
                permitir("DONO", "GESTOR", "ESTOQUE");
                transicao(n);
            }
            case "pedidos_lote" -> {
                permitir("DONO", "GESTOR");
                result.putAll(pedidosLote(n));
            }
            case "titulo" -> {
                permitir("DONO", "GESTOR", "FINANCEIRO");
                BigDecimal v = valor(n, "valor");
                if (v.signum() == 0) erro("Valor deve ser positivo.");
                String t = texto(n, "tipo", 20);
                if (!Set.of("PAGAR", "RECEBER").contains(t)) erro("Tipo inválido.");
                LocalDate data;
                try {
                    data = LocalDate.parse(n.path("vencimento").asText());
                } catch (Exception e) {
                    throw new ResponseStatusException(
                            HttpStatus.BAD_REQUEST, "Vencimento inválido.");
                }
                db.update(
                        "insert into radar_titulo(id,tenant_id,descricao,tipo,valor,vencimento)"
                                + " values(?,?,?,?,?,?)",
                        UUID.randomUUID(),
                        tenant(),
                        texto(n, "descricao", 250),
                        t,
                        v,
                        data);
            }
            case "baixar_titulo" -> {
                permitir("DONO", "GESTOR", "FINANCEIRO");
                UUID tid = id(n, "id");
                var t = um("radar_titulo", tid);
                if (!t.get("estado").equals("ABERTO")) erro("Título já baixado.");
                db.update(
                        "update radar_titulo set estado='BAIXADO' where tenant_id=? and id=?",
                        tenant(),
                        tid);
                result.put(
                        "mensagem",
                        "Baixa manual registrada. Nenhuma transferência bancária realizada.");
            }
            case "despesa" -> {
                permitir("DONO", "GESTOR", "FINANCEIRO");
                BigDecimal v = valor(n, "valor");
                if (v.signum() == 0) erro("Informe valor positivo.");
                lancar(null, "DESPESA", v.negate(), texto(n, "descricao", 80));
            }
            case "conciliar" -> {
                permitir("DONO", "GESTOR", "FINANCEIRO");
                UUID pid = id(n, "id");
                var p = um("radar_pedido", pid);
                if (Set.of("CANCELADO", "DEVOLVIDO").contains(p.get("estado")))
                    erro("Pedido sem recebível aberto.");
                BigDecimal esperado =
                        bd(p.get("preco"))
                                .multiply(bd(p.get("quantidade")))
                                .subtract(bd(p.get("comissao")))
                                .subtract(bd(p.get("frete")))
                                .subtract(bd(p.get("desconto")));
                BigDecimal recebido = valor(n, "valor");
                if (esperado.compareTo(recebido) != 0) {
                    registro(
                            "DIVERGENCIA",
                            Map.of(
                                    "pedido_id",
                                    pid,
                                    "esperado",
                                    esperado.toPlainString(),
                                    "recebido",
                                    recebido.toPlainString()));
                    result.put(
                            "mensagem",
                            "Divergência registrada. Esperado: R$ "
                                    + esperado.toPlainString()
                                    + ". Pedido não conciliado.");
                } else {
                    db.update(
                            "update radar_pedido set conciliado=true where tenant_id=? and id=?",
                            tenant(),
                            pid);
                    result.put(
                            "mensagem",
                            "Conferência manual concluída. Custos e impostos continuam informados"
                                    + " pelo usuário.");
                }
            }
            case "registro" -> {
                String tipo = texto(n, "tipo", 40);
                switch (tipo) {
                    case "MENSAGEM" -> permitir("DONO", "GESTOR", "ATENDIMENTO", "ANALISTA");
                    case "MARCA", "CONCORRENTE", "PRECO_REFERENCIA" ->
                            permitir("DONO", "GESTOR", "MARKETING");
                    case "FORNECEDOR", "COMPRA" -> permitir("DONO", "GESTOR", "ESTOQUE");
                    default -> erro("Tipo de registro não permitido.");
                }
                JsonNode d = n.path("dados");
                if (!d.isObject() || enc(d).length() > 10000) erro("Conteúdo inválido.");
                if (tipo.equals("PRECO_REFERENCIA")) {
                    // Observação de preço de uma referência monitorada (área Mercado).
                    var ref = um("radar_registro", id(d, "referencia_id"));
                    if (!ref.get("tipo").equals("CONCORRENTE"))
                        erro("Referência monitorada não encontrada.");
                    if (valor(d, "preco").signum() == 0) erro("Informe o preço observado.");
                }
                if (tipo.equals("CONCORRENTE")) {
                    String url = d.path("url").asText();
                    if (!url.startsWith("https://")) erro("Use uma URL HTTPS.");
                    if (!d.path("produto_id").asText("").isBlank())
                        um("radar_produto", id(d, "produto_id"));
                    if (!d.path("preco").asText("").isBlank()) valor(d, "preco");
                }
                if (tipo.equals("COMPRA")) {
                    exigirEstoqueProprio(um("radar_produto", id(d, "produto_id")));
                    inteiro(d, "quantidade", 1, 100000);
                    valor(d, "custo_unitario");
                }
                result.put("id", registro(tipo, d));
            }
            case "receber_compra" -> {
                permitir("DONO", "GESTOR", "ESTOQUE");
                UUID cid = id(n, "id");
                var compra = um("radar_registro", cid);
                if (!compra.get("tipo").equals("COMPRA")) erro("Compra não encontrada.");
                var d = dec(compra.get("dados").toString());
                if (d.containsKey("recebida_em")) erro("Compra já recebida.");
                UUID pid = UUID.fromString(d.get("produto_id").toString());
                var p = um("radar_produto", pid);
                exigirEstoqueProprio(p);
                int q = ((Number) d.get("quantidade")).intValue();
                BigDecimal custo = bd(d.get("custo_unitario"));
                int fisico = ((Number) p.get("fisico")).intValue();
                BigDecimal medio =
                        bd(p.get("custo"))
                                .multiply(BigDecimal.valueOf(fisico))
                                .add(custo.multiply(BigDecimal.valueOf(q)))
                                .divide(BigDecimal.valueOf(fisico + q), 2, RoundingMode.HALF_UP);
                db.update(
                        "update radar_produto set fisico=fisico+?,custo=? where tenant_id=? and"
                                + " id=?",
                        q,
                        medio,
                        tenant(),
                        pid);
                movimento(pid, null, "COMPRA", q, 0, "Recebimento de compra " + cid);
                d.put("recebida_em", java.time.Instant.now().toString());
                db.update(
                        "update radar_registro set dados=?::jsonb where tenant_id=? and id=?",
                        enc(d),
                        tenant(),
                        cid);
                BigDecimal total = custo.multiply(BigDecimal.valueOf(q));
                if (total.signum() > 0)
                    db.update(
                            "insert into radar_titulo(id,tenant_id,descricao,tipo,valor,vencimento)"
                                    + " values(?,?,?,'PAGAR',?,current_date)",
                            UUID.randomUUID(),
                            tenant(),
                            "Compra " + cid,
                            total);
                result.put(
                        "mensagem",
                        "Compra recebida uma vez; estoque, custo médio e conta a pagar"
                                + " atualizados.");
            }
            default -> {
                if (RadarCadastros.OPERACOES.contains(op))
                    result.putAll(cadastros.executar(op, n, papel()));
                else if (RadarEmpresa.OPERACOES.contains(op))
                    result.putAll(empresa.executar(op, n, papel(), user().usuarioId()));
                else if (RadarVendedores.OPERACOES.contains(op))
                    result.putAll(vendedores.executar(op, n, papel()));
                else if (RadarAnuncios.OPERACOES.contains(op))
                    result.putAll(anuncios.executar(op, n, papel()));
                else if (op.equals("clientes_lote")) result.putAll(clientes.lote(n, papel()));
                else if (RadarClientes.OPERACOES.contains(op))
                    result.putAll(clientes.salvar(n, papel()));
                else if (op.equals("produtos_lote")) result.putAll(produtos.lote(n, papel()));
                else if (RadarProdutos.OPERACOES.contains(op))
                    result.putAll(produtos.salvar(n, papel()));
                else if (RadarPromocoes.OPERACOES.contains(op))
                    result.putAll(promocoes.executar(op, n, papel()));
                else erro("Operação não reconhecida.");
            }
        }
        auditar(
                op,
                result.getOrDefault("id", n.path("id").asText("lote")).toString(),
                Map.of("resultado", result, "parametros", semDocumento(n)));
        db.update(
                "insert into radar_comando(tenant_id,id,ator,hash,resultado)"
                        + " values(?,?,?,?,?::jsonb)",
                tenant(),
                chave,
                user().usuarioId(),
                hash,
                enc(result));
        return result;
    }

    private UUID produto(JsonNode n) {
        String sku = texto(n, "sku", 80), nome = texto(n, "nome", 250);
        int saldo = n.has("saldo") ? inteiro(n, "saldo", 0, 1000000) : 0;
        String ncm = n.path("ncm").asText("");
        if (!ncm.isBlank() && !ncm.matches("[0-9]{8}")) erro("NCM deve ter 8 dígitos.");
        if (Boolean.TRUE.equals(
                db.queryForObject(
                        "select exists(select 1 from radar_produto where tenant_id=? and sku=?)",
                        Boolean.class,
                        tenant(),
                        sku))) erro("SKU já cadastrado: " + sku);
        UUID id = UUID.randomUUID();
        db.update(
                "insert into"
                    + " radar_produto(id,tenant_id,sku,nome,marca,ncm,descricao,custo,preco,fisico,categoria_id,embalagem_id)"
                    + " values(?,?,?,?,?,?,?,?,?,?,?,?)",
                id,
                tenant(),
                sku,
                nome,
                n.path("marca").asText(""),
                ncm,
                n.path("descricao").asText(""),
                valor(n, "custo"),
                valor(n, "preco"),
                saldo,
                cadastros.vinculoOpcional(n, "categoria_id", "radar_categoria"),
                cadastros.vinculoOpcional(n, "embalagem_id", "radar_embalagem"));
        if (saldo > 0) movimento(id, null, "ABERTURA", saldo, 0, "Saldo inicial informado");
        return id;
    }

    private UUID pedido(JsonNode n) {
        UUID pid = id(n, "produto_id");
        var p = um("radar_produto", pid);
        if ("VARIACAO".equals(p.get("tipo"))) erro("Escolha a variação vendida (cor, tamanho…).");
        if (Boolean.FALSE.equals(p.get("permite_venda"))) erro("Este produto está fora de venda.");
        int q = inteiro(n, "quantidade", 1, 100000);
        // Kit reserva os componentes; produto simples reserva a si mesmo.
        var reservas = itensParaReservar(pid, p, q);
        BigDecimal preco = valor(n, "preco");
        if (preco.signum() == 0) erro("Preço deve ser positivo.");
        BigDecimal bruto = preco.multiply(BigDecimal.valueOf(q));
        String canal = canal(n);
        // Com promoção escolhida, o desconto vem dela (e fica rastreável pelo
        // promocao_id); sem promoção, vale o desconto digitado.
        UUID promocaoId = null;
        BigDecimal desconto = valor(n, "desconto");
        if (!n.path("promocao_id").asText("").isBlank()) {
            promocaoId = id(n, "promocao_id");
            desconto = promocoes.desconto(promocaoId, pid, canal, preco, q);
        }
        if (desconto.compareTo(bruto) > 0) erro("Desconto maior que a venda.");
        // Sem cliente escolhido, o pedido acha o cliente pelo CPF/CNPJ (nunca pelo nome) ou
        // cria um novo, que entra como cadastro incompleto.
        UUID clienteId = cadastros.vinculoOpcional(n, "cliente_id", "radar_cliente");
        String cliente;
        if (clienteId != null) cliente = cadastros.nomeDoCliente(clienteId);
        else {
            cliente = texto(n, "cliente", 160);
            clienteId = clientes.clienteDoPedido(cliente, n.path("cliente_documento").asText(""));
        }
        UUID id = UUID.randomUUID();
        String num = "R-" + id.toString().substring(0, 8).toUpperCase();
        db.update(
                "insert into"
                    + " radar_pedido(id,tenant_id,produto_id,numero,canal,cliente,quantidade,preco,custo_unitario,comissao,frete,imposto,ads,embalagem,desconto,cliente_id,promocao_id)"
                    + " values(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                id,
                tenant(),
                pid,
                num,
                canal,
                cliente,
                q,
                preco,
                p.get("custo"),
                valor(n, "comissao"),
                valor(n, "frete"),
                valor(n, "imposto"),
                valor(n, "ads"),
                valor(n, "embalagem"),
                desconto,
                clienteId,
                promocaoId);
        for (var r : reservas) {
            db.update(
                    "update radar_produto set reservado=reservado+? where tenant_id=? and id=?",
                    r.quantidade(),
                    tenant(),
                    r.produtoId());
            movimento(r.produtoId(), id, "RESERVA", 0, r.quantidade(), "Pedido " + num);
        }
        lancar(id, "RECEITA", bruto, "Pedido local " + num);
        lancar(
                id,
                "CMV",
                bd(p.get("custo")).multiply(BigDecimal.valueOf(q)).negate(),
                "Custo congelado no pedido");
        for (String k : List.of("comissao", "frete", "imposto", "ads", "embalagem"))
            lancar(id, k.toUpperCase(), valor(n, k).negate(), "Valor informado no pedido local");
        lancar(
                id,
                "DESCONTO",
                desconto.negate(),
                promocaoId != null ? "Promoção " + promocaoId : "Valor informado no pedido local");
        return id;
    }

    private static final Map<String, Set<String>> ORIGENS =
            Map.of(
                    "SEPARADO", Set.of("RESERVADO"),
                    "EXPEDIDO", Set.of("SEPARADO"),
                    "CANCELADO", Set.of("RESERVADO", "SEPARADO"));

    /**
     * Ações em lote da lista de pedidos. ESTADO (e EXCLUIR, que cancela: pedido não se apaga, por
     * rastreabilidade) usa a mesma transição de um pedido só, e pula os pedidos em que ela não
     * cabe. MARCADORES e DATA_FATURAMENTO só organizam. LANCAR_CONTAS cria a conta a receber do
     * valor do pedido (bruto menos desconto) para quem ainda não tem.
     */
    private Map<String, Object> pedidosLote(JsonNode n) {
        String acao = texto(n, "acao", 30);
        JsonNode lista = n.path("ids");
        if (!lista.isArray() || lista.isEmpty() || lista.size() > 500)
            erro("Escolha entre 1 e 500 pedidos.");
        List<UUID> ids = new ArrayList<>();
        for (JsonNode i : lista) {
            try {
                ids.add(UUID.fromString(i.asText()));
            } catch (IllegalArgumentException e) {
                erro("Identificador inválido.");
            }
        }
        UUID[] arr = ids.toArray(UUID[]::new);
        var pedidos =
                db.queryForList(
                        "select id, numero, estado, preco, quantidade, desconto from radar_pedido"
                                + " where tenant_id=? and id = any(?) order by numero for update",
                        tenant(),
                        arr);
        if (pedidos.size() != ids.size())
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pedido não encontrado.");
        Map<String, Object> r = new LinkedHashMap<>();
        switch (acao) {
            case "ESTADO", "EXCLUIR" -> {
                String estado = acao.equals("EXCLUIR") ? "CANCELADO" : texto(n, "estado", 30);
                if (!ORIGENS.containsKey(estado)) erro("Situação inválida.");
                if (estado.equals("EXPEDIDO") && !n.path("confirmar_simulacao").asBoolean())
                    erro("Expedição local exige confirmação de simulação; não há NF-e emitida.");
                int feitos = 0;
                List<String> pulados = new ArrayList<>();
                for (var p : pedidos) {
                    if (!ORIGENS.get(estado).contains(p.get("estado").toString())) {
                        pulados.add(p.get("numero").toString());
                        continue;
                    }
                    var uma = json.createObjectNode();
                    uma.put("id", p.get("id").toString());
                    uma.put("estado", estado);
                    uma.put("confirmar_simulacao", true);
                    transicao(uma);
                    feitos++;
                }
                r.put("alterados", feitos);
                r.put("pulados", pulados);
                String verbo =
                        switch (estado) {
                            case "SEPARADO" -> "enviado(s) para separação";
                            case "EXPEDIDO" -> "expedido(s) (simulação local, sem NF-e)";
                            default -> "cancelado(s), com a reserva devolvida ao estoque";
                        };
                r.put(
                        "mensagem",
                        feitos
                                + " pedido(s) "
                                + verbo
                                + "."
                                + (pulados.isEmpty()
                                        ? ""
                                        : " Ficaram como estavam, pela situação atual: "
                                                + String.join(", ", pulados)
                                                + "."));
            }
            case "MARCADORES" -> {
                String modo = n.path("modo").asText("ADICIONAR");
                if (!Set.of("ADICIONAR", "REMOVER", "SUBSTITUIR").contains(modo))
                    erro("Modo inválido.");
                List<String> marcadores = new ArrayList<>();
                for (JsonNode m : n.path("marcadores")) {
                    String t = m.asText("").trim().toLowerCase();
                    if (t.isEmpty()) continue;
                    if (t.length() > 40) erro("Marcador muito longo: " + t);
                    if (!marcadores.contains(t)) marcadores.add(t);
                }
                if (marcadores.size() > 20) erro("No máximo 20 marcadores.");
                if (marcadores.isEmpty() && !modo.equals("SUBSTITUIR"))
                    erro("Informe pelo menos um marcador.");
                String js = enc(marcadores);
                String sql =
                        switch (modo) {
                            case "ADICIONAR" ->
                                    "update radar_pedido set marcadores=(select coalesce(jsonb_agg("
                                            + "distinct x),'[]'::jsonb) from jsonb_array_elements("
                                            + "marcadores || ?::jsonb) x)";
                            case "REMOVER" ->
                                    "update radar_pedido set marcadores=(select"
                                        + " coalesce(jsonb_agg(x),'[]'::jsonb) from"
                                        + " jsonb_array_elements(marcadores) x where not ?::jsonb"
                                        + " @> jsonb_build_array(x))";
                            default -> "update radar_pedido set marcadores=?::jsonb";
                        };
                int k = db.update(sql + " where tenant_id=? and id = any(?)", js, tenant(), arr);
                r.put("alterados", k);
                r.put("mensagem", "Marcadores alterados em " + k + " pedido(s).");
            }
            case "DATA_FATURAMENTO" -> {
                LocalDate data = null;
                String texto = n.path("data").asText("").trim();
                if (!texto.isEmpty()) {
                    try {
                        data = LocalDate.parse(texto);
                    } catch (Exception e) {
                        erro("Data inválida.");
                    }
                }
                int k =
                        db.update(
                                "update radar_pedido set data_faturamento=? where tenant_id=? and"
                                        + " id = any(?)",
                                data,
                                tenant(),
                                arr);
                r.put("alterados", k);
                r.put(
                        "mensagem",
                        (data == null ? "Data de faturamento limpa em " : "Data de faturamento ")
                                + (data == null ? "" : "definida em ")
                                + k
                                + " pedido(s).");
            }
            case "LANCAR_CONTAS" -> {
                if (!financeiro()) erro("Seu cargo não lança contas.");
                LocalDate vencimento;
                try {
                    vencimento = LocalDate.parse(n.path("vencimento").asText());
                } catch (Exception e) {
                    erro("Vencimento inválido.");
                    return r;
                }
                int lancadas = 0;
                List<String> pulados = new ArrayList<>();
                for (var p : pedidos) {
                    String estado = p.get("estado").toString();
                    Integer ja =
                            db.queryForObject(
                                    "select count(*) from radar_titulo where tenant_id=? and"
                                            + " pedido_id=? and tipo='RECEBER'",
                                    Integer.class,
                                    tenant(),
                                    p.get("id"));
                    BigDecimal valor =
                            bd(p.get("preco"))
                                    .multiply(
                                            BigDecimal.valueOf(
                                                    ((Number) p.get("quantidade")).longValue()))
                                    .subtract(bd(p.get("desconto")))
                                    .setScale(2, RoundingMode.HALF_UP);
                    if (Set.of("CANCELADO", "DEVOLVIDO").contains(estado)
                            || (ja != null && ja > 0)
                            || valor.signum() <= 0) {
                        pulados.add(p.get("numero").toString());
                        continue;
                    }
                    db.update(
                            "insert into radar_titulo(id,tenant_id,descricao,tipo,valor,vencimento,"
                                    + "pedido_id) values(?,?,?,'RECEBER',?,?,?)",
                            UUID.randomUUID(),
                            tenant(),
                            "Pedido " + p.get("numero"),
                            valor,
                            vencimento,
                            p.get("id"));
                    lancadas++;
                }
                r.put("lancadas", lancadas);
                r.put("pulados", pulados);
                r.put(
                        "mensagem",
                        lancadas
                                + " conta(s) a receber lançada(s)."
                                + (pulados.isEmpty()
                                        ? ""
                                        : " Sem lançamento (já lançado, cancelado ou devolvido): "
                                                + String.join(", ", pulados)
                                                + "."));
            }
            default -> erro("Ação em lote não reconhecida.");
        }
        return r;
    }

    private void transicao(JsonNode n) {
        UUID id = id(n, "id");
        var p = um("radar_pedido", id);
        String old = p.get("estado").toString(), next = texto(n, "estado", 30);
        // O que foi reservado (o produto ou os componentes do kit) está nos movimentos de
        // reserva do pedido; as transições devolvem ou baixam exatamente isso.
        var reservas = reservasDoPedido(id);
        for (var r : reservas) um("radar_produto", r.produtoId());
        if (next.equals("SEPARADO") && old.equals("RESERVADO")) {
        } else if (next.equals("EXPEDIDO") && old.equals("SEPARADO")) {
            if (!n.path("confirmar_simulacao").asBoolean())
                erro("Expedição local exige confirmação de simulação; não há NF-e emitida.");
            for (var r : reservas) {
                db.update(
                        "update radar_produto set fisico=fisico-?,reservado=reservado-? where"
                                + " tenant_id=? and id=?",
                        r.quantidade(),
                        r.quantidade(),
                        tenant(),
                        r.produtoId());
                movimento(
                        r.produtoId(),
                        id,
                        "EXPEDICAO",
                        -r.quantidade(),
                        -r.quantidade(),
                        "Expedição simulada; sem documento fiscal");
            }
        } else if (next.equals("CANCELADO") && Set.of("RESERVADO", "SEPARADO").contains(old)) {
            permitir("DONO", "GESTOR");
            for (var r : reservas) {
                db.update(
                        "update radar_produto set reservado=reservado-? where tenant_id=? and id=?",
                        r.quantidade(),
                        tenant(),
                        r.produtoId());
                movimento(r.produtoId(), id, "LIBERACAO", 0, -r.quantidade(), "Cancelamento local");
            }
            estornar(id);
        } else if (next.equals("DEVOLVIDO") && old.equals("EXPEDIDO")) {
            permitir("DONO", "GESTOR");
            boolean revenda = n.path("retornar_estoque").asBoolean(false);
            if (revenda) {
                for (var r : reservas) {
                    db.update(
                            "update radar_produto set fisico=fisico+? where tenant_id=? and id=?",
                            r.quantidade(),
                            tenant(),
                            r.produtoId());
                    movimento(
                            r.produtoId(),
                            id,
                            "DEVOLUCAO",
                            r.quantidade(),
                            0,
                            "Devolução inspecionada e disponível para revenda");
                }
            }
            for (var x :
                    db.queryForList(
                            "select tipo,valor from radar_lancamento where tenant_id=? and"
                                    + " pedido_id=? and tipo in ('RECEITA','DESCONTO','CMV')",
                            tenant(),
                            id)) {
                if (!x.get("tipo").equals("CMV") || revenda)
                    lancar(
                            id,
                            x.get("tipo").toString(),
                            bd(x.get("valor")).negate(),
                            "Devolução local: reversão de " + x.get("tipo"));
            }
        } else erro("Transição inválida para o estado atual.");
        db.update(
                "update radar_pedido set estado=?,conciliado=false where tenant_id=? and id=?",
                next,
                tenant(),
                id);
    }

    record Reserva(UUID produtoId, int quantidade) {}

    /**
     * Itens a reservar para vender {@code q} unidades: os componentes do kit (× quantidade no kit)
     * ou o próprio produto. Produto que não controla estoque não reserva nada.
     */
    private List<Reserva> itensParaReservar(UUID pid, Map<String, Object> p, int q) {
        List<Reserva> itens = new ArrayList<>();
        if ("KIT".equals(p.get("tipo"))) {
            for (var c :
                    db.queryForList(
                            "select componente_id, quantidade from radar_kit_item"
                                    + " where tenant_id=? and kit_id=? order by componente_id",
                            tenant(),
                            pid))
                itens.add(
                        new Reserva(
                                (UUID) c.get("componente_id"),
                                ((Number) c.get("quantidade")).intValue() * q));
            if (itens.isEmpty()) erro("Este kit não tem componentes cadastrados.");
        } else itens.add(new Reserva(pid, q));
        List<Reserva> controladas = new ArrayList<>();
        for (var r : itens) {
            var produto = um("radar_produto", r.produtoId());
            if (Boolean.FALSE.equals(produto.get("controla_estoque"))) continue;
            int disponivel =
                    ((Number) produto.get("fisico")).intValue()
                            - ((Number) produto.get("reservado")).intValue();
            if (disponivel < r.quantidade())
                erro(
                        "Estoque disponível insuficiente"
                                + (r.produtoId().equals(pid)
                                        ? "."
                                        : " em " + produto.get("sku") + "."));
            controladas.add(r);
        }
        return controladas;
    }

    private List<Reserva> reservasDoPedido(UUID pedidoId) {
        return db
                .queryForList(
                        "select produto_id, sum(reserva_delta) quantidade from radar_movimento"
                                + " where tenant_id=? and pedido_id=? and tipo='RESERVA'"
                                + " group by produto_id order by produto_id",
                        tenant(),
                        pedidoId)
                .stream()
                .map(
                        r ->
                                new Reserva(
                                        (UUID) r.get("produto_id"),
                                        ((Number) r.get("quantidade")).intValue()))
                .toList();
    }

    /** Kit e produto com variações não têm saldo próprio. */
    private void exigirEstoqueProprio(Map<String, Object> p) {
        if ("KIT".equals(p.get("tipo")))
            erro("O estoque do kit vem dos componentes. Ajuste os produtos que o compõem.");
        if ("VARIACAO".equals(p.get("tipo")))
            erro("O estoque fica em cada variação. Ajuste a variação desejada.");
    }

    private void estornar(UUID id) {
        for (var x :
                db.queryForList(
                        "select tipo,valor from radar_lancamento where tenant_id=? and pedido_id=?",
                        tenant(),
                        id))
            lancar(
                    id,
                    x.get("tipo").toString(),
                    bd(x.get("valor")).negate(),
                    "Cancelamento local: reversão de " + x.get("tipo"));
    }

    @Transactional
    public Map<String, Object> perguntar(String texto) {
        if (texto.isBlank() || texto.length() > 500)
            erro("Escreva uma pergunta de até 500 caracteres.");
        String t = texto.toLowerCase(Locale.ROOT);
        String resposta;
        var refs = new ArrayList<String>();
        if (t.contains("lucro")
                || t.contains("margem")
                || t.contains("fatur")
                || t.contains("sobrou")) {
            permitir("DONO", "GESTOR", "FINANCEIRO");
            var r = resumo();
            resposta =
                    "Na base local: receita registrada R$ "
                            + r.get("bruto")
                            + " e resultado gerencial R$ "
                            + r.get("resultado")
                            + ". São valores informados localmente; não foram apurados por"
                            + " marketplaces. Veja os lançamentos em Financeiro para conferir cada"
                            + " componente.";
            refs.add("radar_lancamento: somas de receita e de todos os valores do tenant");
        } else if (t.contains("estoque") || t.contains("recompr") || t.contains("ruptura")) {
            int qtd =
                    db.queryForObject(
                            "select count(*) from radar_produto where tenant_id=? and"
                                    + " fisico-reservado<=minimo",
                            Integer.class,
                            tenant());
            resposta =
                    "Há "
                            + qtd
                            + " SKU(s) no estoque mínimo. Abra Estoque para verificar disponível,"
                            + " reservado e movimentos. A sugestão usa o mínimo cadastrado, sem"
                            + " previsão estatística.";
            refs.add("radar_produto: fisico - reservado <= minimo");
        } else if (t.contains("pedido") || t.contains("venda")) {
            int qtd =
                    db.queryForObject(
                            "select count(*) from radar_pedido where tenant_id=? and estado in"
                                    + " ('RESERVADO','SEPARADO')",
                            Integer.class,
                            tenant());
            resposta =
                    "Há "
                            + qtd
                            + " pedido(s) local(is) aguardando expedição. A emissão fiscal e a"
                            + " logística externas ainda não estão conectadas.";
            refs.add("radar_pedido: estados RESERVADO e SEPARADO");
        } else
            resposta =
                    "Posso consultar resultado, faturamento, pedidos pendentes ou estoque mínimo"
                        + " desta base local. Para um produto específico ou análise causal, ainda"
                        + " preciso de ferramentas adicionais. Não vou inventar essa informação.";
        auditar("COPILOTO", "consulta", Map.of("intencao", refs, "resposta", resposta));
        return Map.of(
                "resposta",
                resposta,
                "fontes",
                refs,
                "modelo",
                "Heurística local, sem envio a provedor externo");
    }
}
