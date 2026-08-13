package com.plataforma.autenticacao;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;

import com.plataforma.comum.tenant.ContextoTenant;

/**
 * O que a API devolve quando o login da certo: 204, sem corpo. O
 * frontend descobre nome/papel/tenant chamando {@code GET /api/sessao}
 * depois - separar os dois evita duplicar a mesma logica de montagem de
 * resposta em dois lugares (aqui e em SessaoController).
 */
public class TratadorSucessoLogin implements AuthenticationSuccessHandler {

    private static final Logger LOG = LoggerFactory.getLogger(TratadorSucessoLogin.class);

    private final ServicoLogin servicoLogin;

    public TratadorSucessoLogin(ServicoLogin servicoLogin) {
        this.servicoLogin = servicoLogin;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
            Authentication authentication) {

        if (authentication.getPrincipal() instanceof UsuarioAutenticado usuario) {
            // Define o ContextoTenant ANTES de chamar o servico - ver o
            // Javadoc de ServicoLogin.registrarAcessoBemSucedido sobre por
            // que a ORDEM aqui e a parte que importa (armadilha 3 da
            // V014). Limpa no finally pelo mesmo motivo de sempre
            // (FiltroTenant): a thread e reaproveitada entre requisicoes.
            ContextoTenant.definir(usuario.tenantId());
            try {
                servicoLogin.registrarAcessoBemSucedido(usuario.usuarioId());
            } catch (RuntimeException falhaAoRegistrarAcesso) {
                // AQUI, e nao dentro do servico: registrarAcessoBemSucedido
                // e @Transactional, entao uma falha de banco tambem pode
                // vir do COMMIT feito pelo proxy, ja fora do corpo do
                // metodo (UnexpectedRollbackException). So capturando de
                // fora do limite transacional pegamos os dois casos.
                //
                // POR QUE ENGOLIR: neste ponto a autenticacao JA
                // ACONTECEU - o Authentication esta no SecurityContext e o
                // cookie de sessao ja esta a caminho do navegador. Deixar
                // a excecao subir daria 500 para um usuario que esta, de
                // fato, logado. Para quem opera sozinha, esse e o pior
                // tipo de bug: o cliente diz "nao consegui entrar", o log
                // mostra sessao criada, e as duas coisas sao verdade.
                //
                // ultimo_acesso_em e informativo, nunca condicao de
                // autenticacao. Falhou, anota e segue.
                LOG.warn("Falha ao registrar ultimo acesso do usuarioId={}. O login CONTINUA valido.",
                        usuario.usuarioId(), falhaAoRegistrarAcesso);
            } finally {
                ContextoTenant.limpar();
            }
        }

        response.setStatus(HttpServletResponse.SC_NO_CONTENT);
    }
}
