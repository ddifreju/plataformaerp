/**
 * Lista de lacunas como TEXTO simples — usada na resposta de pergunta
 * (`RespostaPergunta.lacunas`, tipo `string[]`: só a descrição já pronta,
 * via `Lacuna::descricao` no backend, sem `codigo` nem `direcaoVies`).
 * Diferente de `ListaLacunas` (que espera o `Lacuna` completo do motor de
 * margem, com `codigo`, para casar contra `acaoParaLacuna` e mostrar a
 * ação específica): aqui não fabricamos um `codigo` fictício para forçar
 * esse casamento — regra 5 do `CLAUDE.md`, "nunca invente dado". Mesma
 * linguagem visual (borda esquerda cinza-pedra, nunca vermelha, guia seção
 * 2.2), sem repetir a lógica de ação que exige um dado que não temos aqui.
 */
export function ListaLacunasTexto({ lacunas }: { lacunas: string[] }) {
  if (lacunas.length === 0) {
    return null;
  }

  return (
    <ul className="mt-2 flex flex-col gap-2">
      {lacunas.map((descricao) => (
        <li key={descricao} className="border-l-2 border-dado-ausente bg-dado-ausente-bg p-3 text-sm text-foreground">
          {descricao}
        </li>
      ))}
    </ul>
  );
}
