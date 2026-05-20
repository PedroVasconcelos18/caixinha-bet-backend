-- V1__baseline.sql — Baseline do schema (Story 1.1).
--
-- Schema VAZIO, versionado de propósito. Esta migration NÃO modela domínio:
-- ela apenas fixa o esqueleto do versionamento Flyway para que toda mudança
-- de schema a partir daqui seja uma migration Vn__descricao.sql incremental.
--
-- As tabelas de domínio (caixinha, participante, pagamento, eventos de
-- pagamento, ledger, ...) entram nas migrations dos épicos seguintes — NUNCA
-- pelo Hibernate (spring.jpa.hibernate.ddl-auto = validate).
--
-- Naming obrigatório de toda migration: snake_case (AR-8/AR-9).

-- Marcador de baseline. Não cria objeto de domínio; serve para o Flyway
-- registrar V1 em flyway_schema_history e travar o ponto inicial do schema.
DO $$
BEGIN
    RAISE NOTICE 'Caixinha Bet — baseline de schema aplicado (V1, schema vazio).';
END $$;
