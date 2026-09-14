package com.plataforma.comum.ambiente;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Teste unitario puro (sem Spring context, sem banco, sem Docker) de
 * {@link ValidadorDeAmbiente} - tarefa 30 (Fase 4, bloco B).
 *
 * {@code MockEnvironment} (spring-test) simula variaveis de ambiente sem
 * tocar o ambiente de verdade do processo: e o que permite testar
 * "faltou X" sem depender de qual variavel esta ou nao definida na
 * maquina de quem roda o teste.
 */
class ValidadorDeAmbienteTest {

    private final ValidadorDeAmbiente validador = new ValidadorDeAmbiente();

    @Test
    void naoValidaNadaNoPerfilDevExplicito() {
        MockEnvironment ambiente = new MockEnvironment();
        ambiente.setActiveProfiles("dev");
        // Nenhuma variavel definida de proposito - dev nao pode exigir.

        assertDoesNotThrow(() -> validador.postProcessEnvironment(ambiente, null));
    }

    @Test
    void naoValidaNadaSemNenhumPerfilAtivo() {
        // Perfil "default" do Spring (ninguem define SPRING_PROFILES_ACTIVE)
        // e o comportamento de desenvolvimento hoje - ver application.yml.
        MockEnvironment ambiente = new MockEnvironment();

        assertDoesNotThrow(() -> validador.postProcessEnvironment(ambiente, null));
    }

    @Test
    void recusaSubirEmStagingSemNenhumaDasDuasVariaveis() {
        MockEnvironment ambiente = new MockEnvironment();
        ambiente.setActiveProfiles("staging");

        VariavelDeAmbienteAusenteException excecao = assertThrows(
                VariavelDeAmbienteAusenteException.class,
                () -> validador.postProcessEnvironment(ambiente, null));

        assertTrue(excecao.getMessage().contains("SPRING_DATASOURCE_PASSWORD"));
        assertTrue(excecao.getMessage().contains("APP_DOCUMENTO_HMAC_CHAVE"));
    }

    @Test
    void mensagemDeErroDizExatamenteQualVariavelFalta() {
        MockEnvironment ambiente = new MockEnvironment();
        ambiente.setActiveProfiles("staging");
        ambiente.setProperty("SPRING_DATASOURCE_PASSWORD", "senha-de-teste");
        // APP_DOCUMENTO_HMAC_CHAVE continua faltando.

        VariavelDeAmbienteAusenteException excecao = assertThrows(
                VariavelDeAmbienteAusenteException.class,
                () -> validador.postProcessEnvironment(ambiente, null));

        assertTrue(excecao.getMessage().contains("APP_DOCUMENTO_HMAC_CHAVE"));
        assertTrue(
                !excecao.getMessage().contains("SPRING_DATASOURCE_PASSWORD: senha"),
                "nao deveria reclamar de SPRING_DATASOURCE_PASSWORD, que ja foi definida");
    }

    @Test
    void variavelEmBrancoContaComoAusente() {
        MockEnvironment ambiente = new MockEnvironment();
        ambiente.setActiveProfiles("staging");
        ambiente.setProperty("SPRING_DATASOURCE_PASSWORD", "   ");
        ambiente.setProperty("APP_DOCUMENTO_HMAC_CHAVE", "chave-de-teste-longa");

        assertThrows(VariavelDeAmbienteAusenteException.class,
                () -> validador.postProcessEnvironment(ambiente, null));
    }

    @Test
    void naoRecusaSubirQuandoAsDuasVariaveisExistem() {
        MockEnvironment ambiente = new MockEnvironment();
        ambiente.setActiveProfiles("staging");
        ambiente.setProperty("SPRING_DATASOURCE_PASSWORD", "senha-de-teste");
        ambiente.setProperty("APP_DOCUMENTO_HMAC_CHAVE", "chave-de-teste-longa");

        assertDoesNotThrow(() -> validador.postProcessEnvironment(ambiente, null));
    }

    @Test
    void perfilProducaoTambemExigeAsVariaveis() {
        MockEnvironment ambiente = new MockEnvironment();
        ambiente.setActiveProfiles("producao");

        assertThrows(VariavelDeAmbienteAusenteException.class,
                () -> validador.postProcessEnvironment(ambiente, null));
    }
}
