-- V2__pagamento_evento.sql — Tabela mínima de eventos de pagamento (Story 1.4).
--
-- Schema GATE-ONLY: suporta a verificação do gate Asaas (Story 1.5) e a
-- invariante de "evento persistido antes de efeito" (AR-5). As colunas de
-- domínio do Pagamento (status, valor, cobrança_id, etc.) entram nos
-- épicos seguintes (FR-7/FR-8 — Épico 3) como migrations Vn__... próprias.
--
-- Invariantes load-bearing nesta tabela:
--   * event_id UNIQUE  → idempotência por construção (defesa final no DB)
--   * provider_timestamp NOT NULL → ordenação por timestamp do PSP (NFR-2)
--   * payload JSONB    → auditoria/replay (NFR-7); raw do Asaas preservado
--
-- Naming snake_case (AR-8) é gerado automaticamente pela JPA naming
-- strategy; aqui em SQL escrevemos snake_case explícito.

CREATE TABLE pagamento_evento (
    id BIGSERIAL PRIMARY KEY,
    event_id VARCHAR(255) NOT NULL,
    provider_timestamp TIMESTAMPTZ NOT NULL,
    payload JSONB NOT NULL,
    recebido_em TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uq_pagamento_evento_event_id ON pagamento_evento (event_id);
CREATE INDEX idx_pagamento_evento_provider_ts ON pagamento_evento (provider_timestamp);
