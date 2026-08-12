package com.plataforma.comum.tenant;

import java.util.Optional;
import java.util.UUID;

/**
 * Guarda o tenant da requisicao corrente numa ThreadLocal.
 *
 * Decisao 0007 (camada 2): o tenant e propagado por CONTEXTO, nunca
 * passado manualmente de metodo em metodo. O {@link FiltroTenant}
 * preenche isto no inicio da requisicao e limpa no finally; todo o
 * resto da aplicacao le daqui, nunca recebe o tenant como parametro.
 *
 * {@link #atual()} LANCA excecao quando nao ha tenant definido. Isto e
 * deliberado: codigo que tenta ler o tenant fora de uma requisicao com
 * tenant resolvido tem um bug, e o bug deve estourar imediatamente,
 * nunca operar silenciosamente com um tenant default ou nulo.
 *
 * {@link #atualOuVazio()} existe para os poucos consumidores que
 * LEGITIMAMENTE podem rodar sem tenant - hoje, so o
 * {@link DataSourceComTenant}: toda conexao entregue pelo pool precisa
 * de um SET no GUC, mesmo quando, por algum motivo, nao ha tenant algum
 * no contexto (nesse caso o GUC vira string vazia, nunca fica sem
 * valor - ver DataSourceComTenant).
 */
public final class ContextoTenant {

    private static final ThreadLocal<UUID> TENANT_CORRENTE = new ThreadLocal<>();

    private ContextoTenant() {
        // classe utilitaria: sem instancia
    }

    public static void definir(UUID tenantId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId nao pode ser nulo");
        }
        TENANT_CORRENTE.set(tenantId);
    }

    /**
     * @return o tenant da thread corrente
     * @throws IllegalStateException se nenhum tenant foi definido nesta thread
     */
    public static UUID atual() {
        UUID tenantId = TENANT_CORRENTE.get();
        if (tenantId == null) {
            throw new IllegalStateException(
                    "Nenhum tenant definido no contexto da thread corrente. Isto e um bug: "
                            + "todo codigo que chega aqui deveria rodar depois do FiltroTenant "
                            + "ter definido o tenant da requisicao.");
        }
        return tenantId;
    }

    /**
     * Para os poucos consumidores que legitimamente podem operar sem
     * tenant definido (ex.: o DataSource, que precisa de um valor - vazio
     * que seja - para toda conexao, mesmo fora de uma requisicao).
     */
    public static Optional<UUID> atualOuVazio() {
        return Optional.ofNullable(TENANT_CORRENTE.get());
    }

    /**
     * Remove o tenant da thread corrente. Deve ser chamado SEMPRE em um
     * bloco finally: o servidor de aplicacao reutiliza threads entre
     * requisicoes, e esquecer de limpar vazaria o tenant de um request
     * para o proximo atendido pela mesma thread.
     */
    public static void limpar() {
        TENANT_CORRENTE.remove();
    }
}
