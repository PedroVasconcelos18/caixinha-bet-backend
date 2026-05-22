-- V18__ledger_conta_reservado.sql — conta `reservado` no ledger (Épico 4 v5).
--
-- Story 4.3: ao preparar o Repasse, o valor de cada Ganhador sai da
-- custódia comum e é ALOCADO (reservado) para aquele Ganhador específico.
-- O ledger registra o par:
--   débito `custodia`  / crédito `reservado`
-- — "este valor saiu do bolo custodiado e está reservado para o
-- Ganhador X". O disparo do PIX (Story 4.6) debita `reservado` na saída.
--
-- A Taxa de Serviço (R$ 10) NÃO é reservada — permanece em `custodia` e
-- representa a retenção da plataforma (PRD §11 v5).

ALTER TABLE ledger_lancamento
    DROP CONSTRAINT ck_ledger_conta;

ALTER TABLE ledger_lancamento
    ADD CONSTRAINT ck_ledger_conta CHECK (conta IN (
        'expectativa',
        'a_receber',
        'custodia',
        'reservado',
        'estorno'
    ));
