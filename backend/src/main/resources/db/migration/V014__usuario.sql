-- =====================================================================
-- V014 — usuario (quem faz login; a fonte do tenant a partir da Fase 3)
-- =====================================================================
-- Convencoes gerais: cabecalho da V005. Especificacao desta tarefa:
-- docs/decisoes/0023-autenticacao-por-sessao-e-tenant-do-servidor.md.
-- Decisoes ja tomadas la e NAO reabertas aqui: sessao no servidor (nao
-- JWT), BCrypt custo 12, usuario pertence a UM tenant, o header
-- X-Tenant-Id deixa de existir, nao ha auto-cadastro publico.
--
-- Esta e a tabela mais delicada do sistema depois de `cliente`, por um
-- motivo diferente: `cliente` guarda dado pessoal de terceiro; `usuario`
-- guarda a CHAVE do isolamento. A partir da tarefa 17, o tenant de toda
-- requisicao sai de uma linha desta tabela. Um erro aqui nao vaza uma
-- tabela: vaza todas.
--
-- ---------------------------------------------------------------------
-- 1. O PROBLEMA CENTRAL DESTA MIGRATION: o ovo e a galinha do login
-- ---------------------------------------------------------------------
-- O molde da decisao 0010 exige RLS com
-- `tenant_id = app_current_tenant_id()`. Mas o login acontece ANTES de
-- existir sessao, logo antes de existir tenant no contexto. O
-- DataSourceComTenant (decisao 0007, camada 4) seta o GUC app.tenant_id
-- como STRING VAZIA nesse momento, app_current_tenant_id() devolve NULL,
-- e `tenant_id = NULL` avalia como falso em toda policy.
--
-- Consequencia literal se nada for feito: a consulta
-- `SELECT senha_hash FROM usuario WHERE email = ?` devolve ZERO linhas
-- SEMPRE, e ninguem nunca consegue entrar. Nao e um bug sutil — o
-- produto nao liga. E a "solucao" obvia e desastrosa (uma policy do tipo
-- `USING (app_current_tenant_id() IS NULL)`) abriria a tabela INTEIRA
-- para qualquer conexao sem tenant, que e exatamente a situacao de toda
-- requisicao nao autenticada.
--
-- AS TRES SAIDAS QUE FORAM CONSIDERADAS, E POR QUE DUAS FORAM RECUSADAS
--
--   (a) `usuario` seguir a regra da `tenant` (V002): sem RLS.
--       RECUSADA. A V002 se justifica porque tenant.id E o tenant e o
--       conteudo e catalogo administrativo (nome, slug, ativo). Aqui o
--       conteudo e credencial e o tenant_id e coluna de verdade. Sem RLS,
--       QUALQUER bug de predicado esquecido em QUALQUER consulta de
--       usuario devolve os usuarios de todos os clientes — e o
--       RlsAtivoEmTodasAsTabelasTest, que descobre tabelas pela coluna
--       tenant_id, quebraria (corretamente).
--
--   (b) Funcao SECURITY DEFINER "restrita".
--       RECUSADA, e esta e a recusa que mais importa registrar porque a
--       opcao PARECE a mais correta. Neste projeto as migrations rodam
--       como o DONO do banco, que em dev (infra/docker-compose.yml,
--       POSTGRES_USER) e em teste (PostgresDeTeste, usuario "dono_teste")
--       e SUPERUSUARIO. Uma funcao SECURITY DEFINER criada por esta
--       migration seria, portanto, de propriedade de um superusuario — e
--       superusuario IGNORA RLS sempre, com ou sem FORCE. O resultado
--       seria uma funcao cujo unico limite e o texto do proprio corpo:
--       um `WHERE` errado numa manutencao futura viraria leitura irrestrita
--       da tabela de credenciais de todos os clientes, sem que nenhuma
--       policy tivesse chance de segurar. Uma protecao que depende de o
--       corpo da funcao estar certo e a mesma "disciplina humana" que a
--       decisao 0007 recusa como garantia.
--       (Variante possivel e NAO adotada: criar um papel dedicado nao
--       superusuario e transferir a propriedade da funcao para ele com
--       ALTER FUNCTION ... OWNER TO. Funcionaria, e permitiria ate esconder
--       senha_hash da aplicacao por GRANT de coluna. Custa um papel novo
--       de cluster, um passo de migration que exige poder criar papel, e
--       mais uma peca para operar sozinha em 20h/semana. Registrado aqui
--       como caminho conhecido para o dia em que houver motivo — nao como
--       esquecimento.)
--
--   (c) ADOTADA: uma SEGUNDA policy de SELECT, permissiva, que abre
--       EXATAMENTE UMA LINHA — a do e-mail que esta sendo autenticado
--       neste instante — e so enquanto nao ha tenant no contexto.
--       O e-mail viaja num GUC proprio, `app.login_email`, setado como
--       LOCAL dentro da MESMA instrucao que faz a leitura (a funcao
--       app_usuario_para_login abaixo). Ver o bloco 2.
--
-- ---------------------------------------------------------------------
-- 2. COMO A POLICY DE LOGIN FUNCIONA, E O QUE ELA EXPOE
-- ---------------------------------------------------------------------
-- Policies permissivas se somam com OU. As duas de SELECT desta tabela
-- sao MUTUAMENTE EXCLUSIVAS por construcao:
--   usuario_select        exige app_current_tenant_id() IS NOT NULL
--                         (implicito: compara tenant_id com ele)
--   usuario_select_login  exige app_current_tenant_id() IS NULL
-- Ou seja: dentro de uma requisicao autenticada, a policy de login e
-- sempre falsa e nao acrescenta nada. Fora de sessao, a policy de tenant
-- e sempre falsa e a de login abre no maximo uma linha.
--
-- O QUE ISTO EXPOE — dito sem eufemismo:
--   Uma conexao como app_aplicacao, sem tenant no contexto, que sete
--   `app.login_email` com um endereco EXATO, le a linha inteira daquele
--   usuario: id, tenant_id, nome, papel, ativo e o senha_hash (BCrypt).
--   Isso e um oraculo de existencia: "este e-mail tem conta, e em qual
--   tenant". E deliberado — e a informacao minima que autenticar por
--   e-mail exige.
--
-- O QUE ISTO NAO EXPOE:
--   - Enumeracao. Nao ha LIKE, prefixo, lista nem contagem: e igualdade
--     exata contra um valor que o chamador ja precisa conhecer. Nao ha
--     como DESCOBRIR enderecos por este caminho.
--   - Qualquer outra linha da tabela, inclusive os demais usuarios do
--     mesmo tenant.
--   - Qualquer outra tabela. A policy e desta tabela e so dela.
--   - Nada por acidente. Uma consulta que esqueceu o predicado de tenant
--     continua devolvendo ZERO linhas, porque sem o GUC app.login_email
--     setado a comparacao vira `email = NULL` => falso. O caminho so abre
--     quando alguem seta o GUC DE PROPOSITO. Falha fechada, como a V001.
--
-- A LINHA DE BASE HONESTA: app_aplicacao ja pode setar app.tenant_id com
-- qualquer valor. O RLS deste sistema contem BUG DE APLICACAO, nao um
-- papel de banco hostil — se a senha do app_aplicacao vazar, o banco
-- inteiro vaza com ou sem esta policy. O que esta policy acrescenta em
-- superficie e uma fresta com formato conhecido, nao uma classe nova de
-- risco. O que ela NAO pode fazer e ficar aberta por acidente, e e por
-- isso que o GUC e LOCAL (ver abaixo).
--
-- POR QUE O GUC E LOCAL, E POR QUE ISSO E O DETALHE QUE SEGURA TUDO:
--   A decisao 0010 registra que `SET` de sessao VAZA NO POOL: a conexao
--   volta ao HikariCP carregando o valor do request anterior. Se
--   app.login_email fosse setado como GUC de sessao, a conexao voltaria
--   ao pool com a fresta ABERTA para aquele e-mail, e o proximo request
--   sem tenant que pegasse essa conexao leria a linha. Por isso
--   app_usuario_para_login usa `set_config(..., is_local => true)` DENTRO
--   de uma funcao: a leitura acontece na mesma instrucao/transacao do
--   set, e o valor e descartado no fim dela — automaticamente, sem
--   depender de ninguem lembrar de limpar.
--   A armadilha inversa da 0010 ("set_config LOCAL fora de transacao
--   explicita e no-op silencioso") NAO se aplica aqui e vale entender por
--   que: ali o problema e que o valor precisava sobreviver ate a PROXIMA
--   instrucao; aqui set e leitura estao na MESMA instrucao (a chamada da
--   funcao), que sempre roda dentro de uma transacao, implicita ou nao.
--   E se ainda assim alguem quebrar isso, o modo de falha e "ninguem
--   consegue logar" — alto, imediato e seguro — nunca "todo mundo le".
--
-- ---------------------------------------------------------------------
-- 3. E-MAIL: UNICO GLOBALMENTE, NAO POR TENANT. E o ponto mais caro.
-- ---------------------------------------------------------------------
-- Decisao: `UNIQUE (email)` sem tenant_id. Contraria a intuicao
-- multi-tenant (e a convencao 2 da V005, que faria `UNIQUE (tenant_id,
-- email)`), e e deliberada.
--
-- POR QUE NAO `UNIQUE (tenant_id, email)`:
--   No instante do login nao existe tenant — e exatamente o problema do
--   bloco 1. Com unicidade por tenant, `WHERE email = ?` pode devolver
--   DUAS OU MAIS linhas (a mesma pessoa trabalhando em duas lojas), e o
--   sistema nao tem como escolher: nao ha sessao, nao ha contexto, nao ha
--   nada. Restariam duas saidas, as duas ruins:
--     - pedir a loja no formulario de login (campo "qual loja?", ou
--       subdominio obrigatorio). Isso e um SELETOR — precisamente o que a
--       decisao 0023 recusou ao descartar "um usuario pertencer a varios
--       tenants" ("precisaria de um seletor — reabrindo justamente o
--       buraco que estamos fechando"). Alem de ser mudanca de produto e
--       de UX, nao de schema, tomada por conta propria.
--     - escolher uma das linhas por algum criterio (a mais recente, a
--       ativa). Isso e autenticar a pessoa no tenant ERRADO de vez em
--       quando, em silencio. E o pior desfecho possivel neste sistema.
--   Ou seja: unicidade por tenant nao "dificulta" o login, ela o torna
--   ambiguo por construcao.
--
-- POR QUE `UNIQUE (email)` GLOBAL e a escolha certa mesmo com seu custo:
--   1. Torna `email -> usuario -> tenant_id` uma funcao total e sem
--      ambiguidade. O login vira uma leitura determinista, e o tenant sai
--      do dado, como a 0023 exige.
--   2. E COMPATIVEL COM O FUTURO JA ACEITO. A 0023 diz que, quando a
--      demanda aparecer (o contador que atende cinco lojas), a modelagem
--      sera `usuario_tenant` com troca explicita de contexto — ou seja,
--      UM login para varios tenants. Isso pressupoe e-mail unico global.
--      Migrar de "unico por tenant" para la seria FUNDIR contas: duas
--      linhas, duas senhas diferentes, e nenhum criterio automatico para
--      decidir qual sobrevive. Migrar de "unico global" para la e mover
--      o par (usuario, tenant) para a tabela nova, sem tocar em senha.
--      A escolha de hoje e a que mantem o caminho da 0023 aberto.
--
-- O CUSTO, DECLARADO: a mesma pessoa NAO pode ter duas contas com o mesmo
-- e-mail em duas lojas. Ela precisa de um segundo endereco. E limitacao
-- real, e o alivio definitivo e `usuario_tenant` (0023), nao um remendo
-- aqui.
--
-- CASO OPERACIONAL QUE ISSO CRIA — e a resposta: funcionaria saiu da loja
-- A (usuario desativado, linha preservada) e vai trabalhar na loja B com
-- o mesmo e-mail. O UNIQUE recusa. Nao existe indice parcial `WHERE ativo`
-- para "resolver" isso de proposito: ele permitiria uma linha ativa e uma
-- inativa com o mesmo e-mail, e a consulta de login voltaria a ser
-- ambigua — trocariamos um problema operacional raro por ambiguidade de
-- autenticacao, que e troca pessima. O caminho e liberar o endereco por
-- PROVISIONAMENTO (reescrever o e-mail da linha desativada para uma forma
-- lapide, ex.: 'desativado+<id>@invalido.local'), operacao deliberada e
-- fora da aplicacao — e por isso `email` NAO esta no GRANT UPDATE.
--
-- NORMALIZACAO: e-mail e guardado sempre em minusculas
-- (ck_usuario_email_normalizado). Sem isso, 'Ana@loja.com' e
-- 'ana@loja.com' seriam DUAS contas, o UNIQUE nao pegaria, e o login
-- falharia dependendo de como a pessoa digitou — bug que so aparece com
-- usuario real. Guardar normalizado (em vez de indexar por lower(email))
-- mantem `WHERE email = ?` usando o indice do UNIQUE diretamente: o
-- CHECK e o que torna essa igualdade correta, os dois andam juntos.
-- Nao usamos a extensao citext: uma coluna com semantica de comparacao
-- diferente de todas as outras do schema e surpresa permanente para quem
-- le, e o CHECK resolve o mesmo com uma linha visivel.
-- Tecnicamente o RFC 5321 permite parte local sensivel a caixa; nenhum
-- provedor real usa isso. Divergencia consciente.
--
-- ---------------------------------------------------------------------
-- 4. O QUE ESTA TABELA NAO TEM, DE PROPOSITO
-- ---------------------------------------------------------------------
-- - canal_id / id_externo / dados_origem / sincronizado_em (convencao 4
--   da V005). Usuario NAO e ingerido de fonte externa: e criado por
--   provisionamento. Carregar campos de procedencia aqui sugeriria um
--   pipeline que nao existe. Quando existir SSO/OIDC (descartado por ora
--   na 0023), a identidade externa entra como tabela propria de
--   identidade federada, nao como coluna solta.
-- - Token de "esqueci minha senha", convite, verificacao de e-mail e
--   segundo fator. Nenhum deles e coluna: sao objetos com validade,
--   consumo unico e expiracao, e viram tabela propria quando o fluxo
--   existir. Um `token` text nesta tabela seria segredo reversivel
--   guardado ao lado de senha_hash, que e o oposto do desenho.
-- - Senha nula / usuario "pendente de ativacao". senha_hash e NOT NULL:
--   provisionamento define uma senha. Modelar "conta sem senha" sem ter o
--   fluxo de convite so criaria um estado que nada sabe tratar.
-- - Historico de acesso. `ultimo_acesso_em` responde "esta conta ainda e
--   usada?" e nada alem. Auditoria de acesso de verdade (quem entrou,
--   quando, de onde, e as tentativas que falharam) e tabela append-only
--   propria, no molde da consulta_auditada (V003). Nao esta nesta fase.
-- =====================================================================


