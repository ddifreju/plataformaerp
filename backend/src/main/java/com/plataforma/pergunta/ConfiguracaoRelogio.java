package com.plataforma.pergunta;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Bean de {@link Clock} para {@link ValidadorDeParametros} resolver
 * {@link PeriodoRelativo} ("mês atual", "últimos 7 dias"...). Nunca
 * {@code OffsetDateTime.now()} chamado direto dentro do validador - a
 * injeção de {@link Clock} é o que torna a resolução de período
 * determinística e testável (fixar o relógio num teste em vez de
 * depender da data em que o teste roda).
 */
@Configuration
public class ConfiguracaoRelogio {

    @Bean
    public Clock relogio() {
        return Clock.systemDefaultZone();
    }
}
