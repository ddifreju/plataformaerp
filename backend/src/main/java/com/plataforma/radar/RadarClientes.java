package com.plataforma.radar;

import static com.plataforma.radar.RadarEntrada.cnpjValido;
import static com.plataforma.radar.RadarEntrada.confirmar;
import static com.plataforma.radar.RadarEntrada.cpfValido;
import static com.plataforma.radar.RadarEntrada.erro;
import static com.plataforma.radar.RadarEntrada.id;
import static com.plataforma.radar.RadarEntrada.inteiroOpcional;
import static com.plataforma.radar.RadarEntrada.permitir;
import static com.plataforma.radar.RadarEntrada.soAlfanumerico;
import static com.plataforma.radar.RadarEntrada.valorOpcional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.plataforma.comum.tenant.ContextoTenant;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Cadastro de clientes do Radar (V021).
 *
 * <p>Salvo pela tela, o cliente precisa dos dados que a NF-e exige (nome, tipo de pessoa, CPF/CNPJ
 * válido e endereço completo). Criado automaticamente por um pedido, entra só com o nome e fica
 * marcado como incompleto até alguém completar. Só CPF/CNPJ identifica o cliente; o nome nunca.
 *
 * <p>CPF/CNPJ é dado pessoal: guardado completo, devolvido mascarado na lista e completo só no
 * detalhe, para os cargos que atendem ou faturam o cliente.
 */
@Service
public class RadarClientes {

    static final Set<String> OPERACOES = Set.of("cliente_salvar", "clientes_lote");

    private static final int MAX_LOTE = 500;

    static final Set<String> VEEM =
            Set.of("DONO", "GESTOR", "ATENDIMENTO", "FINANCEIRO", "ANALISTA");
    static final Set<String> EDITAM = Set.of("DONO", "GESTOR", "ATENDIMENTO");
    static final Set<String> VEEM_DOCUMENTO = Set.of("DONO", "GESTOR", "ATENDIMENTO", "FINANCEIRO");

    /**
     * Estoque cuida de compras: vê, abre e edita contatos que são fornecedor ou transportador e não
     * são cliente. Dado de cliente (CPF, endereço) continua fora do alcance dele.
     */
    static final String ESTOQUE = "ESTOQUE";

    /** Quem escolhe fornecedor no produto (lista curta: id, código, nome). */
    static final Set<String> VEEM_FORNECEDORES = Set.of("DONO", "GESTOR", "ESTOQUE", "FINANCEIRO");

    /** F física, J jurídica, E estrangeira (mora fora), B estrangeira residente no Brasil. */
    private static final Set<String> TIPOS_PESSOA = Set.of("F", "J", "E", "B");

    private static final Set<String> TIPOS_CONTATO =
            Set.of("CLIENTE", "FORNECEDOR", "TRANSPORTADOR");
    private static final Set<String> STATUS_CRM =
            Set.of(
                    "NOVO",
                    "EM_CONTATO",
                    "NEGOCIACAO",
                    "CLIENTE",
                    "FIDELIZADO",
                    "INATIVO",
                    "PERDIDO");
    private static final Set<String> UFS =
            Set.of(
                    "AC", "AL", "AP", "AM", "BA", "CE", "DF", "ES", "GO", "MA", "MT", "MS", "MG",
                    "PA", "PB", "PR", "PE", "PI", "RJ", "RN", "RS", "RO", "RR", "SC", "SP", "SE",
                    "TO");
    private static final Set<String> TIPOS_ANEXO =
            Set.of(
                    "application/pdf",
                    "image/jpeg",
                    "image/png",
                    "image/webp",
                    "text/plain",
                    "text/csv",
                    "application/msword",
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    "application/vnd.ms-excel",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    private static final int MAX_BYTES_ANEXO = 2 * 1024 * 1024;
    private static final int MAX_ANEXOS = 10;
    private static final int MAX_PESSOAS_CONTATO = 20;
    private static final Set<String> COLUNAS_JSON = Set.of("tipos_contato", "pessoas_contato");

    private final JdbcTemplate db;
    private final ObjectMapper json;

    public RadarClientes(JdbcTemplate db, ObjectMapper json) {
        this.db = db;
        this.json = json;
    }

    // ---- leitura -----------------------------------------------------------------------------

    /**
     * Lista de clientes para GET /api/radar, com o histórico de compras de cada um. Classificação:
     * LEAD sem pedido, PRIMEIRA_COMPRA com um, RECORRENTE com dois ou mais (cancelados não contam).
     */
    Map<String, Object> dados(String papel) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put(
                "fornecedores",
                VEEM_FORNECEDORES.contains(papel)
                        ? db.queryForList(
                                "select id, codigo, nome, prazo_entrega_dias from radar_cliente"
                                    + " where tenant_id=? and tipos_contato @> '[\"FORNECEDOR\"]'"
                                    + " order by nome",
                                tenant())
                        : List.of());
        boolean estoque = ESTOQUE.equals(papel);
        if (!VEEM.contains(papel) && !estoque) {
            out.put("clientes", List.of());
            return out;
        }
        var clientes =
                db.queryForList(
                        "select c.id, c.codigo, c.nome, c.fantasia, c.tipo_pessoa, c.documento,"
                            + " c.email, c.telefone, c.celular, c.cidade, c.uf, c.status_crm,"
                            + " c.origem, c.incompleto, c.ativo, c.lista_preco,"
                            + " c.tipos_contato::text tipos_contato, c.vendedor_id,"
                            + " c.prazo_entrega_dias, c.criado_em, coalesce(h.pedidos,0) pedidos,"
                            + " h.primeira_compra, h.ultima_compra, coalesce(h.total,0) total from"
                            + " radar_cliente c left join (select cliente_id, count(*) pedidos,"
                            + " min(criado_em) primeira_compra, max(criado_em) ultima_compra,"
                            + " sum(preco*quantidade-desconto) total from radar_pedido where"
                            + " tenant_id=? and cliente_id is not null and estado<>'CANCELADO'"
                            + " group by cliente_id) h on h.cliente_id=c.id where c.tenant_id=?"
                                + (estoque ? " and not c.tipos_contato @> '[\"CLIENTE\"]'" : "")
                                + " order by c.nome limit 2000",
                        tenant(),
                        tenant());
        for (var c : clientes) {
            c.put("documento", mascarar((String) c.get("documento")));
            c.put("tipos_contato", lista(c.get("tipos_contato")));
            c.put("classificacao", classificacao(((Number) c.get("pedidos")).longValue()));
        }
        out.put("clientes", clientes);
        return out;
    }

