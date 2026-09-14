package com.plataforma.pergunta;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.plataforma.canal.Canal;
import com.plataforma.canal.CategoriaCanal;
import com.plataforma.canal.TipoCanal;
import com.plataforma.margem.Lacuna;
import com.plataforma.margem.ResultadoMargemPeriodo;
import com.plataforma.margem.RotuloTeto;

/**
 * Fábricas de objetos de domínio usadas por {@link ServicoPerguntaTest} e
 * {@link NenhumDigitoInventadoTest} - evita repetir a construção verbosa
 * de {@link ResultadoMargemPeriodo} (13 campos posicionais) em cada teste.
 * Classe de SUPORTE DE TESTE, nunca referenciada por código de produção.
 */
final class DublesDeTeste {

    private DublesDeTeste() {
        // classe utilitaria: sem instancia
    }

    static Canal canal(String codigo, String nome) {
        return new Canal(codigo, nome, TipoCanal.MERCADO_LIVRE, CategoriaCanal.MARKETPLACE, null, null, null);
    }

    static ResultadoMargemPeriodo margem(UUID canalId, OffsetDateTime inicio, OffsetDateTime fim,
            BigDecimal n0, BigDecimal n1, BigDecimal n2, BigDecimal n3, BigDecimal n4,
            Optional<BigDecimal> percentualContribuicao, Optional<BigDecimal> percentualLiquida,
            List<Lacuna> lacunas, RotuloTeto rotulo, int quantidadePedidos) {
        Set<UUID> idsPedido = quantidadePedidos == 0 ? Set.of() : Set.of(UUID.randomUUID());
        Set<UUID> idsCusto = quantidadePedidos == 0 ? Set.of() : Set.of(UUID.randomUUID());
        return new ResultadoMargemPeriodo(canalId, inicio, fim, "escopo de teste", n0, n1, n2, n3, n4,
                percentualContribuicao, percentualLiquida, List.of(), lacunas, rotulo, quantidadePedidos,
                idsPedido, idsCusto);
    }
}