CREATE TABLE usuario (
    id                uuid        NOT NULL DEFAULT gen_random_uuid(),

    -- Parte da identidade. Sem DEFAULT de proposito (decisao 0010): bug
    -- de contexto deve virar erro, nunca linha gravada em silencio.
    -- Aqui isso vale em dobro: uma linha de usuario gravada com o tenant
    -- errado nao e "um registro no lugar errado", e uma pessoa com acesso
    -- aos dados de outro cliente.
    tenant_id         uuid        NOT NULL,

    -- email: o identificador de LOGIN. Unico GLOBALMENTE (nao por
    -- tenant) — a justificativa completa esta no bloco 3 do cabecalho, e
    -- ela e o ponto mais importante desta migration.
    -- Sempre em minusculas (ck_usuario_email_normalizado).
    email             text        NOT NULL,

    -- senha_hash: BCrypt, custo minimo 12 (decisao 0023).
    --
    -- O QUE ENTRA AQUI: exclusivamente a saida do BCryptPasswordEncoder,
    -- no formato `$2<a|b|y>$<custo>$<22 chars de salt + 31 de hash>`,
    -- 60 caracteres no total. O salt ja vai embutido no proprio valor;
    -- nao existe (e nao deve existir) coluna de salt separada.
    --
    -- O QUE NUNCA ENTRA AQUI, EM NENHUMA CIRCUNSTANCIA: a senha em claro,
    -- ou qualquer coisa reversivel (cifra, base64, "ofuscacao"). BCrypt e
    -- funcao de sentido unico de proposito: nem nos conseguimos descobrir
    -- a senha de um cliente, e essa impossibilidade e uma propriedade do
    -- produto, nao uma limitacao. "Recuperar senha" nunca sera devolver a
    -- senha; sera definir outra.
    -- Este valor tambem NUNCA aparece em log, em resposta de API, em
    -- mensagem de erro ou em toString() de entidade. Ver as armadilhas de
    -- Spring Security no fim do arquivo.
    --
    -- O CHECK DE CUSTO >= 12 NAO E DECORATIVO: o construtor padrao de
    -- `new BCryptPasswordEncoder()` no Spring usa custo 10. Trocar a
    -- configuracao por engano (ou aceitar o default) enfraqueceria toda
    -- senha nova em silencio, e ninguem repara olhando a tela. Com o
    -- CHECK, esse erro vira violacao de constraint na primeira gravacao —
    -- alto e imediato, no lugar de uma fraqueza que so aparece num
    -- vazamento futuro. Mesmo racional do ck_evento_ingerido_hash_formato
    -- (V012) e do ck_cliente_documento_hash_formato (V007): a forma do
    -- valor e verificavel, entao e verificada.
    senha_hash        text        NOT NULL,

    -- nome: exibicao na interface ("Ola, Juliana"), e identificacao de
    -- quem fez o que quando houver trilha de acao.
    -- NOT NULL, ao contrario de cliente.nome (V007), e a diferenca e de
    -- procedencia: cliente vem de marketplace que as vezes so entrega
    -- apelido, e inventar nome ali violaria a regra 5 do CLAUDE.md. Aqui
    -- somos NOS que criamos a linha, no provisionamento; se o nome nao
    -- veio, quem cadastrou nao preencheu, e isso e erro de cadastro.
    nome              text        NOT NULL,

    -- ativo: desativacao logica. Nunca DELETE — mesma razao da V002 e da
    -- convencao 6 da V005, com um agravante proprio: `usuario` sera
    -- referenciada por trilhas de acao ("quem alterou esta taxa", "quem
    -- pediu este recalculo"). Apagar a linha da pessoa que saiu da
    -- empresa apagaria a autoria de tudo que ela fez.
    -- E AQUI ESTA O CASO DE USO REAL: "desligar o acesso do funcionario
    -- que saiu" e citado na propria decisao 0023 como razao para sessao
    -- em servidor em vez de JWT. Esta coluna e a outra metade disso — e a
    -- aplicacao PRECISA verifica-la no login (e, idealmente, tambem a
    -- cada requisicao; ver armadilhas no fim).
    ativo             boolean     NOT NULL DEFAULT true,

    -- ------------------------------------------------------------------
    -- papel — LEIA ANTES DE ASSUMIR QUE ISTO PROTEGE ALGUMA COISA
    -- ------------------------------------------------------------------
    -- ESTA COLUNA NAO IMPLEMENTA AUTORIZACAO. Nesta fase ela e apenas um
    -- rotulo que diz as TELAS o que faz sentido mostrar. Nenhum endpoint
    -- a consulta, nenhuma policy de RLS a menciona, nenhum GRANT depende
    -- dela. Um ANALISTA que chamar direto a API que a interface esconde
    -- dele sera atendido normalmente.
    --
    -- Isso esta escrito assim, em maiusculas, porque uma coluna chamada
    -- `papel` com valores 'DONO'/'GESTOR'/'ANALISTA' PARECE controle de
    -- acesso, e daqui a seis meses alguem (inclusive nos) vai olhar o
    -- schema e concluir que o sistema tem autorizacao por papel. Nao tem.
    -- Transformar isto em autorizacao de verdade e TAREFA FUTURA, e
    -- envolve: decidir a matriz papel x operacao, aplicar em cada
    -- endpoint (com teste de negacao, nao so de permissao) e decidir o
    -- que acontece com sessao ja aberta quando o papel muda.
    --
    -- Os tres valores correspondem as tres visoes da Fase 3:
    --   DONO      dono da loja: ve tudo, inclusive margem e custo real
    --   GESTOR    opera o dia a dia: pedidos, devolucoes, atendimento
    --   ANALISTA  analisa: relatorios e consultas, sem operar
    -- Dominio fechado por CHECK (convencao 5 da V005). Sem DEFAULT: quem
    -- cria o usuario declara o papel. Um DEFAULT aqui faria o
    -- esquecimento escolher por nos — e qualquer que fosse o escolhido
    -- estaria errado (default 'DONO' da acesso demais; default 'ANALISTA'
    -- cria contas que nao conseguem trabalhar e ninguem entende por que).
    papel             text        NOT NULL,

    -- ------------------------------------------------------------------
    -- Auditoria
    -- ------------------------------------------------------------------
    criado_em         timestamptz NOT NULL DEFAULT now(),
    -- atualizado_em: convencao 7 da V005. Nao e enfeite nesta tabela: e o
    -- unico registro de QUANDO uma senha foi trocada ou um acesso foi
    -- desligado. Sem ela, "desde quando esta pessoa nao tem mais acesso?"
    -- nao tem resposta.
    atualizado_em     timestamptz NOT NULL DEFAULT now(),
    -- ultimo_acesso_em: NULL enquanto a pessoa nunca entrou — e NULL aqui
    -- e informacao util, nao ausencia a ser preenchida: "conta
    -- provisionada e nunca usada" e um estado real (convite que nao foi
    -- aceito, funcionario que nunca comecou). Nao preencher com criado_em
    -- para "nao ficar vazio": seria inventar dado (regra 5 do CLAUDE.md).
    ultimo_acesso_em  timestamptz,

    CONSTRAINT pk_usuario PRIMARY KEY (id),
    -- Convencao 2 da V005 / decisao 0015: identidade composta. E o indice
    -- com tenant_id na primeira posicao exigido pela 0010 e o alvo das
    -- FKs compostas futuras — e elas VAO existir: toda trilha de acao
    -- ("alterado por") vai referenciar (tenant_id, usuario_id), e sem
    -- esta chave alternativa a FK teria de ser simples, permitindo que
    -- uma acao do tenant A apontasse para um usuario do tenant B.
    CONSTRAINT uq_usuario_tenant_id UNIQUE (tenant_id, id),
    -- FK simples para tenant: excecao unica documentada na convencao 2 da
    -- V005 (tenant e a raiz e nao tem coluna tenant_id).
    CONSTRAINT fk_usuario_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (id),

    -- Unicidade GLOBAL do e-mail. Ver o bloco 3 do cabecalho: nao e
    -- descuido do molde multi-tenant, e a condicao para o login existir.
    CONSTRAINT uq_usuario_email UNIQUE (email),

    -- Validacao minima de formato (tem arroba, sem espaco), mesma da V007
    -- e pela mesma razao: validacao "rigorosa" de e-mail em CHECK rejeita
    -- endereco valido e exotico e ainda assim aceita invalido. O objetivo
    -- e barrar lixo obvio, nao certificar o endereco — quem certifica e o
    -- fluxo de verificacao, que nao existe nesta fase.
    CONSTRAINT ck_usuario_email_formato
        CHECK (email ~ '^[^[:space:]@]+@[^[:space:]@]+$'),
    -- Normalizacao obrigatoria. Este CHECK e LOAD-BEARING: e ele que
    -- torna `WHERE email = ?` (que usa o indice do UNIQUE) equivalente a
    -- `WHERE lower(email) = ?`. Removendo-o, o UNIQUE deixa de impedir
    -- contas duplicadas por diferenca de caixa e o login fica dependente
    -- de como a pessoa digitou.
    -- Nao ha btrim() aqui: ck_usuario_email_formato ja proibe QUALQUER
    -- caractere de espaco no valor inteiro, inclusive nas pontas.
    CONSTRAINT ck_usuario_email_normalizado
        CHECK (email = lower(email)),
    -- Limite do RFC 5321 (320 = 64 da parte local + @ + 255 do dominio).
    -- Nao e limite tecnico do Postgres (text nao tem, e o indice btree
    -- aguentaria muito mais): e limite de sanidade, para que um texto
    -- colado por engano no campo de e-mail vire violacao de constraint em
    -- vez de linha gravada com lixo de 4 KB no identificador de login.
    CONSTRAINT ck_usuario_email_tamanho CHECK (length(email) <= 320),

    -- Formato do BCrypt: $2<a|b|y>$<custo 12..99>$<53 chars>.
    -- O custo entra na propria regex (12-19 ou 20-99) em vez de num
    -- segundo CHECK com cast de substring para int: um cast falharia com
    -- "invalid input syntax" diante de lixo, dando erro feio em vez de
    -- violacao de constraint limpa, e o Postgres nao garante ordem de
    -- avaliacao entre constraints para evitar isso. A regex resolve os
    -- dois testes (formato e custo minimo) numa expressao so.
    -- Aceita $2y$ alem de $2a$/$2b$ (o Spring emite $2a$) para nao
    -- rejeitar hash importado de outra ferramenta numa migracao futura —
    -- as tres variantes sao o mesmo algoritmo.
    CONSTRAINT ck_usuario_senha_hash_formato
        CHECK (senha_hash ~ '^\$2[aby]\$(1[2-9]|[2-9][0-9])\$[./A-Za-z0-9]{53}$'),

    CONSTRAINT ck_usuario_nome_nao_vazio CHECK (btrim(nome) <> ''),

    -- Dominio fechado. NAO ha 'OUTRO' aqui, ao contrario de canal.tipo
    -- (V005): la o escape hatch evita PERDER dado de uma fonte externa
    -- (regra 5 do CLAUDE.md); aqui nao ha fonte externa nenhuma, e um
    -- papel desconhecido nao seria dado preservado, seria uma conta que
    -- nenhuma tela sabe tratar.
    CONSTRAINT ck_usuario_papel CHECK (papel IN (
        'DONO',
        'GESTOR',
        'ANALISTA'
    ))
);

