package com.plataforma.comum.tenant;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Teste unitario puro (sem Spring, sem banco, sem Testcontainers) do
 * {@link ContextoTenant} — a ThreadLocal que implementa a camada 2 da
 * decisao 0007.
 *
 * Estes dois comportamentos sustentam todo o resto do isolamento:
 * <ul>
 *   <li>se {@link ContextoTenant#atual()} nao lancasse quando nao ha
 *       tenant definido, um bug de contexto (por exemplo, codigo que
 *       roda fora do FiltroTenant) operaria silenciosamente sem
 *       tenant — e a proxima camada (DataSourceComTenant) trataria isso
 *       como "sem tenant", que e o comportamento fail-closed correto,
 *       mas o BUG em si passaria despercebido;</li>
 *   <li>se {@link ContextoTenant#limpar()} nao removesse de verdade o
 *       valor, o tenant de uma requisicao vazaria para a proxima
 *       atendida pela mesma thread — servidores de aplicacao reutilizam
 *       threads entre requisicoes, entao isto seria um vazamento real de
 *       tenant entre dois clientes diferentes, nao um detalhe de
 *       implementacao.</li>
 * </ul>
 */
class ContextoTenantTest {

    /**
     * Limpeza defensiva: ContextoTenant e uma ThreadLocal ESTATICA,
     * compartilhada por toda a JVM (por thread). Quando o executor de
     * testes e sequencial (o padrao), classes diferentes desta suite
     * podem rodar na mesma thread. Sem este @AfterEach, um teste que
     * falhasse antes de chamar limpar() deixaria o tenant setado para o
     * PROXIMO teste executado nesta thread.
     */
    @AfterEach
    void limparContextoAposCadaTeste() {
        ContextoTenant.limpar();
    }

    @Test
    void atualLancaQuandoNenhumTenantFoiDefinido() {
        assertThrows(IllegalStateException.class, ContextoTenant::atual);
    }

    @Test
    void atualDevolveOTenantDepoisDeDefinir() {
        UUID tenantId = UUID.randomUUID();

        ContextoTenant.definir(tenantId);

        assertEquals(tenantId, ContextoTenant.atual());
    }

    @Test
    void definirComNuloLancaImediatamente() {
        assertThrows(IllegalArgumentException.class, () -> ContextoTenant.definir(null));
    }

    @Test
    void limparDeFatoRemoveOTenantDoContexto() {
        UUID tenantId = UUID.randomUUID();
        ContextoTenant.definir(tenantId);
        // Prova de que havia algo para vazar antes de provar que nao
        // vaza: sem esta linha, o teste passaria mesmo se definir()
        // nunca tivesse funcionado.
        assertEquals(tenantId, ContextoTenant.atual());

        ContextoTenant.limpar();

        // Depois de limpar, atual() precisa voltar a lancar - nunca
        // devolver o valor antigo, nunca devolver null silenciosamente.
        assertThrows(IllegalStateException.class, ContextoTenant::atual);
        assertTrue(ContextoTenant.atualOuVazio().isEmpty(),
                "limpar() nao removeu o valor da ThreadLocal - o tenant anterior vazaria "
                        + "para a proxima requisicao atendida por esta thread");
    }

    @Test
    void atualOuVazioNaoLancaEDevolveVazioSemTenant() {
        assertTrue(ContextoTenant.atualOuVazio().isEmpty());
    }

    @Test
    void atualOuVazioDevolvePresenteDepoisDeDefinir() {
        UUID tenantId = UUID.randomUUID();
        ContextoTenant.definir(tenantId);

        Optional<UUID> resultado = ContextoTenant.atualOuVazio();

        assertTrue(resultado.isPresent());
        assertEquals(tenantId, resultado.get());
    }
}
