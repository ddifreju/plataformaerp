package com.plataforma.margem;

import java.util.Objects;

/**
 * Uma resposta "nao tenho esse dado" (regra 5 do CLAUDE.md), de primeira
 * classe - nunca um numero silenciosamente errado. Ver o catalogo fechado
 * da secao 9.1 do documento fiscal: {@link CatalogoLacunas} concentra as
 * fabricas que este motor sabe produzir hoje, cada uma citando o numero
 * da linha do catalogo.
 *
 * {@code codigo} e estavel e pensado para o cliente (frontend, outro
 * servico) decidir programaticamente o que fazer - mesmo espirito do
 * campo "erro" de {@link com.plataforma.comum.web.ErroApi}.
 */
public record Lacuna(String codigo, String descricao, DirecaoViesLacuna direcaoVies) {

    public Lacuna {
        if (codigo == null || codigo.isBlank()) {
            throw new IllegalArgumentException("codigo da lacuna nao pode ser vazio");
        }
        if (descricao == null || descricao.isBlank()) {
            throw new IllegalArgumentException("descricao da lacuna nao pode ser vazia: lacuna sem explicacao "
                    + "e indistinguivel de erro nosso escondido");
        }
        Objects.requireNonNull(direcaoVies, "direcaoVies e obrigatoria: sem ela a regra do teto (secao 9.2) "
                + "nao tem como decidir entre COM_TETO e INDETERMINADA");
    }
}
