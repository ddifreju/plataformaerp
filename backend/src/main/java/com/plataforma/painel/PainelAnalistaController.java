package com.plataforma.painel;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Tarefa 20: {@code GET /api/painel/analista}. Sem logica de negocio aqui
 * (CLAUDE.md) - so delega. Tenant vem do contexto (decisao 0007), nunca
 * de parametro da requisicao.
 */
@RestController
public class PainelAnalistaController {

    private final ServicoPainelAnalista servicoPainelAnalista;

    public PainelAnalistaController(ServicoPainelAnalista servicoPainelAnalista) {
        this.servicoPainelAnalista = servicoPainelAnalista;
    }

    @GetMapping("/api/painel/analista")
    public RespostaFilaPendencias pendencias() {
        return servicoPainelAnalista.pendencias();
    }
}
