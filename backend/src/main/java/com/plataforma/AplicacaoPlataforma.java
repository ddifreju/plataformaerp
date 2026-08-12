package com.plataforma;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Ponto de entrada da aplicacao.
 *
 * Pacotes organizados por DOMINIO (com.plataforma.comum.tenant,
 * com.plataforma.auditoria, ...), nunca por camada (nao existe um
 * pacote "controllers" ou "services" cruzando dominios). O component
 * scan padrao do Spring Boot cobre tudo abaixo de com.plataforma, entao
 * nenhuma configuracao adicional de scan e necessaria aqui.
 */
@SpringBootApplication
public class AplicacaoPlataforma {

    public static void main(String[] args) {
        SpringApplication.run(AplicacaoPlataforma.class, args);
    }
}
