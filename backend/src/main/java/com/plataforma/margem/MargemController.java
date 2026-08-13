package com.plataforma.margem;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Tarefa 16: {@code GET /api/margem/periodo}. Sem logica de negocio aqui
 * (CLAUDE.md) - recebe os parametros, delega para
 * {@link ServicoMargemPeriodo} e devolve o resultado.
 *
 * O TENANT NUNCA vem de parametro da requisicao: os repositorios usados
 * por {@link ServicoMargemPeriodo} filtram por tenant sozinhos, via
 * {@code @TenantId} (decisao 0007), lido de {@link com.plataforma.comum.tenant.ContextoTenant}
 * pelo {@link com.plataforma.comum.tenant.FiltroTenant} no inicio da
 * requisicao - nao existe (e nao deveria existir) um {@code @RequestParam
 * tenantId} nesta assinatura.
 *
 * {@code canalId} e OBRIGATORIO de proposito - ver o Javadoc de
 * {@link ServicoMargemPeriodo} sobre a decisao 0017.
 */
@RestController
public class MargemController {

    private final ServicoMargemPeriodo servicoMargemPeriodo;

    public MargemController(ServicoMargemPeriodo servicoMargemPeriodo) {
        this.servicoMargemPeriodo = servicoMargemPeriodo;
    }

    @GetMapping("/api/margem/periodo")
    public ResultadoMargemPeriodo periodo(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime inicio,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime fim,
            @RequestParam UUID canalId) {
        return servicoMargemPeriodo.calcular(inicio, fim, canalId);
    }
}
