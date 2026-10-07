"use client";

// Configurações do sistema: busca no topo, abas por área e a lista de itens
// de cada aba. Nesta primeira etapa só a estrutura existe; cada item ganha a
// sua tela quando for detalhado com a Juliana (um por vez).

import { useState } from "react";
import { normal } from "./produtos-filtros";
import { Badge } from "./ui";

type Item = { id: string; titulo: string; descricao: string; grupo?: string; chaves?: string };

const ABAS: { id: string; rotulo: string; novo?: boolean }[] = [
  { id: "geral", rotulo: "geral" },
  { id: "cadastros", rotulo: "cadastros" },
  { id: "suprimentos", rotulo: "suprimentos" },
  { id: "vendas", rotulo: "vendas" },
  { id: "notas", rotulo: "notas fiscais" },
  { id: "financas", rotulo: "finanças" },
  { id: "ecommerce", rotulo: "e-commerce" },
  { id: "tributacao", rotulo: "tributação (RTC)", novo: true },
];

const ITENS: Record<string, Item[]> = {
  geral: [
    {
      id: "empresa",
      titulo: "Alterar dados da empresa",
      descricao: "Razão social, nome fantasia, CNPJ, inscrições, endereço, logo e contato.",
      chaves: "cnpj razao social logo endereco inscricao estadual",
    },
    {
      id: "usuario",
      titulo: "Alterar dados do usuário",
      descricao: "Seu nome, e-mail, senha e preferências pessoais.",
      chaves: "senha perfil minha conta",
    },
    {
      id: "usuarios",
      titulo: "Cadastro de usuários do sistema",
      descricao: "Quem acessa o Radar, com qual cargo e o que cada um pode ver e fazer.",
      chaves: "permissoes acesso cargo equipe login",
    },
    {
      id: "email",
      titulo: "Configurações do servidor de e-mail",
      descricao: "Endereço de envio dos e-mails do sistema (notas, pedidos, avisos).",
      chaves: "smtp email remetente",
    },
    {
      id: "documentos",
      titulo: "Configurações do envio de documentos",
      descricao: "Para quem vão nota fiscal (XML e DANFE), boletos e pedidos, e quando.",
      chaves: "xml danfe contador boleto envio automatico",
    },
    {
      id: "etiquetas",
      titulo: "Configurações das etiquetas",
      descricao: "Modelos e tamanhos das etiquetas de produto, pedido e endereço.",
      chaves: "etiqueta impressao zebra codigo de barras",
    },
    {
      id: "agenda",
      titulo: "Configurações da agenda",
      descricao: "Tarefas, lembretes e compromissos da equipe.",
      chaves: "tarefas lembrete calendario",
    },
    {
      id: "interface",
      titulo: "Interface do usuário",
      descricao: "Tema, cores, tamanho da letra e telas iniciais.",
      grupo: "Outras configurações",
      chaves: "tema cor aparencia",
    },
    {
      id: "notificacoes",
      titulo: "Central de notificações",
      descricao:
        "Quais avisos você recebe e por onde: estoque baixo, pedidos, anúncios com problema.",
      grupo: "Outras configurações",
      chaves: "avisos alerta notificacao",
    },
    {
      id: "printnode",
      titulo: "Impressão PrintNode",
      descricao: "Imprimir etiquetas e documentos direto na impressora, sem abrir janela.",
      grupo: "Outras configurações",
      chaves: "impressora impressao direta",
    },
    {
      id: "multiempresa",
      titulo: "Multiempresa",
      descricao: "Mais de uma empresa (CNPJ) na mesma conta, com troca rápida entre elas.",
      grupo: "Outras configurações",
      chaves: "filial cnpj empresas",
    },
    {
      id: "token",
      titulo: "Token API",
      descricao: "Chaves de acesso para outros sistemas conversarem com o Radar.",
      grupo: "Outras configurações",
      chaves: "api chave integracao token",
    },
    {
      id: "api",
      titulo: "Configurações de API",
      descricao: "Limites, permissões e avisos automáticos (webhooks) da API.",
      grupo: "Outras configurações",
      chaves: "webhook api integracao",
    },
  ],
};

