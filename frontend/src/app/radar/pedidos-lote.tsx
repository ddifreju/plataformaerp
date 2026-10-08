"use client";

// Ações em lote da lista de pedidos: caixa com "⋯" (edição rápida) em cada
// linha e barra embaixo com os totais dos marcados. Nota fiscal, frete,
// Correios e envio ao e-commerce dependem do CNPJ e das integrações: aparecem
// como "em breve" ou avisam. Pedido não se apaga (rastreabilidade): "excluir"
// cancela e devolve a reserva ao estoque.

import { useState, type ReactNode } from "react";
import { esc, imprimir } from "./produtos-lote";
import { cents, centMoney, str, useFecharFora, type Row } from "./ui";

type Props = {
  pedidos: Row[];
  linhas: Row[];
  produtos: Row[];
  marcados: string[];
  setMarcados: (f: (m: string[]) => string[]) => void;
  veFinanceiro: boolean;
  ativo: boolean;
  executar: (corpo: Record<string, unknown>) => Promise<boolean>;
  children: (k: { cabecalho: ReactNode; celula: (p: Row) => ReactNode }) => ReactNode;
};

type Acao = "SITUACAO" | "SEPARAR" | "EXPEDIR" | "MARCADORES" | "DATA" | "CONTAS" | "EXCLUIR";

const SEM_NOTA =
  "Nota fiscal pelo Radar precisa do CNPJ e do certificado digital. Entra depois do CNPJ.";
const SEM_LOJA =
  "Sincronizar com o marketplace precisa de uma loja conectada. As conexões entram depois do CNPJ, em Integrações.";
const SEM_FRETE =
  "Cotação de frete e etiquetas de transportadora/Correios entram com as integrações de logística, depois do CNPJ.";

/** Valor do pedido em centavos: preço × quantidade − desconto (quando visível). */
export function valorPedido(p: Row) {
  return cents(p.preco) * Number(p.quantidade) - cents(p.desconto ?? 0);
}

const hoje = () => new Date().toISOString().slice(0, 10);

