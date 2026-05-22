-- V11__cobranca_estado_estornada.sql — Estado `estornada` (Story 3.3 v5, FR-8).
--
-- A V9 criou `pagamento_cobranca.estado` com CHECK IN
-- ('ativa','invalidada','expirada','confirmada'). A Story 3.3 processa o
-- evento PAYMENT_REFUNDED do Asaas: uma cobrança `confirmada` cujo
-- pagamento é estornado precisa de um estado próprio — `estornada`.
--
-- Postgres não tem "ALTER CHECK"; recriamos a constraint.

ALTER TABLE pagamento_cobranca
    DROP CONSTRAINT ck_pagamento_cobranca_estado;

ALTER TABLE pagamento_cobranca
    ADD CONSTRAINT ck_pagamento_cobranca_estado CHECK (estado IN (
        'ativa',
        'invalidada',
        'expirada',
        'confirmada',
        'estornada'
    ));