COMMENT ON TABLE  usuario IS
    'Quem faz login. A partir da decisao 0023 o tenant da requisicao sai daqui. email e unico GLOBALMENTE (nao por tenant) - sem isso o login seria ambiguo, porque no momento de autenticar ainda nao existe tenant. Ver bloco 3 do cabecalho da V014.';
COMMENT ON COLUMN usuario.tenant_id IS
    'Parte da identidade do registro. Sem DEFAULT de proposito: bug de contexto deve virar erro, nao linha gravada. Aqui a linha errada nao e um registro fora do lugar, e uma pessoa com acesso aos dados de outro cliente.';
COMMENT ON COLUMN usuario.email IS
    'Identificador de login. UNICO GLOBALMENTE, sempre em minusculas. A unicidade global e o que torna email -> tenant uma funcao sem ambiguidade no instante do login, quando ainda nao ha sessao.';
COMMENT ON COLUMN usuario.senha_hash IS
    'BCrypt, custo minimo 12 (decisao 0023), 60 caracteres com salt embutido. NUNCA a senha em claro nem nada reversivel. Nunca vai para log, resposta de API ou mensagem de erro. O CHECK de custo pega o default 10 do Spring, que enfraqueceria toda senha nova em silencio.';
COMMENT ON COLUMN usuario.nome IS
    'Exibicao na interface e autoria em trilhas futuras. NOT NULL, ao contrario de cliente.nome: aqui a linha e criada por nos, nao ingerida de marketplace.';
