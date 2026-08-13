package com.plataforma.painel;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Tarefa 19: {@code GET /api/painel/gestor}. Sem logica de negocio aqui
 * (CLAUDE.md) - so delega. O tenant nunca vem de parametro da requisicao:
 * os repositorios usados por {@link ServicoPainelGestor} filtram por
 * tenant sozinhos, via {@code @TenantId} (decisao 0007).
 */
@RestController
public class PainelGestorController {

    private final ServicoPainelGestor servicoPainelGestor;

    public PainelGestorController(ServicoPainelGestor servicoPainelGestor) {
        this.servicoPainelGestor = servicoPainelGestor;
    }

    @GetMapping("/api/painel/gestor")
    public RespostaGargalosProcesso gargalos() {
        return servicoPainelGestor.gargalos();
    }
}
