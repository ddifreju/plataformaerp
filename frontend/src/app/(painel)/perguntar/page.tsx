"use client";

import { useState, type FormEvent } from "react";

import { BadgeConfianca } from "@/componentes/BadgeConfianca";
import { CartaoNumeroCitado } from "@/componentes/CartaoNumeroCitado";
import { ListaLacunasTexto } from "@/componentes/ListaLacunasTexto";
import { ErroApiCliente, perguntar } from "@/lib/api/cliente";
import type { RespostaPergunta } from "@/lib/api/tipos";
import {
  LIMITE_CARACTERES_PERGUNTA,
  atalhoDeAmbiguidadeDeMargem,
  contarCaracteres,
  estadoVisualParaTipo,
  limitarTexto,
} from "@/lib/pergunta";
import { ROTULO_CODIGO_INTENCAO, ROTULO_PARAMETRO_PERGUNTA } from "@/lib/rotulos";

/**
 * Tela "Perguntar" (tarefa 26, decisão 0030) — porta de entrada por texto
 * livre. Os três `TipoResposta` são três estados visuais distintos:
 * RESPOSTA mostra número e rótulo de confiança; ESCLARECIMENTO e RECUSA
 * não são erro (guia de interface, tom de voz) — mostram sugestões
 * clicáveis, sem vermelho e sem ícone de alerta. O único vermelho desta
 * tela é falha de rede de verdade (`erroDeRede`), nunca lacuna de dado.
 */
export default function PaginaPerguntar() {
  const [texto, setTexto] = useState("");
  const [carregando, setCarregando] = useState(false);
  const [perguntaEmConsulta, setPerguntaEmConsulta] = useState<string | null>(null);
  const [erroDeRede, setErroDeRede] = useState<string | null>(null);
  const [resposta, setResposta] = useState<RespostaPergunta | null>(null);

  const contagem = contarCaracteres(texto);
  const podeEnviar = !carregando && texto.trim() !== "" && !contagem.excedeu;

  async function enviar(textoParaEnviar: string) {
    const textoLimpo = textoParaEnviar.trim();
    if (textoLimpo === "" || textoLimpo.length > LIMITE_CARACTERES_PERGUNTA) {
      return;
    }

    setErroDeRede(null);
    setCarregando(true);
    setPerguntaEmConsulta(textoLimpo);

    try {
      const respostaRecebida = await perguntar(textoLimpo);
      setResposta(respostaRecebida);
    } catch (erroCapturado) {
      setResposta(null);
      setErroDeRede(
        erroCapturado instanceof ErroApiCliente
          ? erroCapturado.message
          : "Não consegui perguntar agora. Tente novamente em instantes.",
      );
    } finally {
      setCarregando(false);
    }
  }

  function aoSubmeter(evento: FormEvent<HTMLFormElement>) {
    evento.preventDefault();
    void enviar(texto);
  }

  function aoClicarSugestao(sugestao: string) {
    setTexto(limitarTexto(sugestao));
  }

  function aoClicarAtalho(sugestao: string) {
    const textoLimitado = limitarTexto(sugestao);
    setTexto(textoLimitado);
    void enviar(textoLimitado);
  }

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-xl font-medium text-foreground">Perguntar</h1>
        <p className="mt-1 text-base text-texto-secundario">
          Uma pergunta sobre a operação — com o número rastreável, ou a explicação de por que ainda não dá para
          responder.
        </p>
      </div>

      <form onSubmit={aoSubmeter} className="flex flex-col gap-2 rounded border border-borda bg-background p-4">
        <label htmlFor="pergunta-texto" className="text-sm font-medium text-foreground">
          Sua pergunta
        </label>
        <textarea
          id="pergunta-texto"
          name="texto"
          required
          rows={3}
          maxLength={LIMITE_CARACTERES_PERGUNTA}
          value={texto}
          onChange={(evento) => setTexto(evento.target.value)}
          placeholder="Ex.: Quanto sobrou no mês passado no Mercado Livre?"
          aria-describedby="pergunta-texto-contador"
          className="rounded border border-borda px-3 py-2 text-base text-foreground focus:outline-none focus-visible:ring-2 focus-visible:ring-acao"
        />
        <div className="flex flex-wrap items-center justify-between gap-2">
          <p
            id="pergunta-texto-contador"
            className={`text-xs tabular-nums ${contagem.excedeu ? "font-medium text-foreground" : "text-texto-secundario"}`}
          >
            {contagem.usados} / {LIMITE_CARACTERES_PERGUNTA} caracteres
          </p>
          <button
            type="submit"
            disabled={!podeEnviar}
            className="rounded bg-acao px-4 py-2 text-sm font-medium text-white hover:bg-acao-hover disabled:opacity-60"
          >
            Perguntar
          </button>
        </div>
      </form>

      <div aria-live="polite" className="flex flex-col gap-6">
        {carregando && (
          <p className="text-sm text-texto-secundario" role="status">
            Consultando: &ldquo;{perguntaEmConsulta}&rdquo;…
          </p>
        )}

        {!carregando && erroDeRede && (
          <p
            role="alert"
            className="rounded border border-erro-sistema bg-erro-sistema-bg p-4 text-sm text-erro-sistema"
          >
            {erroDeRede}
          </p>
        )}

        {!carregando && !erroDeRede && resposta && (
          <RespostaDetalhada resposta={resposta} aoClicarSugestao={aoClicarSugestao} aoClicarAtalho={aoClicarAtalho} />
        )}
      </div>
    </div>
  );
}

