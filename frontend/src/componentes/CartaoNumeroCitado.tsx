interface PropriedadesCartaoNumeroCitado {
  nome: string;
  valor: string;
}

/**
 * Um `NumeroCitado` da resposta de pergunta (decisão 0030) — diferente dos
 * quatro números do motor de margem (`CartaoNumero`/`ValorMonetario`, que
 * recebem um DECIMAL cru e formatam aqui), `NumeroCitado.valor` já chega
 * PRONTO de `FormatadorDeTexto` no backend ("R$ 44,96", "22,49%", ou uma
 * contagem simples como "12" — ver o Javadoc de `NumeroCitado.java` e de
 * `tipos.ts`). Passar este valor por `ValorMonetario`/`formatarDinheiro`
 * quebraria (o texto não é mais um decimal puro, o parser de
 * `lib/dinheiro.ts` rejeitaria) — os dois caminhos proibidos pela decisão
 * 0026. Este cartão só exibe o texto já pronto, com a mesma linguagem
 * visual (fonte tabular, peso médio) do `CartaoNumero`.
 */
export function CartaoNumeroCitado({ nome, valor }: PropriedadesCartaoNumeroCitado) {
  return (
    <div className="rounded border border-borda bg-background p-4">
      <h3 className="text-sm font-medium text-texto-secundario">{nome}</h3>
      <p className="mt-1 text-2xl font-medium tabular-nums text-foreground">{valor}</p>
    </div>
  );
}
