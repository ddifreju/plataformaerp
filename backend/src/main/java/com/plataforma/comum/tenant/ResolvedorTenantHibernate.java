package com.plataforma.comum.tenant;

import java.util.UUID;

import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.springframework.stereotype.Component;

/**
 * Ponte entre o {@link ContextoTenant} (ThreadLocal preenchida pelo
 * {@link FiltroTenant}) e o Hibernate.
 *
 * E esta classe que faz o Hibernate acrescentar automaticamente o
 * predicado de tenant em toda consulta e preencher o campo anotado com
 * {@code @org.hibernate.annotations.TenantId} em todo insert - decisao
 * 0007, camada 3 ("o predicado nao pode depender do programador lembrar
 * de escreve-lo").
 *
 * O tipo do identificador e UUID, nao String: as colunas tenant_id no
 * banco sao do tipo `uuid` (ver V003), e o campo anotado com @TenantId
 * em ConsultaAuditada tambem e UUID. Os dois lados desta ponte
 * (resolvedor <-> entidade) tem que usar exatamente o mesmo tipo Java.
 *
 * FAIL-CLOSED: quando nao ha tenant no ContextoTenant, esta classe NAO
 * lanca excecao - devolve um UUID sentinela ({@link #SEM_TENANT}) que
 * nao corresponde a tenant nenhum. Isso e deliberado: o Hibernate pode
 * chamar resolveCurrentTenantIdentifier() em momentos internos que nao
 * tem relacao nenhuma com uma requisicao HTTP (por exemplo, ao abrir
 * uma sessao para validar o schema no boot). Lancar excecao aqui
 * derrubaria essas operacoes. O sentinela garante que, se por algum
 * motivo uma consulta chegar a rodar sem tenant no contexto, ela filtra
 * por um id que nao existe e devolve zero linhas - nunca "todas as
 * linhas". O mesmo principio da V001 (app_current_tenant_id), aplicado
 * na camada do ORM em vez de na camada do banco.
 */
@Component
public class ResolvedorTenantHibernate implements CurrentTenantIdentifierResolver<UUID> {

    /**
     * UUID que nunca sera o id de um tenant real: tenant.id e sempre
     * gerado por gen_random_uuid() (V002), que na pratica jamais produz
     * o UUID nulo (todos os bits zerados).
     */
    public static final UUID SEM_TENANT = UUID.fromString("00000000-0000-0000-0000-000000000000");

    @Override
    public UUID resolveCurrentTenantIdentifier() {
        return ContextoTenant.atualOuVazio().orElse(SEM_TENANT);
    }

    @Override
    public boolean validateExistingCurrentSessions() {
        // true: se uma sessao ja aberta com um tenant diferente for
        // reaproveitada, o Hibernate deve reclamar em vez de misturar
        // dado de dois tenants na mesma sessao.
        return true;
    }
}
