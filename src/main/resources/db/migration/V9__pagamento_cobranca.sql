-- V9__pagamento_cobranca.sql — Cobranças PIX (Story 3.2 v5, FR-7).
--
-- Cada cobrança PIX gerada para um Participante é uma linha aqui. O modelo
-- é "1 cobrança ativa por Participante por vez" (FR-7: gerar nova invalida
-- a anterior) — garantido por índice único parcial.
--
-- Estados (`estado`):
--   ativa       — cobrança vigente, aguardando pagamento (Participante em
--                 `pagamento_iniciado`).
--   invalidada  — substituída por uma cobrança nova (FR-7: máx 1 ativa).
--   expirada    — venceu (`expira_em` passou) sem pagamento; Participante
--                 voltou a `aceito` (Story 3.2 expiração lazy).
--   confirmada  — pagamento confirmado pelo Provedor (Story 3.3, webhook).
--
-- Invariantes:
--   * `valor_centavos` BIGINT — dinheiro como inteiro de centavos (padrão
--     do projeto, igual `caixinha.valor_ingresso_centavos`).
--   * `cobranca_id` UNIQUE — id do PSP (Asaas: pay_xxx) não se repete.
--   * Índice único parcial `WHERE estado = 'ativa'` em `participante_id` —
--     impede 2 cobranças ativas simultâneas para o mesmo Participante.
--   * FKs com ON DELETE CASCADE coerentes com o resto do schema.

CREATE TABLE pagamento_cobranca (
    id BIGSERIAL PRIMARY KEY,
    participante_id BIGINT NOT NULL REFERENCES participante(id) ON DELETE CASCADE,
    caixinha_id BIGINT NOT NULL REFERENCES caixinha(id) ON DELETE CASCADE,
    cobranca_id VARCHAR(64) NOT NULL,
    copia_e_cola TEXT NOT NULL,
    qr_code_base64 TEXT NOT NULL,
    valor_centavos BIGINT NOT NULL,
    expira_em TIMESTAMPTZ NOT NULL,
    estado VARCHAR(16) NOT NULL,
    criado_em TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_pagamento_cobranca_estado CHECK (estado IN (
        'ativa',
        'invalidada',
        'expirada',
        'confirmada'
    )),
    CONSTRAINT ck_pagamento_cobranca_valor_positivo CHECK (valor_centavos > 0)
);

CREATE UNIQUE INDEX uq_pagamento_cobranca_cobranca_id
    ON pagamento_cobranca (cobranca_id);

-- Máximo UMA cobrança 'ativa' por Participante (FR-7). Índice único parcial:
-- só linhas com estado='ativa' participam da restrição de unicidade.
CREATE UNIQUE INDEX uq_pagamento_cobranca_participante_ativa
    ON pagamento_cobranca (participante_id)
    WHERE estado = 'ativa';

CREATE INDEX idx_pagamento_cobranca_caixinha
    ON pagamento_cobranca (caixinha_id);
