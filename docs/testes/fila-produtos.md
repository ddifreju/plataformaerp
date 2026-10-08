# Fila de testes: Produtos (para os agentes de teste)

Fila de testes manuais de tudo o que mudou em Produtos (PRs #31, #33, #34 e
a junção das abas). Cada agente pega **uma tarefa por vez**, testa, anota o
resultado e passa para a próxima. O objetivo é **achar falhas**, não consertar.

## Regras para o agente (leia antes)

1. **Não mexa em código, banco, git nem publicação.** Só teste e anote.
2. **Ambiente:** use o Radar **local**, `http://localhost:3000/radar` (use
   `localhost`, não `127.0.0.1`). Se não estiver rodando, siga
   `docs/continuidade/06-como-rodar-e-publicar.md` ou peça para a Jéssica.
   O site (`https://plataformaerp.vercel.app/radar`) só para **olhar**: não
   crie, edite nem apague nada lá.
3. **Logins** (senha de todos: `demo1234`): `dono@demo.plataforma`,
   `gestor@`, `analista@`, `financeiro@`, `estoque@`, `atendimento@`,
   `marketing@` (todos `@demo.plataforma`).
4. **Nunca** digite senha de marketplace, cartão, CPF real ou qualquer dado
   pessoal de verdade. Use nomes e números inventados.
5. Os dados de exemplo (produtos `EX-…`, lojas "Exemplo · …") já estão
   carregados. Pode usar e alterar; prefira criar produtos novos com nome
   começando por `TESTE` para não bagunçar os de exemplo.
6. Teste também no tamanho de **celular (390 px)** quando a tarefa pedir
   tela.
7. **Pegar uma tarefa:** troque `[ ]` por `[em teste: SEU-NOME]`. Ao terminar:
   `[ok]` se passou inteira, ou `[falhou]` e registre em
   [`achados.md`](achados.md) com o número da tarefa.

## Como registrar uma falha

Em [`achados.md`](achados.md), um bloco por falha, com: tarefa, gravidade
(**bloqueia** / **atrapalha** / **detalhe**), onde (menu e tela), passos para
repetir, o que esperava, o que aconteceu, cargo usado e tamanho de tela.
Print quando ajudar (salve em `docs/testes/prints/`).

---

## A. Cadastro do produto

- [ ] **A1. Rascunho.** Cadastros → Produtos → "+ Novo produto". Preencha
  só o nome e o SKU (com SKU automático ligado, só o nome) e salve. Esperado: salva; aparece "Faltam N" com a lista do que
  falta; o produto aparece na lista com "Falta: …" na coluna Cadastro.
- [ ] **A2. Abas.** Abra um produto simples (ex.: `EX-CORT-VOIL`). Esperado:
  abas Dados gerais, Imagens, Fiscal, Anúncios, Conferência e SEO,
  Fornecedores, Observações e Histórico. **Não** aparece "Variações / Kit".
  Em Dados gerais ficam juntos: dados, preço e estoque, dimensões e
  embalagem. Num produto com variação (`EX-CORT-BLACK`) e no kit
  (`EX-KIT-SALA`), a aba "Variações / Kit" aparece.
- [ ] **A3. Salvar com tudo na mesma página.** Mude preço, estoque mínimo,
  peso e uma medida em Dados gerais e salve. Reabra: os valores ficaram.
  Esperado: nenhum campo some ou volta ao valor antigo.
- [ ] **A4. Lista de pendências leva ao campo.** Num produto incompleto
  (`EX-CORT-INF`), clique em cada item de "Faltam N". Esperado: abre a aba
  certa (preço, peso e medidas agora estão em Dados gerais).
- [ ] **A5. Produto com variação.** Crie `TESTE-VAR` com tipo "Com variação",
  2 tipos (Cor, Tamanho) e 4 combinações. Salve. Esperado: 4 variações com
  SKU próprio e estoque próprio; o pai não tem estoque.
