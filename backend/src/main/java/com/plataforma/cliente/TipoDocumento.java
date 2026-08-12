package com.plataforma.cliente;

/**
 * Dominio de cliente.documento_tipo (migration V007,
 * CONSTRAINT ck_cliente_documento_tipo). Nullable: nem toda fonte informa
 * o tipo do documento.
 */
public enum TipoDocumento {
    CPF,
    CNPJ
}
