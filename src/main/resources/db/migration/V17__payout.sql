-- V17__payout.sql — agregado Payout (Épico 4 v5, FR-13).
--
-- Story 4.3: ao apurar uma Caixinha com ≥ 1 Ganhador, o sistema calcula
-- o valor de cada Ganhador e persiste um Payout por Ganhador. O
-- `payout_id` é a chave de IDEMPOTÊNCIA do disparo do PIX (Story 4.6):
-- múltiplos toques no botão "aceitar" não duplicam a transferência.
--
-- estado:
--   pendente_aceite — Payout criado; Ganhador ainda não aceitou (Story 4.3)
--   transferindo    — Ganhador aceitou; PIX disparado no Asaas (Story 4.6)
--   pago            — Asaas confirmou a transferência (Story 4.6)
--   falha           — Asaas recusou (chave inválida); Ganhador corrige (4.6)
--
-- transferencia_id / comprovante: preenchidos no disparo/confirmação (4.6).

CREATE TABLE payout (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    payout_id         UUID        NOT NULL UNIQUE,
    caixinha_id       BIGINT      NOT NULL,
    participante_id   BIGINT      NOT NULL,
    valor_centavos    BIGINT      NOT NULL CHECK (valor_centavos > 0),
    estado            VARCHAR(20) NOT NULL CHECK (estado IN (
                          'pendente_aceite', 'transferindo', 'pago', 'falha'
                      )),
    transferencia_id  VARCHAR(120),
    comprovante       TEXT,
    criado_em         TIMESTAMPTZ NOT NULL,
    atualizado_em     TIMESTAMPTZ NOT NULL,
    -- Um Payout por Participante por Caixinha — idempotência da criação
    -- (a apuração roda uma vez; defesa em profundidade).
    CONSTRAINT uq_payout_participante UNIQUE (caixinha_id, participante_id)
);

CREATE INDEX ix_payout_caixinha ON payout (caixinha_id);