    /** Cliente completo, com anexos (sem o conteúdo) e os últimos pedidos. */
    Map<String, Object> detalhe(String papel, UUID clienteId) {
        if (ESTOQUE.equals(papel)) exigirSemCliente(clienteId);
        else permitir(papel, VEEM.toArray(String[]::new));
        var linhas =
                db.queryForList(
                        "select * from radar_cliente where tenant_id=? and id=?",
                        tenant(),
                        clienteId);
        if (linhas.isEmpty())
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Cliente não encontrado.");
        Map<String, Object> c = new LinkedHashMap<>(linhas.getFirst());
        c.remove("tenant_id");
        if (!VEEM_DOCUMENTO.contains(papel) && !ESTOQUE.equals(papel))
            c.put("documento", mascarar((String) c.get("documento")));
        c.put("tipos_contato", lista(c.get("tipos_contato")));
        c.put("pessoas_contato", lista(c.get("pessoas_contato")));
        c.put(
                "anexos",
                db.queryForList(
                        "select id, nome_arquivo, tipo_conteudo, octet_length(dados) tamanho,"
                                + " criado_em from radar_cliente_anexo"
                                + " where tenant_id=? and cliente_id=? order by criado_em",
                        tenant(),
                        clienteId));
        var pedidos =
                db.queryForList(
                        "select p.id, p.numero, p.canal, p.estado, p.quantidade, p.preco,"
                                + " p.desconto, p.preco*p.quantidade-p.desconto total,"
                                + " p.criado_em, pr.nome produto"
                                + " from radar_pedido p join radar_produto pr"
                                + " on pr.tenant_id=p.tenant_id and pr.id=p.produto_id"
                                + " where p.tenant_id=? and p.cliente_id=?"
                                + " order by p.criado_em desc limit 100",
                        tenant(),
                        clienteId);
        c.put("pedidos", pedidos);
        long validos = pedidos.stream().filter(p -> !"CANCELADO".equals(p.get("estado"))).count();
        c.put("classificacao", classificacao(validos));
        c.put(
                "fonte",
                "Histórico: pedidos do Radar vinculados a este cliente (até os 100 mais recentes);"
                        + " cancelados não contam para a classificação.");
        dinheiroComoTexto(c);
        pedidos.forEach(RadarClientes::dinheiroComoTexto);
        return c;
    }

    // ---- gravação ----------------------------------------------------------------------------

