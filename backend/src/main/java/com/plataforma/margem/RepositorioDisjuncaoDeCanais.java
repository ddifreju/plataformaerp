package com.plataforma.margem;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Tarefa 32 (decisao 0033): "o conjunto de canais pedido e comprovadamente
 * disjunto?" - a pre-condicao que precisa ser verdadeira ANTES de somar
 * qualquer coisa entre canais.
 *
 * <h2>Por que SQL NATIVO, e por que o tenant e um bind parameter explicito</h2>
 * A consulta abaixo e a que o arquiteto deixou pronta no relato dele -
 * reproduzida aqui com a UNICA mudanca de {@code :canais}/{@code :tenant}
 * (named parameters) para {@code ?} posicional, exigencia de
 * {@link JdbcTemplate}; nenhuma coluna, JOIN, CASE ou condicao foi
 * alterada.
 *
 * ATENCAO, e por isso este comentario existe: consulta nativa (JDBC cru)
 * NAO passa pelo Hibernate, entao o predicado automatico de
 * {@code @TenantId} (decisao 0007, camada 3) SIMPLESMENTE NAO EXISTE
 * aqui. A UNICA camada de isolamento de tenant que resta e o
 * {@code c.tenant_id = ?} explicito no LEFT JOIN abaixo (mais o RLS do
 * Postgres por baixo, camada 2) - exatamente o mesmo cuidado que
 * {@code ServicoIngestao} ja toma para o SQL nativo do pipeline de
 * ingestao ({@code ContextoTenant.atual()} lido e passado a mao, nunca
 * omitido). Ver {@code IsolamentoMargemConsolidadaTest} para a prova
 * comportamental disto.
 *
 * <h2>Por que o LEFT JOIN e essencial (nao um detalhe estetico)</h2>
 * Com INNER JOIN, um {@code canalId} de OUTRO TENANT simplesmente SUMIRIA
 * do resultado (a condicao {@code c.tenant_id = ?} nao bateria) - e um
 * conjunto com um id sumido pareceria disjunto POR OMISSAO, o pior
 * desfecho possivel (equivalente a "nao sei, entao deixa passar"). Com
 * LEFT JOIN, a linha continua existindo com {@code c.id IS NULL}, e o
 * CASE a traduz explicitamente para {@link MotivoBloqueioDeSoma#CANAL_INEXISTENTE} -
 * o mesmo motivo, seja o id inexistente de verdade ou de outro tenant
 * (decisao deliberada, mesmo racional de {@code CanalDesconhecidoException}:
 * nao dar a quem tenta adivinhar UUID nenhuma pista de qual dos dois e).
 *
 * <h2>Por que o ramo ESPELHADO_POR_OUTRO_DO_CONJUNTO nao e codigo morto</h2>
 * Logicamente redundante com {@link MotivoBloqueioDeSoma#E_ESPELHO}: se A
 * e espelhado por B, B ja aparece bloqueado como {@code E_ESPELHO}
 * (porque B, o proprio espelho, TAMBEM esta no conjunto pedido - senao
 * este EXISTS nem faria diferenca). Mas SEM este ramo, A apareceria
 * "limpo" (nenhum motivo), e a soma incluiria A sozinho, sem nunca dizer
 * a lojista QUE ele e a metade de um par. E o que faz a recusa nomear os
 * DOIS lados - ver o Javadoc de {@link ServicoMargemPeriodoConsolidada}.
 */
@Repository
public class RepositorioDisjuncaoDeCanais {

    private static final String CONSULTA = """
            SELECT solicitado.id                       AS canal_id,
                   c.codigo                            AS codigo,
                   c.espelha_canal_id                  AS espelha_canal_id,
                   CASE
                       WHEN c.id IS NULL                         THEN 'CANAL_INEXISTENTE'
                       WHEN c.escopo_declarado = 'NAO_DECLARADO' THEN 'NAO_DECLARADO'
                       WHEN c.escopo_declarado = 'ESPELHO'       THEN 'E_ESPELHO'
                       WHEN EXISTS (SELECT 1
                                      FROM canal m
                                     WHERE m.tenant_id        = c.tenant_id
                                       AND m.espelha_canal_id = c.id
                                       AND m.id = ANY(?))      THEN 'ESPELHADO_POR_OUTRO_DO_CONJUNTO'
                   END                                 AS motivo_do_bloqueio
              FROM unnest(?) AS solicitado(id)
              LEFT JOIN canal c ON c.id = solicitado.id AND c.tenant_id = ?
            """;

    private final JdbcTemplate jdbcTemplate;

    public RepositorioDisjuncaoDeCanais(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * @param tenantId lido de {@code ContextoTenant.atual()} por quem
     *         chama (nunca aceito como parametro de fora da aplicacao) -
     *         ver o Javadoc da classe sobre por que o bind e obrigatorio
     *         aqui
     * @param canalIds os canais pedidos para a soma (nunca vazio - quem
     *         chama resolve "todos os canais ativos" antes de chegar
     *         aqui)
     * @return uma linha por {@code canalId} pedido, na mesma ordem em que
     *         {@code unnest} os devolve
     */
    public List<LinhaDisjuncaoCanal> verificar(UUID tenantId, List<UUID> canalIds) {
        if (canalIds == null || canalIds.isEmpty()) {
            throw new IllegalArgumentException(
                    "canalIds nao pode ser vazio: quem chama resolve 'todos os canais ativos' antes de verificar "
                            + "disjuncao - uma lista vazia aqui indicaria zero canais para verificar, nao 'todos'.");
        }

        UUID[] arrayDeCanais = canalIds.toArray(new UUID[0]);

        return jdbcTemplate.query(CONSULTA, (PreparedStatement ps) -> {
            // Os tres binds precisam ser o MESMO array (o texto da
            // consulta usa a[i] duas vezes) - criado na MESMA conexao que
            // vai executar a instrucao, exatamente como o pgjdbc exige
            // para o tipo uuid[].
            ps.setArray(1, criarArrayDeUuid(ps, arrayDeCanais));
            ps.setArray(2, criarArrayDeUuid(ps, arrayDeCanais));
            ps.setObject(3, tenantId);
        }, (linha, indice) -> new LinhaDisjuncaoCanal(
                (UUID) linha.getObject("canal_id"),
                linha.getString("codigo"),
                (UUID) linha.getObject("espelha_canal_id"),
                linha.getString("motivo_do_bloqueio") == null
                        ? null
                        : MotivoBloqueioDeSoma.valueOf(linha.getString("motivo_do_bloqueio"))));
    }

    private static java.sql.Array criarArrayDeUuid(PreparedStatement ps, UUID[] valores) throws SQLException {
        return ps.getConnection().createArrayOf("uuid", valores);
    }
}
