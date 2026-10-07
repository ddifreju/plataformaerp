package com.plataforma.radar;

import static com.plataforma.radar.RadarClientes.campo;
import static com.plataforma.radar.RadarClientes.digitos;
import static com.plataforma.radar.RadarClientes.email;
import static com.plataforma.radar.RadarClientes.uf;
import static com.plataforma.radar.RadarEntrada.cnpjValido;
import static com.plataforma.radar.RadarEntrada.confirmar;
import static com.plataforma.radar.RadarEntrada.erro;
import static com.plataforma.radar.RadarEntrada.id;
import static com.plataforma.radar.RadarEntrada.permitir;
import static com.plataforma.radar.RadarEntrada.soAlfanumerico;

import com.fasterxml.jackson.databind.JsonNode;
import com.plataforma.autenticacao.PapelUsuario;
import com.plataforma.comum.tenant.ContextoTenant;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Configurações → geral: dados da empresa (V031) e usuários do sistema (tabela usuario, V014).
 *
 * <p>Só a dona altera. Senha nunca passa pelos comandos do Radar: lá o corpo inteiro vira hash de
 * idempotência e vai para a auditoria. Por isso criar usuário e trocar senha têm endpoint próprio.
 */
@Service
public class RadarEmpresa {

    static final Set<String> OPERACOES = Set.of("empresa_salvar", "usuario_salvar");

    private static final Set<String> REGIMES =
            Set.of("MEI", "SIMPLES", "SIMPLES_EXCESSO", "PRESUMIDO", "REAL");
    private static final Set<String> PAPEIS =
            Set.copyOf(Arrays.stream(PapelUsuario.values()).map(Enum::name).toList());
    static final int MAX_BYTES_LOGO = 1024 * 1024;

    private final JdbcTemplate db;
    private final PasswordEncoder codificador;

    public RadarEmpresa(JdbcTemplate db, PasswordEncoder codificador) {
        this.db = db;
        this.codificador = codificador;
    }

    /** Todos veem os dados da empresa (nome no menu, CNPJ nos documentos); a lista de usuários, só a dona. */
    Map<String, Object> dados(String papel) {
        Map<String, Object> out = new LinkedHashMap<>();
        var linhas =
                db.queryForList(
                        "select razao_social, nome_fantasia, cnpj, inscricao_estadual,"
                                + " inscricao_municipal, regime_tributario, cnae, email, telefone,"
                                + " celular, site, cep, endereco, numero, complemento, bairro,"
                                + " cidade, uf, municipio_ibge, logo is not null tem_logo,"
                                + " atualizado_em from radar_empresa where tenant_id=?",
                        tenant());
        out.put("empresa", linhas.isEmpty() ? Map.of() : linhas.getFirst());
        out.put(
                "usuarios",
                "DONO".equals(papel)
                        ? db.queryForList(
                                "select id, nome, email, papel, ativo, criado_em, ultimo_acesso_em"
                                        + " from usuario where tenant_id=? order by ativo desc,"
                                        + " nome",
                                tenant())
                        : List.of());
        return out;
    }

    Map<String, Object> executar(String op, JsonNode n, String papel, UUID ator) {
        return op.equals("empresa_salvar") ? salvarEmpresa(n, papel) : salvarUsuario(n, papel, ator);
    }

