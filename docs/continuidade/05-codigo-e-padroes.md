# Código e padrões

## Onde está o quê

**Backend:** `backend/src/main/java/com/plataforma/`
- `radar/`: tudo do Radar.
  - **`RadarService`**: GET `/api/radar` (método `dados()`, que manda tudo
    para a tela) e `comando()`, que despacha as operações. Também cuida da
    trava por empresa (advisory lock), da idempotência (`radar_comando`) e
    da auditoria (`radar_auditoria`).
  - **`RadarController`**: endpoints `/api/radar/...`.
  - **`RadarProdutos`**: cadastro, variações, kit, lote, lixeira, clonar,
    histórico e `pendencias()`.
  - **`RadarClientes`**, **`RadarVendedores`**, **`RadarAnuncios`**,
    **`RadarCadastros`** (categorias, embalagens, fornecedores),
    **`RadarPromocoes`**, **`RadarRelatorios`**.
  - **`RadarEmpresa`**: dados da empresa e usuários.
  - **`RadarConfiguracao`**: configurações por empresa, uma chave por área.
  - **`RadarEntrada`**: validações comuns, como `cnpjValido`, `gtinValido`,
    `valor`, `permitir` e `erro`.
  - **`RadarGuard`**: filtro de permissão e exigência do header CSRF.
- `autenticacao/`: sessão no servidor, BCrypt 12, login por e-mail global
  (decisões 0023 e 0024).
- `comum/tenant/`: `ContextoTenant` e `DataSourceComTenant`, que seta
  `app.tenant_id` para o RLS (decisão 0007).
- `pergunta/`, `margem/`, `painel/`, `integracao/`: módulos das fases 0 a 3.

**Migrations:** `backend/src/main/resources/db/migration/Vnnn__*.sql`, cada
uma com um desfazer em `db/undo/Unnn__*.sql`.

**Frontend:** `frontend/src/app/radar/`, componentes de cliente React 19.
- `radar.tsx`: a casca, com menu, roteamento por `page`, carga de dados e
  as funções `command`/`commandResult`.
- Uma tela por arquivo: `produto.tsx`, `cliente.tsx`, `vendedor.tsx`,
  `anuncios.tsx`, `categorias.tsx`, `embalagens.tsx`, `promocoes.tsx`,
  `configuracoes.tsx` (+ `configuracoes-geral.tsx`,
  `configuracoes-cadastros.tsx`).
- Lote: `produtos-lote.tsx`, `pedidos-lote.tsx`.
- Filtros: `filtros-genericos.tsx` (o componente) e as configurações em
  `produtos-filtros.tsx`, `anuncios-filtros.tsx`, `contatos-filtros.tsx`,
  `catalogo-filtros.tsx` e `pedidos-filtros.tsx`.
- `ui.tsx`: `Table`, `Badge`, `money`, `str`, `cents`. `radar.css`: todo o
  estilo, com prefixo `rd-`.

## Padrões que se repetem

1. **Comandos:**
   - **Como chamar:** toda escrita vai em `POST /api/radar/comandos` com
     `{op, ...}`, os headers `Idempotency-Key` e `X-Radar-Request: 1`.
   - **Onde registrar:** a operação nova entra no `OPERACOES` da classe e
     no `default` do switch em `RadarService.comando`.
   - **Exceção:** senha e upload **não** passam por comandos, porque o corpo
     vira hash e vai para a auditoria. Eles têm endpoint próprio, por
     exemplo `/usuarios/{id}/senha` e `/empresa/logo`.
2. **Tenant em tudo:**
   - toda query leva `tenant_id=?` vindo de `ContextoTenant.atual()`;
   - toda tabela nova tem RLS + FORCE + policy `tenant_isolation`;
   - GRANT explícito para `app_aplicacao` (sem DELETE, salvo motivo);
   - todo PR que toca query precisa de **teste de isolamento**.
3. **Dinheiro:** `BigDecimal` com escala e `RoundingMode` explícitos e
   `NUMERIC` no banco. Na resposta, dinheiro vai como texto (decisão 0026).
4. **Permissão por cargo:** `permitir(papel, "DONO", "GESTOR")`, com o papel
   lido do banco a cada requisição.
   - **Cargos:** DONO, GESTOR, ANALISTA, FINANCEIRO, ATENDIMENTO, ESTOQUE,
     MARKETING.
   - **Custo** só para quem vê o financeiro.
5. **Exclusão:** a regra é soft delete (`excluido_em`) com restaurar. Apagar
   de vez só quando o registro não tem histórico.
6. **Lote no frontend:** componente com render-prop (`cabecalho` e `celula`)
   que dá a caixa de marcar, a barra de baixo e o menu "⋯" por linha
   (`rd-menu-linha`).
7. **Filtros:** por configuração.
   - **Tipos de campo:** `multi`, `texto`, `faixa`, `marca`, `data`,
     `cabe`, `periodo`.
   - **Atalhos e sugestões:** os atalhos têm `chaves`; as sugestões tratam
     a negação "não/sem".
   - **Busca:** `normal()` tira os acentos; números são comparados sem os
     separadores.
   - **Filtros salvos:** ficam no navegador (localStorage).
8. **Selos de Configurações:** item com `pagina` leva à página existente;
   com `tela`, abre uma tela dentro de Configurações; sem os dois, mostra o
   aviso "em desenho".

## Convenções de formatação
- **Prettier com `--print-width 100`:** `radar.tsx`, `produto.tsx`,
  `anuncios.tsx`, `vendedor.tsx`, os arquivos de filtros e de configurações.
- **Prettier com largura padrão (80):** `cliente.tsx` e `produtos-lote.tsx`.
  Rode o prettier com a largura certa de cada arquivo, senão o diff explode.
- Java sem formatador automático: siga o estilo vizinho (4 espaços, linhas
  de até ~100).
- Comentários em português, explicando o **porquê**.

## Testes
- Backend: JUnit + Testcontainers (Postgres real com pgvector). Precisa do
  Docker rodando. São **402 testes** (07/10/2026).
- Radar: `backend/src/test/java/com/plataforma/radar/*Test.java`, com
  `BancoRadarDeTeste.novaEmpresa()` e `naEmpresa(empresa, () -> ...)`.
- Frontend: `npx tsc --noEmit -p .` e `npx eslint src/app/radar/`, mais
  Playwright no navegador para os fluxos.
