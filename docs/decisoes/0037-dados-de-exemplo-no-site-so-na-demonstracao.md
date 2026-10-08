# 0037 — Dados de exemplo no site, só na empresa de demonstração

**Data:** 7 de outubro de 2026
**Status:** aceita (ajusta a 0028)
**Reversibilidade:** [REVERSÍVEL]

## Contexto

A 0028 deixou os dados de demonstração fora do sistema publicado
(`infra/dados-demo.sql`, só banco local), com medo de dado fictício chegar a
uma empresa de cliente. Desde então a empresa de demonstração (`demo-loja`)
passou a existir no site, e é nela que a Jéssica testa cada entrega. Ela pediu
dados fictícios em todas as áreas (anúncios, vendas, atendimento, promoções,
compras, estoque, financeiro) para testar, sem depender de alguém rodar SQL.

## Decisão

Um botão no Painel, **"Carregar dados de exemplo"**, ligado à operação
`dados_exemplo` (`RadarExemplo`). As travas preservam a intenção da 0028:

1. **Só na empresa de demonstração**: o servidor confere o `slug` da empresa
   (`demo-…`, o mesmo critério do `dados-demo.sql`). Em qualquer outra, recusa.
   Teste: `RadarExemploTest.empresaDeClienteNaoRecebeDadosInventados`.
2. **Só em demonstração vazia**: o passo 1 recusa empresa que já tenha pedido
   ou lançamento (operação de verdade). Protege contra uma empresa de cliente
   criada por engano com slug `demo-`. **Regra de provisionamento:** nunca
   criar empresa de cliente com slug começando por `demo-`.
3. **Só o dono** carrega.
4. **Uma vez só**, em passos numerados: o progresso fica em
   `radar_configuracao` (chave `exemplo`), um passo nunca roda duas vezes nem
   fora de ordem, e se a página fechar o botão vira "Continuar".

Como os dados são feitos:

- **Pelas mesmas operações da tela** (o `executar` do `RadarService`): estoque,
  custo médio, lançamentos e contas ficam coerentes. Cada passo é um comando
  (uma transação, uma linha na auditoria).
- **Passos curtos** (uns 50, de poucos segundos): o servidor gratuito fica nos
  EUA e o banco em São Paulo (cerca de 0,2 s por consulta).
- **Sem documento inventado**: clientes e fornecedores entram como cadastro
  incompleto, sem CPF/CNPJ, porque um documento válido inventado pode ser de
  alguém de verdade.
- **Sem integração fingida** (regra 5): lojas "aguardando conexão", anúncios
  prontos e não publicados, **nenhuma nota fiscal**.
- **Pedidos espalhados por 90 dias**: só a data do pedido é ajustada.
  Lançamentos e movimentos de estoque ficam com a data do registro, porque essas
  tabelas não aceitam alteração (são a trilha da regra 3).
- Nomes de lojas, clientes, fornecedores e promoções levam "exemplo"; os
  produtos têm SKU `EX-`. Links de concorrente usam domínio reservado para
  exemplo (`.example`), que não pertence a ninguém.

## Consequências

- `infra/dados-demo.sql` continua valendo para o banco local (fases 0 a 3).
- Apagar os dados de exemplo não existe ainda; se for preciso, entra depois.
