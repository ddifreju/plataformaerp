# O que é o Radar

## A visão, nas palavras da Juliana

> "Nós não somos só um SaaS, não somos só um ERP. A gente quer ser um
> funcionário, um agilizador, um assistente."

O Radar quer **substituir trabalho de gente**:
- dar o número real em tempo real (lucro de verdade, não faturamento bruto);
- automatizar o que é repetitivo, para o lojista ficar à frente da
  concorrência;
- deixar o cliente conectar tudo uma vez só e já ter tudo pronto, sempre com
  a opção de configurar.

Ela repete muito: "quanto mais completo melhor", "inteligente, automatizado,
fácil de operar, que diminui trabalho e retrabalho".

**Público:** e-commerce brasileiro que vende online em marketplaces
(Mercado Livre, Shopee, TikTok Shop, SHEIN). **Nada de loja física:** sem
PDV e sem NFC-e, decisão dela de 07/10/2026.

**Diferenciais já combinados:**
- **Grupo de empresas** (decisão 0035): quem tem várias empresas de
  e-commerce, cada uma com sua assinatura, enxerga o grupo inteiro (lucro,
  estoque, valor) e cada empresa separada.
- **Fabricação própria:** artesanato, impressão 3D, cosméticos. O custo vem
  da matéria-prima e entra na calculadora e no painel. Vai junto com lote e
  validade.
- **Camada de IA** sobre os dados da operação (decisão 0030).
- **Rastreabilidade:** todo número pode ser provado.

## Nomes

- **Radar:** nome do produto em uso na interface (`/radar`). O nome
  definitivo da plataforma ainda está "a definir" no `CLAUDE.md`; os
  estudos estão em `docs/marca/`.
- **Navega:** aparece no nome do serviço do backend no Render
  (`radar-api-navega`) e é como a Juliana se refere ao negócio. Ela não
  detalhou nesta sessão a relação exata entre Navega e Radar; **pergunte se
  precisar**, não suponha.

## O que o Radar já faz (07/10/2026)

**Menu:** Painel, Cadastros, Vendas, Suprimentos, Finanças, Marketing,
Mercado, Radar AI. No rodapé ficam Configurações e Como usar.

### Cadastros

- **Produtos**
  - **Tipos:** simples, com variação (pai e filhas) e kit.
  - **Cadastro em 10 abas:** dados gerais, preço e estoque, dimensões e
    embalagem, variações/kit, imagens, fiscal, anúncios e SEO, fornecedores,
    observações e histórico.
  - **Rascunho:** salva só com nome e SKU; o que falta aparece como
    pendência.
  - **SKU automático:** manual, sequencial ou com prefixo.
  - **Lixeira:** restaurar, ou apagar de vez se o produto não tiver
    histórico.
  - **Clonar** e **aba Histórico**.
  - **Lista:** busca, filtros combináveis com sugestões, filtros salvos,
    edição em massa, reajuste de preço, tags e etiquetas.
  - **Pendências do cadastro** com preenchimento rápido.
- **Clientes e fornecedores:** um cadastro só de contatos, com tipos. O
  cliente é reconhecido pelo CPF/CNPJ, mascarado na lista. Tem lote e
  filtros.
- **Vendedores:** abas, comissão, senha de acesso, lixeira (soft delete) e
  lote.
- **Categorias** (com vínculo a cada marketplace) e **Embalagens** (com
  sugestões).

### Vendas

- **Anúncios:** painel por loja, anúncio sempre vinculado a um produto, lote,
  filtros e "Ver na central".
- **Pedidos:** lote completo (situação, marcadores, data de faturamento,
  lançar contas) e o filtro "mais completo", que busca por número do
  marketplace, número do Radar, NF, CEP, cliente e mais.

### Outras áreas

- **Suprimentos e Finanças:** estoque, compras com custo médio, contas e
  conciliação local.
- **Marketing:** promoções. **Relatórios:** incluem a curva ABC.
- **Configurações:** 8 abas (geral, cadastros, suprimentos, vendas, notas
  fiscais, finanças, e-commerce, tributação RTC).
  - Busca em todas as abas.
  - Cada item tem um selo: "já funciona", "sugestão do Radar", "depende do
    CNPJ" ou "não configurado".
  - **Já funcionam:** dados da empresa, usuários do sistema, cadastro de
    produtos, categorias, embalagens, importar, auditoria e integrações.
  - Os outros itens abrem um aviso de "em desenho".
- **Auditoria:** cada comando gravado, com quem fez.

### O que ainda não é real

- **Envio para marketplace e NF-e:** dependem do CNPJ e das credenciais.
  Hoje o anúncio é um rascunho local.
- **Integrações:** tela e plano prontos (`docs/integracoes/`), conexão real
  não.
- **IA:** há uma camada heurística de perguntas e sugestões por regra. A IA
  de verdade está planejada (decisões 0030 e 0031).
