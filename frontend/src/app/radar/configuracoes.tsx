"use client";

// Configurações do sistema: busca no topo, abas por área e os itens de cada
// aba, do desenho da Juliana mais as sugestões marcadas como "sugestão do
// Radar". Itens que já funcionam levam para a tela que existe; os demais
// ganham a sua tela quando forem detalhados (um por vez).

import { useState } from "react";
import { normal } from "./produtos-filtros";
import { Badge } from "./ui";

type Selo = "funciona" | "sugestao" | "cnpj" | "pendente";

type Item = {
  id: string;
  titulo: string;
  descricao: string;
  grupo?: string;
  chaves?: string;
  selo?: Selo;
  /** Página do Radar que já faz isso: o item leva para lá. */
  pagina?: string;
};

const SELOS: Record<Selo, [string, string]> = {
  funciona: ["já funciona", "green"],
  sugestao: ["sugestão do Radar", "purple"],
  cnpj: ["depende do CNPJ", "amber"],
  pendente: ["não configurado", "gray"],
};

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

const AVISOS: Record<string, { titulo: string; texto: string }> = {
  notas: {
    titulo: "Configurar notas fiscais ficou mais prático",
    texto:
      "Reunimos em um só lugar tudo o que você precisa para configurar a emissão das suas notas. A emissão começa depois do CNPJ e do certificado digital.",
  },
  tributacao: {
    titulo: "Configure sua empresa para a Reforma Tributária do Consumo (RTC)",
    texto:
      "A reforma muda os impostos sobre consumo em etapas a partir de 2026 (CBS e IBS no lugar de PIS, Cofins, ICMS e ISS). Vamos montar esta área com guias passo a passo e as regras em vigor.",
  },
};

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
      id: "importar",
      titulo: "Importar e exportar dados",
      descricao:
        "Planilhas em massa: importar catálogo hoje; em breve, editar no Excel e subir de volta.",
      chaves: "planilha excel csv importacao exportacao",
      selo: "funciona",
      pagina: "importar",
    },
    {
      id: "lgpd",
      titulo: "Segurança, privacidade e LGPD",
      descricao:
        "Sessões abertas, encerrar acessos, dados pessoais dos clientes e pedidos de exclusão.",
      chaves: "seguranca privacidade lgpd sessao",
      selo: "sugestao",
    },
    {
      id: "auditoria",
      titulo: "Auditoria",
      descricao: "Quem fez o quê e quando: todo o histórico de alterações.",
      chaves: "historico log alteracoes",
      selo: "funciona",
      pagina: "auditoria",
    },
    {
      id: "automacoes",
      titulo: "Automações",
      descricao:
        'Regras do tipo "quando acontecer, faça": pedido pago → emitir nota → imprimir etiqueta → avisar o cliente.',
      grupo: "Assistente do Radar",
      chaves: "automacao regra gatilho fluxo automatico",
      selo: "sugestao",
    },
    {
      id: "autonomia",
      titulo: "O que o assistente pode fazer sozinho",
      descricao: "Para cada tarefa, escolha: só sugerir, fazer com sua aprovação ou fazer sozinho.",
      grupo: "Assistente do Radar",
      chaves: "ia radar ai autonomia aprovacao",
      selo: "sugestao",
    },
    {
      id: "rotinas",
      titulo: "Resumos e relatórios automáticos",
      descricao:
        "Resumo do dia e da semana no e-mail ou WhatsApp: vendas, margem, estoque e o que precisa de atenção.",
      grupo: "Assistente do Radar",
      chaves: "resumo diario relatorio whatsapp email",
      selo: "sugestao",
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
      id: "grupo_empresas",
      titulo: "Empresas do grupo",
      descricao:
        "Junte as contas das suas empresas (cada uma com o seu CNPJ e a sua assinatura) em um grupo só.",
      grupo: "Grupo de empresas",
      chaves: "multiempresa filial cnpj empresas grupo holding vincular",
      selo: "sugestao",
    },
    {
      id: "grupo_troca",
      titulo: "Trocar de empresa sem sair",
      descricao: "Um login só para entrar em qualquer empresa do grupo, com troca em um clique.",
      grupo: "Grupo de empresas",
      chaves: "multiempresa trocar empresa login unico",
      selo: "sugestao",
    },
    {
      id: "grupo_painel",
      titulo: "Painel do grupo",
      descricao:
        "Faturamento, lucro, estoque e valor de todas as empresas juntas, e de cada uma separada.",
      grupo: "Grupo de empresas",
      chaves: "multiempresa dashboard consolidado lucro faturamento grupo",
      selo: "sugestao",
    },
    {
      id: "grupo_estoque",
      titulo: "Estoque do grupo",
      descricao:
        "Ver o estoque de todas as empresas em um lugar e transferir produto de uma para outra (com a nota de transferência).",
      grupo: "Grupo de empresas",
      chaves: "multiempresa estoque compartilhado transferencia unificado",
      selo: "sugestao",
    },
    {
      id: "token",
      titulo: "Token API",
      descricao: "Chaves de acesso para lojas, sistemas e parceiros conversarem com o Radar.",
      grupo: "API e parcerias",
      chaves: "api chave integracao token",
    },
    {
      id: "api",
      titulo: "Configurações de API",
      descricao: "Limites, permissões e avisos automáticos (webhooks) da API.",
      grupo: "API e parcerias",
      chaves: "webhook api integracao",
    },
    {
      id: "parceiros",
      titulo: "Apps e parceiros",
      descricao:
        "Aplicativos de parceiros que se conectam ao Radar: você escolhe quais podem entrar e o que podem ver.",
      grupo: "API e parcerias",
      chaves: "parceria app aplicativo loja de apps marketplace de apps",
      selo: "sugestao",
    },
  ],
  cadastros: [
    {
      id: "clientes",
      titulo: "Configurações do cadastro de clientes",
      descricao:
        "Campos obrigatórios, como reconhecer o cliente (CPF/CNPJ) e preenchimento automático pelo CEP.",
      chaves: "cliente cadastro campos",
    },
    {
      id: "produtos",
      titulo: "Configurações do cadastro de produtos",
      descricao: "Campos obrigatórios para nota e marketplaces, SKU automático e valores padrão.",
      chaves: "produto cadastro sku obrigatorio",
    },
    {
      id: "variacoes",
      titulo: "Configurações de variações de produtos",
      descricao: "Tipos de variação (cor, tamanho, voltagem) e como gerar o SKU de cada uma.",
      chaves: "variacao cor tamanho grade",
    },
    {
      id: "atributos",
      titulo: "Configurações de atributos de produtos",
      descricao:
        "Características pedidas pelos marketplaces (material, composição, gênero) para preencher uma vez só.",
      chaves: "atributo ficha tecnica marketplace",
    },
    {
      id: "marcas",
      titulo: "Configurações de marcas de produtos",
      descricao: "Lista de marcas da loja, para não repetir nem escrever diferente.",
      chaves: "marca fabricante",
    },
    {
      id: "medidas",
      titulo: "Tabelas de medidas",
      descricao: "Tabelas de tamanho (P, M, G, numeração) para anúncios de moda e calçados.",
      chaves: "tabela de medidas tamanho moda",
    },
    {
      id: "tags",
      titulo: "Configurações das tags",
      descricao: "Tags para organizar produtos e clientes e usar nos filtros.",
      chaves: "tags etiquetas organizar",
    },
    {
      id: "tipos_contato",
      titulo: "Tipos de contato",
      descricao: "Cliente, fornecedor, transportador e outros tipos que a empresa usar.",
      chaves: "contato fornecedor transportador",
    },
    {
      id: "linhas",
      titulo: "Linhas de produto",
      descricao:
        "Agrupar produtos em linhas ou coleções (verão, premium) para relatórios e preços.",
      chaves: "linha colecao",
    },
    {
      id: "listas_preco",
      titulo: "Listas de preços",
      descricao: "Preços diferentes por tipo de cliente (atacado, varejo) ou por canal.",
      chaves: "lista de preco atacado varejo",
    },
    {
      id: "categorias",
      titulo: "Categorias e vínculo com os marketplaces",
      descricao: "Categorias da loja ligadas à categoria certa de cada marketplace.",
      chaves: "categoria marketplace vinculo",
      selo: "funciona",
      pagina: "categorias",
    },
    {
      id: "embalagens",
      titulo: "Embalagens",
      descricao: "Caixas e envelopes com medida, peso e custo, para o frete e o preço.",
      chaves: "embalagem caixa envelope",
      selo: "funciona",
      pagina: "embalagens",
    },
    {
      id: "ncm",
      titulo: "Sugestão automática de NCM e dados fiscais",
      descricao:
        "Ao cadastrar um produto, o Radar sugere o NCM pela tabela oficial; você confirma.",
      chaves: "ncm fiscal sugestao automatica",
      selo: "sugestao",
    },
    {
      id: "enriquecer",
      titulo: "Preenchimento automático de cadastro",
      descricao:
        "Completar título, descrição e atributos a partir do código de barras ou de um anúncio existente.",
      chaves: "gtin ean codigo de barras descricao automatica",
      selo: "sugestao",
    },
  ],
  suprimentos: [
    {
      id: "depositos",
      titulo: "Depósitos de estoque",
      descricao: "Locais de estoque (loja, galpão, full do marketplace) e qual vende primeiro.",
      chaves: "deposito galpao full",
    },
    {
      id: "estoque",
      titulo: "Configurações de estoque",
      descricao: "Quando reservar e baixar, estoque negativo, mínimo e máximo padrão.",
      chaves: "estoque reserva baixa minimo",
    },
    {
      id: "documentos_compra",
      titulo: "Configurações do envio de documentos",
      descricao: "Envio da ordem de compra ao fornecedor por e-mail.",
      chaves: "ordem de compra email fornecedor",
    },
    {
      id: "marcadores_oc",
      titulo: "Configurações dos marcadores nas ordens de compra",
      descricao: "Marcadores para organizar as compras (urgente, aguardando, parcial).",
      chaves: "marcadores compra",
    },
    {
      id: "ordens",
      titulo: "Configurações de ordens de compra",
      descricao: "Numeração, aprovação e o que acontece ao receber a mercadoria.",
      chaves: "ordem de compra aprovacao",
    },
    {
      id: "conferencia",
      titulo: "Configurações de conferência de compra",
      descricao: "Conferir a mercadoria recebida por código de barras antes de dar entrada.",
      chaves: "conferencia recebimento codigo de barras",
    },
    {
      id: "reposicao",
      titulo: "Reposição automática",
      descricao:
        "O Radar calcula quanto comprar de cada produto pelas vendas e pelo prazo do fornecedor.",
      chaves: "reposicao compra automatica previsao",
      selo: "sugestao",
    },
    {
      id: "alertas_estoque",
      titulo: "Alertas de estoque",
      descricao:
        "Avisar antes de acabar, produto parado há muito tempo e estoque divergente entre canais.",
      chaves: "alerta ruptura parado",
      selo: "sugestao",
    },
  ],
  vendas: [
    {
      id: "propostas",
      titulo: "Configurações das propostas comerciais",
      descricao: "Modelo, validade e aprovação das propostas e orçamentos.",
      chaves: "proposta orcamento",
    },
    {
      id: "pedidos",
      titulo: "Configurações dos pedidos de venda",
      descricao: "Numeração, campos padrão e o que acontece em cada situação do pedido.",
      chaves: "pedido venda numeracao",
    },
    {
      id: "documentos_venda",
      titulo: "Configurações do envio de documentos",
      descricao: "Envio do pedido e da nota ao cliente por e-mail ou WhatsApp.",
      chaves: "envio pedido cliente email",
    },
    {
      id: "marcadores_venda",
      titulo: "Configurações dos marcadores nas vendas",
      descricao: "Marcadores dos pedidos (urgente, presente, retirar na loja).",
      chaves: "marcadores pedidos",
    },
    {
      id: "marcadores_propostas",
      titulo: "Configurações dos marcadores nas propostas comerciais",
      descricao: "Marcadores das propostas.",
      chaves: "marcadores propostas",
    },
    {
      id: "marcadores_devolucoes",
      titulo: "Configurações dos marcadores nas devoluções de vendas",
      descricao: "Marcadores das devoluções.",
      chaves: "marcadores devolucao",
    },
    {
      id: "devolucoes",
      titulo: "Configurações das devoluções de vendas",
      descricao: "Motivos, prazos e o que fazer com o produto devolvido (estoque ou perda).",
      chaves: "devolucao troca",
    },
    {
      id: "fluxo_pedido",
      titulo: "Fluxo automático do pedido",
      descricao:
        "Pedido pago segue sozinho: separar, emitir nota, gerar etiqueta e avisar o cliente.",
      chaves: "fluxo automatico pedido pago",
      selo: "sugestao",
    },
    {
      id: "formas_envio",
      titulo: "Formas de envio",
      descricao:
        "Correios, transportadoras e envios dos marketplaces (Mercado Envios, Shopee Xpress).",
      grupo: "Expedição e Logística",
      chaves: "frete envio correios transportadora",
    },
    {
      id: "gateways_log",
      titulo: "Gateways logísticos",
      descricao: "Plataformas de cotação e etiqueta (Melhor Envio, Frenet, Kangu e outras).",
      grupo: "Expedição e Logística",
      chaves: "melhor envio frenet cotacao",
    },
    {
      id: "expedicao",
      titulo: "Configurações da expedição",
      descricao: "Coleta, romaneio e conferência antes de despachar.",
      grupo: "Expedição e Logística",
      chaves: "expedicao romaneio coleta",
    },
    {
      id: "separacao",
      titulo: "Configurações da separação",
      descricao: "Lista de separação, ordem pelos corredores e conferência por bipe.",
      grupo: "Expedição e Logística",
      chaves: "separacao picking bipe",
    },
    {
      id: "marcadores_separacao",
      titulo: "Configurações dos marcadores na separação",
      descricao: "Marcadores usados na separação.",
      grupo: "Expedição e Logística",
      chaves: "marcadores separacao",
    },
    {
      id: "intelipost",
      titulo: "Configurações da Intelipost",
      descricao: "Integração com a Intelipost para gestão de fretes e transportadoras.",
      grupo: "Expedição e Logística",
      chaves: "intelipost frete",
    },
    {
      id: "rastreio",
      titulo: "Rastreio e aviso de entrega",
      descricao: "Acompanhar a entrega e avisar o cliente a cada etapa, sozinho.",
      grupo: "Expedição e Logística",
      chaves: "rastreio entrega aviso",
      selo: "sugestao",
    },
    {
      id: "crm",
      titulo: "Configurações do CRM",
      descricao: "Como os clientes entram no CRM e quem cuida de cada um.",
      grupo: "CRM",
      chaves: "crm relacionamento",
    },
    {
      id: "marcadores_crm",
      titulo: "Configurações dos marcadores no CRM",
      descricao: "Marcadores dos clientes no CRM.",
      grupo: "CRM",
      chaves: "marcadores crm",
    },
    {
      id: "funil",
      titulo: "Configurações dos estágios no funil do CRM",
      descricao: "Etapas do funil (novo, em contato, negociação, cliente).",
      grupo: "CRM",
      chaves: "funil estagio vendas",
    },
    {
      id: "mensagens",
      titulo: "Mensagens automáticas ao cliente",
      descricao:
        "Pós-venda, pedido de avaliação e reativação de quem parou de comprar, por WhatsApp ou e-mail.",
      grupo: "CRM",
      chaves: "whatsapp mensagem automatica pos venda avaliacao",
      selo: "sugestao",
    },
  ],
  notas: [
    {
      id: "nf_empresa",
      titulo: "Dados da empresa",
      descricao: "Os dados que vão na nota: CNPJ, inscrição estadual, regime e endereço.",
      grupo: "Configurações gerais de notas fiscais",
      chaves: "nota empresa cnpj regime",
      selo: "cnpj",
    },
    {
      id: "certificado",
      titulo: "Configuração do certificado digital",
      descricao: "Certificado A1 da empresa, que assina as notas. Avisamos antes de vencer.",
      grupo: "Configurações gerais de notas fiscais",
      chaves: "certificado digital a1",
      selo: "cnpj",
    },
    {
      id: "ambiente",
      titulo: "Ambiente das notas fiscais",
      descricao: "Homologação (testes, sem valor fiscal) ou produção (notas de verdade).",
      grupo: "Configurações gerais de notas fiscais",
      chaves: "homologacao producao ambiente",
      selo: "pendente",
    },
    {
      id: "nat_entrada",
      titulo: "Naturezas de operação de entrada (tributação)",
      descricao: "Compra, devolução de venda, retorno: como cada entrada é tributada.",
      grupo: "Configurações gerais de notas fiscais",
      chaves: "natureza operacao entrada cfop",
    },
    {
      id: "nat_saida",
      titulo: "Naturezas de operação de saída (tributação)",
      descricao: "Venda, devolução de compra, remessa: CFOP e impostos de cada saída.",
      grupo: "Configurações gerais de notas fiscais",
      chaves: "natureza operacao saida cfop",
    },
    {
      id: "nfe",
      titulo: "Configuração da nota fiscal eletrônica (NF-e)",
      descricao: "Série, numeração e textos padrão da NF-e.",
      grupo: "Notas fiscais de venda",
      chaves: "nfe serie numeracao",
      selo: "cnpj",
    },
    {
      id: "difal",
      titulo: "ICMS DIFAL para não contribuinte",
      descricao: "Diferença de ICMS em vendas para pessoa física de outro estado.",
      grupo: "Notas fiscais de venda",
      chaves: "difal icms interestadual",
    },
    {
      id: "difal_st",
      titulo: "Cálculo diferenciado de ST para consumidor contribuinte - DIFAL",
      descricao: "Substituição tributária e DIFAL em vendas para empresas contribuintes.",
      grupo: "Notas fiscais de venda",
      chaves: "st substituicao tributaria difal",
    },
    {
      id: "intermediadores",
      titulo: "Cadastro de intermediadores",
      descricao: "CNPJ dos marketplaces que vai na nota quando a venda é feita por eles.",
      grupo: "Notas fiscais de venda",
      chaves: "intermediador marketplace cnpj",
    },
    {
      id: "marcadores_nf_saida",
      titulo: "Configurações dos marcadores nas notas fiscais de saída",
      descricao: "Marcadores das notas de saída.",
      grupo: "Notas fiscais de venda",
      chaves: "marcadores nota saida",
    },
    {
      id: "emissao_auto",
      titulo: "Emissão automática de notas",
      descricao:
        "Emitir a nota sozinho quando o pedido for pago ou separado, e mandar ao marketplace.",
      grupo: "Notas fiscais de venda",
      chaves: "emissao automatica nota",
      selo: "sugestao",
    },
    {
      id: "nf_entrada",
      titulo: "Configurações de notas fiscais de entrada",
      descricao: "Como dar entrada nas notas de compra no estoque e no financeiro.",
      grupo: "Notas fiscais de entrada",
      chaves: "nota entrada compra",
    },
    {
      id: "marcadores_nf_entrada",
      titulo: "Configurações dos marcadores nas notas fiscais de entrada",
      descricao: "Marcadores das notas de entrada.",
      grupo: "Notas fiscais de entrada",
      chaves: "marcadores nota entrada",
    },
    {
      id: "manifesto",
      titulo: "Notas recebidas automaticamente",
      descricao:
        "Buscar sozinho as notas emitidas contra o seu CNPJ (manifestação do destinatário) e dar entrada pelo XML.",
      grupo: "Notas fiscais de entrada",
      chaves: "manifestacao destinatario xml compra",
      selo: "sugestao",
    },
    {
      id: "nfse",
      titulo: "Configuração da nota fiscal de serviço (NFS-e)",
      descricao: "Para quem também presta serviço: município, código do serviço e alíquota.",
      grupo: "Notas fiscais de serviço",
      chaves: "nfse servico iss",
      selo: "sugestao",
    },
    {
      id: "marcadores_nfse",
      titulo: "Configurações dos marcadores nas notas de serviço",
      descricao: "Marcadores das notas de serviço.",
      grupo: "Notas fiscais de serviço",
      chaves: "marcadores servico",
    },
    {
      id: "contador",
      titulo: "Envio automático para o contador",
      descricao: "Todo mês, os XMLs e o resumo das notas vão sozinhos para o contador.",
      grupo: "Contabilidade",
      chaves: "contador xml mensal contabilidade",
      selo: "sugestao",
    },
  ],
  financas: [
    {
      id: "fin_gerais",
      titulo: "Configurações gerais",
      descricao: "Regime de caixa ou competência, moeda e fechamento do mês.",
      grupo: "Geral",
      chaves: "financeiro caixa competencia",
    },
    {
      id: "categorias_fin",
      titulo: "Categorias de receita e despesa",
      descricao: "Plano de contas: de onde vem e para onde vai o dinheiro (base do DRE).",
      grupo: "Geral",
      chaves: "plano de contas categoria dre",
    },
    {
      id: "custos_fixos",
      titulo: "Custos fixos e impostos para a margem",
      descricao: "Aluguel, salários e alíquota do Simples, para a margem real de cada venda.",
      grupo: "Geral",
      chaves: "custo fixo simples aliquota margem",
      selo: "sugestao",
    },
    {
      id: "bancos",
      titulo: "Cadastro de contas bancárias",
      descricao: "Contas da empresa nos bancos.",
      grupo: "Contas e caixa",
      chaves: "banco conta corrente",
    },
    {
      id: "contas_fin",
      titulo: "Cadastro de contas financeiras",
      descricao: "Caixa, carteiras dos marketplaces e outras contas onde o dinheiro fica.",
      grupo: "Contas e caixa",
      chaves: "caixa carteira conta",
    },
    {
      id: "open_finance",
      titulo: "Extrato automático do banco (Open Finance)",
      descricao: "Conectar o banco para o extrato entrar sozinho e conciliar com as contas.",
      grupo: "Contas e caixa",
      chaves: "open finance extrato conciliacao bancaria",
      selo: "sugestao",
    },
    {
      id: "recebimento",
      titulo: "Formas de recebimento",
      descricao: "Pix, boleto, cartão, repasse do marketplace, com prazos e taxas.",
      grupo: "Recebimentos e pagamentos",
      chaves: "pix boleto cartao recebimento",
    },
    {
      id: "pagamento",
      titulo: "Formas de pagamento",
      descricao: "Como a empresa paga fornecedores e despesas.",
      grupo: "Recebimentos e pagamentos",
      chaves: "pagamento fornecedor",
    },
    {
      id: "gateways",
      titulo: "Cadastro de gateways",
      descricao: "Gateways de pagamento (Mercado Pago, PagSeguro, Stripe e outros).",
      grupo: "Recebimentos e pagamentos",
      chaves: "gateway pagamento",
    },
    {
      id: "maquininhas",
      titulo: "Configurações das maquininhas de cartão",
      descricao: "Taxas e prazos das maquininhas, para saber quanto cai de verdade.",
      grupo: "Recebimentos e pagamentos",
      chaves: "maquininha cartao taxa",
    },
    {
      id: "repasses",
      titulo: "Conciliação automática dos repasses",
      descricao:
        "Confere o que cada marketplace pagou com as vendas, taxa por taxa, e aponta diferenças.",
      grupo: "Recebimentos e pagamentos",
      chaves: "repasse marketplace conciliacao taxa",
      selo: "sugestao",
    },
    {
      id: "pagar",
      titulo: "Configurações do contas a pagar",
      descricao: "Avisos de vencimento e aprovação de pagamentos.",
      grupo: "Avisos e e-mails",
      chaves: "contas a pagar vencimento",
    },
    {
      id: "receber",
      titulo: "Configurações do contas a receber",
      descricao: "Avisos de vencimento e baixa automática.",
      grupo: "Avisos e e-mails",
      chaves: "contas a receber",
    },
    {
      id: "documentos_fin",
      titulo: "Configurações do envio de documentos",
      descricao: "Envio de boletos e recibos aos clientes.",
      grupo: "Avisos e e-mails",
      chaves: "boleto recibo envio",
    },
    {
      id: "cobranca",
      titulo: "Régua de cobrança",
      descricao: "Lembretes automáticos antes e depois do vencimento, por e-mail e WhatsApp.",
      grupo: "Avisos e e-mails",
      chaves: "cobranca lembrete inadimplencia",
      selo: "sugestao",
    },
    {
      id: "marcadores_fin",
      titulo: "Configurações dos marcadores",
      descricao: "Marcadores das contas a pagar e a receber.",
      grupo: "Marcadores",
      chaves: "marcadores financeiro",
    },
  ],
  ecommerce: [
    {
      id: "ec_gerais",
      titulo: "Configurações gerais",
      descricao: "Como pedidos, estoque e preços conversam com as lojas.",
      chaves: "ecommerce loja geral",
    },
    {
      id: "integracoes",
      titulo: "Integrações",
      descricao:
        "Conectar marketplaces e lojas virtuais (Mercado Livre, Shopee, TikTok Shop, SHEIN...).",
      chaves: "integracao marketplace conectar loja",
      selo: "funciona",
      pagina: "integracoes",
    },
    {
      id: "preco_canal",
      titulo: "Regras de preço por canal",
      descricao:
        "Preço de cada marketplace calculado sozinho a partir do custo, das taxas e da margem desejada.",
      chaves: "preco canal markup taxa marketplace",
      selo: "sugestao",
    },
    {
      id: "estoque_canal",
      titulo: "Sincronização de estoque",
      descricao:
        "Estoque atualizado em todas as lojas a cada venda, com estoque de segurança por canal.",
      chaves: "estoque sincronizacao seguranca",
      selo: "sugestao",
    },
    {
      id: "monitor_anuncios",
      titulo: "Monitor de anúncios",
      descricao:
        "Avisar quando um anúncio for pausado, rejeitado ou perder posição para o concorrente.",
      chaves: "anuncio monitor concorrente pausado",
      selo: "sugestao",
    },
  ],
  tributacao: [
    {
      id: "rtc_tributos",
      titulo: "Configuração dos tributos e códigos de classificação",
      descricao: "Classificação tributária de cada produto para CBS e IBS.",
      grupo: "NF-e",
      chaves: "cbs ibs classificacao tributaria reforma",
      selo: "pendente",
    },
    {
      id: "rtc_calculo",
      titulo: "Habilitar cálculo de tributos da Reforma Tributária",
      descricao: "Calcular e destacar CBS e IBS nas notas, conforme a fase da reforma.",
      grupo: "NF-e",
      chaves: "cbs ibs calculo reforma",
      selo: "pendente",
    },
    {
      id: "rtc_regras",
      titulo: "Cadastro de regras tributárias",
      descricao: "Regras por produto, cliente e estado, para cada nota sair com o imposto certo.",
      grupo: "NF-e",
      chaves: "regra tributaria",
      selo: "pendente",
    },
    {
      id: "rtc_nfse_tributos",
      titulo: "Configuração dos tributos e códigos de classificação",
      descricao: "Classificação dos serviços para a reforma.",
      grupo: "NFS-e",
      chaves: "nfse reforma classificacao",
      selo: "pendente",
    },
    {
      id: "rtc_nfse_calculo",
      titulo: "Habilitar cálculo de tributos da Reforma Tributária",
      descricao: "Calcular CBS e IBS nas notas de serviço.",
      grupo: "NFS-e",
      chaves: "nfse cbs ibs",
      selo: "pendente",
    },
    {
      id: "rtc_simulador",
      titulo: "Simulador do impacto da reforma",
      descricao: "Quanto a reforma muda o preço e a margem de cada produto, ano a ano.",
      grupo: "Planejamento",
      chaves: "simulador impacto reforma margem preco",
      selo: "sugestao",
    },
  ],
};

