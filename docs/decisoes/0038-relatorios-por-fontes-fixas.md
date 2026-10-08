# 0038 — Relatórios montados a partir de fontes fixas

**Data:** 8 de outubro de 2026
**Status:** aceita
**Reversibilidade:** [REVERSÍVEL]

## Contexto

A Jéssica quis uma área única de Relatórios em que a cliente monta o relatório que
quiser, cruzando áreas (financeiro, preço, compras, estoque, atendimento), com
relatórios prontos por área e um atalho "Relatórios" no fim de cada menu.

A tela recebe no máximo os 1.000 registros mais recentes de cada lista. Montar
relatório só com isso esconderia números quando a loja crescer (regra 5).

## Decisão

1. **Fontes fixas no servidor** (`RadarFontes`): pedidos, produtos e estoque,
   movimentos, compras, contas, lançamentos, anúncios e atendimento. Cada uma é um
   SQL fixo, com empresa e período, já cruzando as áreas (pedido com produto,
   categoria, cliente, loja, promoção). Nada de SQL montado pela tela.
2. **A tela escolhe colunas, filtra, agrupa e soma** (`relatorios-montar.tsx`).
   Dinheiro é somado em centavos inteiros; margem de grupo = soma do lucro ÷ soma
   da receita (nunca média de margens).
3. **Quem vê o quê é o mesmo do `dados()`:** custo, comissão, frete, imposto,
   desconto e lucro só para quem vê o financeiro; contas e lançamentos só para ele;
   cidade e UF do cliente só para quem vê o cadastro de clientes; compras e
   atendimento só para os cargos dessas áreas. Teste de isolamento entre empresas
   em todas as fontes.
4. **Limites:** até 20.000 linhas por consulta (a tela avisa se cortou), período
   de até um ano, 20 s por consulta, mensagem de atendimento cortada em 500 letras.
5. **Relatórios salvos** ficam em `radar_configuracao` (chave `relatorios`), com
   formato conferido e no máximo 30 por pessoa; cada cargo só recebe os que pode
   abrir. Cada relatório gerado entra na auditoria (fonte, período e quantidade de
   linhas, sem os dados).
6. **Prontos** são configurações do montador, separadas por área; o relatório
   antigo (resumo e curva ABC) virou um dos prontos.

## Consequências

- Fonte nova = um SQL a mais em `RadarFontes`, com o teste de isolamento.
- Exportação: Excel (CSV com `;`, protegido contra fórmula) e PDF (impressão).