    /** Cria (sem id) ou atualiza (com id) um cliente, exigindo os dados da nota fiscal. */
    Map<String, Object> salvar(JsonNode n, String papel) {
        boolean novo = n.path("id").asText("").isBlank();
        UUID id = novo ? UUID.randomUUID() : id(n, "id");
        Map<String, Object> c = colunas(n, id);
        if (ESTOQUE.equals(papel)) {
            if (c.get("tipos_contato").toString().contains("CLIENTE"))
                throw new ResponseStatusException(
                        HttpStatus.FORBIDDEN, "Seu cargo cadastra só fornecedor e transportador.");
            if (!novo) exigirSemCliente(id);
        } else permitir(papel, EDITAM.toArray(String[]::new));

        // CPF/CNPJ é o que identifica o cliente (nome repete entre pessoas). Se outro cadastro
        // já tem este documento: um cadastro incompleto (criado por pedido) é juntado a ele;
        // fora isso, é engano de digitação ou duplicata e a gravação é recusada.
        String documento = (String) c.get("documento");
        if (documento != null) {
            var outro =
                    db.queryForList(
                            "select id, codigo from radar_cliente where tenant_id=?"
                                    + " and documento=? and id<>?",
                            tenant(),
                            documento,
                            id);
            if (!outro.isEmpty()) {
                var destino = outro.getFirst();
                if (novo || !incompleto(id))
                    erro(
                            "Já existe um cliente com este CPF/CNPJ (código "
                                    + destino.get("codigo")
                                    + ").");
                return juntar(id, (UUID) destino.get("id"), (String) destino.get("codigo"));
            }
        }

        String codigo = (String) c.get("codigo");
        if (codigo == null) {
            if (novo) c.put("codigo", codigo = proximoCodigo());
            else c.remove("codigo");
        }

        if (novo) {
            List<String> nomes = new ArrayList<>(c.keySet());
            db.update(
                    "insert into radar_cliente("
                            + String.join(",", nomes)
                            + ",incompleto,atualizado_em,id,tenant_id) values("
                            + String.join(",", nomes.stream().map(RadarClientes::marcador).toList())
                            + ",false,now(),?,?)",
                    valores(c, id));
        } else {
            confirmar(
                    db.update(
                            "update radar_cliente set "
                                    + String.join(
                                            ",",
                                            c.keySet().stream()
                                                    .map(k -> k + "=" + marcador(k))
                                                    .toList())
                                    + ",incompleto=false,atualizado_em=now()"
                                    + " where id=? and tenant_id=?",
                            valores(c, id)));
        }
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("id", id);
        String tipos = c.getOrDefault("tipos_contato", "").toString();
        String quem =
                tipos.contains("CLIENTE")
                        ? "Cliente"
                        : tipos.contains("FORNECEDOR") ? "Fornecedor" : "Transportador";
        r.put("mensagem", quem + (novo ? " cadastrado." : " atualizado."));
        return r;
    }

    /**
     * Passa pedidos e anexos do cadastro incompleto para o cliente que já tem o mesmo CPF/CNPJ e
     * apaga o incompleto. Os dados do cliente existente não mudam.
     */
    private Map<String, Object> juntar(UUID origem, UUID destino, String codigoDestino) {
        int pedidos =
                db.update(
                        "update radar_pedido set cliente_id=? where tenant_id=? and cliente_id=?",
                        destino,
                        tenant(),
                        origem);
        db.update(
                "update radar_cliente_anexo set cliente_id=? where tenant_id=? and cliente_id=?",
                destino,
                tenant(),
                origem);
        confirmar(
                db.update(
                        "delete from radar_cliente where tenant_id=? and id=? and incompleto",
                        tenant(),
                        origem));
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("id", destino);
        r.put("juntado_de", origem);
        r.put("pedidos_movidos", pedidos);
        r.put(
                "mensagem",
                "Este CPF/CNPJ já era do cliente "
                        + codigoDestino
                        + ". Juntamos os "
                        + pedidos
                        + " pedido(s) nele; confira o cadastro.");
        return r;
    }

    private boolean incompleto(UUID id) {
        var linhas =
                db.queryForList(
                        "select incompleto from radar_cliente where tenant_id=? and id=?",
                        Boolean.class,
                        tenant(),
                        id);
        if (linhas.isEmpty())
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Cliente não encontrado.");
        return Boolean.TRUE.equals(linhas.getFirst());
    }

    /**
     * Cliente de um pedido sem cliente escolhido. Só o CPF/CNPJ identifica: pessoas diferentes têm
     * o mesmo nome. Com documento já cadastrado, o pedido vai para esse cliente; sem documento, ou
     * com um novo, cria um cliente com origem PEDIDO, marcado como incompleto. Roda dentro da trava
     * por empresa do {@link RadarService}.
     */
    UUID clienteDoPedido(String nome, String documentoInformado) {
        String documento = soAlfanumerico(documentoInformado);
        if (documento != null && documento.isEmpty()) documento = null;
        if (documento != null && !cpfValido(documento) && !cnpjValido(documento))
            erro("CPF/CNPJ do cliente inválido: confira os dígitos.");
        if (documento != null) {
            var achados =
                    db.queryForList(
                            "select id from radar_cliente where tenant_id=? and documento=?",
                            UUID.class,
                            tenant(),
                            documento);
            if (!achados.isEmpty()) return achados.getFirst();
        }
        UUID id = UUID.randomUUID();
        db.update(
                "insert into radar_cliente(id,tenant_id,codigo,nome,tipo_pessoa,documento,"
                        + "origem,incompleto) values(?,?,?,?,?,?,'PEDIDO',true)",
                id,
                tenant(),
                proximoCodigo(),
                nome,
                documento != null && documento.length() == 14 ? "J" : "F",
                documento);
        return id;
    }

    // ---- lote --------------------------------------------------------------------------------

