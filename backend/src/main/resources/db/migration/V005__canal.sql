-- =====================================================================
-- V005 — canal (a fonte do dado, e o lugar onde a venda acontece)
-- =====================================================================
-- PRIMEIRA migration da Fase 1. Alem de criar a tabela canal, este
-- cabecalho fixa as CONVENCOES que V006 a V012 repetem sem discutir de
-- novo. Quem for ler o modelo canonico inteiro comeca por aqui.
--
-- ---------------------------------------------------------------------
-- CONVENCAO 1 — molde de RLS (decisao 0010), integral, em toda tabela
-- ---------------------------------------------------------------------
--   tenant_id uuid NOT NULL REFERENCES tenant (id), SEM DEFAULT
--   ENABLE + FORCE ROW LEVEL SECURITY
--   quatro policies (select/insert/update/delete) com app_current_tenant_id()
--   GRANT explicito para app_aplicacao
--   indice com tenant_id na PRIMEIRA posicao
-- Nao existe excecao. O teste RlsAtivoEmTodasAsTabelasTest descobre
-- qualquer tabela nova com coluna tenant_id e quebra se faltar parte.
--
-- ---------------------------------------------------------------------
-- CONVENCAO 2 — tenant_id faz parte da CHAVE, nao e coluna acessoria
-- ---------------------------------------------------------------------
-- Toda tabela tem, alem da PK em id:
--     CONSTRAINT uq_<tabela>_tenant_id UNIQUE (tenant_id, id)
-- Ela parece redundante (id sozinho ja e unico) e nao e. Faz tres coisas:
--   a) e o INDICE com tenant_id na primeira posicao que a 0010 exige;
--   b) e o ALVO das foreign keys COMPOSTAS entre tabelas de dados;
--   c) documenta no schema que a identidade e (tenant, id).
--
-- As FKs entre tabelas de dados sao SEMPRE compostas:
--     FOREIGN KEY (tenant_id, pedido_id) REFERENCES pedido (tenant_id, id)
-- PORQUE: uma FK simples (pedido_id -> pedido.id) e verificada pelo
-- Postgres por dentro, IGNORANDO RLS. Um bug de contexto conseguiria
-- gravar um item_pedido do tenant A apontando para um pedido do tenant B,
-- e o banco aceitaria calado. Com a FK composta isso vira erro de
-- integridade referencial. Custo em JPA: ZERO — a FK composta e uma
-- restricao de banco; o @ManyToOne continua mapeando so a coluna do id.
--
-- Excecao unica: a FK para `tenant` e simples (tenant_id -> tenant.id),
-- porque tenant e a raiz e nao tem coluna tenant_id (ver V002).
--
-- ---------------------------------------------------------------------
-- CONVENCAO 3 — dinheiro e NUMERIC(18,4). Sempre. Sem excecao.
-- ---------------------------------------------------------------------
-- Regra 2 do CLAUDE.md: dinheiro nunca e float. Aqui a escala tambem e
-- decidida de uma vez: NUMERIC(18,4).
--   PORQUE 4 CASAS e nao 2: o produto inteiro depende de rateio (taxa
--   percentual do marketplace por item, custo de Ads dividido entre
--   pedidos, imposto proporcional). Com 2 casas, cada rateio intermediario
--   perde ate meio centavo, e a soma de 300 itens erra reais. Com 4 casas,
--   o erro intermediario fica tres ordens de grandeza abaixo do centavo.
--   O arredondamento para 2 casas acontece UMA VEZ, na apresentacao, com
--   RoundingMode declarado (HALF_UP) — nunca no meio do calculo.
--   PORQUE 18 DIGITOS: 14 digitos inteiros aguentam qualquer faturamento
--   real com folga, e cabe em NUMERIC sem custo relevante.
--
-- NOME DA COLUNA MONETARIA diz o que ela e:
--   valor_unitario_*  -> por unidade, antes de multiplicar pela quantidade
--   valor_bruto_*     -> antes de desconto/taxa
--   valor_liquido_*   -> depois de desconto/taxa
--   valor_total_*     -> agregado da linha ou do documento
-- Quando o nome nao bastar, o COMMENT ON COLUMN resolve a ambiguidade.
-- Toda coluna monetaria tem COMMENT dizendo bruto/liquido/unitario.
--
-- QUANTIDADE tambem nao e float: NUMERIC(14,4). Nao e integer porque
-- existe venda fracionada (granel, metro, kg) e porque devolucao parcial
-- de kit as vezes chega fracionada da fonte.
--
-- MOEDA: toda tabela que agrega dinheiro carrega `moeda char(3)` com
-- default 'BRL'. Marketplace internacional existe (ML CBT, Shopee cross
-- border) e Ads as vezes e cobrado em USD. Guardar a moeda junto do valor
-- custa 3 bytes e evita a pior classe de bug financeiro: somar moedas
-- diferentes sem perceber.
--
-- ---------------------------------------------------------------------
-- CONVENCAO 4 — origem do dado e campo de extensao (decisao 0002)
-- ---------------------------------------------------------------------
-- Toda entidade que pode vir de fora carrega:
--   canal_id        -> de qual integracao veio (NULL = criado aqui dentro)
--   id_externo      -> o identificador NA FONTE (text: ML usa numero,
--                      Shopee usa order_sn alfanumerico, Bling usa outro)
--   dados_origem    -> jsonb com o payload especifico da fonte que NAO
--                      coube no modelo canonico
--   sincronizado_em -> quando lemos a fonte pela ultima vez
--
-- `dados_origem` E O MOAT (decisao 0002). Uma devolucao do Mercado Livre,
-- da Shopee e da loja propria viram a MESMA linha de `devolucao`; o que e
-- especifico de cada uma sobrevive em dados_origem sem poluir o nucleo.
-- Trocar de fonte passa a ser trocar um adaptador. Sem esse campo, cada
-- fonte nova exigiria coluna nova — e o produto viraria refem das fontes.
-- Regra de uso: se um dado do payload passa a ser CONSULTADO por regra de
-- negocio, ele sai do jsonb e vira coluna, com migration. jsonb e deposito
-- do que nao consultamos, nao atalho para nao modelar.
--
-- Alem de dados_origem, guardamos tambem o STATUS CRU da fonte
-- (`status_origem`, `motivo_origem`). Regra 5 do CLAUDE.md: nunca invente
-- dado. Se o mapeamento canonico estiver errado, o valor original ainda
-- esta la para corrigir sem re-ingerir.
--
-- ---------------------------------------------------------------------
-- CONVENCAO 5 — status e dominio: CHECK sobre text, nao ENUM nativo
-- ---------------------------------------------------------------------
-- Escolhido: coluna text + CONSTRAINT CHECK com a lista de valores.
--   Contra ENUM NATIVO do Postgres: ALTER TYPE ... ADD VALUE nao pode ser
--   revertido (nao existe DROP VALUE), o que quebra a decisao 0006 (undo
--   pareado) na primeira vez que um status novo aparecer. E o valor novo
--   nao pode ser usado na mesma transacao em que foi criado.
--   Contra TABELA DE DOMINIO: obrigaria um join em toda leitura, ou uma
--   tabela sem tenant_id (excecao ao molde da 0010) para ser compartilhada.
--   Nao paga o preco para um dominio fechado de ~8 valores.
--   A favor do CHECK: le-se o dominio inteiro no proprio DDL, o undo e um
--   DROP CONSTRAINT, o grep encontra tudo, e o mapeamento em Java e
--   @Enumerated(EnumType.STRING) direto, sem conversor.
-- Custo aceito e registrado: incluir um status novo exige migration
-- (ALTER TABLE ... DROP CONSTRAINT / ADD CONSTRAINT). Isso e desejavel:
-- status canonico novo e mudanca de modelo, nao detalhe de configuracao.
--
-- ---------------------------------------------------------------------
-- CONVENCAO 6 — a aplicacao nao apaga dado de negocio
-- ---------------------------------------------------------------------
-- app_aplicacao recebe SELECT, INSERT, UPDATE nas tabelas de negocio.
-- NAO recebe DELETE. Cancelamento, devolucao, desativacao e exclusao a
-- pedido do titular (LGPD) sao ESTADO — coluna `ativo`, `cancelado_em`,
-- `anonimizado_em` — nunca remocao de linha. Historico fiscal e contabil
-- precisa sobreviver, e um DELETE acidental de pedido nao tem undo.
-- Mesmo racional do append-only de consulta_auditada (V003/0010).
-- Excecao unica: evento_ingerido (V012) recebe DELETE, porque e dado
-- operacional descartavel com politica de retencao.
-- As policies de DELETE existem em todas as tabelas assim mesmo, como
-- defesa em profundidade: se um dia o GRANT mudar, o RLS ja esta no lugar.
--
-- ---------------------------------------------------------------------
-- CONVENCAO 7 — tempo
-- ---------------------------------------------------------------------
-- Sempre timestamptz, nunca timestamp. Marketplace devolve horario em
-- offset proprio (ML manda -03:00 explicito, Shopee manda epoch UTC);
-- `timestamp` sem fuso jogaria fora essa informacao e faria "vendas de
-- ontem" mudar de dia conforme o servidor. Comparacao e agregacao por
-- periodo assumem America/Sao_Paulo na apresentacao, nao no
-- armazenamento.
-- Toda tabela tem criado_em e atualizado_em (quando a NOSSA linha mudou),
-- separados de sincronizado_em (quando lemos a fonte). Sao coisas
-- diferentes e confundi-las esconde atraso de integracao.
-- =====================================================================