- [ ] **A6. Preço promocional das variações.** No `TESTE-VAR`, dê preço
  promocional a uma variação, salve; depois mude só o nome do pai e salve.
  Esperado: o preço promocional da variação continua lá (defeito corrigido
  no #31).
- [ ] **A7. Kit.** Crie `TESTE-KIT` com 2 componentes. Esperado: custo do kit
  = soma dos custos dos componentes × quantidade; o estoque do kit vem dos
  componentes.
- [ ] **A8. Clonar.** Na lista, "⋯" de um produto → "Clonar produto".
  Esperado: abre a cópia com SKU novo, mesmas informações e imagens.
- [ ] **A9. Histórico.** Abra a aba Histórico de um produto que você editou.
  Esperado: cada mudança com data e quem fez.

## B. SKU automático e valores padrão

- [ ] **B1. Modos de SKU.** Configurações → aba cadastros → "Configurações do
  cadastro de produtos". Teste Manual, Sequencial e Com prefixo (ex.: `TST`).
  Em cada modo crie um produto novo. Esperado: Manual exige SKU; Sequencial
  gera 00001, 00002…; Prefixo gera TST-00001…
- [ ] **B2. Valores padrão.** Defina unidade, NCM e origem padrão; crie um
  produto novo. Esperado: já vem preenchido com esses valores.
- [ ] **B3. Quem pode mudar.** Entre como `gestor@` e mude a configuração:
  pode. Como `analista@` e `marketing@`: não pode (botão travado ou mensagem
  de cargo).

## C. Lixeira

- [ ] **C1. Mover e restaurar.** "⋯" → "Mover para a lixeira". Esperado:
  some da lista; "🗑 Lixeira (N)" mostra o produto; "Restaurar" devolve como
  **inativo**.
- [ ] **C2. Apagar de vez sem histórico.** Crie `TESTE-APAGAR`, mande para a
  lixeira e "Apagar de vez". Esperado: some para sempre.
- [ ] **C3. Apagar de vez com histórico.** Mande para a lixeira um produto com
  pedido ou anúncio (ex.: `EX-ALMO-VEL`) e tente "Apagar de vez". Esperado:
  recusa explicando que tem histórico; continua na lixeira. Restaure depois.
- [ ] **C4. Cargos.** `gestor@` pode apagar de vez; `analista@`, `marketing@`
  e `estoque@` não.

## D. Lista de produtos

- [ ] **D1. Coluna Cadastro.** Esperado: ✓ verde para completo; "Falta: …"
  com até 3 itens e o resto em "e mais N" (passe o mouse para ver tudo). No
  produto com variação, conta as variações incompletas.
- [ ] **D2. Coluna Anúncios.** Esperado: número de anúncios do produto (com
  as variações); clicar no número abre a aba Anúncios do produto.
- [ ] **D3. Nome abre a visualização.** Clique no nome do produto. Esperado:
  abre a página de visualização (abas dados gerais, complementares, ficha
  técnica, anúncios, variações ou kit, preços, custos, outros), com "Enviar
  para o e-commerce", "Editar" (abre o formulário na aba equivalente e volta
  para a visualização) e "Mais ações". Custos só para dono, gestor e
  financeiro.
- [ ] **D4. Filtros, busca e edição em massa** continuam funcionando (busca
  por nome/SKU, filtros combinados, filtro salvo, editar dados em massa,
  reajuste de preço, tags). Edição em massa de unidade não oferece CM nem PCT.
- [ ] **D5. Pendências do cadastro.** Na faixa "Pendências do cadastro",
  clique num grupo (ex.: "sem NCM") e complete pela grade rápida. Esperado:
  cada linha salva ao sair do campo e a coluna Cadastro atualiza.

## E. Lojas (Integrações)

- [ ] **E1. Várias lojas no mesmo marketplace.** Configurações → e-commerce →
  Integrações. Crie "TESTE ML 1" e "TESTE ML 2" no Mercado Livre. Esperado:
  as duas aparecem como "Aguardando conexão" (nunca "Conectada").
