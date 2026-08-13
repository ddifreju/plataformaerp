"use client";

import { useEffect, useState, type FormEvent } from "react";

import { CartaoNumero } from "@/componentes/CartaoNumero";
import { RotuloConfianca } from "@/componentes/RotuloConfianca";
import { ValorMonetario } from "@/componentes/ValorMonetario";
import { buscarMargemPeriodo, listarCanais, ErroApiCliente } from "@/lib/api/cliente";
import type { RespostaCanal, RespostaMargemPeriodo } from "@/lib/api/tipos";
import { formatarPercentual } from "@/lib/dinheiro";
import { ROTULO_BLOCO_MARGEM } from "@/lib/rotulos";



function inicioDoMesAtualParaCampo(): string {
  const agora = new Date();
  const inicio = new Date(agora.getFullYear(), agora.getMonth(), 1, 0, 0);
  return paraValorDeCampoLocal(inicio);
}

function agoraParaCampo(): string {
  return paraValorDeCampoLocal(new Date());
}

function paraValorDeCampoLocal(data: Date): string {
  const doisDigitos = (n: number) => String(n).padStart(2, "0");
  return `${data.getFullYear()}-${doisDigitos(data.getMonth() + 1)}-${doisDigitos(data.getDate())}T${doisDigitos(
    data.getHours(),
  )}:${doisDigitos(data.getMinutes())}`;
}

/** Campo `datetime-local` (sem fuso) → `OffsetDateTime` ISO que o backend espera. */
function paraIsoComFuso(valorDoCampo: string): string {
  return new Date(valorDoCampo).toISOString();
}

function formatarDataParaLeitura(iso: string): string {
  return new Date(iso).toLocaleString("pt-BR", { dateStyle: "short", timeStyle: "short" });
}

