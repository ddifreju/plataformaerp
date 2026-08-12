# 0002 — Modelo de dados agnóstico à fonte

**Data:** agosto de 2026
**Status:** aceita

## Contexto

Os dados operacionais de um lojista estão espalhados: pedido no ERP,
conversa no WhatsApp, devolução parte no marketplace parte no e-mail,
frete reverso em outro sistema.

A fundadora tem reserva quanto a depender de ferramentas específicas
(Bling, Tray) que considera limitadas.

## Decisão

O modelo de dados interno é canônico e agnóstico à fonte. Cada fonte externa
entra por um adaptador que traduz para o modelo interno.

Uma devolução do Mercado Livre, do Bling e da loja própria viram o mesmo
objeto canônico, preservando em campo de extensão o que é específico de cada.

## Justificativa

Resolve a tensão entre precisar do dado (que está nessas ferramentas) e não
querer depender delas. Integrar não é endossar — é ler onde o dado está.

Trocar de fonte passa a ser trocar um adaptador, não reescrever o produto.
E abre caminho para, no futuro, substituir a fonte em vez de integrá-la.

## Consequências

- Este é o principal moat técnico do produto
- Custo maior na ingestão, ganho permanente na consulta
- Toda fonte nova precisa de adaptador, não de mudança no núcleo
