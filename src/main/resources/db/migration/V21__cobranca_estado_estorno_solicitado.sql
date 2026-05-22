-- V21__cobranca_estado_estorno_solicitado.sql — estado `estorno_solicitado`
-- (Épico 5 v5, fix code review da Story 5.1).
--
-- O code review do Épico 5 apontou: o DispararReembolsoService estorna
-- cobranças `confirmada`. A cobrança só sai de `confirmada` quando o
-- webhook PAYMENT_REFUNDED chega (minutos depois). Na janela, um
-- re-disparo do reembolso chamaria `estornar` de novo no Asaas →
-- estorno duplicado.
--
-- `estorno_solicitado` é o estado intermediário: o estorno foi DISPARADO
-- no Provedor mas ainda não confirmado. O DispararReembolsoService só
-- estorna cobranças `confirmada` — uma já em `estorno_solicitado` é
-- pulada. O webhook PAYMENT_REFUNDED transiciona estorno_solicitado →
-- estornada.
--
-- Postgres não tem "ALTER CHECK"; recriamos a constraint.
--
-- A coluna `estado` foi criada na V9 como VARCHAR(16); 'estorno_solicitado'
-- tem 18 caracteres — ampliamos para VARCHAR(24).

ALTER TABLE pagamento_cobranca
    ALTER COLUMN estado TYPE VARCHAR(24);

ALTER TABLE pagamento_cobranca
    DROP CONSTRAINT ck_pagamento_cobranca_estado;

ALTER TABLE pagamento_cobranca
    ADD CONSTRAINT ck_pagamento_cobranca_estado CHECK (estado IN (
        'ativa',
        'invalidada',
        'expirada',
        'confirmada',
        'estorno_solicitado',
        'estornada'
    ));
