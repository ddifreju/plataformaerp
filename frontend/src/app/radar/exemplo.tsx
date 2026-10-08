"use client";

// Dados de exemplo da empresa de demonstração: um botão no Painel que carrega, em passos curtos,
// lojas, produtos, anúncios, pedidos dos últimos 90 dias, compras, financeiro, promoções e
// atendimento. O servidor guarda até onde foi: se a página fechar, "Continuar" segue dali.
// Só aparece para a empresa de demonstração (o servidor recusa em qualquer outra).

import { useState } from "react";
import { str } from "./ui";

export type EstadoExemplo = { permitido: boolean; feitas: number; total: number };

type Props = {
  estado?: EstadoExemplo;
  podeCarregar: boolean;
  passo: (etapa: number) => Promise<Record<string, unknown>>;
  aoConcluir: () => Promise<void>;
};

export default function DadosExemplo({ estado, podeCarregar, passo, aoConcluir }: Props) {
  const [rodando, setRodando] = useState(false);
  const [feitasAqui, setFeitasAqui] = useState(0);
  const [texto, setTexto] = useState("");
  const [erro, setErro] = useState("");
  if (!estado?.permitido) return null;
  const feitas = Math.max(estado.feitas, feitasAqui);
  if (feitas >= estado.total && !rodando) return null;

  async function carregar() {
    if (!estado) return;
    setRodando(true);
    setErro("");
    let atual = feitas;
    try {
      while (atual < estado.total) {
        const r = await passo(atual + 1);
        atual = Number(r.etapa);
        setFeitasAqui(atual);
        setTexto(str(r.mensagem));
      }
      await aoConcluir();
    } catch (e) {
      setErro(`${(e as Error).message} Clique em “Continuar” para seguir de onde parou.`);
    } finally {
      setRodando(false);
    }
  }

  const porcento = Math.round((feitas / estado.total) * 100);
  return (
    <section className="rd-card rd-start rd-exemplo">
      <div>
        <h2>Dados de exemplo para testar tudo</h2>
        <p>
          Carrega uma operação fictícia completa da loja Casa Clara: 5 lojas, produtos (com
          variação, kit e alguns incompletos), anúncios prontos, 60 pedidos dos últimos 90 dias,
          clientes, fornecedores, compras, estoque, contas a pagar e a receber, promoções,
          atendimento e concorrentes. Lojas, clientes e fornecedores levam “exemplo” no nome, e os
          produtos têm código começando com EX-. Nada é publicado nos marketplaces e não há nota
          fiscal: uma nota fictícia poderia ser confundida com uma de verdade.
        </p>
        {(rodando || feitas > 0) && (
          <div className="rd-exemplo-progresso" aria-live="polite">
            <progress value={feitas} max={estado.total} />
            <small>
              {porcento}% · {texto || `Passo ${feitas} de ${estado.total}`}
              {rodando && " · leva uns 8 minutos; pode usar o Radar em outra aba"}
            </small>
          </div>
        )}
        {erro && (
          <p className="rd-error" role="alert">
            {erro}
          </p>
        )}
      </div>
      {podeCarregar ? (
        <button disabled={rodando} className="primary" onClick={carregar}>
          {rodando ? "Carregando…" : feitas > 0 ? "Continuar" : "Carregar dados de exemplo"}
        </button>
      ) : (
        <p className="rd-dica">Só o dono carrega os dados de exemplo.</p>
      )}
    </section>
  );
}
