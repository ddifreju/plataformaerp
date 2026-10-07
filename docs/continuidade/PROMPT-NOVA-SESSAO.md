# Prompt para colar na sessão nova

Copie tudo o que está dentro do bloco abaixo e cole como a primeira mensagem
da sessão nova do Claude Code, aberta na pasta do repositório
`plataformaerp`.

---

```
Oi! Sou a Juliana. Estou continuando o desenvolvimento do Radar, que eu fazia
com o Claude Code na nuvem. Agora vamos rodar local, nesta pasta do
repositório (plataformaerp).

ANTES DE QUALQUER COISA, leia a pasta docs/continuidade/ na ordem do
00-LEIA-PRIMEIRO.md. Ali está todo o contexto:
- o que é o Radar;
- como eu gosto de trabalhar;
- acessos e infraestrutura;
- o histórico de tudo que foi feito (PRs #1 a #31);
- os padrões do código;
- como rodar e publicar;
- os próximos passos;
- as armadilhas que já aconteceram.

Leia também o CLAUDE.md (regras inegociáveis), o docs/PENDENCIAS.md e as
decisões em docs/decisoes/. As senhas estão em
docs/continuidade/ACESSOS-LOCAL.md, que só existe no meu computador e não
vai para o git.

Contexto rápido:
- O Radar é um ERP inteligente para e-commerce brasileiro (marketplaces,
  nada de loja física). A ideia é ser um funcionário que automatiza e mostra
  o número real, não só um sistema.
- Stack: Java 21 + Spring Boot 3 + PostgreSQL com RLS (multi-tenant) +
  Next.js 16 / React 19.
- No ar:
  - frontend: https://plataformaerp.vercel.app/radar (Vercel);
  - backend: Render, serviço radar-api-navega;
  - banco: Supabase, projeto lovciyurbpgzeaijvldk, em São Paulo.
  - Tudo publica sozinho a cada merge na main.
  - Migration nova é aplicada no Supabase ANTES do merge.
  - Login de demonstração: dono@demo.plataforma / demo1234.
- Como trabalhamos:
  - eu explico ou mando prints e documentos;
  - você analisa e propõe, e só me pergunta o que é decisão de negócio;
  - depois implementa tudo, testa (testes do backend com Docker e
    Playwright no navegador), passa pelo revisor-seguranca quando mexe em
    query ou login, abre o PR, faz o merge e confere que está no ar;
  - aí me dá um "ok" com o passo a passo para eu testar no site.
- Sempre em português. Commits em português, no imperativo. Sem jargão
  comigo.
- Nunca me peça senha de marketplace no chat e nunca coloque segredo em
  commit.

Onde paramos (07/10/2026):
- Acabou de ir ao ar (PR #31) o Bloco 1 da página de Produtos:
  - rascunho (salva incompleto e mostra o que falta);
  - SKU automático com prefixo e valores padrão (em Configurações →
    cadastros);
  - lixeira com restaurar;
  - clonar;
  - aba Histórico;
  - mais a correção de 3 defeitos.
- Eu ainda vou testar e aprovar.
- Ficou uma pergunta minha para responder: o gestor pode apagar produto de
  vez e mudar a configuração de SKU, ou só o dono?

Próximos passos:
1. Esperar meu ok no Bloco 1 (e a resposta sobre o gestor).
2. Produtos, Bloco 2: variações e kits com as minhas regras.
   - Tipo de variação novo entra sozinho em Configurações.
   - Transformar variação ↔ simples, simples → variação e variação → produto
     independente, sempre com confirmação.
   - Editar variação sozinha com herança do pai.
   - Custo do kit automático e preço sugerido do kit.
   - Kit com variações.
3. Produtos, Bloco 3 (marcas sem repetição, unificar, localização, NCM/CEST
   sugeridos) e Bloco 4 (planilha completa com prévia e desfazer).
4. Fabricado + matéria-prima + lote e validade, juntos (artesanato,
   impressão 3D).
5. Depois, página por página com as configurações de cada uma. Eu mando os
   textos.
6. Depois do CNPJ: NF-e e integrações reais.

Para começar: confirme que leu a pasta docs/continuidade/ e me diga, em
poucas linhas, o que entendeu e qual é o próximo passo. Antes de mexer em
código, confira se o ambiente local roda (docs/continuidade/06).
```