function RespostaDetalhada({
  resposta,
  aoClicarSugestao,
  aoClicarAtalho,
}: {
  resposta: RespostaPergunta;
  aoClicarSugestao: (sugestao: string) => void;
  aoClicarAtalho: (sugestao: string) => void;
}) {
  const estado = estadoVisualParaTipo(resposta.tipo);
  const atalho = atalhoDeAmbiguidadeDeMargem(resposta.intencao, resposta.parametrosUsados);

  return (
    <div className="flex flex-col gap-4">
      {estado === "resposta" && <BlocoResposta resposta={resposta} />}
      {estado === "esclarecimento" && (
        <BlocoNeutro
          texto={resposta.texto}
          tituloSugestoes="Experimente perguntar assim:"
          sugestoes={resposta.perguntasQueSeiResponder}
          aoClicarSugestao={aoClicarSugestao}
        />
      )}
      {estado === "recusa" && (
        <BlocoNeutro
          texto={resposta.texto}
          tituloSugestoes="Isto eu sei responder:"
          sugestoes={resposta.perguntasQueSeiResponder}
          aoClicarSugestao={aoClicarSugestao}
        />
      )}

      {atalho && (
        <button
          type="button"
          onClick={() => aoClicarAtalho(atalho.perguntaSugerida)}
          className="self-start rounded border border-acao px-3 py-2 text-sm font-medium text-acao hover:bg-superficie focus:outline-none focus-visible:ring-2 focus-visible:ring-acao"
        >
          {atalho.rotulo}
        </button>
      )}

      <ComoChegueiNesseNumero resposta={resposta} />
    </div>
  );
}

function BlocoResposta({ resposta }: { resposta: RespostaPergunta }) {
  return (
    <div className="rounded border border-borda bg-background p-4">
      <p className="text-base text-foreground">{resposta.texto}</p>

      {resposta.rotuloConfianca && (
        <div className="mt-3">
          <BadgeConfianca rotulo={resposta.rotuloConfianca} />
        </div>
      )}

      {resposta.numeros.length > 0 && (
        <div className="mt-4 grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {resposta.numeros.map((numero) => (
            <CartaoNumeroCitado key={numero.nome} nome={numero.nome} valor={numero.valor} />
          ))}
        </div>
      )}

      {resposta.lacunas.length > 0 && (
        <div className="mt-4">
          <h3 className="text-sm font-medium text-texto-secundario">Faltam:</h3>
          <ListaLacunasTexto lacunas={resposta.lacunas} />
        </div>
      )}
    </div>
  );
}

/**
 * ESCLARECIMENTO e RECUSA compartilham a mesma aparência neutra —
 * nenhum dos dois é erro (guia, tom de voz): superfície neutra, nenhuma
 * cor de "atenção" ou "erro", sugestões como convite, não como correção.
 */
function BlocoNeutro({
  texto,
  tituloSugestoes,
  sugestoes,
  aoClicarSugestao,
}: {
  texto: string;
  tituloSugestoes: string;
  sugestoes: string[];
  aoClicarSugestao: (sugestao: string) => void;
}) {
  return (
    <div className="rounded border border-borda bg-superficie p-4">
      <p className="text-base text-foreground">{texto}</p>

      {sugestoes.length > 0 && (
        <div className="mt-3">
          <p className="text-sm text-texto-secundario">{tituloSugestoes}</p>
          <ul className="mt-2 flex flex-wrap gap-2">
            {sugestoes.map((sugestao) => (
              <li key={sugestao}>
                <button
                  type="button"
                  onClick={() => aoClicarSugestao(sugestao)}
                  className="rounded-full border border-borda bg-background px-3 py-1.5 text-left text-sm text-foreground hover:bg-superficie focus:outline-none focus-visible:ring-2 focus-visible:ring-acao"
                >
                  {sugestao}
                </button>
              </li>
            ))}
          </ul>
        </div>
      )}
    </div>
  );
}

function ComoChegueiNesseNumero({ resposta }: { resposta: RespostaPergunta }) {
  const parametros = Object.entries(resposta.parametrosUsados);

  return (
    <details className="rounded border border-borda bg-background p-4">
      <summary className="cursor-pointer text-sm font-medium text-foreground focus:outline-none focus-visible:ring-2 focus-visible:ring-acao">
        Como cheguei nesse número
      </summary>
      <div className="mt-3 flex flex-col gap-3 text-sm text-texto-secundario">
        <p>
          Pergunta que entendi:{" "}
          <span className="font-medium text-foreground">
            {resposta.intencao
              ? (ROTULO_CODIGO_INTENCAO[resposta.intencao] ?? resposta.intencao)
              : "Nenhuma — não caiu em nenhuma das perguntas que sei responder."}
          </span>
        </p>

        {parametros.length > 0 && (
          <div>
            <p>Parâmetros usados:</p>
            <ul className="mt-1 flex flex-col gap-1">
              {parametros.map(([chave, valor]) => (
                <li key={chave}>
                  <span className="text-foreground">{ROTULO_PARAMETRO_PERGUNTA[chave] ?? chave}:</span> {valor}
                </li>
              ))}
            </ul>
          </div>
        )}

        <p>
          Consulta registrada:{" "}
          <code className="font-mono text-foreground">{resposta.consultaAuditadaId}</code>
        </p>
      </div>
    </details>
  );
}