-- ---------------------------------------------------------------------
-- canal
-- ---------------------------------------------------------------------
-- Uma linha por INTEGRACAO CONFIGURADA de um tenant, nao por tipo de
-- sistema. Um lojista com duas contas de Mercado Livre tem dois canais.
--
-- Cobre canal de VENDA (ML, Shopee, loja propria), de DADO (ERP) e de
-- COMUNICACAO (WhatsApp, e-mail). Uma tabela so, porque a pergunta que o
-- produto responde e sempre a mesma: "de onde veio este dado?". Separar em
-- tres tabelas obrigaria toda entidade a ter tres FKs opcionais.
-- O Mercado Livre e os tres papeis ao mesmo tempo (vende, e a fonte do
-- pedido, e o cliente manda mensagem por la) — o que confirma a escolha.
--
-- SEGREDO NAO MORA AQUI. Nao existe coluna de token, client_secret ou
-- senha nesta tabela e nao vai existir. CLAUDE.md: "nenhum segredo em
-- codigo, sempre variavel de ambiente". Credencial de integracao vive
-- fora do banco (variavel de ambiente / cofre), referenciada por
-- `chave_credencial`, que guarda apenas o NOME da variavel/segredo.
CREATE TABLE canal (
    id                uuid        NOT NULL DEFAULT gen_random_uuid(),

    -- Parte da identidade. Sem DEFAULT de proposito (decisao 0010): bug
    -- de contexto deve virar erro, nunca linha gravada em silencio.
    tenant_id         uuid        NOT NULL,

    -- codigo: identificador estavel do canal DENTRO do tenant, escolhido
    -- por quem configura ("ml-principal", "shopee-loja2"). E por ele que
    -- o adaptador encontra o canal sem depender de uuid em arquivo de
    -- configuracao. Unico por tenant.
    codigo            text        NOT NULL,
    nome              text        NOT NULL,

    -- tipo: QUAL sistema e. Cresce com o tempo (cada valor novo = uma
    -- migration, ver convencao 5).
    tipo              text        NOT NULL,

    -- categoria: QUE PAPEL o sistema cumpre. Existe separada de `tipo`
    -- porque a regra de negocio pergunta pelo papel, nao pelo produto:
    -- "comissao so se aplica a MARKETPLACE", "conversa so vem de
    -- MARKETPLACE ou COMUNICACAO". Sem ela, toda regra viraria uma lista
    -- de tipos repetida em varios lugares.
    categoria         text        NOT NULL,

    -- id_externo: identificacao da CONTA na fonte (seller_id do ML,
    -- shop_id da Shopee, id da empresa no Bling). Nao e o id de um
    -- registro; e o id da conta que este canal representa.
    id_externo        text,

    -- chave_credencial: NOME da variavel de ambiente / entrada de cofre
    -- que guarda o segredo deste canal. O valor NUNCA entra aqui.
    chave_credencial  text,

    ativo             boolean     NOT NULL DEFAULT true,

    -- dados_origem: campo de extensao (decisao 0002, convencao 4).
    -- Aqui guarda configuracao NAO SECRETA especifica da fonte: site_id
    -- do ML, region da Shopee, versao de API, flags do adaptador.
    dados_origem      jsonb       NOT NULL DEFAULT '{}'::jsonb,

    sincronizado_em   timestamptz,
    criado_em         timestamptz NOT NULL DEFAULT now(),
    atualizado_em     timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT pk_canal PRIMARY KEY (id),
    -- Identidade composta: indice exigido pela 0010 e alvo das FKs
    -- compostas das demais tabelas (convencao 2).
    CONSTRAINT uq_canal_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_canal_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (id),

    CONSTRAINT uq_canal_codigo UNIQUE (tenant_id, codigo),
    CONSTRAINT ck_canal_codigo_formato
        CHECK (codigo ~ '^[a-z0-9][a-z0-9-]{1,62}[a-z0-9]$'),
    CONSTRAINT ck_canal_nome_nao_vazio CHECK (btrim(nome) <> ''),

    -- Dominio de tipo. 'OUTRO' e escape hatch consciente: e melhor
    -- ingerir com 'OUTRO' do que perder o dado (regra 5 do CLAUDE.md).
    -- Aparecer 'OUTRO' em producao e sinal de que falta um valor proprio
    -- na proxima migration, nao um lugar para morar para sempre.
    CONSTRAINT ck_canal_tipo CHECK (tipo IN (
        'MERCADO_LIVRE',
        'SHOPEE',
        'AMAZON',
        'MAGALU',
        'AMERICANAS',
        'SHOPIFY',
        'NUVEMSHOP',
        'WOOCOMMERCE',
        'LOJA_PROPRIA',
        'ERP_BLING',
        'ERP_TINY',
        'WHATSAPP',
        'EMAIL',
        'INSTAGRAM',
        'OUTRO'
    )),
    CONSTRAINT ck_canal_categoria CHECK (categoria IN (
        'MARKETPLACE',
        'LOJA_PROPRIA',
        'ERP',
        'COMUNICACAO'
    )),
    CONSTRAINT ck_canal_id_externo_nao_vazio
        CHECK (id_externo IS NULL OR btrim(id_externo) <> '')
);

