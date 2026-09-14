package com.plataforma.pergunta;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link CatalogoDePerguntas#resolver(String)} é a trava de "código de
 * intenção que não existe no catálogo é rejeitado, não interpretado com
 * boa vontade" (decisão 0030). Este teste prova que ela é ESTRITA: nem
 * código inexistente, nem código que só difere em caixa/espaço, é
 * aceito silenciosamente.
 */
class CatalogoDePerguntasTest {

    private final CatalogoDePerguntas catalogo = new CatalogoDePerguntas();

    @Test
    void resolveTodosOsCincoCodigosDoEnum() {
        for (CodigoIntencao codigo : CodigoIntencao.values()) {
            Optional<CodigoIntencao> resolvido = catalogo.resolver(codigo.name());
            assertTrue(resolvido.isPresent(), "deveria resolver " + codigo.name());
            assertEquals(codigo, resolvido.get());
        }
    }

    @Test
    void rejeitaCodigoInexistente() {
        assertFalse(catalogo.resolver("PREVISAO_DE_VENDAS_FUTURAS").isPresent());
    }

    @Test
    void rejeitaCodigoComCaixaDiferente() {
        assertFalse(catalogo.resolver("margem_do_periodo").isPresent(),
                "resolver nao deveria normalizar caixa - codigo com boa vontade e proibido pela decisao 0030");
    }

    @Test
    void rejeitaCodigoComEspacoEstranho() {
        assertFalse(catalogo.resolver(" MARGEM_DO_PERIODO ").isPresent(),
                "resolver nao deveria fazer trim - espaco estranho e tratado como codigo diferente");
    }

    @Test
    void rejeitaCodigoNulo() {
        assertFalse(catalogo.resolver(null).isPresent());
    }

    @Test
    void todaDescricaoTemPeloMenosUmExemploDePergunta() {
        for (DescricaoIntencao descricao : catalogo.descricoes()) {
            assertFalse(descricao.exemplosDePergunta().isEmpty(),
                    descricao.codigo() + " precisa de exemplo - alimenta ModeloHeuristico e perguntasQueSeiResponder");
        }
    }
}
