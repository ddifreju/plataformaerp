"use client";

// Lojas da empresa nos marketplaces (Integrações). Cada conta vira uma loja com o nome que a
// lojista escolher ("ML Loja X", "Shopee Loja Y"), quantas ela tiver. É desta lista que saem as
// opções do "Anunciar". Sem a autorização do marketplace (depende do CNPJ), a loja fica
// "aguardando conexão" e nada sobe para lá; a tela nunca diz "conectada" antes disso.

import { useState, type FormEvent } from "react";
import { CANAIS, SIGLA } from "./canais";
import { Badge, str, type Row } from "./ui";

type Props = {
  lojas: Row[];
  anuncios: Row[];
  podeEditar: boolean;
  executar: (corpo: Record<string, unknown>) => Promise<boolean>;
};

export default function Lojas({ lojas, anuncios, podeEditar, executar }: Props) {
  const [nova, setNova] = useState({ marketplace: CANAIS[0], nome: "" });
  const [renomeando, setRenomeando] = useState<{ id: string; nome: string } | null>(null);
  const [removendo, setRemovendo] = useState<string | null>(null);

  async function criar(e: FormEvent) {
    e.preventDefault();
    if (await executar({ op: "loja_salvar", ...nova, nome: nova.nome.trim() }))
      setNova((n) => ({ ...n, nome: "" }));
  }

  return (
    <section className="rd-card rd-lojas">
      <div className="rd-card-head">
        <h2>Minhas lojas nos marketplaces</h2>
        <Badge>{lojas.length}</Badge>
      </div>
      <p className="rd-note">
        Cada conta sua num marketplace é uma loja. Pode ter várias no mesmo marketplace: dê um nome
        para não confundir. É para estas lojas que você anuncia pelo botão “Anunciar” dos produtos.
        A conexão com o marketplace (autorização da conta) entra depois do CNPJ; até lá os anúncios
        ficam prontos aqui, esperando.
      </p>
      {lojas.length === 0 && <p className="rd-dica">Nenhuma loja cadastrada ainda.</p>}
      <ul className="rd-lojas-lista">
        {lojas.map((l, i) => {
          const id = str(l.id);
          const qtd = anuncios.filter((a) => a.loja_id === l.id).length;
          return (
            <li key={id}>
              <span className={`rd-channel c${i % 4}`}>{SIGLA[str(l.marketplace)] ?? "?"}</span>
              {renomeando?.id === id ? (
                <form
                  className="rd-lojas-renomear"
                  onSubmit={async (e) => {
                    e.preventDefault();
                    if (await executar({ op: "loja_salvar", id, nome: renomeando.nome.trim() }))
                      setRenomeando(null);
                  }}
                >
                  <input
                    aria-label="Novo nome da loja"
                    maxLength={60}
                    autoFocus
                    value={renomeando.nome}
                    onChange={(e) => setRenomeando({ id, nome: e.target.value })}
                  />
                  <button className="primary" disabled={!renomeando.nome.trim()}>
                    Salvar
                  </button>
                  <button type="button" onClick={() => setRenomeando(null)}>
                    Cancelar
                  </button>
                </form>
              ) : (
                <div>
                  <strong>{str(l.nome)}</strong>
                  <small>
                    {str(l.marketplace)} · {qtd} anúncio(s)
                  </small>
                </div>
              )}
              {l.conectada_em ? (
                <Badge tone="green">Conectada</Badge>
              ) : (
                <Badge tone="amber">Aguardando conexão</Badge>
              )}
              {podeEditar && renomeando?.id !== id && (
                <span className="rd-lojas-acoes">
                  <button onClick={() => setRenomeando({ id, nome: str(l.nome) })}>Renomear</button>
                  {removendo === id ? (
                    <>
                      <button
                        className="perigo"
                        onClick={async () => {
                          await executar({ op: "loja_remover", id });
                          setRemovendo(null);
                        }}
                      >
                        Confirmar remoção
                      </button>
                      <button onClick={() => setRemovendo(null)}>Cancelar</button>
                    </>
                  ) : (
                    <button onClick={() => setRemovendo(id)}>Remover</button>
                  )}
                </span>
              )}
            </li>
          );
        })}
      </ul>
      {podeEditar ? (
        <form className="rd-lojas-nova" onSubmit={criar}>
          <label>
            Marketplace
            <select
              value={nova.marketplace}
              onChange={(e) => setNova((n) => ({ ...n, marketplace: e.target.value }))}
            >
              {CANAIS.map((c) => (
                <option key={c}>{c}</option>
              ))}
            </select>
          </label>
          <label>
            Nome da loja
            <input
              maxLength={60}
              placeholder={`Ex.: ${nova.marketplace} Loja X`}
              value={nova.nome}
              onChange={(e) => setNova((n) => ({ ...n, nome: e.target.value }))}
            />
          </label>
          <button className="primary" disabled={!nova.nome.trim()}>
            + Adicionar loja
          </button>
        </form>
      ) : (
        <p className="rd-dica">Só o dono e o gestor cadastram lojas.</p>
      )}
    </section>
  );
}