COMMENT ON COLUMN usuario.ativo IS
    'Desativacao logica. Nunca DELETE: a linha e referenciada por autoria de acoes. E o mecanismo de "desligar o acesso do funcionario que saiu" citado na decisao 0023.';
COMMENT ON COLUMN usuario.papel IS
    'ROTULO DE INTERFACE, NAO AUTORIZACAO. Nesta fase nenhum endpoint, policy ou GRANT consulta esta coluna: ela so diz as telas o que mostrar. Transformar em autorizacao real e tarefa futura. Ver o bloco da coluna na V014.';
COMMENT ON COLUMN usuario.ultimo_acesso_em IS
    'NULL = conta provisionada e nunca usada (estado real, nao ausencia a preencher). Responde "esta conta ainda e usada?" e nada alem - auditoria de acesso de verdade seria tabela append-only propria.';


-- ---------------------------------------------------------------------
-- Indices
-- ---------------------------------------------------------------------
-- Nao ha CREATE INDEX nesta migration, e a ausencia e deliberada:
--
--   uq_usuario_tenant_id  cria o indice (tenant_id, id) que a decisao
--                         0010 exige (tenant_id na primeira posicao) e
--                         atende "os usuarios desta loja" — que devolve
--                         um punhado de linhas por tenant.
--   uq_usuario_email      cria o indice (email) que atende o UNICO
--                         caminho quente real: a busca do login.
--
-- EXCECAO CONSCIENTE AO MOLDE DA 0010: uq_usuario_email NAO comeca com
-- tenant_id, e nao pode comecar — a consulta de login nao TEM tenant para
-- por no predicado. E a mesma anomalia do bloco 3, vista pelo lado do
-- indice. O requisito da 0010 continua satisfeito por
-- uq_usuario_tenant_id.
--
-- Um indice parcial `(tenant_id) WHERE ativo` foi considerado e recusado:
-- sao poucas dezenas de usuarios por tenant no horizonte visivel, e
-- indice que nao muda plano so custa escrita. Se um dia a tela de
-- usuarios ficar lenta, o indice entra por migration, com o EXPLAIN que
-- o justificou.


