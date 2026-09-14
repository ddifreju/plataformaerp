package com.plataforma.pergunta;

import java.text.Normalizer;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.plataforma.canal.Canal;
import com.plataforma.canal.RepositorioCanal;

/**
 * Validação determinística dos dois parâmetros do catálogo (decisão 0030:
 * "parâmetro é validado, nunca aceito"). Nenhum método aqui lança exceção
 * de parâmetro ruim - o "caminho de erro" de parâmetro sempre é um
 * {@link ResultadoParametro} com {@code esclarecimento} preenchido, porque
 * um parâmetro ausente ou ambíguo não é um bug, é a lojista perguntando
 * de um jeito que o sistema ainda não consegue amarrar sozinho.
 *
 * {@code canal} é sempre resolvido contra {@link RepositorioCanal} do
 * TENANT DO CONTEXTO - o repositório filtra por tenant sozinho (via
 * {@code @TenantId}/RLS, decisão 0007), então nunca existe risco de casar
 * o parâmetro contra o canal de outro tenant. Nunca há canal padrão: sem
 * parâmetro, é esclarecimento; parâmetro ambíguo, é esclarecimento.
 */
@Component
public class ValidadorDeParametros {

    private final RepositorioCanal repositorioCanal;
    private final Clock relogio;

    public ValidadorDeParametros(RepositorioCanal repositorioCanal, Clock relogio) {
        this.repositorioCanal = repositorioCanal;
        this.relogio = relogio;
    }

    public ResultadoParametro<Canal> validarCanal(Map<String, String> parametros) {
        List<Canal> canais = repositorioCanal.findAllByOrderByNomeAsc();
        if (canais.isEmpty()) {
            return ResultadoParametro.esclarecimento(
                    "Você ainda não tem nenhum canal cadastrado. Cadastre um canal antes de perguntar sobre margem.");
        }

        String valor = parametros == null ? null : parametros.get("canal");
        if (valor == null || valor.isBlank()) {
            return ResultadoParametro.esclarecimento(
                    "Para qual canal? " + listarNomes(canais) + ".");
        }

        String normalizado = normalizar(valor);
        List<Canal> candidatos = canais.stream()
                .filter(canal -> normalizar(canal.getNome()).equals(normalizado)
                        || normalizar(canal.getCodigo()).equals(normalizado))
                .toList();

        if (candidatos.isEmpty()) {
            return ResultadoParametro.esclarecimento(
                    "Não encontrei o canal \"" + valor + "\". " + listarNomes(canais) + ".");
        }
        if (candidatos.size() > 1) {
            return ResultadoParametro.esclarecimento(
                    "Mais de um canal corresponde a \"" + valor + "\": " + listarNomes(candidatos)
                            + ". Qual deles?");
        }
        return ResultadoParametro.valido(candidatos.get(0));
    }

    public ResultadoParametro<Periodo> validarPeriodo(Map<String, String> parametros) {
        Map<String, String> params = parametros == null ? Map.of() : parametros;
        String inicioTexto = params.get("inicio");
        String fimTexto = params.get("fim");
        String periodoRelativoTexto = params.get("periodoRelativo");

        if (inicioTexto != null && !inicioTexto.isBlank() && fimTexto != null && !fimTexto.isBlank()) {
            return validarPeriodoExplicito(inicioTexto, fimTexto);
        }

        if (periodoRelativoTexto != null && !periodoRelativoTexto.isBlank()) {
            return validarPeriodoRelativo(periodoRelativoTexto);
        }

        return ResultadoParametro.esclarecimento(
                "Para qual período? Informe uma data de início e fim, ou diga um período como "
                        + periodosAceitosParaTexto() + ".");
    }

    private ResultadoParametro<Periodo> validarPeriodoExplicito(String inicioTexto, String fimTexto) {
        OffsetDateTime inicio;
        OffsetDateTime fim;
        try {
            inicio = OffsetDateTime.parse(inicioTexto);
            fim = OffsetDateTime.parse(fimTexto);
        } catch (DateTimeParseException e) {
            return ResultadoParametro.esclarecimento(
                    "Não entendi as datas do período. Use o formato AAAA-MM-DDThh:mm:ssZ.");
        }
        if (!inicio.isBefore(fim)) {
            return ResultadoParametro.esclarecimento(
                    "O início do período precisa ser antes do fim - recebi início " + inicio + " e fim " + fim + ".");
        }
        return ResultadoParametro.valido(new Periodo(inicio, fim));
    }

    private ResultadoParametro<Periodo> validarPeriodoRelativo(String periodoRelativoTexto) {
        PeriodoRelativo periodoRelativo;
        try {
            periodoRelativo = PeriodoRelativo.valueOf(periodoRelativoTexto.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return ResultadoParametro.esclarecimento(
                    "Não reconheço o período \"" + periodoRelativoTexto + "\". Use um destes: "
                            + periodosAceitosParaTexto() + ".");
        }
        return ResultadoParametro.valido(resolverPeriodoRelativo(periodoRelativo));
    }

    /**
     * Resolve cada {@link PeriodoRelativo} contra o {@link #relogio}
     * injetado - nunca {@code OffsetDateTime.now()} direto (ver Javadoc de
     * {@link ConfiguracaoRelogio}). Fim é sempre EXCLUSIVO, mesma semântica
     * de {@code ServicoMargemPeriodo.calcular}.
     */
    private Periodo resolverPeriodoRelativo(PeriodoRelativo periodoRelativo) {
        OffsetDateTime agora = OffsetDateTime.now(relogio);
        return switch (periodoRelativo) {
            case MES_ATUAL -> new Periodo(inicioDoMes(agora), agora);
            case MES_PASSADO -> new Periodo(inicioDoMes(agora).minusMonths(1), inicioDoMes(agora));
            case ULTIMOS_7_DIAS -> new Periodo(agora.minusDays(7), agora);
            case ULTIMOS_30_DIAS -> new Periodo(agora.minusDays(30), agora);
            case ULTIMOS_90_DIAS -> new Periodo(agora.minusDays(90), agora);
            case ANO_ATUAL -> new Periodo(inicioDoAno(agora), agora);
        };
    }

    private OffsetDateTime inicioDoMes(OffsetDateTime referencia) {
        return referencia.withDayOfMonth(1).toLocalDate().atStartOfDay(referencia.getOffset()).toOffsetDateTime();
    }

    private OffsetDateTime inicioDoAno(OffsetDateTime referencia) {
        return referencia.withDayOfYear(1).toLocalDate().atStartOfDay(referencia.getOffset()).toOffsetDateTime();
    }

    private String periodosAceitosParaTexto() {
        return String.join(", ", List.of(
                PeriodoRelativo.MES_ATUAL.name(), PeriodoRelativo.MES_PASSADO.name(),
                PeriodoRelativo.ULTIMOS_7_DIAS.name(), PeriodoRelativo.ULTIMOS_30_DIAS.name(),
                PeriodoRelativo.ULTIMOS_90_DIAS.name(), PeriodoRelativo.ANO_ATUAL.name()));
    }

    private String listarNomes(List<Canal> canais) {
        return "Canais existentes: " + canais.stream().map(Canal::getNome).reduce((a, b) -> a + ", " + b).orElse("");
    }

    private String normalizar(String texto) {
        String semAcento = Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return semAcento.strip().toLowerCase(Locale.ROOT);
    }
}