    /**
     * Ações em lote sobre os contatos marcados na lista: vendedor, lista de preço, tipo de contato,
     * excluir e unificar. Estoque só alcança lote sem cliente.
     */
    Map<String, Object> lote(JsonNode n, String papel) {
        List<UUID> ids = ids(n.path("ids"));
        if (ESTOQUE.equals(papel)) ids.forEach(this::exigirSemCliente);
        else permitir(papel, EDITAM.toArray(String[]::new));
        String acao = n.path("acao").asText("");
        return switch (acao) {
            case "VENDEDOR" -> {
                UUID vendedor = vendedor(n, null);
                int k = atualizarTodos("vendedor_id=?", vendedor, ids);
                yield Map.of(
                        "mensagem",
                        vendedor == null
                                ? k + " contato(s) ficaram sem vendedor padrão."
                                : k + " contato(s) vinculados ao vendedor.");
            }
            case "LISTA_PRECO" -> {
                String lista = campo(n, "lista_preco", 60, "Lista de preço");
                int k = atualizarTodos("lista_preco=?", lista, ids);
                yield Map.of(
                        "mensagem",
                        lista == null
                                ? k + " contato(s) ficaram sem lista de preço."
                                : k + " contato(s) na lista de preço " + lista + ".");
            }
            case "TIPO_CONTATO" -> {
                String tipos = tiposContato(n);
                if (ESTOQUE.equals(papel) && tipos.contains("CLIENTE"))
                    throw new ResponseStatusException(
                            HttpStatus.FORBIDDEN, "Seu cargo não define o tipo Cliente.");
                int k = atualizarTodos("tipos_contato=?::jsonb", tipos, ids);
                yield Map.of("mensagem", "Tipo de contato definido em " + k + " cadastro(s).");
            }
            case "EXCLUIR" -> excluir(ids);
            case "UNIFICAR" -> unificar(id(n, "principal_id"), ids);
            default -> {
                erro("Ação em lote não reconhecida.");
                yield Map.of();
            }
        };
    }

    private int atualizarTodos(String set, Object valor, List<UUID> ids) {
        int total = 0;
        for (UUID id : ids)
            total +=
                    db.update(
                            "update radar_cliente set "
                                    + set
                                    + ",atualizado_em=now() where tenant_id=? and id=?",
                            valor,
                            tenant(),
                            id);
        if (total != ids.size())
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Contato não encontrado.");
        return total;
    }

    /**
     * Exclui só quem não tem histórico: contato com pedido ou ligado a produto como fornecedor não
     * sai (o pedido precisa continuar rastreável). Para esses, a saída é inativar o cadastro.
     */
    private Map<String, Object> excluir(List<UUID> ids) {
        List<String> presos = new ArrayList<>();
        for (UUID id : ids) {
            var linha =
                    db.queryForList(
                            "select c.codigo, c.nome, exists(select 1 from radar_pedido p where"
                                + " p.tenant_id=c.tenant_id and p.cliente_id=c.id) tem_pedido,"
                                + " exists(select 1 from radar_produto_fornecedor f where"
                                + " f.tenant_id=c.tenant_id and f.fornecedor_id=c.id) fornece from"
                                + " radar_cliente c where c.tenant_id=? and c.id=?",
                            tenant(),
                            id);
            if (linha.isEmpty())
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Contato não encontrado.");
            var c = linha.getFirst();
            if (Boolean.TRUE.equals(c.get("tem_pedido")) || Boolean.TRUE.equals(c.get("fornece")))
                presos.add(c.get("codigo") + " " + c.get("nome"));
        }
        if (!presos.isEmpty())
            erro(
                    "Não dá para excluir quem tem pedidos ou fornece produtos: "
                            + String.join(", ", presos)
                            + ". Inative esses cadastros em vez de excluir.");
        for (UUID id : ids) {
            db.update(
                    "delete from radar_cliente_anexo where tenant_id=? and cliente_id=?",
                    tenant(),
                    id);
            db.update("delete from radar_cliente where tenant_id=? and id=?", tenant(), id);
        }
        return Map.of("mensagem", ids.size() + " cadastro(s) excluído(s).");
    }