-- ---------------------------------------------------------------------
-- Row Level Security — molde da decisao 0010, integral, + a policy de login
-- ---------------------------------------------------------------------
ALTER TABLE usuario ENABLE ROW LEVEL SECURITY;
-- FORCE, nao so ENABLE: sem FORCE o DONO da tabela escapa das policies, e
-- o teste de isolamento rodando como dono passaria falsamente.
ALTER TABLE usuario FORCE  ROW LEVEL SECURITY;

CREATE POLICY usuario_select ON usuario
    FOR SELECT
    USING (tenant_id = app_current_tenant_id());

CREATE POLICY usuario_insert ON usuario
    FOR INSERT
    WITH CHECK (tenant_id = app_current_tenant_id());

-- USING + WITH CHECK: so com USING seria possivel alcancar a propria
-- linha e reescrever o tenant_id dela, "doando" o registro a outro
-- tenant. Nesta tabela isso seria pior do que em qualquer outra: seria
-- doar uma CREDENCIAL, ou seja, dar a alguem acesso permanente aos dados
-- de outro cliente.
CREATE POLICY usuario_update ON usuario
    FOR UPDATE
    USING (tenant_id = app_current_tenant_id())
    WITH CHECK (tenant_id = app_current_tenant_id());

-- Existe como defesa em profundidade mesmo sem GRANT DELETE (convencao 6
-- da V005): se um dia o GRANT mudar, a policy ja esta no lugar.
CREATE POLICY usuario_delete ON usuario
    FOR DELETE
    USING (tenant_id = app_current_tenant_id());

