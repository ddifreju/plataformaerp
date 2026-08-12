-- =====================================================================
-- U004 — Desfaz V004__papel_aplicacao.sql
-- =====================================================================
-- Primeiro undo a rodar (ordem decrescente: U004, U003, U002, U001).
-- Os GRANTs precisam sair antes das tabelas, senao o DROP ROLE falha
-- com "role cannot be dropped because some objects depend on it".
--
-- Depois de aplicar, limpe o historico do Flyway:
--     DELETE FROM flyway_schema_history WHERE version = '004';
-- =====================================================================

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_catalog.pg_roles WHERE rolname = 'app_aplicacao') THEN

        -- Revogacoes espelhando exatamente os GRANTs da V004.
        -- IF EXISTS nas tabelas porque o undo pode ser rodado em base
        -- onde U003/U002 ja passaram (fora da ordem recomendada).
        IF EXISTS (SELECT 1 FROM pg_catalog.pg_class c
                   JOIN pg_catalog.pg_namespace n ON n.oid = c.relnamespace
                   WHERE c.relname = 'consulta_auditada' AND n.nspname = 'public') THEN
            REVOKE SELECT, INSERT ON TABLE public.consulta_auditada FROM app_aplicacao;
        END IF;

        IF EXISTS (SELECT 1 FROM pg_catalog.pg_class c
                   JOIN pg_catalog.pg_namespace n ON n.oid = c.relnamespace
                   WHERE c.relname = 'tenant' AND n.nspname = 'public') THEN
            REVOKE SELECT ON TABLE public.tenant FROM app_aplicacao;
        END IF;

        IF EXISTS (SELECT 1 FROM pg_catalog.pg_proc p
                   JOIN pg_catalog.pg_namespace n ON n.oid = p.pronamespace
                   WHERE p.proname = 'app_current_tenant_id' AND n.nspname = 'public') THEN
            REVOKE EXECUTE ON FUNCTION public.app_current_tenant_id() FROM app_aplicacao;
        END IF;

        REVOKE USAGE ON SCHEMA public FROM app_aplicacao;

        -- Rede de seguranca: se sobrou qualquer privilegio concedido a
        -- este papel neste banco, o DROP ROLE falharia. DROP OWNED BY
        -- remove privilegios e objetos pertencentes ao papel NESTE banco.
        -- E seguro aqui porque app_aplicacao nao e dono de nenhum objeto
        -- por design (ver V004).
        DROP OWNED BY app_aplicacao;

        -- ATENCAO: papel e objeto de CLUSTER. Se o mesmo papel for usado
        -- por outro banco no mesmo cluster, o DROP ROLE abaixo falha ate
        -- que DROP OWNED BY seja executado tambem naquele banco. Isso e
        -- proposital: melhor falhar do que quebrar outro ambiente.
        DROP ROLE app_aplicacao;

        RAISE NOTICE 'papel app_aplicacao removido';
    ELSE
        RAISE NOTICE 'papel app_aplicacao nao existe; nada a desfazer';
    END IF;
END
$$;
