package com.plataforma.pergunta;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.plataforma.canal.Canal;
import com.plataforma.canal.CategoriaCanal;
import com.plataforma.canal.RepositorioCanal;
import com.plataforma.canal.TipoCanal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Teste unitário puro (Mockito, sem Spring, sem banco) de
 * {@link ValidadorDeParametros}. Prova a regra dura da decisão 0030:
 * "parâmetro é validado, nunca aceito" - canal ausente/ambíguo/inexistente
 * e período mal formado sempre viram esclarecimento, nunca exceção nem
 * valor padrão silencioso.
 *
 * Isolamento de tenant: {@link RepositorioCanal#findAllByOrderByNomeAsc()}
 * já filtra por tenant sozinho via {@code @TenantId}/RLS (decisão 0007) -
 * não é uma consulta NOVA introduzida por esta tarefa, então não é
 * reexercitado aqui com Postgres real; a garantia estrutural já existe
 * nos testes de isolamento do pacote {@code canal}/{@code comum.tenant}.
 */
class ValidadorDeParametrosTest {

    private static final ZoneOffset OFFSET = ZoneOffset.of("-03:00");
    private static final OffsetDateTime AGORA = OffsetDateTime.of(2026, 9, 14, 15, 30, 0, 0, OFFSET);

    private final RepositorioCanal repositorioCanal = mock(RepositorioCanal.class);
    private final Clock relogio = Clock.fixed(AGORA.toInstant(), OFFSET);
    private final ValidadorDeParametros validador = new ValidadorDeParametros(repositorioCanal, relogio);

    private Canal canal(String codigo, String nome) {
        return new Canal(codigo, nome, TipoCanal.MERCADO_LIVRE, CategoriaCanal.MARKETPLACE, null, null, null);
    }

    // ------------------------------------------------------------------
    // canal
    // ------------------------------------------------------------------

    @Test
    void canalAusenteViraEsclarecimentoListandoOsCanaisExistentes() {
        when(repositorioCanal.findAllByOrderByNomeAsc())
                .thenReturn(List.of(canal("ml-classico", "Mercado Livre Clássico"), canal("shopee", "Shopee")));

        ResultadoParametro<Canal> resultado = validador.validarCanal(Map.of());

        assertFalse(resultado.valido());
        assertTrue(resultado.esclarecimento().contains("Mercado Livre Clássico"));
        assertTrue(resultado.esclarecimento().contains("Shopee"));
    }

    @Test
    void canalAusenteSemNenhumCanalCadastradoOrientaCadastrarPrimeiro() {
        when(repositorioCanal.findAllByOrderByNomeAsc()).thenReturn(List.of());

        ResultadoParametro<Canal> resultado = validador.validarCanal(Map.of("canal", "Shopee"));

        assertFalse(resultado.valido());
        assertTrue(resultado.esclarecimento().toLowerCase(Locale.ROOT).contains("não tem nenhum canal"));
    }

    @Test
    void canalInexistenteViraEsclarecimento() {
        when(repositorioCanal.findAllByOrderByNomeAsc()).thenReturn(List.of(canal("shopee", "Shopee")));

        ResultadoParametro<Canal> resultado = validador.validarCanal(Map.of("canal", "Amazon"));

        assertFalse(resultado.valido());
        assertTrue(resultado.esclarecimento().contains("Amazon"));
    }

    @Test
    void canalAmbiguoViraEsclarecimentoListandoOsCandidatos() {
        // Dois canais diferentes que normalizam para o mesmo nome (mesmo
        // cenario que o cadastro poderia permitir hoje, sem constraint de
        // unicidade de NOME - so codigo e unico).
        when(repositorioCanal.findAllByOrderByNomeAsc())
                .thenReturn(List.of(canal("ml-1", "Mercado Livre"), canal("ml-2", "Mercado Livre")));

        ResultadoParametro<Canal> resultado = validador.validarCanal(Map.of("canal", "Mercado Livre"));

        assertFalse(resultado.valido());
        assertTrue(resultado.esclarecimento().contains("Qual deles?"));
    }

    @Test
    void canalCasaPorNomeIgnorandoAcentoECaixa() {
        when(repositorioCanal.findAllByOrderByNomeAsc())
                .thenReturn(List.of(canal("ml-classico", "Mercado Livre Clássico")));

        ResultadoParametro<Canal> resultado = validador.validarCanal(Map.of("canal", "mercado livre classico"));

        assertTrue(resultado.valido());
        assertEquals("Mercado Livre Clássico", resultado.valor().getNome());
    }

    @Test
    void canalCasaPorCodigo() {
        when(repositorioCanal.findAllByOrderByNomeAsc())
                .thenReturn(List.of(canal("ml-classico", "Mercado Livre Clássico")));

        ResultadoParametro<Canal> resultado = validador.validarCanal(Map.of("canal", "ML-CLASSICO"));

        assertTrue(resultado.valido());
        assertEquals("ml-classico", resultado.valor().getCodigo());
    }

    // ------------------------------------------------------------------
    // periodo - explicito
    // ------------------------------------------------------------------

    @Test
    void periodoAusenteViraEsclarecimento() {
        ResultadoParametro<Periodo> resultado = validador.validarPeriodo(Map.of());

        assertFalse(resultado.valido());
    }

    @Test
    void periodoComInicioMaiorOuIgualAoFimViraEsclarecimento() {
        Map<String, String> parametros = Map.of(
                "inicio", "2026-09-14T00:00:00-03:00",
                "fim", "2026-09-01T00:00:00-03:00");

        ResultadoParametro<Periodo> resultado = validador.validarPeriodo(parametros);

        assertFalse(resultado.valido());
    }

    @Test
    void periodoComInicioIgualAoFimViraEsclarecimento() {
        Map<String, String> parametros = Map.of(
                "inicio", "2026-09-01T00:00:00-03:00",
                "fim", "2026-09-01T00:00:00-03:00");

        ResultadoParametro<Periodo> resultado = validador.validarPeriodo(parametros);

        assertFalse(resultado.valido());
    }

    @Test
    void periodoExplicitoValidoResolveParaOMesmoIntervalo() {
        Map<String, String> parametros = Map.of(
                "inicio", "2026-09-01T00:00:00-03:00",
                "fim", "2026-09-14T00:00:00-03:00");

        ResultadoParametro<Periodo> resultado = validador.validarPeriodo(parametros);

        assertTrue(resultado.valido());
        assertEquals(OffsetDateTime.parse("2026-09-01T00:00:00-03:00"), resultado.valor().inicio());
        assertEquals(OffsetDateTime.parse("2026-09-14T00:00:00-03:00"), resultado.valor().fim());
    }

    @Test
    void periodoComDataMalFormadaViraEsclarecimento() {
        Map<String, String> parametros = Map.of("inicio", "ontem", "fim", "hoje");

        ResultadoParametro<Periodo> resultado = validador.validarPeriodo(parametros);

        assertFalse(resultado.valido());
    }

    // ------------------------------------------------------------------
    // periodo - relativo, com Clock FIXO (determinismo e requisito)
    // ------------------------------------------------------------------

    @Test
    void periodoRelativoInvalidoViraEsclarecimento() {
        ResultadoParametro<Periodo> resultado = validador.validarPeriodo(Map.of("periodoRelativo", "SEMANA_QUE_VEM"));

        assertFalse(resultado.valido());
    }

    @Test
    void mesAtualResolveDoInicioDoMesAteAgora() {
        ResultadoParametro<Periodo> resultado = validador.validarPeriodo(Map.of("periodoRelativo", "MES_ATUAL"));

        assertTrue(resultado.valido());
        assertEquals(OffsetDateTime.of(2026, 9, 1, 0, 0, 0, 0, OFFSET), resultado.valor().inicio());
        assertEquals(AGORA, resultado.valor().fim());
    }

    @Test
    void mesPassadoResolveParaOMesCalendarioAnteriorInteiro() {
        ResultadoParametro<Periodo> resultado = validador.validarPeriodo(Map.of("periodoRelativo", "MES_PASSADO"));

        assertTrue(resultado.valido());
        assertEquals(OffsetDateTime.of(2026, 8, 1, 0, 0, 0, 0, OFFSET), resultado.valor().inicio());
        assertEquals(OffsetDateTime.of(2026, 9, 1, 0, 0, 0, 0, OFFSET), resultado.valor().fim());
    }

    @Test
    void ultimos7DiasResolveParaAgoraMenos7Dias() {
        ResultadoParametro<Periodo> resultado = validador.validarPeriodo(Map.of("periodoRelativo", "ULTIMOS_7_DIAS"));

        assertTrue(resultado.valido());
        assertEquals(AGORA.minusDays(7), resultado.valor().inicio());
        assertEquals(AGORA, resultado.valor().fim());
    }

    @Test
    void ultimos30DiasResolveParaAgoraMenos30Dias() {
        ResultadoParametro<Periodo> resultado = validador.validarPeriodo(Map.of("periodoRelativo", "ULTIMOS_30_DIAS"));

        assertTrue(resultado.valido());
        assertEquals(AGORA.minusDays(30), resultado.valor().inicio());
        assertEquals(AGORA, resultado.valor().fim());
    }

    @Test
    void ultimos90DiasResolveParaAgoraMenos90Dias() {
        ResultadoParametro<Periodo> resultado = validador.validarPeriodo(Map.of("periodoRelativo", "ULTIMOS_90_DIAS"));

        assertTrue(resultado.valido());
        assertEquals(AGORA.minusDays(90), resultado.valor().inicio());
        assertEquals(AGORA, resultado.valor().fim());
    }

    @Test
    void anoAtualResolveDoInicioDoAnoAteAgora() {
        ResultadoParametro<Periodo> resultado = validador.validarPeriodo(Map.of("periodoRelativo", "ANO_ATUAL"));

        assertTrue(resultado.valido());
        assertEquals(OffsetDateTime.of(2026, 1, 1, 0, 0, 0, 0, OFFSET), resultado.valor().inicio());
        assertEquals(AGORA, resultado.valor().fim());
    }

    @Test
    void periodoRelativoAceitaCaixaMinuscula() {
        ResultadoParametro<Periodo> resultado = validador.validarPeriodo(Map.of("periodoRelativo", "mes_atual"));

        assertTrue(resultado.valido());
    }
}
