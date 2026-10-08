# Próximos passos (em ordem)

## Agora: esperando a Jéssica
1. **Testar o "Anunciar" por loja** (decisão 0036): lojas em Integrações,
   passo a passo com conferência e coluna "Cadastro". Bloco 1 ela já tinha
   validado.
2. **Respondido:** dono e gestor apagam produto de vez e mudam o SKU (como
   está). Ela quer que dono/gestor possam **liberar permissões para outras
   pessoas**; proposta enviada (tela de permissões por pessoa em
   Configurações → Usuários, depois do Bloco 2), esperando: quando fazer,
   criar o cargo "Administrativo" e se o gestor libera o que ele não tem.
3. **Dados fictícios em todas as áreas** para ela testar (anúncios, vendas,
   atendimento, promoções, compras, estoque, financeiro; NF-e só se der para
   simular sem enganar). Pedido dela de 07/10/2026.
4. **Relatórios:** ela prefere uma área única de relatórios em que o cliente
   cruza qualquer coisa (financeiro, preço, compras, estoque, atendimento).
   Proposta enviada; esperando o ok dela.

## Depois: Produtos, Bloco 2 (variações e kits)
Regras dela (anotadas no documento de referência que ela mandou):
- **Tipo de variação aprendido:** se a pessoa digitar "Amperagem" no
  cadastro, o tipo entra sozinho na lista de Configurações → variações e
  fica disponível para todos os produtos.
- **Transformações, sempre com confirmação** que lista o que vai acontecer,
  por exemplo: "Este produto tem 6 variações (…) e 3 anúncios. Transformar
  em simples exclui as variações e desvincula os anúncios. Confirmar?"
  - variação → simples;
  - simples → variação, num pai novo ou num pai existente;
  - variação → produto independente.
- **Editar uma variação sozinha.** Hoje é bloqueado. Fazer com "regras de
  herança": o pai escolhe quais campos repassa e a variação pode ter
  valores próprios nos outros.
- **Kit:**
  - custo recalculado quando o custo de um componente muda;
  - preço sugerido igual à soma dos preços dos componentes menos um
    desconto em %;
  - **kit com variações**, em que cada variação é um kit;
  - **enviar o kit como kit** aos marketplaces que aceitam (fica para quando
    houver integração; deixar a estrutura pronta).

## Produtos, Bloco 3 (organização)
- Cadastro de marcas com **detector de repetidas** ("Nike" / "nike" /
  "NIKE ") e unificação com um clique.
- Unificar produtos duplicados, mantendo o histórico.
- Localização no estoque (corredor, prateleira).
- Sugestão de NCM pela tabela oficial e de CEST a partir do NCM (a chave
  liga e desliga em Configurações → cadastros); CEST em lote por NCM.

## Produtos, Bloco 4 (planilha)
- Importar todos os campos, com variações pelo SKU do pai, kits e imagens
  por link.
- **Prévia antes de confirmar e botão de desfazer.** No ERP de referência a
  importação não tem volta.
- Exportar, editar no Excel e subir de volta.

## Fabricado, matéria-prima, lote e validade (um pacote só)
- Tipos "fabricado" e "matéria-prima", com a estrutura de produção: quanto
  de cada insumo vai em cada produto.
- Custo vindo da matéria-prima; entra na calculadora e no painel de quem
  produz.
- Lote e validade na entrada (compra/XML), na saída (vence primeiro, sai
  primeiro) e na NF-e.
- Público: artesanato, impressão 3D, cosméticos próprios.

## Depois de Produtos
- **Página por página**, cada uma junto com as suas configurações: clientes
  e fornecedores, suprimentos, vendas e pedidos, finanças. A Jéssica manda
  os textos e prints de cada uma.
- Lote e filtros em Atendimento e Promoções, quando ela mandar os desenhos.
- **Grupo de empresas, fase 1:** um login para várias empresas
  (`usuario_tenant`, troca explícita e auditada). Ver decisão 0035; precisa
  de revisão de segurança caprichada.
- **Depois do CNPJ:** NF-e e integrações reais (Mercado Livre primeiro,
  depois Shopee, TikTok Shop, SHEIN e Bling). Ligar
  `RadarProdutos.pendencias()` para bloquear envio e nota de produto
  incompleto.
- **Tributação RTC (reforma):** pesquisar as regras vigentes com cuidado
  antes de construir. Não inventar alíquota.

## Pequenas melhorias oferecidas e ainda sem resposta
- Redirecionar `/` e `/login` para `/radar`.
- Juntar as consultas do backend (a tela leva de 7 a 9 s porque o servidor
  está nos EUA e o banco em SP).
- Sessões derrubadas ao trocar a senha; a própria pessoa poder trocar a
  senha ("Alterar dados do usuário").