-- ---------------------------------------------------------------------
-- A policy que torna o login possivel. Ver blocos 1 e 2 do cabecalho.
-- ---------------------------------------------------------------------
-- AS PERMISSIVE explicito (e o default, mas aqui a semantica e o ponto):
-- policies permissivas se combinam com OU. Esta soma-se a usuario_select
-- sem nunca se sobrepor a ela, porque as duas exigem coisas opostas sobre
-- app_current_tenant_id():
--
--   dentro de uma sessao (tenant resolvido) -> esta policy e FALSA
--   fora de sessao (tenant NULL)            -> usuario_select e FALSA e
--                                              esta abre no maximo UMA
--                                              linha, a do e-mail exato
--                                              posto em app.login_email
--
-- Sem o GUC setado, current_setting('app.login_email', true) devolve
-- NULL, nullif(...) mantem NULL, e `email = NULL` avalia NULL, tratado
-- como falso: ZERO linhas. Mesma falha fechada da V001 — o caminho
-- precisa ser aberto DE PROPOSITO, nunca por esquecimento.
--
-- lower(...) do lado do GUC, nunca do lado da coluna: manter `usuario.email`
-- como referencia crua preserva o uso do indice de uq_usuario_email. Como
-- ck_usuario_email_normalizado garante que a coluna ja esta em minusculas,
-- a comparacao e exata e correta.
--
-- O prefixo com ponto em `app.login_email` e obrigatorio: GUC customizado
-- sem prefixo o Postgres rejeita (armadilha registrada na decisao 0010).
CREATE POLICY usuario_select_login ON usuario
    AS PERMISSIVE
    FOR SELECT
    USING (
        app_current_tenant_id() IS NULL
        AND email = lower(nullif(btrim(current_setting('app.login_email', true)), ''))
    );


-- ---------------------------------------------------------------------
-- app_usuario_para_login — o UNICO caminho de leitura pre-sessao
-- ---------------------------------------------------------------------
-- Existe por um motivo tecnico preciso, nao por organizacao: ela coloca o
-- `set_config` LOCAL e a leitura DENTRO DA MESMA INSTRUCAO. Se a
-- aplicacao setasse o GUC num comando e lesse noutro, precisaria de uma
-- transacao explicita e de lembrar de limpar depois — e "lembrar de
-- limpar" e como a fresta ficaria aberta numa conexao devolvida ao pool
-- (decisao 0010, vazamento de GUC no pool). Aqui o Postgres limpa
-- sozinho no fim da instrucao/transacao.
--
-- SECURITY INVOKER (o default) — DE PROPOSITO, e isto e o contrario do
-- que a intuicao pede. Ver o bloco 1(b) do cabecalho: nesta instalacao as
-- migrations rodam como superusuario, entao SECURITY DEFINER faria a
-- funcao ignorar RLS por completo e a unica protecao passaria a ser o
-- texto do corpo dela. Como INVOKER, quem autoriza a leitura e a POLICY,
-- que e verificada pelo motor: mesmo que alguem reescreva esta funcao com
-- um WHERE errado, ela continua incapaz de devolver mais de uma linha,
-- porque a policy so abre a linha cujo e-mail esta no GUC.
-- Dito de outro jeito: esta funcao nao concede nenhum privilegio. Ela e
-- uma conveniencia segura, e nao a fonte da permissao.
--
-- Requer que o chamador tenha SELECT em `usuario` e em `tenant` — o
-- app_aplicacao ja tem os dois (V004 concede SELECT em tenant).
--
-- SET search_path: mesma defesa da V001 contra sequestro por schema no
-- search_path do chamador.
CREATE FUNCTION app_usuario_para_login(p_email text)
RETURNS TABLE (
    usuario_id        uuid,
    usuario_tenant_id uuid,
    usuario_email     text,
    usuario_senha_hash text,
    usuario_nome      text,
    usuario_papel     text,
    usuario_ativo     boolean,
    tenant_ativo      boolean
)
LANGUAGE plpgsql
-- VOLATILE (o default, escrito aqui como ausencia deliberada de STABLE):
-- esta funcao TEM efeito colateral — ela altera um GUC. Marca-la STABLE
-- seria mentir para o planejador sobre isso, e a diferenca de desempenho
-- e irrelevante numa chamada por login. Ver tambem a V001, que e STABLE
-- porque de fato so LE o GUC.
SET search_path = pg_catalog, public
AS $funcao$
DECLARE
    v_email text;
BEGIN
    -- Guarda de coerencia. Se chegar aqui com tenant ja resolvido, a
    -- policy usuario_select_login seria falsa e o retorno seria ZERO
    -- linhas — ou seja, "usuario nao encontrado" para uma pessoa que
    -- existe. Falhar com mensagem explicita evita a hora de depuracao que
    -- esse zero silencioso custaria.
    -- Consequencia pratica: re-autenticacao DENTRO de uma sessao (uma
    -- futura tela de "confirme sua senha") NAO usa esta funcao; ela le a
    -- propria linha pelo caminho normal, com tenant no contexto.
    IF app_current_tenant_id() IS NOT NULL THEN
        RAISE EXCEPTION
            'app_usuario_para_login foi chamada com tenant ja resolvido (%). Esta funcao e o caminho PRE-sessao; dentro de uma sessao leia usuario pelo caminho normal, com o predicado de tenant.',
            app_current_tenant_id();
    END IF;

    v_email := lower(btrim(coalesce(p_email, '')));

    -- E-mail vazio nao abre nada (o nullif da policy ja garantiria isso;
    -- retornar cedo evita a leitura inutil e deixa a intencao visivel).
    IF v_email = '' THEN
        RETURN;
    END IF;

    -- is_local => true: vale ate o fim da transacao corrente. Como o SET
    -- e o SELECT abaixo estao na mesma chamada de funcao, e portanto na
    -- mesma transacao (implicita, se o chamador nao abriu uma), a fresta
    -- fecha sozinha quando a instrucao termina. Nada volta ao pool
    -- carregando este valor.
    PERFORM set_config('app.login_email', v_email, true);

    -- Todas as colunas qualificadas (u./t.) de proposito: os parametros
    -- de saida desta funcao tem nome, e referencia nao qualificada a
    -- `id`/`email` seria ambigua para o plpgsql.
    --
    -- O JOIN com tenant devolve tenant_ativo porque a aplicacao precisa
    -- recusar o login de usuario ativo em loja DESATIVADA. Sem isso, o
    -- fim de um contrato nao tiraria ninguem do sistema: as pessoas
    -- continuariam entrando normalmente numa conta encerrada.
    -- Nao filtramos por ativo aqui: devolvemos os dois flags para que a
    -- aplicacao possa REGISTRAR internamente o motivo exato da recusa
    -- enquanto responde a mesma mensagem generica em todos os casos (ver
    -- armadilhas no fim do arquivo).
    RETURN QUERY
        SELECT u.id, u.tenant_id, u.email, u.senha_hash, u.nome, u.papel,
               u.ativo, t.ativo
          FROM usuario u
          JOIN tenant  t ON t.id = u.tenant_id
         WHERE u.email = v_email;
