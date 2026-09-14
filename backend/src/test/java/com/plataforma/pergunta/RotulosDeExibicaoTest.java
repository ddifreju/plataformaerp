package com.plataforma.pergunta;

import org.junit.jupiter.api.Test;

import com.plataforma.devolucao.StatusDevolucao;
import com.plataforma.pedido.StatusPedido;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Trava contra a duplicação de vocabulário entre esta classe e
 * {@code frontend/src/lib/rotulos.ts} apodrecer em silêncio: itera sobre
 * TODO valor de {@link StatusPedido} e {@link StatusDevolucao} e falha se
 * algum não tiver rótulo cadastrado em {@link RotulosDeExibicao}. Um
 * status novo adicionado ao enum sem rótulo aqui derruba esta suíte antes
 * de chegar em produção como {@code enum.name()} cru na frase que a
 * lojista lê.
 */
class RotulosDeExibicaoTest {

    @Test
    void todoStatusPedidoTemRotuloDeExibicao() {
        for (StatusPedido status : StatusPedido.values()) {
            String rotulo;
            try {
                rotulo = RotulosDeExibicao.de(status);
            } catch (IllegalStateException e) {
                fail("StatusPedido." + status + " nao tem rotulo de exibicao cadastrado: " + e.getMessage());
                return;
            }
            assertNotNull(rotulo, "rotulo de " + status + " nao pode ser nulo");
            assertFalse(rotulo.isBlank(), "rotulo de " + status + " nao pode ser vazio");
            assertFalse(rotulo.equals(status.name()),
                    "rotulo de " + status + " nao pode ser o proprio enum.name() cru");
        }
    }

    @Test
    void todoStatusDevolucaoTemRotuloDeExibicao() {
        for (StatusDevolucao status : StatusDevolucao.values()) {
            String rotulo;
            try {
                rotulo = RotulosDeExibicao.de(status);
            } catch (IllegalStateException e) {
                fail("StatusDevolucao." + status + " nao tem rotulo de exibicao cadastrado: " + e.getMessage());
                return;
            }
            assertNotNull(rotulo, "rotulo de " + status + " nao pode ser nulo");
            assertFalse(rotulo.isBlank(), "rotulo de " + status + " nao pode ser vazio");
            assertFalse(rotulo.equals(status.name()),
                    "rotulo de " + status + " nao pode ser o proprio enum.name() cru");
        }
    }
}
