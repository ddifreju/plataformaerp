package com.plataforma.margem;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/**
 * Acesso a taxa_canal (nivel 2 da hierarquia de taxas, decisao 0019).
 * Predicado de tenant vem do {@code @TenantId} da entidade (decisao 0007)
 * - JPQL (nao SQL nativo), entao o Hibernate acrescenta o predicado
 * sozinho, igual as consultas {@code @Query} de {@code RepositorioCusto}.
 *
 * {@link #buscarCandidatas} e a consulta de selecao do cabecalho da V013,
 * TRADUZIDA para JPQL (em vez de nativa) exatamente para manter esse
 * predicado automatico de tenant. A unica diferenca do SQL literal do
 * cabecalho: o {@code LIMIT 2} vira responsabilidade de quem CHAMA este
 * metodo ({@link SelecaoTaxaCanal#selecionar}, que so olha os dois
 * primeiros elementos da lista) - JPQL padrao do Spring Data nao aceita
 * LIMIT dentro da propria anotacao {@code @Query} sem paginacao, e
 * paginar aqui so pra pegar 2 seria complexidade sem ganho.
 */
public interface RepositorioTaxaCanal extends JpaRepository<TaxaCanal, UUID> {

    @Query("""
            SELECT t FROM TaxaCanal t
            WHERE t.canalId = :canalId
              AND t.tipoTaxa = :tipoTaxa
              AND t.vigenciaInicio <= :momento
              AND (t.vigenciaFim IS NULL OR t.vigenciaFim > :momento)
              AND t.categoriaCanal IN (:categoria, '*')
              AND t.tipoAnuncio IN (:tipoAnuncio, '*')
              AND (t.faixaValorMin IS NULL OR :valor >= t.faixaValorMin)
              AND (t.faixaValorMax IS NULL OR :valor < t.faixaValorMax)
            ORDER BY t.especificidade DESC
            """)
    List<TaxaCanal> buscarCandidatas(UUID canalId, TipoTaxaCanal tipoTaxa, OffsetDateTime momento,
            String categoria, String tipoAnuncio, BigDecimal valor);

    /**
     * GUARDA DO CURINGA (cabecalho da V013): usada quando o adaptador NAO
     * preservou {@code categoria}/{@code tipoAnuncio} do anuncio (ainda
     * nao implementado - pendencia P5 do documento fiscal). Se esta
     * contagem for maior que zero, existe taxa MAIS ESPECIFICA cadastrada
     * para o tipo de taxa neste canal/vigencia, e aplicar o curinga sem
     * saber a categoria/anuncio real seria dado plausivel e errado - o
     * chamador deve tratar como lacuna (nivel 3) em vez de usar
     * {@link #buscarCandidatas} com {@code '*'} direto.
     */
    @Query("""
            SELECT COUNT(t) FROM TaxaCanal t
            WHERE t.canalId = :canalId
              AND t.tipoTaxa = :tipoTaxa
              AND (t.categoriaCanal <> '*' OR t.tipoAnuncio <> '*')
              AND t.vigenciaInicio <= :momento
              AND (t.vigenciaFim IS NULL OR t.vigenciaFim > :momento)
            """)
    long contarTaxasMaisEspecificasQueCuringa(UUID canalId, TipoTaxaCanal tipoTaxa, OffsetDateTime momento);
}