    private Map<String, Object> salvarEmpresa(JsonNode n, String papel) {
        permitir(papel, "DONO");
        Map<String, Object> c = new LinkedHashMap<>();
        c.put("razao_social", campo(n, "razao_social", 200, "Razão social"));
        c.put("nome_fantasia", campo(n, "nome_fantasia", 200, "Nome fantasia"));
        String cnpj = soAlfanumerico(campo(n, "cnpj", 20, "CNPJ"));
        if (cnpj != null && cnpj.isEmpty()) cnpj = null;
        if (cnpj != null && !cnpjValido(cnpj)) erro("CNPJ inválido: confira os dígitos.");
        c.put("cnpj", cnpj);
        String ie = campo(n, "inscricao_estadual", 20, "Inscrição estadual");
        if (ie != null) {
            ie = ie.toUpperCase();
            if (!ie.equals("ISENTO")) ie = ie.replaceAll("[^0-9]", "");
            if (ie.isEmpty()) erro("Inscrição estadual inválida: use só números ou ISENTO.");
        }
        c.put("inscricao_estadual", ie);
        c.put("inscricao_municipal", campo(n, "inscricao_municipal", 20, "Inscrição municipal"));
        String regime = campo(n, "regime_tributario", 20, "Regime tributário");
        if (regime != null && !REGIMES.contains(regime)) erro("Regime tributário inválido.");
        c.put("regime_tributario", regime);
        String cnae = digitos(campo(n, "cnae", 12, "CNAE"));
        if (cnae != null && cnae.isEmpty()) cnae = null;
        if (cnae != null && cnae.length() != 7) erro("CNAE deve ter 7 dígitos.");
        c.put("cnae", cnae);
        c.put("email", email(campo(n, "email", 320, "E-mail"), "E-mail"));
        c.put("telefone", campo(n, "telefone", 40, "Telefone"));
        c.put("celular", campo(n, "celular", 40, "Celular"));
        c.put("site", campo(n, "site", 200, "Site"));
        String cep = digitos(campo(n, "cep", 9, "CEP"));
        if (cep != null && cep.isEmpty()) cep = null;
        if (cep != null && cep.length() != 8) erro("CEP deve ter 8 dígitos.");
        c.put("cep", cep);
        c.put("endereco", campo(n, "endereco", 200, "Endereço"));
        c.put("numero", campo(n, "numero", 20, "Número"));
        c.put("complemento", campo(n, "complemento", 120, "Complemento"));
        c.put("bairro", campo(n, "bairro", 120, "Bairro"));
        c.put("cidade", campo(n, "cidade", 120, "Cidade"));
        c.put("uf", uf(campo(n, "uf", 2, "UF")));
        String ibge = digitos(campo(n, "municipio_ibge", 7, "Código IBGE"));
        c.put("municipio_ibge", ibge != null && ibge.length() == 7 ? ibge : null);

        // As colunas vêm só da lista fixa acima.
        List<String> nomes = new ArrayList<>(c.keySet());
        List<Object> valores = new ArrayList<>(c.values());
        valores.addFirst(tenant());
        db.update(
                "insert into radar_empresa(tenant_id,"
                        + String.join(",", nomes)
                        + ") values(?"
                        + ",?".repeat(nomes.size())
                        + ") on conflict (tenant_id) do update set "
                        + String.join(",", nomes.stream().map(k -> k + "=excluded." + k).toList())
                        + ",atualizado_em=now()",
                valores.toArray());
        return Map.of("id", "empresa", "mensagem", "Dados da empresa salvos.");
    }

    /** Edita nome, cargo e situação de um usuário. E-mail não muda: é o login. */
    private Map<String, Object> salvarUsuario(JsonNode n, String papel, UUID ator) {
        permitir(papel, "DONO");
        UUID id = id(n, "id");
        var atual = usuario(id);
        String nome = campo(n, "nome", 200, "Nome");
        if (nome == null) erro("Preencha o nome.");
        String novoPapel = n.path("papel").asText("").trim().toUpperCase();
        if (!PAPEIS.contains(novoPapel)) erro("Cargo inválido.");
        boolean ativo = n.path("ativo").asBoolean(true);
        if (id.equals(ator)) {
            if (!novoPapel.equals(atual.get("papel")))
                erro("Você não pode mudar o seu próprio cargo.");
            if (!ativo) erro("Você não pode desativar o seu próprio acesso.");
        }
        boolean deixaDeSerDono =
                "DONO".equals(atual.get("papel"))
                        && Boolean.TRUE.equals(atual.get("ativo"))
                        && (!novoPapel.equals("DONO") || !ativo);
        if (deixaDeSerDono && donosAtivos() <= 1)
            erro("A empresa precisa de pelo menos um dono com acesso.");
        confirmar(
                db.update(
                        "update usuario set nome=?,papel=?,ativo=?,atualizado_em=now() where"
                                + " tenant_id=? and id=?",
                        nome,
                        novoPapel,
                        ativo,
                        tenant(),
                        id));
        return Map.of(
                "id",
                id,
                "mensagem",
                ativo ? "Usuário atualizado." : "Usuário desativado: o acesso foi desligado.");
    }

