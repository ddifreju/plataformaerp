"use client";

import { useEffect, useState } from "react";

import { buscarPainelAnalista, ErroApiCliente } from "@/lib/api/cliente";
import type {
  ItemDevolucaoAberta,
  ItemEventoComErro,
  ItemItemSemVariacao,
  ItemVariacaoSemCusto,
  RespostaFilaPendencias,
} from "@/lib/api/tipos";

function formatarData(iso: string): string {
  return new Date(iso).toLocaleString("pt-BR", { dateStyle: "short", timeStyle: "short" });
}

/**
 * Tela "Pendências" (tarefa 20) — fila do que precisa de decisão. Cada
 * item já chega do backend com `acao`: o texto do que fazer, pronto (os
 * `Item*.de(...)` do backend). Esta tela não distingue "papel" de quem
 * olha, e não deve ser confundida com `docs/PENDENCIAS.md` (lista interna
 * de decisões da fundadora) — aqui é sempre lacuna de DADO.
 *
 * Sem paginação nesta versão (mesma limitação do backend, Javadoc de
 * `RespostaFilaPendencias`): cada lista já vem cortada nos 100 itens
 * mais recentes.
 */
export default function PaginaPendencias() {
  const [dados, setDados] = useState<RespostaFilaPendencias | null>(null);
  const [carregando, setCarregando] = useState(true);
  const [erro, setErro] = useState<string | null>(null);

  useEffect(() => {
    // Sem reset de "carregando"/"erro" aqui pelo mesmo motivo do painel
    // de Operação: o estado inicial já cobre a única execução deste
    // efeito.
    let cancelado = false;
    buscarPainelAnalista()
      .then((resposta) => {
        if (!cancelado) setDados(resposta);
      })
      .catch((erroCapturado) => {
        if (cancelado) return;
        setErro(
          erroCapturado instanceof ErroApiCliente
            ? erroCapturado.message
            : "Não consegui consultar a fila de pendências agora.",
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
        <h1 className="text-xl font-medium text-foreground">Pendências</h1>
        <p className="mt-1 text-base text-texto-secundario">
          O que resolver para os números de margem ficarem mais confiáveis.
        </p>
      </div>

      {carregando && (
        <p className="text-sm text-texto-secundario" role="status">
          Consultando a fila de pendências…
        </p>
      )}

      {erro && (
        <p role="alert" className="rounded border border-valor-negativo bg-valor-negativo-bg p-4 text-sm text-valor-negativo">
          {erro}
        </p>
      )}

      {dados && (
        <div className="flex flex-col gap-8">
          <SecaoEventosComErro itens={dados.eventosComErro} />
          <SecaoItensSemVariacao itens={dados.itensSemVariacao} />
          <SecaoVariacoesSemCusto itens={dados.variacoesSemCusto} />
          <SecaoDevolucoesAbertas itens={dados.devolucoesAbertas} />
        </div>
      )}
    </div>
  );
}

function CabecalhoSecao({ titulo, quantidade }: { titulo: string; quantidade: number }) {
  return (
    <h2 className="text-base font-medium text-foreground">
      {titulo} <span className="font-normal text-texto-secundario">({quantidade})</span>
    </h2>
  );
}

function SemPendencias() {
  return <p className="mt-2 text-sm text-texto-secundario">Nenhuma pendência deste tipo agora.</p>;
}

function SecaoEventosComErro({ itens }: { itens: ItemEventoComErro[] }) {
  return (
    <section aria-labelledby="titulo-eventos-com-erro">
      <div id="titulo-eventos-com-erro">
        <CabecalhoSecao titulo="Eventos de ingestão com erro" quantidade={itens.length} />
      </div>
      {itens.length === 0 ? (
        <SemPendencias />
      ) : (
        <div className="mt-2 overflow-x-auto rounded border border-borda bg-background">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-borda text-left text-texto-secundario">
                <th scope="col" className="px-3 py-2 font-medium">Evento</th>
                <th scope="col" className="px-3 py-2 font-medium">Recebido em</th>
                <th scope="col" className="px-3 py-2 font-medium">Tentativas</th>
                <th scope="col" className="px-3 py-2 font-medium">O que aconteceu</th>
                <th scope="col" className="px-3 py-2 font-medium">O que fazer</th>
              </tr>
            </thead>
            <tbody>
              {itens.map((item) => (
                <tr key={item.id} className="border-b border-borda align-top last:border-0">
                  <th scope="row" className="px-3 py-2 text-left font-normal text-foreground">
                    {item.tipoEvento}
                    <span className="block text-xs text-texto-secundario">{item.idExterno}</span>
                  </th>
                  <td className="px-3 py-2 text-texto-secundario">{formatarData(item.recebidoEm)}</td>
                  <td className="px-3 py-2 tabular-nums text-texto-secundario">{item.tentativas}</td>
                  <td className="px-3 py-2 text-texto-secundario">
                    {item.erroMensagem ?? <span className="text-dado-ausente">Sem mensagem de erro registrada.</span>}
                  </td>
                  <td className="px-3 py-2 font-medium text-foreground">{item.acao}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
}

function SecaoItensSemVariacao({ itens }: { itens: ItemItemSemVariacao[] }) {
  return (
    <section aria-labelledby="titulo-itens-sem-variacao">
      <div id="titulo-itens-sem-variacao">
        <CabecalhoSecao titulo="Itens sem variação casada no catálogo" quantidade={itens.length} />
      </div>
      {itens.length === 0 ? (
        <SemPendencias />
      ) : (
        <div className="mt-2 overflow-x-auto rounded border border-borda bg-background">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-borda text-left text-texto-secundario">
                <th scope="col" className="px-3 py-2 font-medium">SKU de origem</th>
                <th scope="col" className="px-3 py-2 font-medium">Título de origem</th>
                <th scope="col" className="px-3 py-2 font-medium">Criado em</th>
                <th scope="col" className="px-3 py-2 font-medium">O que fazer</th>
              </tr>
            </thead>
            <tbody>
              {itens.map((item) => (
                <tr key={item.id} className="border-b border-borda align-top last:border-0">
                  <th scope="row" className="px-3 py-2 text-left font-normal text-foreground">
                    {item.skuOrigem ?? <span className="text-dado-ausente">Sem SKU informado</span>}
                  </th>
                  <td className="px-3 py-2 text-texto-secundario">{item.tituloOrigem ?? "—"}</td>
                  <td className="px-3 py-2 text-texto-secundario">{formatarData(item.criadoEm)}</td>
                  <td className="px-3 py-2 font-medium text-foreground">{item.acao}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
}

function SecaoVariacoesSemCusto({ itens }: { itens: ItemVariacaoSemCusto[] }) {
  return (
    <section aria-labelledby="titulo-variacoes-sem-custo">
      <div id="titulo-variacoes-sem-custo">
        <CabecalhoSecao titulo="Variações sem custo cadastrado" quantidade={itens.length} />
      </div>
      {itens.length === 0 ? (
        <SemPendencias />
      ) : (
        <div className="mt-2 overflow-x-auto rounded border border-borda bg-background">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-borda text-left text-texto-secundario">
                <th scope="col" className="px-3 py-2 font-medium">SKU</th>
                <th scope="col" className="px-3 py-2 font-medium">Descrição</th>
                <th scope="col" className="px-3 py-2 font-medium">Criada em</th>
                <th scope="col" className="px-3 py-2 font-medium">O que fazer</th>
              </tr>
            </thead>
            <tbody>
              {itens.map((item) => (
                <tr key={item.id} className="border-b border-borda align-top last:border-0">
                  <th scope="row" className="px-3 py-2 text-left font-normal text-foreground">
                    {item.sku}
                  </th>
                  <td className="px-3 py-2 text-texto-secundario">{item.descricaoVariacao ?? "—"}</td>
                  <td className="px-3 py-2 text-texto-secundario">{formatarData(item.criadoEm)}</td>
                  <td className="px-3 py-2 font-medium text-foreground">{item.acao}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
}

function SecaoDevolucoesAbertas({ itens }: { itens: ItemDevolucaoAberta[] }) {
  return (
    <section aria-labelledby="titulo-devolucoes-abertas">
      <div id="titulo-devolucoes-abertas">
        <CabecalhoSecao titulo="Devoluções em aberto" quantidade={itens.length} />
      </div>
      {itens.length === 0 ? (
        <SemPendencias />
      ) : (
        <div className="mt-2 overflow-x-auto rounded border border-borda bg-background">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-borda text-left text-texto-secundario">
                <th scope="col" className="px-3 py-2 font-medium">Status</th>
                <th scope="col" className="px-3 py-2 font-medium">Motivo</th>
                <th scope="col" className="px-3 py-2 font-medium">Aberta em</th>
                <th scope="col" className="px-3 py-2 font-medium">O que fazer</th>
              </tr>
            </thead>
            <tbody>
              {itens.map((item) => (
                <tr key={item.id} className="border-b border-borda align-top last:border-0">
                  <th scope="row" className="px-3 py-2 text-left font-normal text-foreground">
                    {item.status}
                  </th>
                  <td className="px-3 py-2 text-texto-secundario">{item.motivo ?? "—"}</td>
                  <td className="px-3 py-2 text-texto-secundario">{formatarData(item.abertaEm)}</td>
                  <td className="px-3 py-2 font-medium text-foreground">{item.acao}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
}
