package com.plataforma.ingestao;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Acesso a evento_ingerido. Predicado de tenant vem do @TenantId da
 * entidade (decisao 0007) - nenhum metodo aqui deve escrever
 * "WHERE tenant_id = ?" a mao.
 *
 * findByCanalIdAndTipoEventoAndIdExterno deriva a chave natural de
 * idempotencia (uq_evento_ingerido_chave_natural), MENOS o tenant_id, que
 * o Hibernate ja restringe sozinho via @TenantId.
 *
 * IMPORTANTE: o UPSERT atomico descrito no cabecalho da V012
 * (INSERT ... ON CONFLICT ... DO UPDATE ... WHERE hash_payload IS
 * DISTINCT FROM ...) NAO tem equivalente direto em Spring Data JPA/JPQL -
 * um @Query nativeQuery=true faria isso, mas perderia o predicado de
 * tenant automatico (so o RLS protegeria, ver risco no proprio SQL). Este
 * repositorio deliberadamente NAO tenta reproduzir esse upsert: quem
 * implementar o pipeline de ingestao (fora do escopo desta tarefa) decide
 * como executar aquela instrucao especifica, com o cuidado de concorrencia
 * que o comentario da V012 exige.
 */
public interface RepositorioEventoIngerido extends JpaRepository<EventoIngerido, UUID> {

    Optional<EventoIngerido> findByCanalIdAndTipoEventoAndIdExterno(
            UUID canalId, TipoEvento tipoEvento, String idExterno);
}
