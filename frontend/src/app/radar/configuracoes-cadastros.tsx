"use client";

// Configurações → cadastros → "Configurações do cadastro de produtos": SKU
// automático e valores que todo produto novo já traz preenchidos.

import { useState } from "react";
import { Campo } from "./cliente";
import { ORIGENS, UNIDADES } from "./produto";
import { str } from "./ui";

type Executar = (corpo: Record<string, unknown>) => Promise<Record<string, unknown> | null>;

const MODOS: [string, string, string][] = [
  ["MANUAL", "Manual", "Você digita o SKU de cada produto."],
  ["SEQUENCIAL", "Automático (1, 2, 3…)", "O Radar numera em sequência: 00001, 00002…"],
  ["PREFIXO", "Automático com prefixo", "Ex.: CAM-00001, CAM-00002. Bom para separar linhas."],
];

export function ConfigProdutos({
  config,
  podeEditar,
  executar,
  voltar,
}: {
  config: Record<string, unknown>;
  podeEditar: boolean;
  executar: Executar;
  voltar: () => void;
}) {
  const [v, setV] = useState({
    sku_modo: str(config.sku_modo) || "MANUAL",
    sku_prefixo: str(config.sku_prefixo),
    sku_digitos: str(config.sku_digitos) || "5",
    unidade_padrao: str(config.unidade_padrao) || "UN",
    ncm_padrao: str(config.ncm_padrao),
    origem_padrao: str(config.origem_padrao),
  });
  const [salvando, setSalvando] = useState(false);
  const set = (k: keyof typeof v) => (e: { target: { value: string } }) =>
    setV((a) => ({ ...a, [k]: e.target.value }));
  const digitos = Math.min(10, Math.max(1, Number(v.sku_digitos) || 5));
  const exemplo =
    v.sku_modo === "MANUAL"
      ? ""
      : `${v.sku_modo === "PREFIXO" ? v.sku_prefixo.toUpperCase() : ""}${"1".padStart(digitos, "0")}`;

  async function salvar() {
    setSalvando(true);
    try {
      const r = await executar({
        op: "configuracao_salvar",
        chave: "produtos",
        valor: { ...v, sku_digitos: digitos },
      });
      if (r) voltar();
    } finally {
      setSalvando(false);
    }
  }

  return (
    <div className="rd-produto-form">
      <div className="rd-toolbar">
        <button type="button" onClick={voltar}>
          ← Configurações
        </button>
        <span className="rd-produto-titulo">Cadastro de produtos</span>
        {podeEditar && (
          <button type="button" className="primary" disabled={salvando} onClick={salvar}>
            {salvando ? "Salvando…" : "Salvar"}
          </button>
        )}
      </div>
      {!podeEditar && <p className="rd-note">Só o dono ou o gestor alteram estas configurações.</p>}

      <section className="rd-card">
        <h3 className="rd-config-secao">Código (SKU)</h3>
        <div className="rd-opcoes-grandes" role="radiogroup" aria-label="Como o SKU é criado">
          {MODOS.map(([k, r, d]) => (
            <label key={k} className={v.sku_modo === k ? "ativo" : ""}>
              <input
                type="radio"
                name="sku_modo"
                value={k}
                checked={v.sku_modo === k}
                disabled={!podeEditar}
                onChange={set("sku_modo")}
              />
              <strong>{r}</strong>
              <small>{d}</small>
            </label>
          ))}
        </div>
        {v.sku_modo !== "MANUAL" && (
          <div className="rd-form-grid">
            {v.sku_modo === "PREFIXO" && (
              <Campo rotulo="Prefixo" dica="Até 12 letras, números ou hífen.">
                <input
                  id="cfg-sku_prefixo"
                  value={v.sku_prefixo}
                  maxLength={12}
                  disabled={!podeEditar}
                  onChange={set("sku_prefixo")}
                />
              </Campo>
            )}
            <Campo rotulo="Quantidade de dígitos" dica="Completa com zeros à esquerda.">
              <input
                id="cfg-sku_digitos"
                type="number"
                min={1}
                max={10}
                value={v.sku_digitos}
                disabled={!podeEditar}
                onChange={set("sku_digitos")}
              />
            </Campo>
            <p className="rd-note wide">
              O próximo produto sem SKU recebe algo como <strong>{exemplo}</strong>, seguindo o
              maior número já usado. Você ainda pode digitar um SKU próprio quando quiser.
            </p>
          </div>
        )}
      </section>

      <section className="rd-card">
        <h3 className="rd-config-secao">Valores padrão para produtos novos</h3>
        <p className="rd-note">
          Todo produto novo já começa com estes valores. Dá para trocar em cada produto.
        </p>
        <div className="rd-form-grid">
          <Campo rotulo="Unidade" dica="Unidade usada na nota fiscal.">
            <select
              id="cfg-unidade_padrao"
              value={v.unidade_padrao}
              disabled={!podeEditar}
              onChange={set("unidade_padrao")}
            >
              {UNIDADES.map(([k, r]) => (
                <option key={k} value={k}>
                  {r}
                </option>
              ))}
            </select>
          </Campo>
          <Campo
            rotulo="NCM"
            dica="8 dígitos. Útil quando quase tudo que você vende é do mesmo tipo."
          >
            <input
              id="cfg-ncm_padrao"
              value={v.ncm_padrao}
              inputMode="numeric"
              maxLength={10}
              disabled={!podeEditar}
              onChange={set("ncm_padrao")}
            />
          </Campo>
          <Campo rotulo="Origem (ICMS)" largo>
            <select
              id="cfg-origem_padrao"
              value={v.origem_padrao}
              disabled={!podeEditar}
              onChange={set("origem_padrao")}
            >
              <option value="">0 - Nacional (padrão)</option>
              {ORIGENS.map(([k, r]) => (
                <option key={k} value={k}>
                  {r}
                </option>
              ))}
            </select>
          </Campo>
        </div>
      </section>

      <section className="rd-card">
        <h3 className="rd-config-secao">Salvar incompleto</h3>
        <p className="rd-note">
          O produto pode ser salvo só com nome e SKU. O que a nota fiscal e os marketplaces exigem
          aparece como pendência no cadastro e em &quot;Pendências do cadastro&quot;, e só bloqueia
          na hora de enviar ao marketplace ou emitir a nota.
        </p>
      </section>
    </div>
  );
}
