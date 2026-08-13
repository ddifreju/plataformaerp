package com.plataforma.autenticacao;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

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
            } finally {
                ContextoTenant.limpar();
            }
        }

        response.setStatus(HttpServletResponse.SC_NO_CONTENT);
    }
}
