package com.plataforma.autenticacao;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletResponse;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.beans.factory.annotation.Value;
import java.util.Arrays;
import java.util.List;

/**
 * Tarefa 17, item 2: {@code SecurityFilterChain}, BCrypt custo 12,
 * {@code UserDetailsService} proprio, login por formulario/JSON, logout.
 *
 * Este e o unico lugar da aplicacao que monta a cadeia do Spring
 * Security. {@link com.plataforma.comum.tenant.FiltroTenant} e registrado
 * SEPARADAMENTE (em {@code ConfiguracaoTenant}, com uma ordem que o
 * coloca DEPOIS desta cadeia inteira) - decisao 0023, "a ordem dos
 * filtros muda, e isso e o ponto delicado".
 */
@Configuration
@EnableWebSecurity
public class ConfiguracaoSeguranca {

    /** Origens de dev autorizadas. Vazio em producao (mesma origem). */
    @Value("${app.cors.origens-permitidas:}")
    private String origensPermitidas;

    @Bean
    public PasswordEncoder codificadorDeSenha() {
        // Custo 12 (decisao 0023). O CHECK ck_usuario_senha_hash_formato
        // (V014) recusa no banco qualquer hash com custo menor que 12 -
        // isto aqui e a primeira linha de defesa, o banco e a segunda,
        // porque o construtor padrao "new BCryptPasswordEncoder()" usa
        // custo 10 e enfraqueceria toda senha nova em silencio.
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public AuthenticationManager gerenciadorDeAutenticacao(
            UserDetailsService servicoUserDetails, PasswordEncoder codificadorDeSenha) {

        DaoAuthenticationProvider provedor = new DaoAuthenticationProvider();
        provedor.setUserDetailsService(servicoUserDetails);
        provedor.setPasswordEncoder(codificadorDeSenha);
        // true e o padrao do Spring Security, escrito aqui EXPLICITO
        // porque e load-bearing para a regra de seguranca da tarefa 17
        // ("falha de login nao distingue e-mail inexistente de senha
        // errada"): converte UsernameNotFoundException em
        // BadCredentialsException e roda um BCrypt "de mentira" quando o
        // usuario nao existe, para que nem o TIPO da excecao nem o TEMPO
        // de resposta denunciem a ausencia da conta (armadilha 5 da V014).
        provedor.setHideUserNotFoundExceptions(true);
        return new ProviderManager(provedor);
    }

    @Bean
    public SecurityFilterChain cadeiaDeSeguranca(
            HttpSecurity http,
            AuthenticationManager gerenciadorDeAutenticacao,
            ServicoLogin servicoLogin,
            ObjectMapper objectMapper) throws Exception {

        FiltroLoginJson filtroLogin = new FiltroLoginJson(gerenciadorDeAutenticacao, objectMapper);
        filtroLogin.setAuthenticationSuccessHandler(new TratadorSucessoLogin(servicoLogin));
        filtroLogin.setAuthenticationFailureHandler(new TratadorFalhaLogin(objectMapper));
        // Protecao contra fixacao de sessao: troca o ID da sessao no login
        // (decisao 0023, item 2 - "changeSessionId no login, nao
        // desligar"). Isto e o PADRAO do Spring Security quando se usa
        // .formLogin() - mas como este filtro e construido a mao (para
        // aceitar JSON, ver FiltroLoginJson), o Spring NAO liga esta
        // estrategia sozinho: sem esta linha, o filtro ficaria com
        // NullAuthenticatedSessionStrategy (um no-op) e a protecao
        // estaria silenciosamente desligada, apesar de nunca termos
        // pedido para desliga-la.
        filtroLogin.setSessionAuthenticationStrategy(new ChangeSessionIdAuthenticationStrategy());

        http
                // CSRF DESLIGADO, deliberadamente. O vetor classico de CSRF
                // e um <form> HTML de outro site submetido automaticamente
                // pelo navegador, carregando o cookie de sessao, contra um
                // endpoint que aceita corpo urlencoded/multipart "de forma
                // inocente" - nao e o caso aqui: esta API so aceita JSON
                // (ver FiltroLoginJson), e um <form> comum nao consegue
                // montar um corpo application/json. A defesa que sobra e o
                // cookie SameSite=Lax (application.yml): sob Lax, o
                // navegador NAO anexa o cookie de sessao em requisicoes de
                // origem cruzada feitas por fetch/XHR (so em navegacao de
                // topo GET) - exatamente o vetor de CSRF via JavaScript que
                // sobraria. Ligar CSRF token exigiria um endpoint so para
                // distribuir o token e o frontend gerenciar isso a mais,
                // sem ganho real de protecao neste desenho.
                // AS TRES CONDICOES QUE SUSTENTAM ISTO (decisao 0025):
                //   1. Nenhum GET pode alterar estado. Lax PERMITE o cookie
                //      em navegacao top-level GET, entao um GET que escreve
                //      seria exploravel mesmo com Lax. Isto virou regra de
                //      revisao: endpoint GET que muda estado quebra a
                //      seguranca do sistema, nao so o estilo.
                //   2. Em producao, frontend e backend na MESMA ORIGEM,
                //      atras de proxy reverso.
                //   3. Se algum dia o cookie precisar de SameSite=None,
                //      a protecao CSRF VOLTA A SER OBRIGATORIA - None
                //      desliga justamente a defesa em que isto se apoia.
                // Se qualquer uma cair, esta decisao cai junto.
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(fonteDeConfiguracaoCors()))
                .sessionManagement(sessao -> sessao.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .authorizeHttpRequests(autorizacao -> autorizacao
                        // MESMA allowlist do FiltroTenant (tarefa 17, item
                        // 5): rota de login e as duas rotas de actuator sao
                        // as UNICAS isentas de autenticacao. Tudo o mais
                        // nasce EXIGINDO sessao - allowlist, nunca denylist
                        // (decisao 0007, mesmo principio).
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info", "/api/login")
                        .permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(excecoes -> excecoes
                        .authenticationEntryPoint(new PontoEntradaNaoAutenticado(objectMapper))
                        .accessDeniedHandler(new TratadorAcessoNegado(objectMapper)))
                .logout(logout -> logout
                        .logoutUrl("/api/logout")
                        .invalidateHttpSession(true)
                        .deleteCookies("JSESSIONID")
                        .logoutSuccessHandler((requisicao, resposta, autenticacao) ->
                                resposta.setStatus(HttpServletResponse.SC_NO_CONTENT)))
                .addFilterAt(filtroLogin, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * CORS para DESENVOLVIMENTO (decisao 0025).
     *
     * Em dev o frontend roda em localhost:3000 e o backend em
     * localhost:8080 - origens diferentes, entao o navegador exige CORS
     * com credenciais para o cookie de sessao viajar.
     *
     * NOTA QUE EVITA CONFUSAO: localhost:3000 e localhost:8080 sao o
     * MESMO SITE para efeito de SameSite (o atributo olha o dominio
     * registravel, nao a porta). Ou seja, o cookie flui entre eles sob
     * Lax normalmente - o que precisa de configuracao aqui e o CORS, nao
     * o SameSite.
     *
     * ALLOWLIST EXPLICITA, NUNCA "*": com allowCredentials(true) o
     * curinga e proibido pelo proprio padrao, e aceitar qualquer origem
     * devolveria exatamente o problema que o SameSite resolve.
     *
     * Em PRODUCAO o valor esperado desta variavel e VAZIO: frontend e
     * backend ficam na mesma origem atras de proxy reverso (condicao 2
     * da decisao 0025), e ai nao existe requisicao de origem cruzada
     * para autorizar.
     */
    @Bean
    public CorsConfigurationSource fonteDeConfiguracaoCors() {
        CorsConfiguration configuracao = new CorsConfiguration();

        // Sem default permissivo: se a variavel nao existir, a lista fica
        // vazia e nenhuma origem cruzada e aceita. Falha fechada.
        if (!origensPermitidas.isBlank()) {
            configuracao.setAllowedOrigins(Arrays.stream(origensPermitidas.split(","))
                    .map(String::trim)
                    .filter(origem -> !origem.isEmpty())
                    .toList());
        }
        configuracao.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuracao.setAllowedHeaders(List.of("Content-Type", "Accept"));
        // Sem isto o navegador descarta o cookie de sessao na resposta.
        configuracao.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource fonte = new UrlBasedCorsConfigurationSource();
        fonte.registerCorsConfiguration("/api/**", configuracao);
        return fonte;
    }
}
