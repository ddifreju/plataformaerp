"use client";

import { useEffect, useState } from "react";

import { Navegacao } from "@/componentes/Navegacao";
import { buscarSessao } from "@/lib/api/cliente";
import type { RespostaSessao } from "@/lib/api/tipos";

/**
 * Layout compartilhado das três telas autenticadas (Resultado, Operação,
 * Pendências). Busca a sessão uma vez, no cliente — o cookie de sessão
 * vai junto automaticamente (`credentials: 'include'` em `lib/api/cliente.ts`).
 * Erro 401 já redireciona para `/login` dentro do próprio cliente de API;
 * aqui só cobrimos o estado de carregamento, honestamente.
 */
export default function LayoutPainel({ children }: { children: React.ReactNode }) {
  const [sessao, setSessao] = useState<RespostaSessao | null>(null);
  const [carregandoSessao, setCarregandoSessao] = useState(true);

  useEffect(() => {
    let cancelado = false;
    buscarSessao()
      .then((resposta) => {
        if (!cancelado) setSessao(resposta);
      })
      .catch(() => {
        // 401 já disparou o redirecionamento para /login dentro do
        // cliente de API. Qualquer outro erro (rede, 500) deixa a tela
        // sem dado de sessão — as páginas abaixo lidam com suas próprias
        // chamadas e mostram seus próprios erros.
      })
      .finally(() => {
        if (!cancelado) setCarregandoSessao(false);
      });
    return () => {
      cancelado = true;
    };
  }, []);

  return (
    <div className="flex min-h-full flex-1 flex-col">
      <Navegacao nomeUsuario={sessao?.nome} tenantNome={sessao?.tenantNome} />
      <main className="mx-auto w-full max-w-6xl flex-1 px-4 py-6">
        {carregandoSessao ? (
          <p className="text-sm text-texto-secundario">Consultando sua sessão…</p>
        ) : (
          children
        )}
      </main>
    </div>
  );
}
