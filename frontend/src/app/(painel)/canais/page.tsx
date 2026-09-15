"use client";

import { useEffect, useState, type FormEvent } from "react";

import { declararEscopoCanal, ErroApiCliente, listarCanais } from "@/lib/api/cliente";
import type { RequisicaoEscopoCanal, RespostaCanal } from "@/lib/api/tipos";
import { candidatosParaEspelho, textoDoEstadoDeEscopo, type EscolhaEscopo } from "@/lib/canais";
import { ROTULO_CATEGORIA_CANAL, ROTULO_TIPO_CANAL } from "@/lib/rotulos";

function formatarData(iso: string): string {
  return new Date(iso).toLocaleString("pt-BR", { dateStyle: "short", timeStyle: "short" });
}

/**
 * Tela "Canais" (tarefa 34, decisão 0033) — o único lugar onde a lojista
 * declara a origem dos pedidos de cada canal. Enquanto um canal fica
 * `NAO_DECLARADO`, o sistema se recusa a somá-lo com outros (fail-closed);
 * esta tela é o que destrava "quanto sobrou no mês?" na tela Resultado.
 *
 * Sem contagem de pedido por canal aqui: não existe endpoint que devolva
 * isso, e a decisão 0033 já avisa que mostrar esse número ajudaria a
 * decidir — registrado no relato da tarefa como pendência futura, nunca
 * estimado no cliente (regra 5 do CLAUDE.md).
 */
