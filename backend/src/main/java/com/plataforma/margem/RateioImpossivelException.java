package com.plataforma.margem;

/**
 * Lancada quando {@link Rateio#ratear} nao tem como dividir o total: soma
 * dos pesos igual a zero. Secao 6.3, algoritmo, passo 0: "ABORTE. Nao
 * existe rateio com peso zero. Reporte lacuna. Nao divida por zero, nao
 * distribua igualmente 'porque da na mesma' - peso zero significa que nao
 * sabemos como dividir."
 *
 * Tambem cobre a falha de asserção do passo 7 (soma das quotas diferente
 * do total) - se isso acontecer e bug do algoritmo, nao dado do mundo, e
 * "falhou? nao grave nada" (secao 6.3).
 */
public class RateioImpossivelException extends RuntimeException {

    public RateioImpossivelException(String mensagem) {
        super(mensagem);
    }
}