COMMENT ON TABLE  canal IS
    'Integracao configurada de um tenant. Responde "de onde veio este dado". Cobre canal de venda, de dado (ERP) e de comunicacao. Nunca guarda segredo.';
COMMENT ON COLUMN canal.tenant_id IS
    'Parte da identidade do registro. Sem DEFAULT de proposito: bug de contexto deve virar erro, nao linha gravada.';
COMMENT ON COLUMN canal.codigo IS
    'Identificador estavel do canal dentro do tenant, usado pelos adaptadores em configuracao. Unico por tenant.';
COMMENT ON COLUMN canal.tipo IS
    'Qual sistema e (MERCADO_LIVRE, SHOPEE, ERP_BLING...). Valor novo exige migration - ver convencao 5 na V005.';
COMMENT ON COLUMN canal.categoria IS
    'Que papel o sistema cumpre (MARKETPLACE, LOJA_PROPRIA, ERP, COMUNICACAO). A regra de negocio pergunta pelo papel, nao pelo produto.';
COMMENT ON COLUMN canal.id_externo IS
    'Identificacao da CONTA na fonte (seller_id, shop_id). Nao e id de registro.';
COMMENT ON COLUMN canal.chave_credencial IS
    'NOME da variavel de ambiente ou entrada de cofre com o segredo deste canal. O segredo em si nunca e gravado no banco.';
