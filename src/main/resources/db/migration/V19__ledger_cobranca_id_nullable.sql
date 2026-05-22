-- V19__ledger_cobranca_id_nullable.sql — cobranca_id opcional (Épico 4 v5).
--
-- A V10 declarou `ledger_lancamento.cobranca_id` como NOT NULL — fazia
-- sentido quando todo lançamento vinha de uma cobrança (custódia/estorno).
-- Story 4.3 introduz a RESERVA do prêmio (débito custodia / crédito
-- reservado): essa movimentação não tem uma cobrança associada — a
-- referência de auditoria é o `payout_id`, gravado em `evento_ref`.
--
-- Torna `cobranca_id` nullable. `evento_ref` continua NOT NULL — todo
-- lançamento tem SEMPRE uma referência de auditoria (event_id de webhook,
-- cobrancaId de geração, ou payout_id de reserva).

ALTER TABLE ledger_lancamento
    ALTER COLUMN cobranca_id DROP NOT NULL;
