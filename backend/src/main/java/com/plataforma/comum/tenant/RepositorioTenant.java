package com.plataforma.comum.tenant;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Acesso a tabela tenant, somente leitura (o papel de banco da
 * aplicacao so tem GRANT SELECT nela - ver migration V004).
 *
 * CANDIDATA OBVIA A CACHE: existsByIdAndAtivoTrue roda em TODA
 * requisicao, porque o FiltroTenant chama isto antes de deixar
 * qualquer requisicao passar. Nao adicionamos cache agora de proposito:
 * o catalogo de tenants ainda e pequeno, a consulta e por chave
 * primaria (rapida), e um cache aqui precisaria de invalidacao correta
 * no exato momento em que um tenant e desativado - do contrario um
 * tenant desativado continuaria acessando o sistema pelo tempo de vida
 * do cache. Quando o volume de requisicoes tornar esta consulta um
 * gargalo medido (nao suposto), resolve-se com cache + invalidacao
 * explicita no fluxo de desativacao de tenant.
 */
public interface RepositorioTenant extends JpaRepository<Tenant, UUID> {

    boolean existsByIdAndAtivoTrue(UUID id);
}
