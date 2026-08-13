package com.plataforma.autenticacao;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * O {@code principal} que fica no {@code SecurityContext} apos login -
 * decisao 0023: e daqui, e so daqui, que o resto da aplicacao (a comecar
 * pelo {@link com.plataforma.comum.tenant.FiltroTenant}) descobre o
 * tenant da requisicao.
 *
 * Guarda {@code senhaHash} porque {@link org.springframework.security.authentication.dao.DaoAuthenticationProvider}
 * precisa dele em {@link #getPassword()} para comparar contra a senha
 * informada no login - depois desse instante ele nunca mais e lido. Este
 * objeto vive na sessao HTTP pelo tempo de vida da sessao; nao serialize
 * ele numa resposta (por isso {@link com.plataforma.autenticacao.RespostaSessao}
 * e um record separado, so com os campos que o frontend precisa).
 */
public final class UsuarioAutenticado implements UserDetails {

    private final UUID usuarioId;
    private final UUID tenantId;
    private final String email;
    private final String senhaHash;
    private final String nome;
    private final PapelUsuario papel;
    private final boolean usuarioAtivo;
    private final boolean tenantAtivo;

    public UsuarioAutenticado(UsuarioParaLogin usuario) {
        this.usuarioId = usuario.id();
        this.tenantId = usuario.tenantId();
        this.email = usuario.email();
        this.senhaHash = usuario.senhaHash();
        this.nome = usuario.nome();
        this.papel = usuario.papel();
        this.usuarioAtivo = usuario.ativo();
        this.tenantAtivo = usuario.tenantAtivo();
    }

    public UUID usuarioId() {
        return usuarioId;
    }

    public UUID tenantId() {
        return tenantId;
    }

    public String nome() {
        return nome;
    }

    public PapelUsuario papel() {
        return papel;
    }

    @Override
    public String getPassword() {
        return senhaHash;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        // papel NAO e autorizacao nesta fase (ver o Javadoc de PapelUsuario
        // e o comentario da coluna na V014) - nenhuma authority concedida.
        return List.of();
    }

    /**
     * Verificado NO LOGIN: se false, o {@code DaoAuthenticationProvider}
     * recusa a autenticacao com {@code DisabledException} ANTES mesmo de
     * comparar a senha (ver ConfiguracaoSeguranca e TratadorFalhaLogin
     * sobre por que isso nunca aparece diferente na resposta ao cliente).
     *
     * A sessao ja aberta NAO cai sozinha so por causa disto - este objeto
     * fica em cache na sessao HTTP e nao e reconsultado no banco a cada
     * requisicao. Quem revalida "o usuario continua ativo?" a CADA
     * requisicao e o FiltroTenant, contra o banco (armadilha 6 da V014) -
     * ver {@link com.plataforma.autenticacao.RepositorioUsuario#existsByIdAndAtivoTrue}.
     */
    @Override
    public boolean isEnabled() {
        return usuarioAtivo && tenantAtivo;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }
}
