package com.plataforma.canal;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Teste unitario dos metodos de intencao de escopo declarado (tarefa 31,
 * decisao 0033) - as QUATRO colunas mudam JUNTAS, nunca uma por vez (a
 * regra que os CHECK da migration V016 exigem, ver o cabecalho dela,
 * secao 5).
 */
class CanalTest {

    @Test
    void canalNovoNasceNaoDeclarado() {
        Canal canal = novoCanal();

        assertEquals(EscopoCanal.NAO_DECLARADO, canal.getEscopoDeclarado());
        assertNull(canal.getEspelhaCanalId());
        assertNull(canal.getEscopoDeclaradoEm());
        assertNull(canal.getEscopoDeclaradoPor());
    }

    @Test
    void declararFontePrimariaPreencheAsQuatroColunasCoerentemente() {
        Canal canal = novoCanal();
        UUID usuarioId = UUID.randomUUID();
        OffsetDateTime atualizadoEmAntes = canal.getAtualizadoEm();

        canal.declararFontePrimaria(usuarioId);

        assertEquals(EscopoCanal.FONTE_PRIMARIA, canal.getEscopoDeclarado());
        assertNull(canal.getEspelhaCanalId(), "FONTE_PRIMARIA nunca tem espelhaCanalId (ck_canal_espelho_exige_alvo)");
        assertNotNull(canal.getEscopoDeclaradoEm());
        assertEquals(usuarioId, canal.getEscopoDeclaradoPor());
        assertTrue(canal.getAtualizadoEm().isAfter(atualizadoEmAntes)
                        || canal.getAtualizadoEm().isEqual(atualizadoEmAntes),
                "atualizadoEm precisa ser carimbado de novo");
    }

    @Test
    void declararFontePrimariaAceitaUsuarioNuloParaDeclaracaoForaDaAplicacao() {
        Canal canal = novoCanal();

        canal.declararFontePrimaria(null);

        assertEquals(EscopoCanal.FONTE_PRIMARIA, canal.getEscopoDeclarado());
        assertNull(canal.getEscopoDeclaradoPor(), "seed/provisionamento declara sem usuario logado - NULL e estado real");
        assertNotNull(canal.getEscopoDeclaradoEm(), "escopoDeclaradoEm e preenchido mesmo sem usuario");
    }

    @Test
    void declararEspelhoDePreencheAsQuatroColunasCoerentemente() {
        Canal canal = novoCanal();
        UUID canalAlvoId = UUID.randomUUID();
        UUID usuarioId = UUID.randomUUID();

        canal.declararEspelhoDe(canalAlvoId, usuarioId);

        assertEquals(EscopoCanal.ESPELHO, canal.getEscopoDeclarado());
        assertEquals(canalAlvoId, canal.getEspelhaCanalId());
        assertNotNull(canal.getEscopoDeclaradoEm());
        assertEquals(usuarioId, canal.getEscopoDeclaradoPor());
    }

    @Test
    void declararEspelhoDeSemAlvoLancaExcecao() {
        Canal canal = novoCanal();

        assertThrows(IllegalArgumentException.class, () -> canal.declararEspelhoDe(null, UUID.randomUUID()));
    }

    @Test
    void declararEspelhoDeSiMesmoLancaExcecao() {
        Canal canal = novoCanal();

        assertThrows(IllegalArgumentException.class, () -> canal.declararEspelhoDe(canal.getId(), UUID.randomUUID()));
    }

    @Test
    void limparDeclaracaoVoltaAsQuatroColunasParaOEstadoNaoDeclarado() {
        Canal canal = novoCanal();
        canal.declararEspelhoDe(UUID.randomUUID(), UUID.randomUUID());

        canal.limparDeclaracao();

        assertEquals(EscopoCanal.NAO_DECLARADO, canal.getEscopoDeclarado());
        assertNull(canal.getEspelhaCanalId());
        assertNull(canal.getEscopoDeclaradoEm());
        assertNull(canal.getEscopoDeclaradoPor());
    }

    private static Canal novoCanal() {
        return new Canal("canal-teste-" + UUID.randomUUID().toString().substring(0, 8),
                "Canal de teste", TipoCanal.MERCADO_LIVRE, CategoriaCanal.MARKETPLACE, null, null, null);
    }
}