    /**
     * Junta os cadastros marcados no principal: pedidos, anexos e vínculos de fornecedor passam
     * para ele, os tipos de contato somam e os demais são apagados. Documentos diferentes são
     * pessoas diferentes (CPF/CNPJ é o que identifica) e não se juntam.
     */
    private Map<String, Object> unificar(UUID principal, List<UUID> ids) {
        if (!ids.contains(principal)) erro("O cadastro principal precisa estar entre os marcados.");
        if (ids.size() < 2) erro("Marque ao menos dois cadastros para unificar.");
        var linhas =
                db.queryForList(
                        "select id, codigo, documento, tipos_contato::text tipos from radar_cliente"
                                + " where tenant_id=? and id = any(?)",
                        tenant(),
                        ids.toArray(UUID[]::new));
        if (linhas.size() != ids.size())
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Contato não encontrado.");
        var documentos =
                linhas.stream()
                        .map(l -> (String) l.get("documento"))
                        .filter(d -> d != null)
                        .distinct()
                        .toList();
        if (documentos.size() > 1)
            erro(
                    "Os cadastros têm CPF/CNPJ diferentes: são pessoas ou empresas diferentes e"
                            + " não podem ser unificados.");
        List<String> tipos = new ArrayList<>();
        for (var l : linhas)
            for (Object t : lista(l.get("tipos")))
                if (!tipos.contains(t.toString())) tipos.add(t.toString());
        int pedidos = 0;
        for (UUID outro : ids) {
            if (outro.equals(principal)) continue;
            pedidos +=
                    db.update(
                            "update radar_pedido set cliente_id=? where tenant_id=? and"
                                    + " cliente_id=?",
                            principal,
                            tenant(),
                            outro);
            db.update(
                    "update radar_cliente_anexo set cliente_id=? where tenant_id=? and"
                            + " cliente_id=?",
                    principal,
                    tenant(),
                    outro);
            // Vínculo de fornecedor: recria no principal o que ele ainda não tinha.
            db.update(
                    "insert into radar_produto_fornecedor(tenant_id,produto_id,fornecedor_id,"
                            + "codigo_no_fornecedor) select tenant_id,produto_id,?,"
                            + "codigo_no_fornecedor from radar_produto_fornecedor f"
                            + " where tenant_id=? and fornecedor_id=? and not exists (select 1"
                            + " from radar_produto_fornecedor x where x.tenant_id=f.tenant_id"
                            + " and x.produto_id=f.produto_id and x.fornecedor_id=?)",
                    principal,
                    tenant(),
                    outro,
                    principal);
            db.update(
                    "delete from radar_produto_fornecedor where tenant_id=? and fornecedor_id=?",
                    tenant(),
                    outro);
            db.update("delete from radar_cliente where tenant_id=? and id=?", tenant(), outro);
        }
        String documento = documentos.isEmpty() ? null : documentos.getFirst();
        db.update(
                "update radar_cliente set tipos_contato=?::jsonb,"
                        + "documento=coalesce(documento,?),atualizado_em=now()"
                        + " where tenant_id=? and id=?",
                escrever(tipos),
                documento,
                tenant(),
                principal);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("id", principal);
        r.put(
                "mensagem",
                (ids.size() - 1)
                        + " cadastro(s) unificado(s) no principal; "
                        + pedidos
                        + " pedido(s) passaram para ele.");
        return r;
    }

    /** Endereço de entrega dos marcados, para imprimir etiquetas. Sem CPF/CNPJ. */
    List<Map<String, Object>> etiquetas(String papel, List<UUID> ids) {
        if (ids.isEmpty() || ids.size() > MAX_LOTE)
            erro("Escolha entre 1 e " + MAX_LOTE + " cadastros.");
        if (ESTOQUE.equals(papel)) ids.forEach(this::exigirSemCliente);
        else permitir(papel, VEEM_DOCUMENTO.toArray(String[]::new));
        return db.queryForList(
                "select codigo, nome, fantasia, endereco, numero, complemento, bairro, cidade, uf,"
                        + " cep, pais from radar_cliente where tenant_id=? and id = any(?)"
                        + " order by nome",
                tenant(),
                ids.toArray(UUID[]::new));
    }

    private static List<UUID> ids(JsonNode lista) {
        if (!lista.isArray() || lista.isEmpty() || lista.size() > MAX_LOTE)
            erro("Escolha entre 1 e " + MAX_LOTE + " cadastros.");
        List<UUID> out = new ArrayList<>();
        for (JsonNode i : lista) {
            try {
                UUID u = UUID.fromString(i.asText());
                if (!out.contains(u)) out.add(u);
            } catch (IllegalArgumentException e) {
                erro("Identificador inválido.");
            }
        }
        return out;
    }

    // ---- anexos ------------------------------------------------------------------------------

    UUID adicionarAnexo(
            String papel, UUID clienteId, String nomeArquivo, String tipoConteudo, byte[] dados) {
        permitir(papel, EDITAM.toArray(String[]::new));
        Integer existe =
                db.queryForObject(
                        "select count(*) from radar_cliente where tenant_id=? and id=?",
                        Integer.class,
                        tenant(),
                        clienteId);
        if (existe == null || existe == 0)
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Cliente não encontrado.");
        if (tipoConteudo == null || !TIPOS_ANEXO.contains(tipoConteudo))
            erro("Envie PDF, imagem (JPG, PNG, WEBP), texto, Word ou Excel.");
        if (dados.length == 0 || dados.length > MAX_BYTES_ANEXO)
            erro("Cada anexo pode ter no máximo 2 MB.");
        Integer existentes =
                db.queryForObject(
                        "select count(*) from radar_cliente_anexo where tenant_id=? and"
                                + " cliente_id=?",
                        Integer.class,
                        tenant(),
                        clienteId);
        if (existentes != null && existentes >= MAX_ANEXOS)
            erro("Cada cliente aceita até " + MAX_ANEXOS + " anexos.");
        UUID id = UUID.randomUUID();
        db.update(
                "insert into radar_cliente_anexo(id,tenant_id,cliente_id,nome_arquivo,"
                        + "tipo_conteudo,dados) values(?,?,?,?,?,?)",
                id,
                tenant(),
                clienteId,
                nomeDeArquivo(nomeArquivo),
                tipoConteudo,
                dados);
        return id;
    }

    Map<String, Object> anexo(String papel, UUID anexoId) {
        permitir(papel, VEEM_DOCUMENTO.toArray(String[]::new));
        var linhas =
                db.queryForList(
                        "select nome_arquivo, tipo_conteudo, dados from radar_cliente_anexo"
                                + " where tenant_id=? and id=?",
                        tenant(),
                        anexoId);
        if (linhas.isEmpty())
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Anexo não encontrado.");
        return linhas.getFirst();
    }

