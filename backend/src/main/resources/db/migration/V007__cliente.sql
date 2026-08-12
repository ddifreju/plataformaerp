-- =====================================================================
-- V007 — cliente (DADO PESSOAL — leia o bloco de LGPD antes de mexer)
-- =====================================================================
-- Convencoes gerais da Fase 1: cabecalho da V005.
--
-- ---------------------------------------------------------------------
-- LGPD: esta e a tabela mais sensivel do sistema
-- ---------------------------------------------------------------------
-- Toda coluna aqui e dado pessoal de um terceiro que nunca contratou
-- nada conosco: ele comprou do nosso cliente. Somos OPERADOR (LGPD
-- Art. 5, VII); o lojista e o controlador. Isso muda o desenho:
--
-- 1. MINIMIZACAO (Art. 6, III). Guardamos o que a operacao exige e nada
--    alem. Concretamente, o que NAO existe nesta tabela e nao deve ser
--    adicionado sem decisao registrada:
--      - endereco completo (rua, numero, complemento). Nos ANALISAMOS
--        venda, nao despachamos encomenda. O que a analise de frete e de
--        ICMS interestadual precisa e CEP/cidade/UF, e isso fica no
--        PEDIDO (V008), nao no cliente: e atributo daquela entrega, nao
--        da pessoa.
--      - data de nascimento, genero, RG.
--
-- 2. DOCUMENTO (CPF/CNPJ): NAO guardamos em claro. Ver o bloco da coluna
--    documento_hash abaixo — a decisao e HMAC com chave fora do banco,
--    nao SHA-256 puro, e o motivo esta explicado la.
--
-- 3. EXCLUSAO A PEDIDO DO TITULAR (Art. 18, VI) x GUARDA FISCAL. Os dois
--    convivem: apagar a LINHA destruiria o historico de venda, que o
--    lojista e obrigado a guardar (Art. 16, I — cumprimento de obrigacao
--    legal). Por isso a exclusao aqui e ANONIMIZACAO:
--      UPDATE cliente SET nome = NULL, email = NULL, telefone = NULL,
--                         documento_hash = NULL, documento_mascarado = NULL,
--                         apelido_origem = NULL, dados_origem = '{}'::jsonb,
--                         anonimizado_em = now()
--      WHERE id = ...;
--    O pedido continua apontando para a mesma linha, os numeros do
--    faturamento nao mudam, e a pessoa deixa de ser identificavel.
--    E por isso tambem que a aplicacao NAO tem DELETE aqui: DELETE seria
--    o caminho errado disponivel ao lado do caminho certo.
--
-- 4. dados_origem NESTA TABELA carrega dado pessoal (o payload do
--    marketplace vem com nome, apelido, as vezes telefone). Ele entra na
--    anonimizacao acima. Nao e "campo tecnico".
--
-- 5. PENDENTE (docs/PENDENCIAS.md): retencao e base legal. Por quanto
--    tempo guardar cliente sem pedido recente, e sob qual base legal, e
--    decisao de negocio/juridica, nao tecnica — mesma situacao ja
--    registrada para consulta_auditada na decisao 0012. Nao bloqueia a
--    Fase 1, mas bloqueia rodar com cliente real.
--
-- 6. NAO MODELADO DE PROPOSITO: consentimento de marketing / opt-in de
--    WhatsApp. Disparo ativo por WhatsApp exige opt-in e o desenho disso
--    depende do BSP e da politica do lojista (decisao de negocio). Quando
--    entrar, entra como tabela propria de consentimento com data, origem
--    e texto aceito — nao como boolean solto aqui, que nao prova nada.
-- =====================================================================