export default function PedidosLote({
  pedidos,
  linhas,
  produtos,
  marcados,
  setMarcados,
  veFinanceiro,
  ativo,
  executar,
  children,
}: Props) {
  const [menu, setMenu] = useState(false);
  const caixaMenu = useFecharFora<HTMLDivElement>(menu, () => setMenu(false));
  const [menuLinha, setMenuLinha] = useState<{ id: string; x: number; y: number } | null>(null);
  // Esc fecha o "⋯" da linha (o clique fora já fecha pela capa).
  useFecharFora(!!menuLinha, () => setMenuLinha(null));
  const [acao, setAcao] = useState<Acao | null>(null);
  const [alvo, setAlvo] = useState<string[]>([]);
  const [aviso, setAviso] = useState("");
  const [estado, setEstado] = useState("SEPARADO");
  const [modoMarcadores, setModoMarcadores] = useState("ADICIONAR");
  const [marcadores, setMarcadores] = useState("");
  const [data, setData] = useState(hoje());

  const todos = linhas.length > 0 && linhas.every((p) => marcados.includes(str(p.id)));
  const selecionados = pedidos.filter((p) => marcados.includes(str(p.id)));
  const somaMarcados = selecionados.reduce((s, p) => s + valorPedido(p), 0);
  const somaLista = linhas.reduce((s, p) => s + valorPedido(p), 0);
  const nomeProduto = (id: unknown) => str(produtos.find((x) => x.id === id)?.nome);
  const pedidoLinha = menuLinha && pedidos.find((p) => str(p.id) === menuLinha.id);

  function fecharMenus() {
    setMenu(false);
    setMenuLinha(null);
  }

  function avisar(texto: string) {
    fecharMenus();
    setAviso(texto);
  }

  function abrir(a: Acao, ids: string[] = marcados) {
    fecharMenus();
    setAviso("");
    setAlvo(ids);
    setMarcadores("");
    setData(hoje());
    setEstado("SEPARADO");
    setAcao(a);
  }

  function imprimirPedidos(ids: string[] = marcados) {
    fecharMenus();
    const lista = pedidos.filter((p) => ids.includes(str(p.id)));
    const linhasHtml = lista
      .map(
        (p) => `<tr><td>${esc(p.numero)}</td><td>${esc(p.cliente)}</td><td>${esc(p.canal)}</td>
<td>${esc(p.quantidade)} × ${esc(nomeProduto(p.produto_id))}</td><td class="n">${esc(centMoney(valorPedido(p)))}</td>
<td>${esc(p.estado)}</td></tr>`,
      )
      .join("");
    const ok = imprimir(
      "Pedidos",
      `<h2>Pedidos</h2><p>${lista.length} pedido(s) · ${new Date().toLocaleString("pt-BR")}</p>
<table><thead><tr><th>Pedido</th><th>Cliente</th><th>Canal</th><th>Itens</th><th>Valor</th><th>Situação</th></tr></thead>
<tbody>${linhasHtml}</tbody></table>`,
      "table{width:100%;border-collapse:collapse}th,td{border-bottom:1px solid #ddd;padding:4px 6px;text-align:left}td.n{text-align:right}th{background:#f3f4f6}",
    );
    if (!ok) setAviso("O navegador bloqueou a janela de impressão. Libere pop-ups para este site.");
  }

  // Etiqueta do pedido para separação e conferência (não é etiqueta de envio).
  function imprimirEtiquetas(ids: string[] = marcados) {
    fecharMenus();
    const html = pedidos
      .filter((p) => ids.includes(str(p.id)))
      .map(
        (p) => `<div class="etq"><strong>${esc(p.numero)}</strong><span>${esc(p.cliente)}</span>
<span>${esc(p.quantidade)} × ${esc(nomeProduto(p.produto_id))}</span><small>${esc(p.canal)}</small></div>`,
      )
      .join("");
    const ok = imprimir(
      "Etiquetas de pedidos",
      `<div class="grade">${html}</div>`,
      ".grade{display:grid;grid-template-columns:repeat(3,1fr);gap:3mm}.etq{border:1px dashed #999;border-radius:2mm;padding:3mm;display:flex;flex-direction:column;gap:1mm;break-inside:avoid}strong{font-size:13pt}",
    );
    if (!ok) setAviso("O navegador bloqueou a janela de impressão. Libere pop-ups para este site.");
  }

  // Declaração de conteúdo: itens e valores vêm do pedido; endereços, peso e
  // assinatura ficam em branco para preencher (o Radar não guarda o endereço de entrega).
  function imprimirDeclaracao(ids: string[] = marcados) {
    fecharMenus();
    const html = pedidos
      .filter((p) => ids.includes(str(p.id)))
      .map(
        (p) => `<section><h3>Declaração de conteúdo · Pedido ${esc(p.numero)}</h3>
<table class="partes"><tr><td><b>Remetente</b><br>Nome: __________________________<br>Endereço: ________________________<br>CEP: ________ Cidade/UF: __________<br>CPF/CNPJ: ________________</td>
<td><b>Destinatário</b><br>Nome: ${esc(p.cliente)}<br>Endereço: ________________________<br>CEP: ________ Cidade/UF: __________<br>CPF/CNPJ: ________________</td></tr></table>
<table><thead><tr><th>Item</th><th>Conteúdo</th><th>Quant.</th><th>Valor</th></tr></thead>
<tbody><tr><td>1</td><td>${esc(nomeProduto(p.produto_id))}</td><td>${esc(p.quantidade)}</td><td>${esc(centMoney(valorPedido(p)))}</td></tr></tbody></table>
<p>Peso total (kg): ________</p>
<p class="dec">Declaro que não me enquadro no conceito de contribuinte previsto no art. 4º da Lei Complementar nº 87/1996, uma vez que não realizo, com habitualidade ou em volume que caracterize intuito comercial, operações de circulação de mercadoria, ainda que se iniciem no exterior, ou estou dispensado da emissão da nota fiscal por força da legislação tributária vigente, responsabilizando-me, nos termos da lei e a quem de direito, por informações inverídicas.</p>
<p>__________________, ____ de ______________ de ______ &nbsp;&nbsp; Assinatura: ______________________</p></section>`,
      )
      .join("");
    const ok = imprimir(
      "Declaração de conteúdo",
      html,
      "section{break-after:page;padding:4mm 0}table{width:100%;border-collapse:collapse;margin:3mm 0}th,td{border:1px solid #999;padding:4px 6px;text-align:left;vertical-align:top}table.partes td{width:50%;line-height:1.8}.dec{font-size:8.5pt}",
    );
    if (!ok) setAviso("O navegador bloqueou a janela de impressão. Libere pop-ups para este site.");
  }

  async function confirmar() {
    const corpo: Record<string, unknown> = { op: "pedidos_lote", ids: alvo };
    if (acao === "SITUACAO" || acao === "SEPARAR" || acao === "EXPEDIR") {
      const destino = acao === "SEPARAR" ? "SEPARADO" : acao === "EXPEDIR" ? "EXPEDIDO" : estado;
      corpo.acao = "ESTADO";
      corpo.estado = destino;
      if (destino === "EXPEDIDO") corpo.confirmar_simulacao = true;
      if (destino === "CANCELADO") corpo.acao = "EXCLUIR";
    } else if (acao === "MARCADORES") {
      corpo.acao = "MARCADORES";
      corpo.modo = modoMarcadores;
      corpo.marcadores = marcadores
        .split(",")
        .map((t) => t.trim())
        .filter(Boolean);
    } else if (acao === "DATA") {
      corpo.acao = "DATA_FATURAMENTO";
      corpo.data = data;
    } else if (acao === "CONTAS") {
      corpo.acao = "LANCAR_CONTAS";
      corpo.vencimento = data;
    } else corpo.acao = "EXCLUIR";
    const ok = await executar(corpo);
    setAcao(null);
    if (ok) setMarcados((m) => m.filter((id) => !alvo.includes(id)));
  }

  const item = (rotulo: ReactNode, onClick: () => void) => (
    <li>
      <button role="menuitem" onClick={onClick}>
        {rotulo}
      </button>
    </li>
  );
  const emBreve = (rotulo: string, motivo: string, sufixo = "em breve") => (
    <li>
      <button role="menuitem" disabled title={motivo}>
        {rotulo} <small>({sufixo})</small>
      </button>
    </li>
  );
  const titulos: Record<Acao, string> = {
    SITUACAO: "Alterar situação",
    SEPARAR: "Enviar para separação",
    EXPEDIR: "Enviar para expedição",
    MARCADORES: "Alterar marcadores",
    DATA: "Alterar data de faturamento",
    CONTAS: "Lançar contas a receber",
    EXCLUIR: "Excluir pedidos",
  };

  const caixa = (p: Row) => {
    const id = str(p.id);
    return (
      <span className="rd-celula-lote">
        <input
          type="checkbox"
          aria-label={`Selecionar pedido ${str(p.numero)}`}
          checked={marcados.includes(id)}
          onChange={(e) =>
            setMarcados((m) => (e.target.checked ? [...m, id] : m.filter((x) => x !== id)))
          }
        />
        <button
          type="button"
          className="rd-linha-mais"
          aria-label={`Edição rápida do pedido ${str(p.numero)}`}
          title="Edição rápida"
          aria-expanded={menuLinha?.id === id}
          onClick={(e) => {
            const r = e.currentTarget.getBoundingClientRect();
            // Perto do rodapé o menu abre para cima, para não sair da tela.
            const altura = Math.min(420, window.innerHeight * 0.6);
            const y =
              r.bottom + altura > window.innerHeight ? Math.max(8, r.top - altura) : r.bottom + 4;
            setMenu(false);
            setMenuLinha(menuLinha?.id === id ? null : { id, x: r.left, y });
          }}
        >
          ⋯
        </button>
      </span>
    );
  };
  const cabecalho = (
    <input
      type="checkbox"
      aria-label="Marcar todos da lista"
      title="Marcar todos da lista"
      checked={todos}
      onChange={(e) => setMarcados(() => (e.target.checked ? linhas.map((p) => str(p.id)) : []))}
    />
  );

  return (
    <>
      {children({
        cabecalho: ativo ? cabecalho : "",
        celula: (p) => (ativo ? caixa(p) : ""),
      })}
      {menuLinha && pedidoLinha && (
        <>
          <div className="rd-menu-fundo" onClick={() => setMenuLinha(null)} />
          <ul
            role="menu"
            className="rd-menu-linha"
            style={{ left: menuLinha.x, top: menuLinha.y }}
            aria-label={`Edição rápida do pedido ${str(pedidoLinha.numero)}`}
          >
            <li className="rd-menu-titulo">
              <span className="rd-circulo-cheio" aria-hidden="true">
                ⋯
              </span>
              Pedido {str(pedidoLinha.numero)}
            </li>
            {pedidoLinha.estado === "RESERVADO" &&
              item("⇉ Enviar para separação", () => abrir("SEPARAR", [menuLinha.id]))}
            {pedidoLinha.estado === "SEPARADO" &&
              item("🚚 Enviar para expedição", () => abrir("EXPEDIR", [menuLinha.id]))}
            {item("🖨 Imprimir pedido", () => imprimirPedidos([menuLinha.id]))}
            {item("🏷 Imprimir etiqueta", () => imprimirEtiquetas([menuLinha.id]))}
            {item("🖨 Imprimir declaração de conteúdo", () => imprimirDeclaracao([menuLinha.id]))}
            <li className="rd-menu-sep" />
            {item("🏷 Alterar marcadores", () => abrir("MARCADORES", [menuLinha.id]))}
            {item("▦ Alterar data de faturamento", () => abrir("DATA", [menuLinha.id]))}
            {veFinanceiro &&
              item("$ Lançar conta a receber", () => abrir("CONTAS", [menuLinha.id]))}
            {["RESERVADO", "SEPARADO"].includes(str(pedidoLinha.estado)) && (
              <>
                <li className="rd-menu-sep" />
                {item("🗑 Excluir (cancelar) pedido", () => abrir("EXCLUIR", [menuLinha.id]))}
              </>
            )}
          </ul>
        </>
      )}
      {aviso && (
        <div className="rd-error" role="alert">
          {aviso}
        </div>
      )}
      {ativo && marcados.length > 0 && (
        <div
          className="rd-barra-lote rd-barra-compacta"
          role="region"
          aria-label="Ações para os pedidos marcados"
        >
          <span className="rd-barra-qtd rd-pilula">
            <span aria-hidden="true">↥</span>
            {String(marcados.length).padStart(2, "0")}
            <button
              type="button"
              aria-label="Limpar seleção"
              title="Limpar seleção"
              onClick={() => setMarcados(() => [])}
            >
              ✕
            </button>
          </span>
          <button type="button" className="primary" onClick={() => avisar(SEM_NOTA)}>
            🗎 Gerar notas fiscais
          </button>
          <button type="button" onClick={() => avisar(SEM_LOJA)}>
            ↻ Sincronizar situação
          </button>
          <button type="button" onClick={() => avisar(SEM_FRETE)}>
            ▦ Cotar fretes
          </button>
          <button type="button" onClick={() => imprimirEtiquetas()}>
            🖨 Imprimir etiqueta
          </button>
          <div className="rd-mais-acoes" ref={caixaMenu}>
            <button
              type="button"
              aria-expanded={menu}
              onClick={() => {
                setMenuLinha(null);
                setMenu(!menu);
              }}
            >
              Mais ações{" "}
              <span className="rd-circulo" aria-hidden="true">
                ⋯
              </span>
            </button>
            {menu && (
              <ul role="menu" className="rd-menu-cima rd-menu-longo">
                {emBreve("🗎 Gerar notas fiscais em lote", SEM_NOTA)}
                {emBreve("▣ Gerar notas consumidor (NFC-e)", SEM_NOTA)}
                {emBreve("↻ Sincronizar situação com e-commerce", SEM_LOJA)}
                <li className="rd-menu-sep" />
                {emBreve("▦ Cotar fretes", SEM_FRETE)}
                {emBreve("🖨 Imprimir etiqueta com um clique", SEM_FRETE)}
                {item("⇉ Enviar para separação", () => abrir("SEPARAR"))}
                {item("🚚 Enviar para expedição", () => abrir("EXPEDIR"))}
                {emBreve("⇪ Enviar para Correios Log+", SEM_FRETE)}
                {emBreve("⇄ Consultar pedidos Correios Log+", SEM_FRETE)}
                <li className="rd-menu-sep" />
                {item("🖨 Imprimir pedidos", () => imprimirPedidos())}
                {item("🏷 Imprimir etiquetas", () => imprimirEtiquetas())}
                {emBreve("🖨 Imprimir etiquetas dos Correios", SEM_FRETE)}
                {emBreve("🖨 Imprimir etiquetas de transportadora", SEM_FRETE)}
                {item("🖨 Imprimir declaração de conteúdo", () => imprimirDeclaracao())}
                {emBreve("🖨 Imprimir DACE", SEM_FRETE)}
                <li className="rd-menu-sep" />
                {veFinanceiro
                  ? item("$ Lançar contas", () => abrir("CONTAS"))
                  : emBreve("$ Lançar contas", "Seu cargo não lança contas.", "sem permissão")}
                {emBreve(
                  "▣ Lançar estoque",
                  "O estoque já é reservado ao criar o pedido e baixado na expedição.",
                  "automático",
                )}
                <li className="rd-menu-sep" />
                {item("🏷 Alterar marcadores", () => abrir("MARCADORES"))}
                {item("⤨ Alterar situação", () => abrir("SITUACAO"))}
                {item("▦ Alterar data de faturamento", () => abrir("DATA"))}
                {emBreve("⇪ Exportar contatos para o SIGEP", SEM_FRETE)}
                {emBreve("▥ Enviar dados da NF-e para e-commerce", SEM_NOTA)}
                {emBreve(
                  "⇪ Enviar pedidos para outras empresas",
                  "Quando a conta tiver mais de uma empresa",
                )}
                <li className="rd-menu-sep" />
                {item("🗑 Excluir pedidos", () => abrir("EXCLUIR"))}
              </ul>
            )}
          </div>
          <div className="rd-barra-totais">
            <span className="rd-barra-total rd-barra-destaque">
              <strong>
                {(somaMarcados / 100).toLocaleString("pt-BR", { minimumFractionDigits: 2 })}
              </strong>
              <small>selecionados (R$)</small>
            </span>
            <span className="rd-barra-total">
              <strong>{String(marcados.length).padStart(2, "0")}</strong>
              <small>quantidade</small>
            </span>
            <span className="rd-barra-total">
              <strong>
                {(somaLista / 100).toLocaleString("pt-BR", { minimumFractionDigits: 2 })}
              </strong>
              <small>valor total (R$)</small>
            </span>
            <button
              type="button"
              className="rd-barra-topo"
              aria-label="Voltar ao topo da página"
              title="Voltar ao topo da página"
              onClick={() => window.scrollTo({ top: 0, behavior: "smooth" })}
            >
              ↑
            </button>
          </div>
        </div>
      )}
      {acao && (
        <div className="rd-modal-backdrop" onClick={() => setAcao(null)}>
          <section
            className="rd-modal"
            role="dialog"
            aria-modal="true"
            aria-label={titulos[acao]}
            onClick={(e) => e.stopPropagation()}
          >
            <div className="rd-card-head">
              <h2>{titulos[acao]}</h2>
              <button aria-label="Fechar" onClick={() => setAcao(null)}>
                ×
              </button>
            </div>
            <p className="rd-note">
              {alvo.length === 1
                ? `Pedido ${str(pedidos.find((p) => str(p.id) === alvo[0])?.numero)}.`
                : `${alvo.length} pedidos marcados.`}
            </p>
            {acao === "SITUACAO" && (
              <div className="rd-form-grid">
                <label className="wide">
                  Nova situação
                  <select value={estado} onChange={(e) => setEstado(e.target.value)}>
                    <option value="SEPARADO">Separado (vale para pedidos reservados)</option>
                    <option value="EXPEDIDO">
                      Expedido, simulação local (vale para pedidos separados)
                    </option>
                    <option value="CANCELADO">Cancelado (devolve a reserva ao estoque)</option>
                  </select>
                </label>
              </div>
            )}
            {(acao === "EXPEDIR" || (acao === "SITUACAO" && estado === "EXPEDIDO")) && (
              <p>
                Simulação local: baixa o estoque, mas não emite NF-e nem contrata frete. Só os
                pedidos já separados mudam; os outros ficam como estão.
              </p>
            )}
            {acao === "SEPARAR" && (
              <p>Os pedidos reservados vão para separação. Os outros ficam como estão.</p>
            )}
            {acao === "MARCADORES" && (
              <div className="rd-form-grid">
                <label>
                  O que fazer
                  <select
                    value={modoMarcadores}
                    onChange={(e) => setModoMarcadores(e.target.value)}
                  >
                    <option value="ADICIONAR">Adicionar estes marcadores</option>
                    <option value="REMOVER">Remover estes marcadores</option>
                    <option value="SUBSTITUIR">Trocar todos por estes</option>
                  </select>
                </label>
                <label className="wide">
                  Marcadores
                  <input
                    placeholder="ex.: urgente, presente, retirar na loja"
                    value={marcadores}
                    onChange={(e) => setMarcadores(e.target.value)}
                  />
                  <small className="rd-dica">Separe por vírgula.</small>
                </label>
              </div>
            )}
            {(acao === "DATA" || acao === "CONTAS") && (
              <div className="rd-form-grid">
                <label>
                  {acao === "DATA" ? "Data de faturamento" : "Vencimento"}
                  <input type="date" value={data} onChange={(e) => setData(e.target.value)} />
                  <small className="rd-dica">
                    {acao === "DATA"
                      ? "Em branco limpa a data. É só um registro: o Radar ainda não emite NF-e."
                      : "Cria uma conta a receber por pedido, no valor do pedido (bruto menos desconto). Pedido que já tem conta, cancelado ou devolvido fica de fora."}
                  </small>
                </label>
              </div>
            )}
            {acao === "EXCLUIR" && (
              <p>
                Pedido não é apagado, para manter o histórico. Os pedidos reservados ou separados
                são cancelados e a reserva volta para o estoque. Pedido expedido fica como está: use
                &quot;Devolver&quot;.
              </p>
            )}
            <div className="rd-modal-foot">
              <button onClick={() => setAcao(null)}>Cancelar</button>
              <button
                className={
                  acao === "EXCLUIR" || (acao === "SITUACAO" && estado === "CANCELADO")
                    ? "perigo"
                    : "primary"
                }
                onClick={confirmar}
              >
                {acao === "EXCLUIR" ? "Excluir (cancelar)" : "Confirmar"}
              </button>
            </div>
          </section>
        </div>
      )}
    </>
  );
}
