package com.plataforma.integracao;

/**
 * Regra 5 do CLAUDE.md ("nunca invente dado") virando tipo: um campo que
 * o modelo canonico tem, mas que ESTA tradução especifica nao conseguiu
 * preencher com dado real da fonte. O campo canonico correspondente fica
 * {@code NULL} (ou no valor neutro possivel, quando a coluna e
 * {@code NOT NULL}); esta entrada e o registro auditavel de que a
 * ausencia foi PERCEBIDA e DECLARADA, nunca preenchida com uma estimativa
 * silenciosa.
 *
 * Nao e exclusivo de "a fonte nunca manda isso" (ex.: Mercado Livre nao
 * expõe repasse líquido no recurso de pedido) - tambem cobre "a fonte
 * manda um valor que este adaptador nao sabe traduzir com confianca"
 * (ex.: situacao customizada do Bling sem tabela de traducao por tenant).
 * Os dois casos têm a mesma consequência prática: nada deve ser
 * apresentado como fato sem ser.
 *
 * @param campo  caminho do campo canonico afetado, no formato
 *               {@code entidade.coluna} (ex.: "pedido.valor_frete_cobrado")
 * @param motivo explicação legível, em português, do porque ficou ausente -
 *               referencia o documento de mapeamento correspondente
 *               quando aplicável
 */
public record CampoAusente(String campo, String motivo) {

    public CampoAusente {
        if (campo == null || campo.isBlank()) {
            throw new IllegalArgumentException("campo nao pode ser vazio - sem ele a entrada nao e rastreavel.");
        }
        if (motivo == null || motivo.isBlank()) {
            throw new IllegalArgumentException("motivo nao pode ser vazio - declarar ausencia sem explicar o porque nao ajuda ninguem.");
        }
    }
}