    void removerAnexo(String papel, UUID anexoId) {
        permitir(papel, EDITAM.toArray(String[]::new));
        if (db.update(
                        "delete from radar_cliente_anexo where tenant_id=? and id=?",
                        tenant(),
                        anexoId)
                == 0)
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Anexo não encontrado.");
    }

    // ---- campos ------------------------------------------------------------------------------

    /** Colunas validadas. Junta todos os obrigatórios que faltam numa mensagem só. */
    private Map<String, Object> colunas(JsonNode n, UUID id) {
        List<String> faltando = new ArrayList<>();
        Map<String, Object> c = new LinkedHashMap<>();

        String codigo = campo(n, "codigo", 30, "Código");
        if (codigo != null) {
            Integer repetido =
                    db.queryForObject(
                            "select count(*) from radar_cliente where tenant_id=? and codigo=?"
                                    + " and id<>?",
                            Integer.class,
                            tenant(),
                            codigo,
                            id);
            if (repetido != null && repetido > 0)
                erro("Já existe um cliente com o código " + codigo + ".");
        }
        c.put("codigo", codigo);
        c.put("nome", exigir(faltando, campo(n, "nome", 200, "Nome"), "Nome"));
        c.put("fantasia", campo(n, "fantasia", 200, "Fantasia"));

        String tipo = n.path("tipo_pessoa").asText("F").trim().toUpperCase();
        if (!TIPOS_PESSOA.contains(tipo)) erro("Tipo de pessoa inválido.");
        boolean noBrasil = !"E".equals(tipo);
        c.put("tipo_pessoa", tipo);

        // Documento: CPF para física e estrangeira residente (opcional nesta), CNPJ para
        // jurídica; estrangeira de fora usa passaporte/documento do país.
        String documento = soAlfanumerico(campo(n, "documento", 20, "CPF/CNPJ"));
        if (documento != null && documento.isEmpty()) documento = null;
        switch (tipo) {
            case "F" -> {
                if (documento == null) faltando.add("CPF");
                else if (!cpfValido(documento)) erro("CPF inválido: confira os dígitos.");
            }
            case "J" -> {
                if (documento == null) faltando.add("CNPJ");
                else if (!cnpjValido(documento)) erro("CNPJ inválido: confira os dígitos.");
            }
            case "B" -> {
                if (documento != null && !cpfValido(documento))
                    erro("CPF inválido: confira os dígitos.");
            }
            default -> documento = null;
        }
        c.put("documento", documento);
        String estrangeiro = campo(n, "documento_estrangeiro", 20, "Documento estrangeiro");
        c.put("documento_estrangeiro", noBrasil && !"B".equals(tipo) ? null : estrangeiro);
        c.put("pais", noBrasil ? null : exigir(faltando, campo(n, "pais", 60, "País"), "País"));

        Integer contribuinte = inteiroOpcional(n, "contribuinte", 1, 9);
        if (contribuinte != null && !Set.of(1, 2, 9).contains(contribuinte))
            erro("Indicador de contribuinte inválido.");
        String ie = campo(n, "inscricao_estadual", 20, "Inscrição estadual");
        if (ie != null) {
            ie = ie.toUpperCase();
            if (!ie.equals("ISENTO")) ie = ie.replaceAll("[^0-9]", "");
            if (ie.isEmpty()) erro("Inscrição estadual inválida: use só números ou ISENTO.");
        }
        if (Integer.valueOf(1).equals(contribuinte) && (ie == null || ie.equals("ISENTO")))
            erro("Contribuinte de ICMS precisa da inscrição estadual.");
        c.put("contribuinte", contribuinte);
        c.put("inscricao_estadual", ie);
        c.put("inscricao_municipal", campo(n, "inscricao_municipal", 20, "Inscrição municipal"));
        c.put("tipos_contato", tiposContato(n));

        // Endereço principal (o da nota).
        String cep = digitos(campo(n, "cep", 9, "CEP"));
        if (noBrasil) {
            exigir(faltando, cep, "CEP");
            if (cep != null && cep.length() != 8) erro("CEP deve ter 8 dígitos.");
        } else if (cep != null && cep.length() != 8) cep = null;
        c.put("cep", cep);
        c.put("endereco", exigir(faltando, campo(n, "endereco", 200, "Endereço"), "Endereço"));
        c.put("numero", exigir(faltando, campo(n, "numero", 20, "Número"), "Número"));
        c.put("complemento", campo(n, "complemento", 120, "Complemento"));
        String bairro = campo(n, "bairro", 120, "Bairro");
        c.put("bairro", noBrasil ? exigir(faltando, bairro, "Bairro") : bairro);
        c.put("cidade", exigir(faltando, campo(n, "cidade", 120, "Município"), "Município"));
        String uf = uf(campo(n, "uf", 2, "UF"));
        c.put("uf", noBrasil ? exigir(faltando, uf, "UF") : null);
        String ibge = digitos(campo(n, "municipio_ibge", 7, "Código IBGE"));
        c.put("municipio_ibge", ibge != null && ibge.length() == 7 ? ibge : null);

        // Cobrança em outro endereço: completa ou nada.
        boolean cobranca = n.path("cobranca_diferente").asBoolean(false);
        c.put("cobranca_diferente", cobranca);
        String cobCep = digitos(campo(n, "cobranca_cep", 9, "CEP de cobrança"));
        if (cobranca && cobCep != null && cobCep.length() != 8)
            erro("CEP de cobrança deve ter 8 dígitos.");
        c.put("cobranca_cep", cobranca ? exigir(faltando, cobCep, "CEP de cobrança") : null);
        cobranca(c, faltando, n, cobranca, "cobranca_endereco", 200, "Endereço de cobrança", true);
        cobranca(c, faltando, n, cobranca, "cobranca_numero", 20, "Número de cobrança", true);
        cobranca(c, faltando, n, cobranca, "cobranca_complemento", 120, "Complemento", false);
        cobranca(c, faltando, n, cobranca, "cobranca_bairro", 120, "Bairro de cobrança", true);
        cobranca(c, faltando, n, cobranca, "cobranca_cidade", 120, "Município de cobrança", true);
        String cobUf = uf(campo(n, "cobranca_uf", 2, "UF de cobrança"));
        c.put("cobranca_uf", cobranca ? exigir(faltando, cobUf, "UF de cobrança") : null);

        // Contato.
        c.put("telefone", campo(n, "telefone", 40, "Telefone"));
        c.put("telefone_adicional", campo(n, "telefone_adicional", 40, "Telefone adicional"));
        c.put("celular", campo(n, "celular", 40, "Celular"));
        c.put("website", campo(n, "website", 300, "WebSite"));
        c.put("email", email(campo(n, "email", 320, "E-mail"), "E-mail"));
        c.put(
                "email_nfe",
                email(campo(n, "email_nfe", 320, "E-mail para NF-e"), "E-mail para NF-e"));
        c.put(
                "observacoes_contato",
                campo(n, "observacoes_contato", 2000, "Observações do contato"));
        c.put("pessoas_contato", pessoasContato(n));

        // Fiscal e comercial.
        c.put("regime_tributario", inteiroOpcional(n, "regime_tributario", 1, 4));
        String suframa = digitos(campo(n, "inscricao_suframa", 12, "Inscrição Suframa"));
        if (suframa != null && (suframa.length() < 8 || suframa.length() > 9))
            erro("Inscrição Suframa deve ter 8 ou 9 dígitos.");
        c.put("inscricao_suframa", suframa);
        c.put("data_nascimento", dataNascimento(campo(n, "data_nascimento", 10, "Nascimento")));
        String status = n.path("status_crm").asText("NOVO").trim().toUpperCase();
        if (!STATUS_CRM.contains(status)) erro("Status no CRM inválido.");
        c.put("status_crm", status);
        c.put("prazo_entrega_dias", inteiroOpcional(n, "prazo_entrega_dias", 0, 365));
        c.put("vendedor_id", vendedor(n, id));
        c.put("condicao_pagamento", campo(n, "condicao_pagamento", 60, "Condição de pagamento"));
        c.put("lista_preco", campo(n, "lista_preco", 60, "Lista de preço"));
        BigDecimal limite = valorOpcional(n, "limite_credito");
        c.put("limite_credito", limite == null ? BigDecimal.ZERO.setScale(2) : limite);
        c.put("observacao", campo(n, "observacao", 1000, "Observações"));
        c.put("ativo", n.path("ativo").asBoolean(true));

        if (!faltando.isEmpty())
            erro("Preencha os campos obrigatórios: " + String.join(", ", faltando) + ".");
        return c;
    }