export default function Configuracoes({ go }: { go: (pagina: string) => void }) {
  const [aba, setAba] = useState("geral");
  const [busca, setBusca] = useState("");
  const [aberto, setAberto] = useState<Item | null>(null);
  const [avisosFechados, setAvisosFechados] = useState<string[]>([]);

  const termo = normal(busca.trim());
  const achados = termo
    ? Object.entries(ITENS).flatMap(([idAba, itens]) =>
        itens
          .filter((i) =>
            normal(`${i.titulo} ${i.descricao} ${i.grupo ?? ""} ${i.chaves ?? ""}`).includes(termo),
          )
          .map((i) => ({ ...i, aba: idAba })),
      )
    : [];
  const itens = ITENS[aba] ?? [];
  const grupos = [...new Set(itens.map((i) => i.grupo ?? ""))];
  const rotuloAba = (id: string) => ABAS.find((a) => a.id === id)?.rotulo ?? id;
  const aviso = !termo && !avisosFechados.includes(aba) ? AVISOS[aba] : undefined;

  const linha = (i: Item, idAba?: string) => (
    <li key={`${idAba ?? aba}-${i.id}`}>
      <button
        type="button"
        className="rd-config-item"
        onClick={() => (i.pagina ? go(i.pagina) : setAberto(i))}
      >
        <span>
          <strong>
            {i.titulo}
            {i.selo && (
              <>
                {" "}
                <Badge tone={SELOS[i.selo][1]}>{SELOS[i.selo][0]}</Badge>
              </>
            )}
          </strong>
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
          {aviso && (
            <div className="rd-config-aviso" role="note">
              <span aria-hidden="true">💬</span>
              <div>
                <strong>{aviso.titulo}</strong>
                <p>{aviso.texto}</p>
              </div>
              <button
                type="button"
                aria-label="Esconder aviso"
                title="Esconder aviso"
                onClick={() => setAvisosFechados([...avisosFechados, aba])}
              >
                ✕
              </button>
            </div>
          )}
          {grupos.map((g) => (
            <div key={g || "principal"}>
              {g && <h3 className="rd-config-grupo">{g}</h3>}
              <ul className="rd-config-lista">
                {itens.filter((i) => (i.grupo ?? "") === g).map((i) => linha(i))}
              </ul>
            </div>
          ))}
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