export default function PaginaResultado() {
  const [canalId, setCanalId] = useState("");
  const [canais, setCanais] = useState<RespostaCanal[]>([]);
  const [carregandoCanais, setCarregandoCanais] = useState(true);
  const [inicioCampo, setInicioCampo] = useState(inicioDoMesAtualParaCampo);
  const [fimCampo, setFimCampo] = useState(agoraParaCampo);
  const [erroDeValidacao, setErroDeValidacao] = useState<string | null>(null);
  const [erroDeConsulta, setErroDeConsulta] = useState<string | null>(null);
  const [consultaEmAndamento, setConsultaEmAndamento] = useState<{ canalId: string; inicio: string; fim: string } | null>(
    null,
  );
  const [resultado, setResultado] = useState<RespostaMargemPeriodo | null>(null);

  useEffect(() => {
    let cancelado = false;
    listarCanais()
      .then((lista) => {
        if (!cancelado) {
          setCanais(lista);
        }
      })
      .catch(() => {
        // Sem lista, o seletor fica vazio e o formulário não deixa
        // consultar. Não inventamos um canal nem caímos num "todos"
        // silencioso (decisão 0021).
        if (!cancelado) {
          setCanais([]);
        }
      })
      .finally(() => {
        if (!cancelado) {
          setCarregandoCanais(false);
        }
      });
    return () => {
      cancelado = true;
    };
  }, []);

  const nomeDoCanalEscolhido = canais.find((canal) => canal.id === canalId)?.nome ?? canalId;

  async function aoConsultar(evento: FormEvent<HTMLFormElement>) {
    evento.preventDefault();
    setErroDeValidacao(null);
    setErroDeConsulta(null);

    if (canalId === "") {
      setErroDeValidacao("Escolha um canal — a consulta exige exatamente um (decisão 0021).");
      return;
    }

    const inicioIso = paraIsoComFuso(inicioCampo);
    const fimIso = paraIsoComFuso(fimCampo);

    setConsultaEmAndamento({ canalId: canalId.trim(), inicio: inicioIso, fim: fimIso });
    setResultado(null);
    try {
      const resposta = await buscarMargemPeriodo({ inicio: inicioIso, fim: fimIso, canalId: canalId.trim() });
      setResultado(resposta);
    } catch (erroCapturado) {
      setErroDeConsulta(
        erroCapturado instanceof ErroApiCliente
          ? erroCapturado.message
          : "Não consegui consultar a margem agora. Tente novamente em instantes.",
      );
    } finally {
      setConsultaEmAndamento(null);
    }
  }

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-xl font-medium text-foreground">Resultado</h1>
        <p className="mt-1 text-base text-texto-secundario">
          Quanto sobrou de verdade, e de onde saiu cada centavo.
        </p>
      </div>

      <form
        onSubmit={aoConsultar}
        className="grid grid-cols-1 gap-4 rounded border border-borda bg-background p-4 sm:grid-cols-[2fr_1fr_1fr_auto] sm:items-end"
      >
        <div className="flex flex-col gap-1">
          <label htmlFor="canalId" className="text-sm font-medium text-foreground">
            Canal
          </label>
          <select
            id="canalId"
            name="canalId"
            required
            value={canalId}
            onChange={(evento) => setCanalId(evento.target.value)}
            aria-describedby="canalId-ajuda"
            disabled={carregandoCanais || canais.length === 0}
            className="rounded border border-borda px-3 py-2 text-sm text-foreground"
          >
            {/* Sem opcao "todos" DE PROPOSITO: nao e limitacao de tela,
                e a decisao 0021. Somar canais que podem espelhar a mesma
                venda contaria o faturamento duas vezes. */}
            <option value="">
              {carregandoCanais ? "Carregando canais…" : "Escolha um canal"}
            </option>
            {canais.map((canal) => (
              <option key={canal.id} value={canal.id}>
                {canal.nome}
                {canal.ativo ? "" : " (desativado)"}
              </option>
            ))}
          </select>
          <p id="canalId-ajuda" className="text-xs text-texto-secundario">
            Um canal por consulta, nunca &quot;todos&quot; — somar canais pode contar a mesma venda duas vezes
            (decisão 0021).
          </p>
          {!carregandoCanais && canais.length === 0 && (
            <p className="text-xs text-dado-ausente">
              Nenhum canal cadastrado ainda. Sem canal não há venda para calcular.
            </p>
          )}
        </div>

        <div className="flex flex-col gap-1">
          <label htmlFor="inicio" className="text-sm font-medium text-foreground">
            Início do período
          </label>
          <input
            id="inicio"
            name="inicio"
            type="datetime-local"
            required
            value={inicioCampo}
            onChange={(evento) => setInicioCampo(evento.target.value)}
            className="rounded border border-borda px-3 py-2 text-sm text-foreground tabular-nums"
          />
        </div>

        <div className="flex flex-col gap-1">
          <label htmlFor="fim" className="text-sm font-medium text-foreground">
            Fim do período
          </label>
          <input
            id="fim"
            name="fim"
            type="datetime-local"
            required
            value={fimCampo}
            onChange={(evento) => setFimCampo(evento.target.value)}
            className="rounded border border-borda px-3 py-2 text-sm text-foreground tabular-nums"
          />
        </div>

        <button
          type="submit"
          disabled={consultaEmAndamento !== null}
          className="rounded bg-acao px-4 py-2 text-sm font-medium text-white hover:bg-acao-hover disabled:opacity-60"
        >
          Consultar
        </button>

        {erroDeValidacao && (
          <p role="alert" className="text-sm text-valor-negativo sm:col-span-4">
            {erroDeValidacao}
          </p>
        )}
      </form>

      {consultaEmAndamento && (
        <p className="text-sm text-texto-secundario" role="status">
          Consultando margem do canal {nomeDoCanalEscolhido} entre{" "}
          {formatarDataParaLeitura(consultaEmAndamento.inicio)} e {formatarDataParaLeitura(consultaEmAndamento.fim)}…
        </p>
      )}

      {erroDeConsulta && (
        <p role="alert" className="rounded border border-valor-negativo bg-valor-negativo-bg p-4 text-sm text-valor-negativo">
          {erroDeConsulta}
        </p>
      )}

      {resultado && <ResultadoDetalhado resultado={resultado} />}
    </div>
  );
}

