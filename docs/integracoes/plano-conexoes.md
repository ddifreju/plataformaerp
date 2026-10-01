# Plano de conexões com marketplaces

Situação em 01/10/2026: **em pausa, esperando o CNPJ.**

A empresa está sendo aberta no Simples Nacional (não é MEI). Previsão da
Juliana: CNPJ pronto até o fim da semana de 05 a 09/10/2026. Junto com ele vêm o
domínio e a emissão de notas fiscais. Enquanto isso, nada de pedir acesso às
APIs.

## O que já está pronto no Radar

- Página de Anúncios com painel por loja e link para a central do vendedor.
- Importação de anúncios (`RadarAnuncios.importar`): acha o produto por SKU ou
  GTIN ou cria um produto incompleto já vinculado. Reimportar atualiza.
- Falta: página de Integrações (onde o cliente conecta a loja) e os conectores.

## Primeira lista (prioridade da Juliana)

1. Mercado Livre
2. Shopee
3. TikTok Shop
4. Shopify
5. SHEIN

Antes de fechar a ordem: pesquisa do que o mercado mais procura hoje em ERP
para e-commerce, para confirmar ou ajustar a lista.

## Papelada provável

Cada portal tem as próprias regras e elas mudam. Confirmar uma a uma, no portal
de desenvolvedores de cada marketplace, antes de enviar o pedido. Itens que
costumam ser pedidos para aprovar um sistema integrador:

- [ ] CNPJ ativo e cartão CNPJ
- [ ] Contrato social e dados do responsável legal
- [ ] Domínio próprio com site no ar (página da empresa e do produto)
- [ ] E-mail no domínio da empresa (não Gmail/Hotmail)
- [ ] Política de privacidade e termos de uso publicados no site (LGPD)
- [ ] Descrição do sistema: o que faz, quais dados acessa e por quê
- [ ] Endereço de retorno (redirect) do Radar para a autorização da loja
- [ ] Conta de vendedor ativa no marketplace, para testar (a loja pessoal da
      Juliana serve)

## Próximos passos quando o CNPJ sair

1. Juliana avisa.
2. Pesquisa de mercado e ordem final das integrações.
3. Para cada marketplace: levantar no portal os requisitos atuais, montar a
   papelada e revisar antes de enviar, para evitar rejeição.
4. Começar pelo Mercado Livre: criar a aplicação, conector e página de
   Integrações, testar com a loja pessoal.