END;
$funcao$;

COMMENT ON FUNCTION app_usuario_para_login(text) IS
    'Unico caminho de leitura de usuario ANTES de existir sessao. Seta app.login_email como GUC LOCAL e le na mesma instrucao, para que a policy usuario_select_login abra exatamente uma linha e a fresta feche sozinha no fim da transacao. SECURITY INVOKER de proposito: quem autoriza e a policy, nao a funcao (ver bloco 1b da V014).';


-- ---------------------------------------------------------------------
-- Privilegios (GRANT explicito, sempre — decisao 0010)
-- ---------------------------------------------------------------------
-- SELECT na tabela inteira, senha_hash incluido.
--   Nao da para esconder senha_hash por GRANT de coluna sem tornar a
--   funcao de login SECURITY DEFINER, e essa porta esta fechada pelo
--   bloco 1(b). Fica o registro honesto: qualquer consulta autenticada
--   consegue ler o hash BCrypt dos usuarios DO PROPRIO TENANT. A defesa
--   contra o vazamento acidental disso (serializar a entidade numa
--   resposta JSON, imprimir em log) e de aplicacao — ver armadilhas.
GRANT SELECT ON TABLE usuario TO app_aplicacao;

-- INSERT: concedido. "Adicionar um usuario a minha loja" e operacao
--   legitima de aplicacao, e a policy usuario_insert ja a confina ao
--   tenant do contexto. Recusar o INSERT empurraria cada contratacao dos
--   nossos clientes para SQL manual, o que nao se sustenta.
--   ATENCAO: como `papel` NAO e autorizacao nesta fase (ver a coluna),
--   nada no banco impede um ANALISTA de criar um DONO. Quem tem de
--   impedir e o endpoint, e essa regra ainda nao existe. Enquanto nao
--   existir, NAO exponha criacao de usuario na API — a decisao 0023 ja
--   diz que o primeiro usuario vem de provisionamento; o resto e a
--   mesma conversa.
GRANT INSERT ON TABLE usuario TO app_aplicacao;

-- UPDATE POR COLUNA, no molde da V013. As colunas fora da lista sao
-- exclusoes deliberadas, uma a uma:
--
--   email       FORA. E o identificador de login e e unico GLOBALMENTE.
--               Dois efeitos ruins de deixar a aplicacao troca-lo:
--               (1) trocar o e-mail e trocar a identidade da conta — e
--                   operacao de recuperacao, com verificacao, nao um
--                   campo de formulario de perfil;
--               (2) a colisao acontece ENTRE TENANTS. O erro de unique
--                   violation devolvido ao usuario A revelaria que o
--                   endereco ja existe em algum outro cliente nosso —
--                   vazamento por mensagem de erro, que nenhuma policy
--                   de RLS pega, porque a constraint roda abaixo dela.
--               Liberar um e-mail (funcionario que mudou de loja) e
--               provisionamento. Ver bloco 3 do cabecalho.
--   tenant_id   FORA. Mover uma credencial de tenant e dar acesso ao
--               dado de outro cliente. A policy usuario_update (WITH
--               CHECK) ja impediria; a ausencia do GRANT torna a questao
--               irrelevante. Duas camadas, de novo de proposito.
--   id          FORA. Chave.
--   criado_em   FORA. Fato historico nao se reescreve.
--
-- E as que entram, com o caso de uso de cada uma:
--   senha_hash        trocar a propria senha
--   nome              corrigir exibicao
--   ativo             desligar o acesso de quem saiu (decisao 0023)
--   papel             mudar o que a interface mostra
--   ultimo_acesso_em  carimbo do login bem-sucedido
--   atualizado_em     acompanha qualquer uma das anteriores
GRANT UPDATE (senha_hash, nome, ativo, papel, ultimo_acesso_em, atualizado_em)
    ON TABLE usuario TO app_aplicacao;

-- Sem DELETE (convencao 6 da V005). Usuario se desativa. A linha e
-- referenciada por autoria de acoes; apagar a pessoa apagaria o rastro do
-- que ela fez, que e justamente o que a regra 3 do CLAUDE.md precisa
-- preservar.

-- REVOKE de PUBLIC antes do GRANT: o Postgres concede EXECUTE a PUBLIC em
-- toda funcao nova por padrao. Aqui isso importa pouco (a funcao e
-- SECURITY INVOKER e o chamador ainda precisa de SELECT em usuario), mas
-- deixar EXECUTE aberto a PUBLIC significaria que qualquer papel futuro
-- com SELECT nesta tabela — um papel de relatorio, por exemplo — ganharia
-- de graca a capacidade de acionar a policy de login. Fail-closed tambem
-- no privilegio (decisao 0010).
REVOKE ALL ON FUNCTION app_usuario_para_login(text) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION app_usuario_para_login(text) TO app_aplicacao;