function ResultadoDetalhado({ resultado }: { resultado: RespostaMargemPeriodo }) {
  return (
    <div className="flex flex-col gap-6">
      <p className="text-sm text-texto-secundario">
        {resultado.escopoCanal} — {resultado.quantidadePedidos} pedido(s) entre{" "}
        {formatarDataParaLeitura(resultado.inicio)} e {formatarDataParaLeitura(resultado.fim)}.
      </p>

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <CartaoNumero
          rotulo="Faturamento bruto"
          fraseDeApoio="O que o comprador pagou, frete incluído."
          valorDecimal={resultado.faturamentoBrutoN0}
        />
        <CartaoNumero
          rotulo="Receita líquida"
          fraseDeApoio="Faturamento menos devolução e desconto. Ainda não tirei taxa de canal, frete nem imposto."
          valorDecimal={resultado.receitaLiquidaN1}
        />
        <CartaoNumero
          rotulo="Margem por pedido"
          fraseDeApoio="O que sobra depois dos custos diretos: mercadoria, comissão, frete, imposto. Cada centavo daqui bate com a fatura do canal."
          valorDecimal={resultado.margemContribuicaoN2}
          aplicarCorDeSinal
        />
        <CartaoNumero
          rotulo="Resultado do pedido"
          fraseDeApoio="Margem menos Ads e outros custos rateados entre pedidos. A parte rateada é estimativa nossa, não fato do canal."
          valorDecimal={resultado.resultadoPeriodoN3}
          aplicarCorDeSinal
        />
      </div>

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
        <PercentualOuAusencia titulo="Margem por pedido, em %" valor={resultado.margemContribuicaoPercentual} />
        <PercentualOuAusencia titulo="Margem líquida, em %" valor={resultado.margemLiquidaPercentual} />
      </div>

      <RotuloConfianca
        rotulo={resultado.rotulo}
        lacunas={resultado.lacunas}
        valorTeto={resultado.margemContribuicaoN2}
        percentualTeto={resultado.margemContribuicaoPercentual}
      />

      <section aria-labelledby="titulo-decomposicao">
        <h2 id="titulo-decomposicao" className="text-base font-medium text-foreground">
          De onde saiu cada centavo
        </h2>
        <div className="mt-2 overflow-x-auto rounded border border-borda bg-background">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-borda text-left text-texto-secundario">
                <th scope="col" className="px-3 py-2 font-medium">
                  Bloco
                </th>
                <th scope="col" className="px-3 py-2 text-right font-medium">
                  Valor no período
                </th>
                <th scope="col" className="px-3 py-2 font-medium">
                  Origem
                </th>
              </tr>
            </thead>
            <tbody>
              {resultado.decomposicao.map((linha) => (
                <tr key={linha.bloco} className="border-b border-borda last:border-0">
                  <th scope="row" className="px-3 py-2 text-left font-normal text-foreground">
                    {ROTULO_BLOCO_MARGEM[linha.bloco]}
                  </th>
                  <td className="px-3 py-2 text-right">
                    <ValorMonetario valorDecimal={linha.valor} />
                  </td>
                  <td className="px-3 py-2">
                    {linha.contemEstimativa ? (
                      <span className="border-b border-dashed border-dado-estimado text-xs text-dado-estimado">
                        Inclui custo estimado (ver detalhamento abaixo)
                      </span>
                    ) : (
                      <span className="text-xs text-texto-secundario">Informado pelo canal/fatura</span>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>

      <p className="text-xs text-texto-secundario">
        Rastreabilidade: esta consulta usou {resultado.idsPedidoUsados.length} pedido(s) e{" "}
        {resultado.idsCustoUsados.length} linha(s) de custo. Guarde o período e o canal acima — é o que provamos
        se este número for contestado (regra 3 do CLAUDE.md).
      </p>
    </div>
  );
}

function PercentualOuAusencia({ titulo, valor }: { titulo: string; valor: string | null }) {
  return (
    <div className="rounded border border-borda bg-background p-4">
      <h3 className="text-sm font-medium text-texto-secundario">{titulo}</h3>
      {valor === null ? (
        <p className="mt-1 text-base text-dado-ausente">
          Não calculável — faturamento bruto zero neste período. Nem 0%, nem &quot;—&quot; seriam verdade aqui.
        </p>
      ) : (
        <p className="mt-1 text-2xl font-medium tabular-nums text-foreground">{formatarPercentual(valor)}</p>
      )}
    </div>
  );
}
