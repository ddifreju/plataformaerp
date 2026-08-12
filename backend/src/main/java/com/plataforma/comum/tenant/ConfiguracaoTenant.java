package com.plataforma.comum.tenant;

import javax.sql.DataSource;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariDataSource;

import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.Ordered;

/**
 * Fiacao das quatro camadas de isolamento de tenant descritas na
 * decisao 0007. Cada bean abaixo corresponde a uma camada; o comentario
 * de cada uma explica o porque, os detalhes moram nas proprias classes
 * (FiltroTenant, DataSourceComTenant, ResolvedorTenantHibernate).
 */
@Configuration
public class ConfiguracaoTenant {

    // ------------------------------------------------------------------
    // Camada 1 — filtro que resolve o tenant da requisicao
    // ------------------------------------------------------------------

    /**
     * FiltroTenant nao e @Component de proposito (ver o comentario na
     * propria classe): registramos aqui, explicitamente, com a ordem
     * mais alta possivel, para garantir que ele roda antes de qualquer
     * outro filtro da aplicacao.
     */
    @Bean
    public FilterRegistrationBean<FiltroTenant> registroFiltroTenant(
            RepositorioTenant repositorioTenant, ObjectMapper objectMapper) {

        FilterRegistrationBean<FiltroTenant> registro = new FilterRegistrationBean<>();
        registro.setFilter(new FiltroTenant(repositorioTenant, objectMapper));
        registro.setOrder(Ordered.HIGHEST_PRECEDENCE);
        registro.addUrlPatterns("/*");
        return registro;
    }

    // ------------------------------------------------------------------
    // Camada 4 — DataSource que seta o GUC app.tenant_id em toda conexao
    // ------------------------------------------------------------------

    /**
     * O pool de conexoes de verdade (Hikari), construido a mao a partir
     * das propriedades spring.datasource.* (inclusive
     * spring.datasource.hikari.*, se algum dia forem usadas).
     *
     * Construimos manualmente aqui, em vez de deixar a auto-configuracao
     * do Spring Boot criar o bean "dataSource" padrao, porque e a unica
     * forma de garantir que ELE fica escondido atras do
     * DataSourceComTenant: assim que declaramos qualquer bean do tipo
     * DataSource neste contexto, a auto-configuracao do Spring Boot
     * detecta que ja existe um DataSource e desiste de criar o dela
     * (@ConditionalOnMissingBean(DataSource.class)) - entao a
     * responsabilidade de montar o Hikari passa a ser nossa.
     *
     * autowireCandidate = false NAO e detalhe: a decisao 0010 escolheu
     * SET de sessao em vez de SET LOCAL apoiada numa unica premissa - a
     * de que NINGUEM consegue uma conexao sem passar pelo decorador. Sem
     * isto, qualquer codigo futuro poderia injetar o HikariDataSource
     * pelo tipo concreto e obter conexoes SEM o GUC de tenant setado -
     * conexoes reaproveitadas do pool, possivelmente carregando o tenant
     * de um request anterior, com o RLS ativo e "funcionando". Falha
     * silenciosa e plausivel, a pior categoria. Com autowireCandidate =
     * false, o pool "nu" deixa de ser alcancavel por injecao e a premissa
     * passa a ser garantida pelo container, nao por disciplina humana.
     */
    @Bean(autowireCandidate = false)
    @ConfigurationProperties(prefix = "spring.datasource.hikari")
    public HikariDataSource poolDeConexoes(DataSourceProperties propriedadesDoDatasource) {
        return propriedadesDoDatasource.initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();
    }

    /**
     * O DataSource que a aplicacao (JPA, JDBC) efetivamente enxerga:
     * o pool Hikari envolvido pelo decorador que seta o GUC de tenant
     * em toda conexao entregue (ver DataSourceComTenant). @Primary
     * porque, com dois beans do tipo DataSource no contexto
     * (poolDeConexoes e este), qualquer injecao por tipo precisa saber
     * qual usar - e tem que ser sempre este, nunca o pool "nu".
     */
    @Bean
    @Primary
    public DataSource dataSource(DataSourceProperties propriedadesDoDatasource) {
        // Chamada direta a outro @Bean da MESMA classe @Configuration: o
        // Spring intercepta por CGLIB e devolve o singleton ja gerenciado
        // (com @ConfigurationProperties aplicado e close() no shutdown),
        // em vez de construir um segundo pool. E o que permite manter o
        // pool como bean gerenciado sendo, ao mesmo tempo,
        // autowireCandidate = false: ninguem alcanca o pool "nu" por
        // injecao, mas o ciclo de vida dele continua correto.
        return new DataSourceComTenant(poolDeConexoes(propriedadesDoDatasource));
    }

    // ------------------------------------------------------------------
    // Camada 3 — resolvedor de tenant do Hibernate
    // ------------------------------------------------------------------

    /**
     * Registra o ResolvedorTenantHibernate junto do Hibernate, para que
     * toda entidade anotada com @org.hibernate.annotations.TenantId
     * (hoje, ConsultaAuditada) tenha o predicado de tenant acrescentado
     * automaticamente em toda consulta e preenchido automaticamente em
     * todo insert.
     *
     * Usamos a string literal do nome da propriedade
     * ("hibernate.tenant_identifier_resolver") em vez de referenciar a
     * constante da classe de settings do Hibernate: esse valor e estavel
     * desde o Hibernate 5 e evita depender de qual classe hospeda a
     * constante nesta versao especifica do Hibernate trazida pelo Spring
     * Boot.
     */
    @Bean
    public HibernatePropertiesCustomizer personalizadorHibernateParaTenant(
            ResolvedorTenantHibernate resolvedorTenantHibernate) {
        return propriedades -> propriedades.put(
                "hibernate.tenant_identifier_resolver", resolvedorTenantHibernate);
    }
}
