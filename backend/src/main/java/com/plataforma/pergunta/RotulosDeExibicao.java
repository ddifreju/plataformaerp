package com.plataforma.pergunta;

import java.util.Map;

import com.plataforma.devolucao.StatusDevolucao;
import com.plataforma.pedido.StatusPedido;

/**
 * Rótulo em português para os enums de status que aparecem em prosa de
 * {@link RespostaPergunta#texto()} (correção de revisão de código:
 * {@code ServicoPergunta.responderGargalos} concatenava
 * {@code item.status().name()} direto no texto, e a lojista lia
 * "PAGO: 10, ENVIADO: 3" - jargão de enum vazando pra frase, enquanto os
 * outros quatro caminhos da camada são português corrido).
 *
 * <p><b>IRMÃO A MANTER EM SINCRONIA:</b> {@code frontend/src/lib/rotulos.ts}
 * ({@code ROTULO_STATUS_PEDIDO} e {@code ROTULO_STATUS_DEVOLUCAO}). Os
 * textos abaixo são cópia literal, palavra por palavra, dos rótulos de lá -
 * o frontend já tinha decidido esse vocabulário para status de pedido e
 * devolução em outras telas, e a prosa desta camada usa exatamente o
 * mesmo, para não apresentar dois nomes diferentes para o mesmo status em
 * partes diferentes do produto.
 *
 * <p>Isso cria duplicação entre backend e frontend - aceita, porque agora
 * o backend produz PROSA PRONTA (não delega formatação pro cliente). Para
 * a duplicação não apodrecer em silêncio quando um status novo for
 * adicionado ao enum, {@code RotulosDeExibicaoTest} itera sobre TODOS os
 * valores de {@link StatusPedido} e {@link StatusDevolucao} e falha se
 * algum não tiver rótulo aqui.
 */
final class RotulosDeExibicao {

    private static final Map<StatusPedido, String> ROTULO_STATUS_PEDIDO = Map.of(
            StatusPedido.AGUARDANDO_PAGAMENTO, "Aguardando pagamento",
            StatusPedido.PAGAMENTO_RECUSADO, "Pagamento recusado",
            StatusPedido.PAGO, "Pago",
            StatusPedido.EM_SEPARACAO, "Em separação",
            StatusPedido.ENVIADO, "Enviado",
            StatusPedido.ENTREGUE, "Entregue",
            StatusPedido.CANCELADO, "Cancelado",
            StatusPedido.DEVOLVIDO, "Devolvido");

    private static final Map<StatusDevolucao, String> ROTULO_STATUS_DEVOLUCAO = Map.of(
            StatusDevolucao.ABERTA, "Aberta",
            StatusDevolucao.EM_ANALISE, "Em análise",
            StatusDevolucao.EM_MEDIACAO, "Em mediação",
            StatusDevolucao.APROVADA, "Aprovada",
            StatusDevolucao.RECUSADA, "Recusada",
            StatusDevolucao.EM_TRANSITO, "Em trânsito",
            StatusDevolucao.RECEBIDA, "Recebida",
            StatusDevolucao.CONCLUIDA, "Concluída",
            StatusDevolucao.CANCELADA, "Cancelada");

    private RotulosDeExibicao() {
        // classe utilitaria: sem instancia
    }

    static String de(StatusPedido status) {
        String rotulo = ROTULO_STATUS_PEDIDO.get(status);
        if (rotulo == null) {
            throw new IllegalStateException("StatusPedido sem rotulo de exibicao cadastrado: " + status);
        }
        return rotulo;
    }

    static String de(StatusDevolucao status) {
        String rotulo = ROTULO_STATUS_DEVOLUCAO.get(status);
        if (rotulo == null) {
            throw new IllegalStateException("StatusDevolucao sem rotulo de exibicao cadastrado: " + status);
        }
        return rotulo;
    }
}