export default function PaginaCanais() {
  const [canais, setCanais] = useState<RespostaCanal[]>([]);
  const [carregando, setCarregando] = useState(true);
  const [erroDeCarga, setErroDeCarga] = useState<string | null>(null);
  const [canalEmEdicaoId, setCanalEmEdicaoId] = useState<string | null>(null);

  useEffect(() => {
    let cancelado = false;
    listarCanais()
      .then((lista) => {
        if (!cancelado) setCanais(lista);
      })
      .catch((erroCapturado) => {
        if (cancelado) return;
        setErroDeCarga(
          erroCapturado instanceof ErroApiCliente
            ? erroCapturado.message
            : "Não consegui consultar os canais agora.",
        );
      })
      .finally(() => {
        if (!cancelado) setCarregando(false);
      });
    return () => {
      cancelado = true;
    };
  }, []);

  async function aoDeclarar(canalId: string, requisicao: RequisicaoEscopoCanal) {
    const canalAtualizado = await declararEscopoCanal(canalId, requisicao);
    setCanais((atual) => atual.map((canal) => (canal.id === canalAtualizado.id ? canalAtualizado : canal)));
    setCanalEmEdicaoId(null);
  }

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-xl font-medium text-foreground">Canais</h1>
        <p className="mt-1 text-base text-texto-secundario">
          Se a mesma venda chega por dois canais e os dois entram na mesma soma, o faturamento aparece dobrado.
        </p>
      </div>

      {carregando && (
        <p className="text-sm text-texto-secundario" role="status">
          Consultando canais…
        </p>
      )}

      {erroDeCarga && (
        <p role="alert" className="rounded border border-erro-sistema bg-erro-sistema-bg p-4 text-sm text-erro-sistema">
          {erroDeCarga}
        </p>
      )}

      {!carregando && !erroDeCarga && canais.length === 0 && (
        <p className="text-sm text-texto-secundario">Nenhum canal cadastrado ainda.</p>
      )}

      {!carregando && !erroDeCarga && canais.length > 0 && (
        <ul className="flex flex-col gap-4">
          {canais.map((canal) => (
            <li key={canal.id}>
              <CartaoCanal
                canal={canal}
                canais={canais}
                emEdicao={canalEmEdicaoId === canal.id}
                aoAbrirEdicao={() => setCanalEmEdicaoId(canal.id)}
                aoFecharEdicao={() => setCanalEmEdicaoId(null)}
                aoDeclarar={(requisicao) => aoDeclarar(canal.id, requisicao)}
              />
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

interface PropriedadesCartaoCanal {
  canal: RespostaCanal;
  canais: RespostaCanal[];
  emEdicao: boolean;
  aoAbrirEdicao: () => void;
  aoFecharEdicao: () => void;
  aoDeclarar: (requisicao: RequisicaoEscopoCanal) => Promise<void>;
}

function CartaoCanal({ canal, canais, emEdicao, aoAbrirEdicao, aoFecharEdicao, aoDeclarar }: PropriedadesCartaoCanal) {
  const estado = textoDoEstadoDeEscopo(canal, canais);
  const jaDeclarado = canal.escopoDeclarado !== "NAO_DECLARADO";

  return (
    <div className="rounded border border-borda bg-background p-4">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h2 className="text-base font-medium text-foreground">{canal.nome}</h2>
          <p className="text-sm text-texto-secundario">
            {ROTULO_TIPO_CANAL[canal.tipo]} · {ROTULO_CATEGORIA_CANAL[canal.categoria]}
            {!canal.ativo && " · desativado"}
          </p>
        </div>

        {!emEdicao && (
          <button
            type="button"
            onClick={aoAbrirEdicao}
            className="rounded border border-borda px-3 py-1.5 text-sm font-medium text-foreground hover:bg-superficie"
          >
            {jaDeclarado ? "Corrigir declaração" : "Declarar"}
          </button>
        )}
      </div>

      <div className="mt-3 rounded border border-borda bg-superficie p-3">
        <p className="text-sm font-medium text-foreground">{estado.rotulo}</p>
        <p className="mt-1 text-sm text-texto-secundario">{estado.detalhe}</p>
        {canal.escopoDeclaradoEm && (
          <p className="mt-1 text-xs text-texto-secundario">Declarado em {formatarData(canal.escopoDeclaradoEm)}.</p>
        )}
      </div>

      {emEdicao && (
        <FormularioDeclaracao canal={canal} canais={canais} aoCancelar={aoFecharEdicao} aoDeclarar={aoDeclarar} />
      )}
    </div>
  );
}

interface PropriedadesFormularioDeclaracao {
  canal: RespostaCanal;
  canais: RespostaCanal[];
  aoCancelar: () => void;
  aoDeclarar: (requisicao: RequisicaoEscopoCanal) => Promise<void>;
}

/**
 * Formulário de declaração — SEMPRE começa em branco, mesmo para corrigir
 * uma declaração já existente: nenhuma opção vem marcada (nem a que já
 * está declarada hoje), porque um valor pré-marcado vira declaração por
 * inércia, que a decisão 0033 trata como pior que nenhuma declaração. O
 * estado atual já está visível acima, em `CartaoCanal`; este formulário só
 * grava uma escolha nova e explícita.
 */
function FormularioDeclaracao({ canal, canais, aoCancelar, aoDeclarar }: PropriedadesFormularioDeclaracao) {
  const [escolha, setEscolha] = useState<EscolhaEscopo>("");
  const [espelhaCanalId, setEspelhaCanalId] = useState("");
  const [enviando, setEnviando] = useState(false);
  const [erro, setErro] = useState<string | null>(null);

  const candidatos = candidatosParaEspelho(canais, canal.id);

  async function aoSubmeter(evento: FormEvent<HTMLFormElement>) {
    evento.preventDefault();
    setErro(null);

    if (escolha === "") {
      setErro("Escolha uma das duas opções abaixo antes de salvar.");
      return;
    }
    if (escolha === "ESPELHO" && espelhaCanalId === "") {
      setErro("Escolha de qual canal este é cópia.");
      return;
    }

    setEnviando(true);
    try {
      await aoDeclarar(
        escolha === "FONTE_PRIMARIA" ? { escopo: "FONTE_PRIMARIA" } : { escopo: "ESPELHO", espelhaCanalId },
      );
    } catch (erroCapturado) {
      // Mostra a mensagem do servidor tal como veio (404 canal_desconhecido,
      // 409 cadeia_de_espelho_invalida já nomeiam a cadeia em português) —
      // nunca uma mensagem genérica nossa por cima da dele.
      setErro(
        erroCapturado instanceof ErroApiCliente
          ? erroCapturado.message
          : "Não consegui salvar a declaração agora. Tente novamente em instantes.",
      );
      setEnviando(false);
    }
  }

  return (
    <form onSubmit={aoSubmeter} className="mt-3 flex flex-col gap-3 rounded border border-borda p-3">
      <fieldset className="flex flex-col gap-2">
        <legend className="text-sm font-medium text-foreground">De onde vêm os pedidos deste canal?</legend>

        <label className="flex items-start gap-2 text-sm text-foreground">
          <input
            type="radio"
            name={`escopo-${canal.id}`}
            value="FONTE_PRIMARIA"
            checked={escolha === "FONTE_PRIMARIA"}
            onChange={() => setEscolha("FONTE_PRIMARIA")}
            className="mt-1"
          />
          <span>Os pedidos nascem aqui.</span>
        </label>

        <label className="flex items-start gap-2 text-sm text-foreground">
          <input
            type="radio"
            name={`escopo-${canal.id}`}
            value="ESPELHO"
            checked={escolha === "ESPELHO"}
            onChange={() => setEscolha("ESPELHO")}
            className="mt-1"
          />
          <span>Os pedidos daqui são cópia dos de outro canal.</span>
        </label>
        <p className="pl-6 text-xs text-texto-secundario">
          Nas duas opções o canal continua ingerindo e continua consultável sozinho — a declaração muda só se ele
          entra numa soma com outros canais.
        </p>
      </fieldset>

      {escolha === "ESPELHO" && (
        <div className="flex flex-col gap-1 pl-6">
          <label htmlFor={`espelha-${canal.id}`} className="text-sm font-medium text-foreground">
            Cópia de qual canal?
          </label>
          <select
            id={`espelha-${canal.id}`}
            value={espelhaCanalId}
            onChange={(evento) => setEspelhaCanalId(evento.target.value)}
            className="rounded border border-borda px-3 py-2 text-sm text-foreground sm:max-w-xs"
          >
            {/* Sem opção pré-marcada de propósito (decisão 0033): a
                lojista precisa escolher ativamente. */}
            <option value="">Escolha o canal de origem</option>
            {candidatos.map((candidato) => (
              <option key={candidato.id} value={candidato.id}>
                {candidato.nome}
              </option>
            ))}
          </select>
          {candidatos.length === 0 && (
            <p className="text-xs text-dado-ausente">Nenhum outro canal disponível como origem ainda.</p>
          )}
        </div>
      )}

      {erro && (
        <p role="alert" className="rounded border border-erro-sistema bg-erro-sistema-bg p-3 text-sm text-erro-sistema">
          {erro}
        </p>
      )}

      <div className="flex gap-2">
        <button
          type="submit"
          disabled={enviando}
          className="rounded bg-acao px-4 py-2 text-sm font-medium text-white hover:bg-acao-hover disabled:opacity-60"
        >
          {enviando ? "Salvando…" : "Salvar declaração"}
        </button>
        <button
          type="button"
          onClick={aoCancelar}
          disabled={enviando}
          className="rounded border border-borda px-4 py-2 text-sm font-medium text-foreground hover:bg-superficie disabled:opacity-60"
        >
          Cancelar
        </button>
      </div>
    </form>
  );
}