COMMENT ON COLUMN canal.dados_origem IS
    'Campo de extensao (decisao 0002): configuracao nao secreta especifica da fonte. E o que permite trocar a fonte sem reescrever o produto.';
COMMENT ON COLUMN canal.sincronizado_em IS
    'Ultima leitura bem-sucedida da fonte. Diferente de atualizado_em, que e quando ESTA linha mudou.';


-- ---------------------------------------------------------------------
-- Indices
-- ---------------------------------------------------------------------
-- uq_canal_tenant_id e uq_canal_codigo ja cobrem tenant_id na primeira
-- posicao. O indice abaixo atende o caminho quente do adaptador:
-- "quais canais ativos deste tenant preciso sincronizar agora".
-- Parcial em ativo porque canal desativado nunca entra nessa varredura, e
-- indice parcial nao paga por linha que nao interessa.
CREATE INDEX ix_canal_tenant_categoria_ativo
    ON canal (tenant_id, categoria)
    WHERE ativo;


-- ---------------------------------------------------------------------
-- Row Level Security — molde da decisao 0010, integral
-- ---------------------------------------------------------------------
ALTER TABLE canal ENABLE ROW LEVEL SECURITY;
-- FORCE, nao so ENABLE: sem FORCE o DONO da tabela escapa das policies,
-- e o teste de isolamento rodando como dono passaria falsamente.
ALTER TABLE canal FORCE ROW LEVEL SECURITY;

CREATE POLICY canal_select ON canal
    FOR SELECT
    USING (tenant_id = app_current_tenant_id());

CREATE POLICY canal_insert ON canal
    FOR INSERT
    WITH CHECK (tenant_id = app_current_tenant_id());

-- USING + WITH CHECK: so com USING seria possivel alcancar a propria
-- linha e reescrever o tenant_id dela, "doando" o registro a outro tenant.
CREATE POLICY canal_update ON canal
    FOR UPDATE
    USING (tenant_id = app_current_tenant_id())
    WITH CHECK (tenant_id = app_current_tenant_id());

CREATE POLICY canal_delete ON canal
    FOR DELETE
    USING (tenant_id = app_current_tenant_id());


-- ---------------------------------------------------------------------
-- Privilegios (GRANT explicito, sempre — decisao 0010)
-- ---------------------------------------------------------------------
-- Sem DELETE: canal se desativa (ativo = false), nao se apaga. Apagar
-- canal com pedido historico atras dele quebraria a rastreabilidade da
-- origem do dado. Ver convencao 6 no topo deste arquivo.
GRANT SELECT, INSERT, UPDATE ON TABLE canal TO app_aplicacao;
