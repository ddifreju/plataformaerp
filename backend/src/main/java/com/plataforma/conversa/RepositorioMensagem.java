package com.plataforma.conversa;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Acesso a mensagem. Repositorio proprio (nao so acessivel via Conversa):
 * a fila de atendimento le mensagem diretamente (montar o thread ao abrir
 * a conversa - ix_mensagem_tenant_conversa_enviada), e a anonimizacao a
 * pedido do titular (LGPD, ver cabecalho da V011) precisa localizar e
 * atualizar todas as mensagens de uma conversa, nao so a conversa em si.
 *
 * Predicado de tenant vem do @TenantId da entidade (decisao 0007) -
 * nenhum metodo aqui deve escrever "WHERE tenant_id = ?" a mao.
 */
public interface RepositorioMensagem extends JpaRepository<Mensagem, UUID> {

    List<Mensagem> findByConversaIdOrderByEnviadaEmAsc(UUID conversaId);
}
