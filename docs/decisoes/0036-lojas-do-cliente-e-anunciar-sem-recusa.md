# 0036 — Lojas do cliente e anunciar sem recusa

**Data:** 7 de outubro de 2026
**Status:** aceita
**Reversibilidade:** [REVERSÍVEL] (V033 tem undo)

## Contexto

O botão "Preparar 4 canais" criava rascunho de anúncio em quatro marketplaces
fixos, iguais para todo cliente. A Jéssica pediu:

- os canais vêm das **lojas que o cliente conectou**: se ele tem 3, aparecem 3;
  se tem 10, aparecem 10. Pode ter **várias lojas no mesmo marketplace**, cada
  uma com o nome que ele escolher ("ML Loja X", "ML Loja Y");
- marketplaces de agora: Mercado Livre, Shopee, TikTok Shop e AliExpress. A
  SHEIN sai;
- anunciar é um passo a passo: escolher as lojas, ligar a categoria ali mesmo,
  ajustar título, preço e quantidade por loja e **conferir as regras de cada
  marketplace antes**, porque anúncio recusado depois de "enviar" é o que ela
  mais quer evitar;
- a quantidade do anúncio é livre (decisão dela): não é limitada pelo estoque.

## Decisão

1. **`radar_loja`, tabela própria do Radar**, e não a `canal` das fases 0 a 3.
   A `canal` carrega o escopo declarado da margem (decisão 0033), espelhos e
   taxas; misturar lojas cadastradas à mão nela mudaria as somas de margem.
   Quando a conexão real existir, a loja ganha `conectada_em` e o vínculo com
   o `canal` correspondente.
2. **Loja sem conexão fica "aguardando conexão"** (regra 5): nunca aparece como
   conectada antes da autorização do marketplace.
3. **Anúncio ganha `loja_id`, `estoque` e o estado `PRONTO`**: passou na
   conferência e espera a loja ser conectada para subir.
4. **Uma lista só de marketplaces e regras**: `RadarAnuncios.CANAIS`/`REGRAS`
   no servidor (que trava) e `frontend/src/app/radar/canais.ts` na tela (que
   confere antes, para corrigir na hora). As regras vêm só de fonte oficial
   (`docs/integracoes/regras-de-anuncio.md`); sem número oficial, não há
   limite inventado.
5. **O cadastro completo é exigido para anunciar**: `RadarProdutos.pendencias()`
   trava o anúncio, como combinado (o produto salva incompleto; o bloqueio fica
   no envio e na nota).

## Anunciar sem recusa: o que entra com a conexão real

Pedido explícito da Jéssica: quando o cliente clicar em subir, o anúncio tem de
subir. Parte das regras só o marketplace informa, e só com a loja conectada.
Ao construir cada integração, é obrigatório:

- ler os limites **por categoria e por loja** em tempo real (Mercado Livre:
  `max_title_length`, `max_pictures_per_item`, `minimum_price`, atributos
  `required` e `conditional_required`; Shopee: `get_item_limit`; TikTok: regras
  da categoria) e levá-los para a conferência do passo a passo;
- mostrar a lista de categorias do marketplace para o cliente só escolher;
- **testar o anúncio antes de enviar** onde o marketplace oferece validação
  (Mercado Livre: `POST /items/validate`);
- traduzir cada erro devolvido pelo marketplace em instrução, com o campo para
  corrigir ali mesmo.

## Consequências

- O "Enviar para o e-commerce" da edição em massa abre o mesmo passo a passo.
- Anúncios antigos (sem loja, inclusive SHEIN) continuam aparecendo; só não se
  cria anúncio novo na SHEIN.
- **Sem limite de anúncios por produto e loja** (decisão da Jéssica): o
  lojista pode ter vários anúncios do mesmo produto na mesma loja, para testar
  título, estratégia de ads ou ter mais catálogo. Nem o banco nem a tela
  impedem; o passo a passo só avisa que já existe anúncio ali.
