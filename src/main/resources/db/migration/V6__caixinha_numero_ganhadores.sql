-- V6__caixinha_numero_ganhadores.sql — Nº de Ganhadores no wizard (Story 2.2 v5).
--
-- PRD v5 (2026-05-21) introduziu o campo "Nº de Ganhadores" (1, 2 ou 3) na
-- criação da Caixinha (FR-1 v5). Determina entre quantos o Prêmio é rateado
-- na apuração; se na apuração houver mais palpiteiros corretos que o
-- Nº de Ganhadores, o Organizador escolhe quais são os Ganhadores (FR-12 v5).
--
-- Regras (do PRD v5 + arquitetura):
--   * numero_ganhadores ∈ {1, 2, 3} — CHECK SQL como defesa final.
--   * numero_ganhadores ≤ minimo_participantes — não faz sentido ratear
--     entre mais Ganhadores do que o piso de pagantes confirmados.
--     Validado no use case e via CHECK SQL.
--
-- DEFAULT 1 só para o ALTER TABLE de tabelas existentes (zero registros
-- em produção, mas mantém a migration idempotente em ambientes que já
-- rodaram V4). O DEFAULT é removido em seguida — toda criação subsequente
-- exige o campo explicitamente vindo do wizard (FR-1 v5).

ALTER TABLE caixinha
    ADD COLUMN numero_ganhadores INTEGER NOT NULL DEFAULT 1;

ALTER TABLE caixinha
    ALTER COLUMN numero_ganhadores DROP DEFAULT;

ALTER TABLE caixinha
    ADD CONSTRAINT ck_caixinha_numero_ganhadores
        CHECK (numero_ganhadores BETWEEN 1 AND 3);

ALTER TABLE caixinha
    ADD CONSTRAINT ck_caixinha_ganhadores_le_minimo
        CHECK (numero_ganhadores <= minimo_participantes);
