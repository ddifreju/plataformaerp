package com.plataforma.ingestao;

import java.util.List;
import java.util.UUID;

import com.plataforma.integracao.CampoAusente;

/**
 * O que {@link ServicoIngestao#ingerir} devolve para quem chamou (webhook,
 * job de polling futuro, teste). Dois formatos, correspondendo aos casos
 * do cabecalho da V012:
 *
 * <ul>
 *   <li>{@link #reenvioIdentico()} - caso 2: mesma chave natural, mesmo
 *       hash. No-op, nada foi processado, nada foi alterado no banco.</li>
 *   <li>{@link #processado(UUID, UUID, List)} - casos 1 e 3: evento novo
 *       ou atualizacao legitima. O adaptador rodou e o pedido foi
 *       persistido (ou reconhecido como ja existente pela segunda trava
 *       de uq_pedido_origem - ver ServicoIngestao.persistirResultado).</li>
 * </ul>
 */
public record ResultadoIngestao(
        boolean processado,
        UUID idEvento,
        UUID idPedido,
        List<CampoAusente> camposAusentes) {

    public ResultadoIngestao {
        camposAusentes = (camposAusentes == null) ? List.of() : List.copyOf(camposAusentes);
    }

    public static ResultadoIngestao reenvioIdentico() {
        return new ResultadoIngestao(false, null, null, List.of());
    }

    public static ResultadoIngestao processado(UUID idEvento, UUID idPedido, List<CampoAusente> camposAusentes) {
        return new ResultadoIngestao(true, idEvento, idPedido, camposAusentes);
    }
}