-- =====================================================================
-- ARMADILHAS PARA QUEM FOR IMPLEMENTAR O SPRING SECURITY
-- =====================================================================
-- Todas ja custaram tempo de alguem em algum projeto. Estao aqui porque
-- este arquivo e o que sobrevive.
--
-- 1. NAO USE UM `UserDetailsService` QUE FACA `findByEmail` VIA JPA.
--    A entidade `Usuario` vai ter @TenantId (padrao deste projeto), e o
--    Hibernate vai acrescentar o predicado de tenant a consulta. No
--    login nao ha tenant, entao o predicado vira `tenant_id = null` e o
--    resultado e SEMPRE vazio — antes mesmo do RLS entrar em cena. A
--    consulta de autenticacao e
--        SELECT * FROM app_usuario_para_login(?)
--    por JdbcTemplate/JdbcClient, fora do EntityManager.
--
-- 2. A ORDEM DOS FILTROS (decisao 0023, secao "a ordem dos filtros muda").
--    O FiltroTenant precisa rodar DEPOIS da cadeia do Spring Security.
--    Se continuar em HIGHEST_PRECEDENCE, ele roda antes de existir
--    principal, nao acha tenant nenhum e lanca — em toda requisicao,
--    inclusive na de login.
--
-- 3. `ultimo_acesso_em` NO SUCCESS HANDLER FALHA EM SILENCIO.
--    O UPDATE roda com RLS ativo. Se o ContextoTenant ainda nao foi
--    definido quando o handler pega a conexao (e no handler ele
--    provavelmente ainda nao foi — ver a armadilha 2), a policy
--    usuario_update nao casa nenhuma linha e o UPDATE afeta ZERO linhas
--    SEM ERRO. O resultado e a coluna que nunca atualiza e ninguem
--    percebe. Duas providencias, as duas necessarias:
--      - definir o ContextoTenant com o tenant do usuario recem
--        autenticado ANTES de obter a conexao (o GUC e setado no
--        getConnection() do DataSourceComTenant, entao a ordem importa);
--      - conferir o retorno do update e falhar/logar se nao for 1. Um
--        UPDATE que diz ter afetado zero linhas e informacao, nao ruido.
--
-- 4. NAO GUARDE `senha_hash` NA ENTIDADE DE LEITURA GERAL.
--    A aplicacao tem SELECT nessa coluna (ver GRANT), entao nada no banco
--    impede que ela caia num JSON de resposta ou num log de debug. Mapeie
--    o hash apenas no objeto usado pela autenticacao, e nunca no DTO que
--    volta pela API. Nao adianta ter BCrypt custo 12 e imprimir o hash.
--
-- 5. MENSAGEM DE ERRO UNICA PARA OS QUATRO CASOS.
--    "e-mail nao existe", "senha errada", "usuario desativado" e "loja
--    desativada" respondem a mesma coisa ao cliente. Diferenciar
--    transforma o endpoint de login num verificador publico de contas —
--    e daria de graca, pela porta da frente, o oraculo que a policy de
--    login restringe tanto no banco. Registre o motivo real no log
--    interno, nunca na resposta.
--    No mesmo espirito: mantenha `hideUserNotFoundExceptions` no padrao
--    (true) do DaoAuthenticationProvider, que alem de unificar a exceção
--    ainda roda um BCrypt "de mentira" quando o usuario nao existe, para
--    que o TEMPO de resposta tambem nao denuncie a existencia da conta.
--
-- 6. `ativo` E VERIFICADO NO LOGIN, MAS A SESSAO JA ABERTA NAO CAI SOZINHA.
--    Desativar um usuario impede o PROXIMO login; nao expulsa quem ja
--    esta dentro. Como a decisao 0023 cita "desligar o acesso do
--    funcionario que saiu" como razao para sessao em servidor, a promessa
--    so se cumpre se a sessao existente tambem for invalidada. Duas
--    formas, ambas simples: revalidar `ativo` a cada requisicao, ou
--    invalidar as sessoes daquele usuario no momento da desativacao
--    (SessionRegistry). Escolher uma e parte da tarefa 17; nao escolher
--    nenhuma e ter a coluna sem ter o comportamento.
--
-- 7. O TENANT SAI DO USUARIO, MAS O USUARIO PODE ESTAR EM TENANT INATIVO.
--    `tenant.ativo` e devolvido por app_usuario_para_login exatamente
--    para isso. Ignorar esse campo significa que encerrar um contrato nao
--    tira ninguem de dentro do sistema.
--
-- 8. PROVISIONAR O PRIMEIRO USUARIO EXIGE O GUC — OU NAO EXIGE, DEPENDE
--    DE QUEM CONECTA. Com FORCE ROW LEVEL SECURITY, um INSERT feito pelo
--    DONO da tabela tambem passa pela policy: sem `app.tenant_id` setado,
--    ele e recusado. Rodando como SUPERUSUARIO (o caso do psql de dev), o
--    RLS e ignorado e o INSERT passa. Ou seja, o mesmo script pode
--    funcionar ou falhar dependendo do papel — se falhar, nao e bug:
--        SELECT set_config('app.tenant_id', '<uuid-do-tenant>', false);
--        INSERT INTO usuario (tenant_id, email, senha_hash, nome, papel)
--        VALUES ('<uuid>', 'dona@loja.com.br', '<bcrypt custo 12>',
--                'Nome da Dona', 'DONO');
--    O hash e gerado FORA do banco (BCryptPasswordEncoder(12) ou htpasswd
--    -B -C 12). Nao existe funcao de BCrypt aqui de proposito: gerar hash
--    no banco faria a senha em claro passar pelo log de statements do
--    Postgres.
--
-- 9. NAO CHAME A FUNCAO DE LOGIN DENTRO DE UMA TRANSACAO LONGA.
--    O GUC e LOCAL, ou seja, vale ate o fim da TRANSACAO — nao ate o fim
--    da funcao. Numa chamada solta (autocommit) isso e uma instrucao e
--    acabou. Se a autenticacao rodar dentro de um @Transactional que
--    continua fazendo outras coisas, a fresta fica aberta para AQUELE
--    e-mail durante todo o resto da transacao. Nao vaza para o pool (o
--    fim da transacao limpa) e nao alcanca outra linha, mas o principio
--    e manter a janela do tamanho da operacao: a leitura de login e uma
--    chamada curta e independente.
--
-- 10. TESTE DE ISOLAMENTO DESTA TABELA — o caso que nao pode faltar:
--    com o contexto no tenant A e `app.login_email` apontando para o
--    e-mail de um usuario do tenant B, a leitura tem de devolver ZERO
--    linhas (a policy de login exige tenant NULL). E o caso que prova que
--    a fresta nao vira travessia lateral dentro de uma sessao.
-- =====================================================================