    /** Cria um usuário com senha inicial. Devolve o id. */
    UUID criarUsuario(String papel, JsonNode n) {
        permitir(papel, "DONO");
        String nome = campo(n, "nome", 200, "Nome");
        String mail = email(campo(n, "email", 320, "E-mail"), "E-mail");
        String novoPapel = n.path("papel").asText("").trim().toUpperCase();
        List<String> faltando = new ArrayList<>();
        if (nome == null) faltando.add("Nome");
        if (mail == null) faltando.add("E-mail");
        if (!faltando.isEmpty())
            erro("Preencha os campos obrigatórios: " + String.join(", ", faltando) + ".");
        if (!PAPEIS.contains(novoPapel)) erro("Cargo inválido.");
        String hash = hash(n.path("senha").asText(null), n.path("confirmacao").asText(null));
        UUID id = UUID.randomUUID();
        try {
            db.update(
                    "insert into usuario(id,tenant_id,email,senha_hash,nome,papel)"
                            + " values(?,?,?,?,?,?)",
                    id,
                    tenant(),
                    mail,
                    hash,
                    nome,
                    novoPapel);
        } catch (DuplicateKeyException e) {
            // O e-mail é único no Radar inteiro (decisão 0024): o login ainda não sabe a empresa.
            erro("Este e-mail já é usado por outro acesso ao Radar. Use outro e-mail.");
        }
        return id;
    }

    void alterarSenha(String papel, UUID usuarioId, String senha, String confirmacao) {
        permitir(papel, "DONO");
        usuario(usuarioId);
        confirmar(
                db.update(
                        "update usuario set senha_hash=?,atualizado_em=now() where tenant_id=?"
                                + " and id=?",
                        hash(senha, confirmacao),
                        tenant(),
                        usuarioId));
    }

    void salvarLogo(String papel, byte[] dados, String tipo) {
        permitir(papel, "DONO");
        if (dados.length == 0 || dados.length > MAX_BYTES_LOGO)
            erro("O logo pode ter no máximo 1 MB.");
        if (!tipo(dados).equals(tipo)) erro("Envie o logo em PNG, JPG ou WEBP.");
        db.update(
                "insert into radar_empresa(tenant_id,logo,logo_tipo) values(?,?,?) on conflict"
                        + " (tenant_id) do update set logo=excluded.logo,"
                        + "logo_tipo=excluded.logo_tipo,atualizado_em=now()",
                tenant(),
                dados,
                tipo);
    }

    void removerLogo(String papel) {
        permitir(papel, "DONO");
        db.update(
                "update radar_empresa set logo=null,logo_tipo=null,atualizado_em=now() where"
                        + " tenant_id=?",
                tenant());
    }

    Map<String, Object> logo() {
        var l =
                db.queryForList(
                        "select logo, logo_tipo from radar_empresa where tenant_id=? and logo is"
                                + " not null",
                        tenant());
        if (l.isEmpty())
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "A empresa não tem logo.");
        return l.getFirst();
    }

    /** O tipo pelo conteúdo do arquivo, não pelo que o navegador declarou. */
    static String tipo(byte[] d) {
        if (d.length > 8
                && (d[0] & 0xFF) == 0x89
                && d[1] == 'P'
                && d[2] == 'N'
                && d[3] == 'G') return "image/png";
        if (d.length > 3 && (d[0] & 0xFF) == 0xFF && (d[1] & 0xFF) == 0xD8 && (d[2] & 0xFF) == 0xFF)
            return "image/jpeg";
        if (d.length > 12
                && new String(d, 0, 4, StandardCharsets.US_ASCII).equals("RIFF")
                && new String(d, 8, 4, StandardCharsets.US_ASCII).equals("WEBP"))
            return "image/webp";
        return "";
    }

    private String hash(String senha, String confirmacao) {
        if (senha == null || senha.length() < 8)
            erro("A senha precisa de pelo menos 8 caracteres.");
        // BCrypt só considera os primeiros 72 bytes.
        if (senha.getBytes(StandardCharsets.UTF_8).length > 72)
            erro("A senha pode ter no máximo 72 caracteres.");
        if (!senha.equals(confirmacao)) erro("A confirmação não confere com a senha.");
        return codificador.encode(senha);
    }

    private Map<String, Object> usuario(UUID id) {
        var l =
                db.queryForList(
                        "select papel, ativo from usuario where tenant_id=? and id=?",
                        tenant(),
                        id);
        if (l.isEmpty())
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuário não encontrado.");
        return l.getFirst();
    }

    private int donosAtivos() {
        Integer k =
                db.queryForObject(
                        "select count(*) from usuario where tenant_id=? and papel='DONO' and ativo",
                        Integer.class,
                        tenant());
        return k == null ? 0 : k;
    }

    private UUID tenant() {
        return ContextoTenant.atual();
    }
}
