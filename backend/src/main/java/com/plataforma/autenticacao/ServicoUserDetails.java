package com.plataforma.autenticacao;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;

/**
 * {@code UserDetailsService} proprio (decisao 0023, item 2 da tarefa 17).
 * So compoe: a busca de verdade e {@link RepositorioLoginUsuario}, o
 * UNICO caminho de leitura de usuario antes de existir sessao.
 */
@Component
public class ServicoUserDetails implements UserDetailsService {

    private final RepositorioLoginUsuario repositorioLoginUsuario;

    public ServicoUserDetails(RepositorioLoginUsuario repositorioLoginUsuario) {
        this.repositorioLoginUsuario = repositorioLoginUsuario;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        UsuarioParaLogin usuario = repositorioLoginUsuario.buscarPorEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException(
                        // Esta mensagem so existe para o log INTERNO do
                        // Spring Security. Com hideUserNotFoundExceptions
                        // = true (ConfiguracaoSeguranca), o
                        // DaoAuthenticationProvider converte esta excecao
                        // em BadCredentialsException antes que qualquer
                        // coisa chegue perto de uma resposta HTTP -
                        // armadilha 5 da V014: "e-mail nao existe" nunca
                        // pode ser distinguivel de "senha errada".
                        "Nenhum usuario para o e-mail informado."));
        return new UsuarioAutenticado(usuario);
    }
}
