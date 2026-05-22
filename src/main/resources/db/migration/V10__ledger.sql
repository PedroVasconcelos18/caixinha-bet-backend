-- V10__ledger.sql — Ledger de dupla entrada (Story 3.3 v5, FR-8 / AR-6).
--
-- Toda movimentação financeira é registrada como um PAR de lançamentos
-- (um débito + um crédito) compartilhando a mesma `transacao_id`. A
-- invariante de dupla entrada — Σ débitos == Σ créditos por transação —
-- é garantida pelo LedgerService (sempre grava o par) e auditada pelo
-- job de reconciliação (Story 3.6).
--
-- Contas (lógicas, não bancárias): no MVP o ledger é a trilha INTERNA
-- reconciliável contra o extrato do Asaas. Não há conta bancária por
-- trás de cada `conta` — são categorias contábeis.
--   a_receber  — valor que o Participante deve (cobrança gerada).
--   custodia   — valor custodiado no Asaas após o pagamento confirmar.
--   estorno    — contrapartida quando um pagamento é estornado.
--
-- evento_ref guarda o event_id do Asaas que originou a movimentação —
-- liga o lançamento ao webhook que o causou (auditoria NFR-7).

CREATE TABLE ledger_lancamento (
    id BIGSERIAL PRIMARY KEY,
    transacao_id UUID NOT NULL,
    conta VARCHAR(16) NOT NULL,
    tipo VARCHAR(8) NOT NULL,
    valor_centavos BIGINT NOT NULL,
    caixinha_id BIGINT NOT NULL REFERENCES caixinha(id) ON DELETE CASCADE,
    participante_id BIGINT NOT NULL REFERENCES participante(id) ON DELETE CASCADE,
    cobranca_id VARCHAR(64) NOT NULL,
    evento_ref VARCHAR(255) NOT NULL,
    criado_em TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_ledger_conta CHECK (conta IN ('a_receber', 'custodia', 'estorno')),
    CONSTRAINT ck_ledger_tipo CHECK (tipo IN ('debito', 'credito')),
    CONSTRAINT ck_ledger_valor_positivo CHECK (valor_centavos > 0)
);

CREATE INDEX idx_ledger_transacao ON ledger_lancamento (transacao_id);
CREATE INDEX idx_ledger_caixinha ON ledger_lancamento (caixinha_id);
CREATE INDEX idx_ledger_cobranca ON ledger_lancamento (cobranca_id);