CREATE TABLE cliente (
    id                    uuid        NOT NULL DEFAULT gen_random_uuid(),
    tenant_id             uuid        NOT NULL,

    -- Origem (convencao 4 da V005). canal_id NULL = cadastro proprio.
    canal_id              uuid,
    id_externo            text,

    -- tipo: pessoa fisica ou juridica. Muda tratamento fiscal (e, na
    -- pratica, se o "dado pessoal" e mesmo pessoal: CNPJ de empresa nao e
    -- dado pessoal, CPF de MEI e).
    tipo                  text        NOT NULL DEFAULT 'PESSOA_FISICA',

    -- nome: NULL depois de anonimizado, e NULL tambem quando a fonte nao
    -- entrega (Mercado Livre so expoe o apelido em varias operacoes).
    -- Preencher com "Cliente ML" seria inventar dado (regra 5 do
    -- CLAUDE.md). Se nao veio, fica NULL.
    nome                  text,

    -- apelido_origem: o identificador publico usado pela fonte (nickname
    -- do ML, username da Shopee). Coluna propria porque nao e o nome da
    -- pessoa e nao deve ser exibido como se fosse.
    apelido_origem        text,

    email                 text,
    -- telefone em formato E.164 quando possivel (+5511999998888). Nao ha
    -- CHECK de formato rigido: rejeitar telefone mal formatado da fonte
    -- perderia o dado inteiro, e perder e pior que guardar torto.
    telefone              text,

    documento_tipo        text,

    -- documento_hash: HMAC-SHA256 do documento (SO OS DIGITOS), com chave
    -- secreta guardada FORA do banco (variavel de ambiente / cofre).
    --
    -- PORQUE HASH E NAO O DOCUMENTO EM CLARO: o unico uso real que temos
    -- para CPF/CNPJ e RECONHECER que o comprador do Mercado Livre e o
    -- mesmo da loja propria. Isso e igualdade, e igualdade nao precisa do
    -- valor. Nos nao emitimos nota fiscal; se um dia emitirmos, o
    -- documento em claro entra por migration dedicada, com cifragem em
    -- repouso e registro de acesso — decisao consciente, nunca efeito
    -- colateral.
    --
    -- PORQUE HMAC E NAO SHA-256 PURO: o espaco de CPF tem ~10^11 valores
    -- validos. Um SHA-256 sem segredo e revertido por forca bruta em
    -- minutos num notebook — seria minimizacao de mentira. Com HMAC, quem
    -- obtiver apenas um dump do banco nao consegue reverter, porque a
    -- chave nao esta no banco. Usar tenant_id como "sal" NAO resolveria
    -- isso: o tenant_id esta na mesma linha.
    -- A chave e por INSTALACAO. Trocar a chave invalida todos os hashes e
    -- exige recalculo a partir da fonte — anote isso antes de trocar.
    documento_hash        text,

    -- documento_mascarado: para o humano conferir na tela sem que o valor
    -- completo exista no banco. Ex.: '***.456.789-**'.
    documento_mascarado   text,

    -- anonimizado_em: marca de exclusao a pedido do titular (Art. 18, VI).
    -- A linha sobrevive porque o pedido depende dela; a pessoa, nao.
    anonimizado_em        timestamptz,

    dados_origem          jsonb       NOT NULL DEFAULT '{}'::jsonb,
    sincronizado_em       timestamptz,
    criado_em             timestamptz NOT NULL DEFAULT now(),
    atualizado_em         timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT pk_cliente PRIMARY KEY (id),
    CONSTRAINT uq_cliente_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_cliente_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (id),
    CONSTRAINT fk_cliente_canal
        FOREIGN KEY (tenant_id, canal_id) REFERENCES canal (tenant_id, id),

    CONSTRAINT ck_cliente_tipo CHECK (tipo IN ('PESSOA_FISICA', 'PESSOA_JURIDICA')),
    CONSTRAINT ck_cliente_documento_tipo
        CHECK (documento_tipo IS NULL OR documento_tipo IN ('CPF', 'CNPJ')),
    -- HMAC-SHA256 em hexadecimal: exatamente 64 caracteres. O CHECK
    -- existe para pegar cedo o erro de alguem gravar o documento em claro
    -- nesta coluna por engano — o valor em claro nunca passa neste teste.
    CONSTRAINT ck_cliente_documento_hash_formato
        CHECK (documento_hash IS NULL OR documento_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_cliente_id_externo_nao_vazio
        CHECK (id_externo IS NULL OR btrim(id_externo) <> ''),
    -- E-mail: validacao minima (tem arroba, sem espaco). Validacao
    -- rigorosa de e-mail em CHECK e armadilha classica — rejeita endereco
    -- valido e exotico e ainda assim aceita invalido. O objetivo aqui e
    -- so barrar lixo obvio de parsing.
    CONSTRAINT ck_cliente_email_formato
        CHECK (email IS NULL OR (email ~ '^[^[:space:]@]+@[^[:space:]@]+$'))
);

COMMENT ON TABLE  cliente IS
    'Comprador. DADO PESSOAL: somos operador, o lojista e controlador. Exclusao a pedido do titular e ANONIMIZACAO (anonimizado_em), nunca DELETE - o historico fiscal do pedido precisa sobreviver.';
COMMENT ON COLUMN cliente.tenant_id IS
    'Parte da identidade do registro. Sem DEFAULT de proposito: bug de contexto deve virar erro, nao linha gravada.';
COMMENT ON COLUMN cliente.nome IS
    'NULL quando a fonte nao entrega (o ML costuma expor so o apelido) ou depois de anonimizado. Nunca preencher com valor inventado.';
COMMENT ON COLUMN cliente.apelido_origem IS
    'Identificador publico na fonte (nickname). Nao e o nome da pessoa e nao deve ser exibido como se fosse.';
COMMENT ON COLUMN cliente.documento_hash IS
    'HMAC-SHA256 (hex) dos digitos do CPF/CNPJ, com chave fora do banco. Serve so para casar o mesmo comprador entre canais. SHA-256 puro seria reversivel por forca bruta no espaco de CPF - por isso HMAC.';
COMMENT ON COLUMN cliente.documento_mascarado IS
    'Forma mascarada para conferencia humana. O documento completo nao existe no banco.';
COMMENT ON COLUMN cliente.anonimizado_em IS
    'Quando o titular exerceu o direito de exclusao (LGPD Art. 18, VI). Linha preservada para nao destruir o historico de venda (Art. 16, I).';
COMMENT ON COLUMN cliente.dados_origem IS
    'Campo de extensao (decisao 0002). ATENCAO: nesta tabela carrega dado pessoal e entra na rotina de anonimizacao.';

-- Casar o mesmo comprador entre canais: este e o caminho quente da
-- deduplicacao de cliente. Parcial porque a maioria das linhas nao tem
-- documento (marketplace raramente entrega).
CREATE INDEX ix_cliente_tenant_documento_hash
    ON cliente (tenant_id, documento_hash)
    WHERE documento_hash IS NOT NULL;

-- Segundo criterio de casamento, e busca no atendimento.
CREATE INDEX ix_cliente_tenant_email
    ON cliente (tenant_id, email)
    WHERE email IS NOT NULL;

-- Reingestao idempotente: o mesmo comprador da mesma fonte nao duplica.
CREATE UNIQUE INDEX uq_cliente_origem
    ON cliente (tenant_id, canal_id, id_externo)
    WHERE id_externo IS NOT NULL;

ALTER TABLE cliente ENABLE ROW LEVEL SECURITY;
ALTER TABLE cliente FORCE  ROW LEVEL SECURITY;

CREATE POLICY cliente_select ON cliente
    FOR SELECT
    USING (tenant_id = app_current_tenant_id());

CREATE POLICY cliente_insert ON cliente
    FOR INSERT
    WITH CHECK (tenant_id = app_current_tenant_id());

CREATE POLICY cliente_update ON cliente
    FOR UPDATE
    USING (tenant_id = app_current_tenant_id())
    WITH CHECK (tenant_id = app_current_tenant_id());

CREATE POLICY cliente_delete ON cliente
    FOR DELETE
    USING (tenant_id = app_current_tenant_id());

-- Sem DELETE, e aqui isso e decisao de LGPD, nao so de historico: o
-- caminho correto para atender o titular e a anonimizacao (UPDATE). Ter
-- DELETE disponivel ao lado significaria, mais cedo ou mais tarde,
-- alguem apagando a linha e levando junto o faturamento do pedido.
GRANT SELECT, INSERT, UPDATE ON TABLE cliente TO app_aplicacao;
