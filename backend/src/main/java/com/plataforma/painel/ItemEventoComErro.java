package com.plataforma.painel;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.plataforma.ingestao.EventoIngerido;

/**
 * Um evento de ingestao que falhou (tarefa 20 - fila de pendencias do
 * analista). {@code acao} diz o que fazer, como o requisito pede.
 *
 * DE PROPOSITO NAO INCLUI {@code payloadBruto}: e o maior volume de dado
 * pessoal do banco (nome/endereco/contato do consumidor final do cliente -
 * ver docs/PENDENCIAS.md) e nao e necessario para a fila decidir o que
 * fazer. {@code erroMensagem} basta para a acao "revisar e reprocessar".
 */
public record ItemEventoComErro(
        UUID id, UUID canalId, String tipoEvento, String idExterno,
        String erroMensagem, OffsetDateTime recebidoEm, int tentativas, String acao) {

    private static final String ACAO = "Revisar o motivo da falha e reprocessar este evento.";

    public static ItemEventoComErro de(EventoIngerido evento) {
        return new ItemEventoComErro(
                evento.getId(),
                evento.getCanalId(),
                evento.getTipoEvento().name(),
                evento.getIdExterno(),
                evento.getErroMensagem(),
                evento.getRecebidoEm(),
                evento.getTentativas(),
                ACAO);
    }
}
