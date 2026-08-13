package com.plataforma.canal;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Lista os canais do tenant, para a interface montar o seletor.
 *
 * PORQUE ESTE ENDPOINT EXISTE: a decisao 0021 obriga a tela de Resultado
 * a informar UM canal explicito - nunca "todos", porque somar canais
 * sobrepostos contaria a mesma venda duas vezes. Sem uma lista, a unica
 * saida do frontend era pedir o UUID do canal digitado a mao, o que
 * ninguem faz numa ferramenta de trabalho.
 *
 * NAO ha tenantId na assinatura: as consultas filtram por tenant sozinhas
 * via @TenantId (decisao 0007, camada 3), a partir do ContextoTenant que
 * o FiltroTenant preencheu com o tenant do usuario AUTENTICADO. Aceitar
 * tenant por parametro aqui seria reabrir o buraco que a decisao 0023
 * fechou.
 *
 * Sem logica de negocio no controller (CLAUDE.md): so lista e mapeia.
 * E somente leitura - a condicao 1 da decisao 0025 (nenhum GET altera
 * estado) e o que sustenta o CSRF desligado.
 */
@RestController
@RequestMapping("/api/canais")
public class CanalController {

    private final RepositorioCanal repositorioCanal;

    public CanalController(RepositorioCanal repositorioCanal) {
        this.repositorioCanal = repositorioCanal;
    }

    @GetMapping
    public List<RespostaCanal> listar() {
        return repositorioCanal.findAllByOrderByNomeAsc()
                .stream()
                .map(RespostaCanal::de)
                .toList();
    }
}
