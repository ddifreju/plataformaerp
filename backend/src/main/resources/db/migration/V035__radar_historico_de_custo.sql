-- =====================================================================
-- V035 — Histórico de custos dos produtos
-- =====================================================================
-- Cada mudança de custo de um produto vira uma linha: valor antes, valor
-- depois, motivo, quem e quando (regra 3: todo número rastreável).
--
-- Quem grava é um gatilho em radar_produto, para não depender de cada
-- caminho do código (cadastro, edição, lote, recebimento de compra,
-- importação, clonagem). O usuário vem de app.usuario_id, que o Radar
-- define no começo de cada comando; fora de comando fica nulo.
-- "Iniciar histórico de custos" (botão da lista) grava o custo de hoje
-- como ponto de partida dos produtos que ainda não têm histórico.
--
-- Só se acrescenta: sem UPDATE nem DELETE para a aplicação.
-- =====================================================================

CREATE TABLE radar_custo_historico (
    id          uuid          PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   uuid          NOT NULL REFERENCES tenant(id),
    produto_id  uuid          NOT NULL,
    antes       numeric(18,2),
    depois      numeric(18,2),
    motivo      varchar(60)   NOT NULL,
    usuario_id  uuid,
    criado_em   timestamptz   NOT NULL DEFAULT now(),
    -- Produto excluído de vez leva o histórico junto (a lixeira não exclui).
    FOREIGN KEY (tenant_id, produto_id) REFERENCES radar_produto (tenant_id, id) ON DELETE CASCADE
);
CREATE INDEX ON radar_custo_historico (tenant_id, produto_id, criado_em);
CREATE INDEX ON radar_custo_historico (tenant_id, criado_em);

ALTER TABLE radar_custo_historico ENABLE ROW LEVEL SECURITY;
ALTER TABLE radar_custo_historico FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON radar_custo_historico
 USING (tenant_id = app_current_tenant_id())
 WITH CHECK (tenant_id = app_current_tenant_id());
GRANT SELECT, INSERT ON radar_custo_historico TO app_aplicacao;

CREATE FUNCTION radar_registra_custo() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'UPDATE' AND OLD.custo IS NOT DISTINCT FROM NEW.custo THEN
        RETURN NEW;
    END IF;
    IF TG_OP = 'INSERT' AND NEW.custo IS NULL THEN
        RETURN NEW;
    END IF;
    INSERT INTO radar_custo_historico (tenant_id, produto_id, antes, depois, motivo, usuario_id)
    VALUES (
        NEW.tenant_id,
        NEW.id,
        CASE WHEN TG_OP = 'UPDATE' THEN OLD.custo END,
        NEW.custo,
        CASE
            WHEN TG_OP = 'INSERT' THEN 'Cadastro do produto'
            -- Recebimento de compra soma ao físico e recalcula o custo médio no mesmo UPDATE.
            WHEN NEW.fisico > OLD.fisico THEN 'Compra recebida (custo médio)'
            ELSE 'Alteração do custo'
        END,
        nullif(current_setting('app.usuario_id', true), '')::uuid
    );
    RETURN NEW;
END;
$$;

CREATE TRIGGER radar_produto_custo_historico
 AFTER INSERT OR UPDATE OF custo ON radar_produto
 FOR EACH ROW EXECUTE FUNCTION radar_registra_custo();
