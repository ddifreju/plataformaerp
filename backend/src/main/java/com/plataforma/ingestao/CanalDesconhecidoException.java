package com.plataforma.ingestao;

/**
 * Canal informado na ingestao nao existe ou nao pertence ao tenant da
 * requisicao.
 *
 * Os dois casos sao a MESMA excecao de proposito, com a mesma mensagem:
 * distinguir "nao existe" de "existe mas e de outro tenant" contaria a
 * quem esta tentando adivinhar UUIDs que aquele canal existe em algum
 * lugar do sistema. Mesmo raciocinio do TenantDesconhecidoException do
 * FiltroTenant.
 *
 * A consulta que origina esta excecao ja e restrita ao tenant pelo
 * @TenantId (decisao 0007, camada 3) - por isso "nao encontrou" ja
 * cobre os dois casos sem nenhuma comparacao manual de tenant.
 */
public class CanalDesconhecidoException extends RuntimeException {

    public CanalDesconhecidoException(String mensagem) {
        super(mensagem);
    }
}
