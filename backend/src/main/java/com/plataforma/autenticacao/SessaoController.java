package com.plataforma.autenticacao;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Tarefa 17, item 7: {@code GET /api/sessao}. Sem logica de negocio aqui
 * (CLAUDE.md) - so extrai o principal ja autenticado e delega para
 * {@link ServicoSessao}, no mesmo padrao de
 * {@link com.plataforma.margem.MargemController}.
 */
@RestController
public class SessaoController {

    private final ServicoSessao servicoSessao;

    public SessaoController(ServicoSessao servicoSessao) {
        this.servicoSessao = servicoSessao;
    }

    @GetMapping("/api/sessao")
    public RespostaSessao sessaoAtual(@AuthenticationPrincipal UsuarioAutenticado usuario) {
        return servicoSessao.sessaoAtual(usuario);
    }
}
