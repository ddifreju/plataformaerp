# Histórico do que foi feito

Todos os PRs estão em https://github.com/ddifreju/plataformaerp/pulls?q=is%3Apr.
A ordem é cronológica, de setembro a outubro de 2026. Antes disso vieram as
fases 0 a 3 (agosto), descritas em `docs/ESTADO.md`.

## Base e menu
- Menu reorganizado em grupos; área de Inteligência de Mercado.
- Cadastros de clientes, fornecedores, categorias e embalagens; promoções e
  relatórios.
- [PR #1](https://github.com/ddifreju/plataformaerp/pull/1): libera o git
  push para o Claude.
- [#2](https://github.com/ddifreju/plataformaerp/pull/2): o login espera o
  servidor acordar no plano gratuito.

## Cadastros completos
- [#3](https://github.com/ddifreju/plataformaerp/pull/3): cadastro de
  produto completo (abas, variações, kit, imagens, fiscal). Migration V020.
- [#4](https://github.com/ddifreju/plataformaerp/pull/4): cadastro de
  cliente completo; cliente criado a partir do pedido (V021).
- [#5](https://github.com/ddifreju/plataformaerp/pull/5): cliente
  reconhecido **só por CPF/CNPJ**; relatórios com ids internos (V022).
- [#6](https://github.com/ddifreju/plataformaerp/pull/6): página de
  anúncios refeita, com painel por loja, vínculo obrigatório com produto e
  importação (V023).
- [#7](https://github.com/ddifreju/plataformaerp/pull/7): conexões pausadas
  até o CNPJ; plano de integrações.
- [#8](https://github.com/ddifreju/plataformaerp/pull/8): categorias
  vinculadas aos marketplaces; embalagens sugeridas com tags (V024).
- [#9](https://github.com/ddifreju/plataformaerp/pull/9): acorda o servidor
  assim que o login abre (espera até 7 minutos).
- [#10](https://github.com/ddifreju/plataformaerp/pull/10): vendedores;
  fornecedores passam a fazer parte do cadastro de contatos (V025).
- [#11](https://github.com/ddifreju/plataformaerp/pull/11): o produto passa
  a exigir o que a NF-e e os marketplaces pedem. **Afrouxado no #31: agora é
  rascunho.**

## Edição em lote (layout dos prints dela)
- [#12](https://github.com/ddifreju/plataformaerp/pull/12): lote em
  clientes e fornecedores.
- [#13](https://github.com/ddifreju/plataformaerp/pull/13) e
  [#14](https://github.com/ddifreju/plataformaerp/pull/14): lote e
  pendências em produtos. Tem "⋯" por linha, contador em pílula e envio ao
  e-commerce (V026).
- [#15](https://github.com/ddifreju/plataformaerp/pull/15): lote nos
  anúncios de cada loja (V027). A operação se chama `anuncios_acao_lote`; o
  nome `anuncios_lote` já existia ("Preparar 4 canais").
- [#16](https://github.com/ddifreju/plataformaerp/pull/16): vendedores com
  abas, lixeira (soft delete, V028), comissão e senha de acesso.
- [#17](https://github.com/ddifreju/plataformaerp/pull/17): lote em
  pedidos. Muda situação, marcadores, data de faturamento e lança contas a
  receber (V029).

## Filtros inteligentes (componente genérico `filtros-genericos.tsx`)
- [#18](https://github.com/ddifreju/plataformaerp/pull/18): produtos.
- [#19](https://github.com/ddifreju/plataformaerp/pull/19): anúncios, com
  os atalhos "Ver na central" e "abrir produto".
- [#22](https://github.com/ddifreju/plataformaerp/pull/22): clientes,
  fornecedores e vendedores.
- [#23](https://github.com/ddifreju/plataformaerp/pull/23): categorias e
  embalagens.
- [#24](https://github.com/ddifreju/plataformaerp/pull/24): pedidos, o
  filtro "mais completo". Busca por número do marketplace, número do Radar,
  NF, CEP e cliente (V030).

## Hospedagem
- [#20](https://github.com/ddifreju/plataformaerp/pull/20): pacote de
  instalação na Oracle Cloud. O cadastro foi recusado e o pacote não foi
  usado.
- [#21](https://github.com/ddifreju/plataformaerp/pull/21): GitHub Action
  que mantém o backend acordado (reserva).
- [#25](https://github.com/ddifreju/plataformaerp/pull/25): documenta o
  despertador no Supabase (pg_cron, o principal).
- O frontend foi para a Vercel, configurado direto no painel.

## Configurações
- [#26](https://github.com/ddifreju/plataformaerp/pull/26): página de
  Configurações, aba geral.
- [#27](https://github.com/ddifreju/plataformaerp/pull/27): as 8 abas, com
  os itens dos prints dela, sugestões de automação ("sugestão do Radar"),
  selos honestos e busca global. Integrações e Auditoria passam para dentro
  de Configurações.
- [#28](https://github.com/ddifreju/plataformaerp/pull/28): saem PDV e
  NFC-e (nada de loja física); entram "Grupo de empresas" e "API e
  parcerias". **Decisão 0035** (grupo de empresas em 3 fases).
- [#29](https://github.com/ddifreju/plataformaerp/pull/29): tira o selo
  "Novo" da aba tributação, a pedido dela.
- [#30](https://github.com/ddifreju/plataformaerp/pull/30): **dados da
  empresa** (V031 `radar_empresa`, busca por CEP, logo) e **usuários do
  sistema** (criar, editar, desativar, trocar senha). O nome fantasia
  aparece no menu.

## Produtos: defeitos e Bloco 1
- [#31](https://github.com/ddifreju/plataformaerp/pull/31):
  - **Defeitos corrigidos:**
    - a lista parava em 1.000 produtos;
    - a edição em massa oferecia as unidades CM e PCT, que o servidor
      recusa;
    - salvar o pai apagava o preço promocional das variações.
  - **Bloco 1:**
    - rascunho;
    - SKU automático e valores padrão (V032 `radar_configuracao`);
    - lixeira (V032 `radar_produto.excluido_em`);
    - clonar;
    - aba Histórico.

## Sessão local (a partir de 07/10/2026)
- [#33](https://github.com/ddifreju/plataformaerp/pull/33): anunciar por loja
  (lojas do cliente em Integrações, passo a passo com conferência das regras
  oficiais, coluna Cadastro e Anúncios, aba Anúncios no produto). V033,
  decisão 0036. Marketplaces: Mercado Livre, Shopee, TikTok Shop, AliExpress.
- #34: dados de exemplo na empresa de demonstração (decisão 0037).

## Decisões de negócio tomadas pela Jéssica nesta sessão
- Nada de loja física (sem PDV e sem NFC-e).
- Grupo de empresas é um diferencial; vale começar pela arquitetura
  (decisão 0035).
- API aberta para parcerias.
- Fabricação própria (artesanato, impressão 3D) e matéria-prima entram,
  **junto com lote e validade**.
- O produto pode ser salvo incompleto; o bloqueio fica no envio ao
  marketplace e na nota.
- Configurações andam junto com cada página: tópico por tópico (Produtos,
  depois os outros), cada um com as suas configurações.
- Estoque do grupo, por regra fiscal: cada CNPJ tem o seu estoque e a
  transferência entre empresas é com nota.
- Edição em lote e filtros em **todas** as páginas, no padrão dos prints
  dela.

## Pendências abertas com ela
- **O gestor pode apagar produto de vez e mudar a configuração de SKU?**
  Hoje pode. Perguntado e sem resposta ainda.
- Redirecionar `/` e `/login` para `/radar`: oferecido, sem resposta.
- Juntar as consultas do backend para a tela carregar mais rápido:
  oferecido, sem resposta.
- Ela vai mandar os textos e prints das próximas páginas (Atendimento,
  Promoções e as outras) quando chegar a vez de cada uma.
