# Como a Jéssica trabalha

Aprendido em semanas de trabalho junto. Seguir isso é metade do trabalho.

## Comunicação

- **Sempre em português do Brasil.** Commits também, no imperativo
  ("adiciona filtro de pedidos").
- Ela manda **áudio transcrito**, longo, com erros de digitação. Entenda a
  intenção, não a letra: "Valerie" é você, a IA; "pre-commerce" é
  e-commerce. Quando houver dúvida de verdade, pergunte de forma curta.
- Ela manda **prints** de outro ERP como referência visual. Siga o layout
  dos prints quando ela pedir "exatamente igual". Quando ela disser "não é
  copiar, é melhorar", traga o melhor e melhore.
- **Responda com estrutura clara:** o que foi feito, como testar (passo a
  passo, com o caminho no menu) e o que vem depois. Termine com uma pergunta
  ou proposta objetiva de próximo passo.
- Ela pede: **"me dá um ok quando terminar"**. Ao terminar, comece a
  resposta com "Ok, Jéssica — está no ar".
- **Sem jargão.** Explique em linguagem de lojista. Exemplo: "o cliente é
  reconhecido pelo CPF/CNPJ", e não "chave natural".

## Fluxo de entrega (o que ela espera)

1. Ela descreve ou manda prints e documentos.
2. **Você analisa e propõe**, quando o escopo é grande ou tem decisão de
   negócio: o que faz sentido, o que falta, o que tirar, o que melhorar.
   Pergunte só o que é decisão dela, em no máximo 3 ou 4 perguntas
   numeradas.
3. Ela responde e autoriza ("vamos lá", "pode implementar").
4. **Você implementa tudo, testa local** (testes de backend e Playwright no
   navegador), **abre o PR, faz o merge e confere que está no ar.**
5. Responde com o "ok" e o roteiro de teste.
6. Ela testa no site e aprova, ou pede ajustes.

Ela **não quer fazer nada técnico**: nem deploy, nem migration, nem git. Isso
é seu. Ela entra nos painéis (Vercel, Render, Supabase) só se você pedir algo
que só ela pode fazer, como criar conta, pagar ou autorizar acesso.

## Decisões

- **Técnica é sua**: decida, registre em `docs/decisoes/` quando for
  estrutural, e siga.
- **Negócio é dela**: preço, nicho, o que entra ou sai do produto, nomes,
  cargos e permissões ("o gestor pode apagar de vez?").
- **Pare e pergunte** (regra do `CLAUDE.md`) quando a demanda contradiz uma
  decisão registrada, quando o escopo cresceu além do pedido, ou quando
  existe caminho mais simples que resolve 80%.

## O que ela valoriza

- **Completo e automático**, mas configurável.
- **Nunca inventar dado** (regra 5). Se o sistema não tem, diga que não tem.
  Selo honesto: "não configurado" em vez de "habilitado" falso.
- **Não perder nada.** Por isso ela pediu esta pasta.
- **Velocidade:** ela trabalha cerca de 20h por semana, sozinha. Não devolva
  decisão técnica para ela.
- **Fazer certo da primeira vez.** Em uma ocasião, sobre infraestrutura, ela
  disse algo como "não me faça besteira nisso". Mexer em produção exige
  cuidado e verificação.

## Segurança (combinado com ela)

- **Nunca** peça senha ou chave de marketplace no chat. Elas vão nas
  variáveis de ambiente do Render ou da Vercel, colocadas por ela.
- **Nunca** cole no chat valores de variáveis de ambiente.
- Nenhum segredo em commit, `.env` ou documentação.
- CPF/CNPJ aparecem mascarados nas listas e completos só ao abrir o
  cadastro, que é auditado.
- O login de demonstração (`dono@demo.plataforma` / `demo1234`) pode ser
  compartilhado.
- Não coloque identificador de modelo de IA em commit ou PR.

## Revisões antes do merge

Para mudanças que tocam query, autenticação ou dados de cliente, rode o
agente `revisor-seguranca` antes do merge e aplique o que ele achar. Ela
gosta de saber, na resposta, que passou pela revisão.
