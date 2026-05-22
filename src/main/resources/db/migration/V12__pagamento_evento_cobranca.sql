-- V12__pagamento_evento_cobranca.sql — cobranca_id no evento (Story 3.3 v5).
--
-- A V2 criou pagamento_evento gate-only (event_id, provider_timestamp,
-- payload). A Story 3.3 precisa, ao processar um evento, saber qual foi
-- o evento de maior provider_timestamp JÁ aplicado àquela cobrança
-- (regra de ordenação FR-8 / NFR-2). Derivar do JSONB a cada query é
-- frágil e lento; uma coluna dedicada + índice resolve.
--
-- NULLABLE porque eventos antigos (do gate, Story 1.5) não têm o vínculo;
-- e eventos que não são de cobrança (IGNORADO) também não preenchem.

ALTER TABLE pagamento_evento
    ADD COLUMN cobranca_id VARCHAR(64) NULL;

CREATE INDEX idx_pagamento_evento_cobranca
    ON pagamento_evento (cobranca_id, provider_timestamp);
