"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";

import { NOME_PRODUTO } from "@/lib/marca";

const ITENS_DE_NAVEGACAO = [
  { href: "/resultado", rotulo: "Resultado" },
  { href: "/operacao", rotulo: "Operação" },
  { href: "/pendencias", rotulo: "Pendências" },
] as const;

interface PropriedadesNavegacao {
  nomeUsuario?: string;
  tenantNome?: string;
}

/**
 * Navegação principal — três rótulos, sem submenu de "papéis" (guia,
 * seção 5): a mesma pessoa entra em "Pendências" de manhã, olha
 * "Resultado" no fim do mês e usa "Operação" quando algo trava.
 */
export function Navegacao({ nomeUsuario, tenantNome }: PropriedadesNavegacao) {
  const caminhoAtual = usePathname();

  return (
    <header className="border-b border-borda bg-background">
      <div className="mx-auto flex max-w-6xl flex-wrap items-center justify-between gap-3 px-4 py-3">
        <span className="text-base text-foreground">{NOME_PRODUTO}</span>

        <nav aria-label="Navegação principal">
          <ul className="flex gap-1">
            {ITENS_DE_NAVEGACAO.map((item) => {
              const ativo = caminhoAtual?.startsWith(item.href) ?? false;
              return (
                <li key={item.href}>
                  <Link
                    href={item.href}
                    aria-current={ativo ? "page" : undefined}
                    className={`inline-block rounded px-3 py-2 text-sm font-medium ${
                      ativo ? "bg-acao text-white" : "text-texto-secundario hover:bg-superficie"
                    }`}
                  >
                    {item.rotulo}
                  </Link>
                </li>
              );
            })}
          </ul>
        </nav>

        {(nomeUsuario || tenantNome) && (
          <div className="text-sm text-texto-secundario">
            {nomeUsuario}
            {nomeUsuario && tenantNome ? " · " : ""}
            {tenantNome}
          </div>
        )}
      </div>
    </header>
  );
}
