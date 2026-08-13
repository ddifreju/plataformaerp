"use client";

import { useEffect, useState } from "react";

import { buscarPainelGestor, ErroApiCliente } from "@/lib/api/cliente";
import type { RespostaGargalosProcesso } from "@/lib/api/tipos";
import { ROTULO_STATUS_DEVOLUCAO, ROTULO_STATUS_PEDIDO } from "@/lib/rotulos";

/**
 * Tela "Operação" (tarefa 19) — gargalo do PROCESSO, nunca de pessoa
 * (decisão 0003). `RespostaGargalosProcesso` não tem, de propósito,
 * nenhum campo "quem processou" — não inventamos um aqui.
 */
export default function PaginaOperacao() {
  const [dados, setDados] = useState<RespostaGargalosProcesso | null>(null);
  const [carregando, setCarregando] = useState(true);
  const [erro, setErro] = useState<string | null>(null);

  useEffect(() => {
    // Sem reset de "carregando"/"erro" aqui: o estado inicial já cobre a
    // única execução deste efeito (sem dependências, roda uma vez por
    // montagem) — chamar setState de novo, síncrono, no corpo do efeito
    // só geraria uma renderização em cascata desnecessária.
    let cancelado = false;
    buscarPainelGestor()
      .then((resposta) => {
        if (!cancelado) setDados(resposta);
      })
      .catch((erroCapturado) => {
        if (cancelado) return;
        setErro(
          erroCapturado instanceof ErroApiCliente
            ? erroCapturado.message
            : "Não consegui consultar os gargalos do processo agora.",
        );
      })
      .finally(() => {
        if (!cancelado) setCarregando(false);
      });
    return () => {
      cancelado = true;
    };
  }, []);

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-xl font-medium text-foreground">Operação</h1>
        <p className="mt-1 text-base text-texto-secundario">Onde a operação está travando.</p>
      </div>

      {carregando && (
        <p className="text-sm text-texto-secundario" role="status">
          Consultando gargalos do processo…
        </p>
      )}

      {erro && (
        <p role="alert" className="rounded border border-valor-negativo bg-valor-negativo-bg p-4 text-sm text-valor-negativo">
          {erro}
        </p>
      )}

      {dados && <PainelGargalos dados={dados} />}
    </div>
  );
}

function PainelGargalos({ dados }: { dados: RespostaGargalosProcesso }) {
  return (
    <div className="flex flex-col gap-6">
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
        <div className="rounded border border-borda bg-background p-4">
          <h2 className="text-sm font-medium text-texto-secundario">Eventos de ingestão com erro</h2>
          <p className="mt-1 text-2xl font-medium tabular-nums text-foreground">{dados.eventosIngestaoComErro}</p>
          <p className="mt-1 text-sm text-texto-secundario">
            Falharam ao entrar no sistema — cada um vira um item na fila de Pendências.
          </p>
        </div>
        <div className="rounded border border-borda bg-background p-4">
          <h2 className="text-sm font-medium text-texto-secundario">Pedidos sem custo de mercadoria</h2>
          <p className="mt-1 text-2xl font-medium tabular-nums text-foreground">{dados.pedidosSemCustoMercadoria}</p>
          <p className="mt-1 text-sm text-texto-secundario">
            Sem essa linha de custo, a margem de cada um sai com o rótulo &quot;Com teto&quot; ou pior.
          </p>
        </div>
      </div>

      <section aria-labelledby="titulo-pedidos-por-status">
        <h2 id="titulo-pedidos-por-status" className="text-base font-medium text-foreground">
          Pedidos por status
        </h2>
        <TabelaContagem
          linhas={dados.pedidosPorStatus}
          rotulos={ROTULO_STATUS_PEDIDO}
          rotuloColunaCategoria="Status do pedido"
        />
      </section>

      <section aria-labelledby="titulo-devolucoes-por-status">
        <h2 id="titulo-devolucoes-por-status" className="text-base font-medium text-foreground">
          Devoluções por status
        </h2>
        <TabelaContagem
          linhas={dados.devolucoesPorStatus}
          rotulos={ROTULO_STATUS_DEVOLUCAO}
          rotuloColunaCategoria="Status da devolução"
        />
      </section>
    </div>
  );
}

function TabelaContagem<TStatus extends string>({
  linhas,
  rotulos,
  rotuloColunaCategoria,
}: {
  linhas: { status: TStatus; quantidade: number }[];
  rotulos: Record<TStatus, string>;
  rotuloColunaCategoria: string;
}) {
  if (linhas.length === 0) {
    return <p className="mt-2 text-sm text-texto-secundario">Nenhum registro neste status hoje.</p>;
  }

  return (
    <div className="mt-2 overflow-x-auto rounded border border-borda bg-background">
      <table className="w-full text-sm">
        <thead>
          <tr className="border-b border-borda text-left text-texto-secundario">
            <th scope="col" className="px-3 py-2 font-medium">
              {rotuloColunaCategoria}
            </th>
            <th scope="col" className="px-3 py-2 text-right font-medium">
              Quantidade
            </th>
          </tr>
        </thead>
        <tbody>
          {linhas.map((linha) => (
            <tr key={linha.status} className="border-b border-borda last:border-0">
              <th scope="row" className="px-3 py-2 text-left font-normal text-foreground">
                {rotulos[linha.status]}
              </th>
              <td className="px-3 py-2 text-right tabular-nums text-foreground">{linha.quantidade}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