export default function Configuracoes() {
  const [aba, setAba] = useState("geral");
  const [busca, setBusca] = useState("");
  const [aberto, setAberto] = useState<Item | null>(null);

  const termo = normal(busca.trim());
  const achados = termo
    ? Object.entries(ITENS).flatMap(([idAba, itens]) =>
        itens
          .filter((i) => normal(`${i.titulo} ${i.descricao} ${i.chaves ?? ""}`).includes(termo))
          .map((i) => ({ ...i, aba: idAba })),
      )
    : [];
  const itens = ITENS[aba] ?? [];
  const grupos = [...new Set(itens.map((i) => i.grupo ?? ""))];
  const rotuloAba = (id: string) => ABAS.find((a) => a.id === id)?.rotulo ?? id;

  const linha = (i: Item, idAba?: string) => (
    <li key={`${idAba ?? aba}-${i.id}`}>
      <button type="button" className="rd-config-item" onClick={() => setAberto(i)}>
        <span>
          <strong>{i.titulo}</strong>
          <small>{i.descricao}</small>
        </span>
        {idAba && <Badge>{rotuloAba(idAba)}</Badge>}
        <span aria-hidden="true">›</span>
      </button>
    </li>
  );

  return (
    <section className="rd-card rd-config">
      <div className="rd-busca-produtos rd-config-busca">
        <input
          aria-label="Buscar configuração"
          placeholder="Busque pela funcionalidade ou dúvida"
          value={busca}
          onChange={(e) => setBusca(e.target.value)}
        />
        <span className="rd-busca-lupa" aria-hidden="true">
          ⌕
        </span>
      </div>

      {termo ? (
        <>
          <p className="rd-note">
            {achados.length
              ? `${achados.length} configuração(ões) para "${busca.trim()}"`
              : `Nada encontrado para "${busca.trim()}".`}
          </p>
          <ul className="rd-config-lista">{achados.map((i) => linha(i, i.aba))}</ul>
        </>
      ) : (
        <>
          <div className="rd-tabs rd-config-abas" role="tablist" aria-label="Áreas de configuração">
            {ABAS.map((a) => (
              <button
                key={a.id}
                role="tab"
                aria-selected={aba === a.id}
                className={aba === a.id ? "active" : ""}
                onClick={() => setAba(a.id)}
              >
                {a.rotulo}
                {a.novo && <span className="rd-novo">Novo</span>}
              </button>
            ))}
          </div>
          {itens.length === 0 ? (
            <p className="rd-note rd-config-vazio">
              Esta área vai ser montada nas próximas etapas, depois da aba geral.
            </p>
          ) : (
            grupos.map((g) => (
              <div key={g || "principal"}>
                {g && <h3 className="rd-config-grupo">{g}</h3>}
                <ul className="rd-config-lista">
                  {itens.filter((i) => (i.grupo ?? "") === g).map((i) => linha(i))}
                </ul>
              </div>
            ))
          )}
        </>
      )}

      {aberto && (
        <div className="rd-modal-backdrop" onClick={() => setAberto(null)}>
          <section
            className="rd-modal"
            role="dialog"
            aria-modal="true"
            aria-label={aberto.titulo}
            onClick={(e) => e.stopPropagation()}
          >
            <div className="rd-card-head">
              <h2>{aberto.titulo}</h2>
              <button aria-label="Fechar" onClick={() => setAberto(null)}>
                ×
              </button>
            </div>
            <p>{aberto.descricao}</p>
            <p className="rd-note">
              Esta configuração ainda está sendo desenhada. Ela entra no ar assim que for detalhada.
            </p>
            <div className="rd-modal-foot">
              <button className="primary" onClick={() => setAberto(null)}>
                Entendi
              </button>
            </div>
          </section>
        </div>
      )}
    </section>
  );
}