    private void cobranca(
            Map<String, Object> c,
            List<String> faltando,
            JsonNode n,
            boolean ativa,
            String coluna,
            int max,
            String rotulo,
            boolean obrigatorio) {
        String v = campo(n, coluna, max, rotulo);
        c.put(coluna, !ativa ? null : obrigatorio ? exigir(faltando, v, rotulo) : v);
    }

    private String tiposContato(JsonNode n) {
        List<String> tipos = new ArrayList<>();
        for (JsonNode t : n.path("tipos_contato")) {
            String v = t.asText("").trim().toUpperCase();
            if (!TIPOS_CONTATO.contains(v)) erro("Tipo de contato inválido: " + v);
            if (!tipos.contains(v)) tipos.add(v);
        }
        if (tipos.isEmpty()) tipos.add("CLIENTE");
        return escrever(tipos);
    }

    private String pessoasContato(JsonNode n) {
        JsonNode lista = n.path("pessoas_contato");
        if (lista.size() > MAX_PESSOAS_CONTATO)
            erro("Cadastre até " + MAX_PESSOAS_CONTATO + " pessoas de contato.");
        List<Map<String, String>> pessoas = new ArrayList<>();
        for (JsonNode p : lista) {
            String nome = campo(p, "nome", 120, "Nome da pessoa de contato");
            if (nome == null) erro("Toda pessoa de contato precisa de nome.");
            Map<String, String> m = new LinkedHashMap<>();
            m.put("nome", nome);
            m.put("setor", campo(p, "setor", 80, "Setor"));
            m.put("email", email(campo(p, "email", 320, "E-mail do contato"), "E-mail do contato"));
            m.put("telefone", campo(p, "telefone", 40, "Telefone do contato"));
            m.put("ramal", campo(p, "ramal", 10, "Ramal"));
            pessoas.add(m);
        }
        return escrever(pessoas);
    }

