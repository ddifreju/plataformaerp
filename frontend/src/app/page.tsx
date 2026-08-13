import { redirect } from "next/navigation";

/**
 * A raiz não é uma tela própria — "Resultado" é a pergunta mais comum
 * (guia, seção 5: "olha 'Resultado' no fim do mês"). Quem não tiver
 * sessão válida é redirecionado para `/login` pelo próprio cliente de
 * API na primeira chamada feita pelo layout do grupo `(painel)`.
 */
export default function PaginaRaiz() {
  redirect("/resultado");
}
