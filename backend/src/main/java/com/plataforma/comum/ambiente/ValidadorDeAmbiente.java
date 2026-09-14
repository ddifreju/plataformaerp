package com.plataforma.comum.ambiente;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;

/**
 * Tarefa 30 (Fase 4, bloco B): falha cedo, com mensagem acionavel, quando
 * falta variavel de ambiente obrigatoria FORA do perfil {@code dev}.
 *
 * PORQUE UM {@link EnvironmentPostProcessor} E NAO UM {@code @PostConstruct}
 * NUM {@code @Component}: um {@code EnvironmentPostProcessor} roda ANTES do
 * contexto Spring ser criado - a aplicacao nunca chega a instanciar um
 * unico bean (nem tenta abrir conexao com o banco) quando falta uma
 * variavel. Isto tambem e o que torna o teste PURO: {@link #postProcessEnvironment}
 * e um metodo comum, chamavel direto com um {@code ConfigurableEnvironment}
 * de teste, sem subir Spring nem Docker (ver {@code ValidadorDeAmbienteTest}).
 *
 * Registrado em {@code META-INF/spring.factories} (mecanismo classico do
 * Spring Boot para este tipo de extensao - nao e o mesmo arquivo usado
 * para autoconfiguracao de bean).
 *
 * O QUE CONTA COMO "PERFIL DEV": nenhum perfil ativo (o "default" do
 * Spring, que e o que a aplicacao usa hoje quando ninguem define
 * SPRING_PROFILES_ACTIVE - ver application.yml, onde todo default de
 * variavel e default de desenvolvimento) OU o perfil {@code dev}
 * explicito. Qualquer outro perfil ativo (por exemplo {@code staging})
 * exige as variaveis abaixo.
 */
public class ValidadorDeAmbiente implements EnvironmentPostProcessor, Ordered {

    private static final String PERFIL_DEV = "dev";

    /**
     * Nome da variavel de ambiente -> explicacao mostrada quando ela
     * falta. LinkedHashMap para a mensagem de erro sair sempre na mesma
     * ordem, o que importa para o teste e para quem le o log.
     */
    private static final Map<String, String> VARIAVEIS_OBRIGATORIAS = new LinkedHashMap<>();

    static {
        VARIAVEIS_OBRIGATORIAS.put(
                "SPRING_DATASOURCE_PASSWORD",
                "senha do usuario 'app_aplicacao' no Postgres (spring.datasource.password). "
                        + "Sem ela a aplicacao nao consegue abrir nenhuma conexao com o banco.");
        VARIAVEIS_OBRIGATORIAS.put(
                "APP_DOCUMENTO_HMAC_CHAVE",
                "chave HMAC-SHA256 usada para gerar 'cliente.documento_hash' a partir do CPF/CNPJ "
                        + "(ver docs/PENDENCIAS.md). Gere uma chave aleatoria longa e guarde fora do "
                        + "git - trocar a chave depois invalida todos os hashes ja gravados.");
    }

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (emPerfilDeDesenvolvimento(environment)) {
            return;
        }

        List<String> faltando = VARIAVEIS_OBRIGATORIAS.keySet().stream()
                .filter(nome -> valorAusente(environment.getProperty(nome)))
                .toList();

        if (faltando.isEmpty()) {
            return;
        }

        throw new VariavelDeAmbienteAusenteException(mensagemDeErro(environment, faltando));
    }

    private boolean valorAusente(String valor) {
        return valor == null || valor.isBlank();
    }

    private boolean emPerfilDeDesenvolvimento(ConfigurableEnvironment environment) {
        String[] perfisAtivos = environment.getActiveProfiles();
        if (perfisAtivos.length == 0) {
            return true;
        }
        return List.of(perfisAtivos).contains(PERFIL_DEV);
    }

    private String mensagemDeErro(ConfigurableEnvironment environment, List<String> faltando) {
        StringBuilder mensagem = new StringBuilder("Nao e possivel subir no(s) perfil(is) ")
                .append(List.of(environment.getActiveProfiles()))
                .append(" sem as seguintes variaveis de ambiente:\n");

        for (String nome : faltando) {
            mensagem.append(" - ").append(nome).append(": ").append(VARIAVEIS_OBRIGATORIAS.get(nome)).append('\n');
        }

        mensagem.append("Defina-as no ambiente (nunca em arquivo versionado) antes de subir a aplicacao. ")
                .append("Em desenvolvimento local, rode sem SPRING_PROFILES_ACTIVE (ou com 'dev') "
                        + "para usar os defaults de application.yml.");

        return mensagem.toString();
    }

    @Override
    public int getOrder() {
        // Roda por ULTIMO entre os EnvironmentPostProcessor: precisa que
        // application.yml/application-staging.yml ja tenham sido lidos,
        // para que ACTIVE_PROFILES esteja resolvido corretamente.
        return Ordered.LOWEST_PRECEDENCE;
    }
}
