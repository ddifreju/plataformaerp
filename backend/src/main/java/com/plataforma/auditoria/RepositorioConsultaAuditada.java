package com.plataforma.auditoria;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Acesso a trilha de auditoria (consulta_auditada). Toda consulta feita
 * por aqui ja sai filtrada por tenant automaticamente: a entidade tem
 * @org.hibernate.annotations.TenantId, e o Hibernate acrescenta o
 * predicado sozinho (decisao 0007, camada 3). O Row Level Security no
 * banco (V003) e a segunda camada, para o dia em que alguem escrever
 * SQL nativo por fora deste repositorio e esquecer o predicado.
 *
 * Sem controller ainda: a trilha de auditoria hoje so e escrita por
 * codigo interno (quando existir - Fase 2, motor de margem). Expor
 * leitura por HTTP e decisao de outra tarefa.
 */
public interface RepositorioConsultaAuditada extends JpaRepository<ConsultaAuditada, UUID> {
}
