package com.plataforma.margem;

import java.time.OffsetDateTime;

/**
 * Periodo invalido pedido ao endpoint da tarefa 16 (inicio nao anterior a
 * fim). Tipada, nunca {@code IllegalArgumentException} genérica saindo
 * direto do controller - ver {@link com.plataforma.comum.web.TratadorGlobalDeErros}
 * para a traducao HTTP.
 */
public class PeriodoInvalidoException extends RuntimeException {

    public PeriodoInvalidoException(OffsetDateTime inicio, OffsetDateTime fim) {
        super("Periodo invalido: inicio (" + inicio + ") precisa ser anterior a fim (" + fim + ").");
    }
}