    /** Estoque só alcança contato que não é cliente; cliente de outro cargo dá 403. */
    private void exigirSemCliente(UUID id) {
        var tipos =
                db.queryForList(
                        "select tipos_contato::text from radar_cliente where tenant_id=? and id=?",
                        String.class,
                        tenant(),
                        id);
        if (tipos.isEmpty())
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Contato não encontrado.");
        if (tipos.getFirst().contains("CLIENTE"))
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "Seu cargo não permite abrir cadastro de cliente.");
    }

    /**
     * Vendedor escolhido para o cliente. Excluído não pode ser escolhido, mas o cliente que já
     * tinha esse vendedor continua salvando sem trocar.
     */
    private UUID vendedor(JsonNode n, UUID cliente) {
        if (n.path("vendedor_id").asText("").isBlank()) return null;
        UUID v = id(n, "vendedor_id");
        Integer existe =
                db.queryForObject(
                        "select count(*) from radar_vendedor where tenant_id=? and id=? and"
                                + " (excluido_em is null or exists (select 1 from radar_cliente"
                                + " c where c.tenant_id=radar_vendedor.tenant_id and c.id=? and"
                                + " c.vendedor_id=radar_vendedor.id))",
                        Integer.class,
                        tenant(),
                        v,
                        cliente);
        if (existe == null || existe == 0) erro("Vendedor não encontrado.");
        return v;
    }

    private String proximoCodigo() {
        Integer ultimo =
                db.queryForObject(
                        "select coalesce(max(substring(codigo from 2)::int),0) from radar_cliente"
                                + " where tenant_id=? and codigo ~ '^C[0-9]{1,9}$'",
                        Integer.class,
                        tenant());
        return "C%05d".formatted((ultimo == null ? 0 : ultimo) + 1);
    }

    // ---- apoio -------------------------------------------------------------------------------

    /** CPF vira ***.456.789-**; CNPJ vira **.345.678/****-**. */
    static String mascarar(String documento) {
        if (documento == null) return null;
        if (documento.length() == 11)
            return "***." + documento.substring(3, 6) + "." + documento.substring(6, 9) + "-**";
        if (documento.length() == 14)
            return "**." + documento.substring(2, 5) + "." + documento.substring(5, 8) + "/****-**";
        return "***";
    }

    static String classificacao(long pedidos) {
        return pedidos == 0 ? "LEAD" : pedidos == 1 ? "PRIMEIRA_COMPRA" : "RECORRENTE";
    }

    static String campo(JsonNode n, String campo, int max, String rotulo) {
        String s = n.path(campo).asText("").trim();
        if (s.length() > max) erro("O campo " + rotulo + " passa de " + max + " caracteres.");
        return s.isBlank() ? null : s;
    }

    static String exigir(List<String> faltando, String valor, String rotulo) {
        if (valor == null) faltando.add(rotulo);
        return valor;
    }

    static String email(String s, String rotulo) {
        if (s != null && !s.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")) erro(rotulo + " inválido.");
        return s == null ? null : s.toLowerCase();
    }

    static String digitos(String s) {
        return s == null ? null : s.replaceAll("[^0-9]", "");
    }

    static String uf(String s) {
        if (s == null) return null;
        String v = s.toUpperCase();
        if (!UFS.contains(v)) erro("UF inválida: " + s);
        return v;
    }

    static LocalDate dataNascimento(String s) {
        if (s == null) return null;
        try {
            LocalDate d = LocalDate.parse(s);
            if (d.isAfter(LocalDate.now()) || d.getYear() < 1900)
                erro("Data de nascimento inválida.");
            return d;
        } catch (DateTimeParseException e) {
            erro("Data de nascimento inválida.");
            return null;
        }
    }

    private static String nomeDeArquivo(String nome) {
        String base =
                nome == null
                        ? ""
                        : nome.replaceAll(".*[/\\\\]", "").replaceAll("[\\p{Cntrl}\"]", "").trim();
        if (base.isEmpty()) base = "anexo";
        return base.length() > 200 ? base.substring(base.length() - 200) : base;
    }

    private static String marcador(String coluna) {
        return COLUNAS_JSON.contains(coluna) ? "?::jsonb" : "?";
    }

    private static Object[] valores(Map<String, Object> c, UUID id) {
        List<Object> v = new ArrayList<>(c.values());
        v.add(id);
        v.add(tenant());
        return v.toArray();
    }

    private String escrever(Object valor) {
        try {
            return json.writeValueAsString(valor);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    /** jsonb (texto ou PGobject) para lista. */
    private List<Object> lista(Object valor) {
        if (valor == null) return List.of();
        String texto = valor.toString();
        try {
            return json.readValue(texto, new TypeReference<List<Object>>() {});
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void dinheiroComoTexto(Map<String, Object> linha) {
        linha.replaceAll((k, v) -> v instanceof BigDecimal b ? b.toPlainString() : v);
    }

    private static UUID tenant() {
        return ContextoTenant.atual();
    }
}