- [ ] **E2. Nome repetido.** Tente criar outra "teste ml 1" (minúsculas).
  Esperado: recusa por nome repetido.
- [ ] **E3. Renomear e remover.** Renomeie uma e remova a outra. Esperado: ao
  remover uma loja com anúncio pronto, os anúncios dela voltam a rascunho.
- [ ] **E4. Marketplaces da lista.** Esperado: só Mercado Livre, Shopee,
  TikTok Shop e AliExpress (SHEIN não aparece para loja nova).
- [ ] **E5. Cargos.** `marketing@` vê as lojas mas não cria/renomeia/remove.
- [ ] **E6. Painel.** O card "Canais da operação" mostra as lojas da empresa
  (não os 4 canais fixos).

## F. Anunciar (passo a passo)

- [ ] **F1. Abrir.** Na lista, botão "Anunciar" de um produto completo.
  Esperado: passo 1 mostra só as lojas da empresa, com "Marcar todas".
- [ ] **F2. Categoria ali mesmo.** Com um produto sem categoria: criar
  categoria no próprio passo, escolher e ligar à do marketplace (código e
  nome inventados). Esperado: avança sem sair da tela.
- [ ] **F3. Título por marketplace.** No passo 3: Mercado Livre aceita até
  60 letras; TikTok Shop de 25 a 200; AliExpress até 128; Shopee sem limite
  fixo. Fora do limite, o contador fica vermelho e "Continuar" trava.
- [ ] **F4. Preço e quantidade.** TikTok: preço de R$ 0,50 a R$ 10.000 e
  quantidade de 1 a 99.999. Mercado Livre: quantidade pelo menos 1. Preço
  abaixo do custo: bloqueado (como `dono@`, que vê o custo, o aviso aparece no
  passo 4; como `marketing@`, o servidor recusa ao salvar).
- [ ] **F5. Conferência.** Num produto incompleto, o passo 4 mostra o que
  falta e deixa completar ali (origem, NCM, código de barras ou motivo, peso,
  medidas, descrição, imagem). TikTok exige descrição com 30 palavras.
  Esperado: ao completar tudo, aparece "Tudo certo".
- [ ] **F6. Foto pequena.** Envie uma imagem pequena (ex.: 200×200 px) a um
  produto e anuncie no Mercado Livre e no TikTok. Esperado: aviso de foto
  pequena (ML 500 px no maior lado; TikTok 300 px nos dois lados).
- [ ] **F7. Salvar.** Esperado: anúncios ficam "Pronto para publicar" e
  aparecem na aba Anúncios do produto e em Cadastros → Anúncios, com o nome da
  loja.
- [ ] **F8. Vários anúncios do mesmo produto na mesma loja.** Anuncie de novo
  o mesmo produto na mesma loja. Esperado: permite, sem aviso nem trava.
- [ ] **F9. Em massa.** Marque 3 produtos → "⇪ Enviar para o e-commerce" (na
  barra e em "Mais ações"). Esperado: abre o mesmo passo a passo com os 3.
- [ ] **F10. Produto com variação.** Anunciar `EX-CORT-BLACK`. Esperado: cada
  variação vira um anúncio.
- [ ] **F11. Cargos.** `marketing@` anuncia; `estoque@` e `atendimento@` não
  veem o botão (e o servidor recusa).

## G. Aba Anúncios e Conferência

- [ ] **G1.** Aba Anúncios do produto: lista por marketplace, com título,
  loja, preço, quantidade e situação ("Pronto para publicar").
- [ ] **G2.** Aba Conferência e SEO: "Pronto para anunciar?" por marketplace
  com as regras (título, imagem, descrição) e os campos de SEO salvando.

## H. Tela e celular

- [ ] **H1.** Em 390 px: lista de produtos, cadastro (Dados gerais longo),
  passo a passo Anunciar e Integrações sem rolagem para os lados e sem botão
  cortado.
- [ ] **H2.** Em 1440 px: nada desalinhado nas mesmas telas.
