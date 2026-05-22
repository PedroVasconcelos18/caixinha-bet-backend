-- V20__ledger_conta_repassado.sql — conta `repassado` no ledger (Épico 4 v5).
--
-- Story 4.6: quando o PIX do prêmio é confirmado pelo Asaas, o valor SAI
-- da reserva do Ganhador. O ledger registra o par:
--   débito `reservado`  / crédito `repassado`
-- — "o valor reservado para o Ganhador X saiu da plataforma via PIX".
-- `repassado` é a conta-espelho da saída efetiva de dinheiro.

ALTER TABLE ledger_lancamento
    DROP CONSTRAINT ck_ledger_conta;

ALTER TABLE ledger_lancamento
    ADD CONSTRAINT ck_ledger_conta CHECK (conta IN (
        'expectativa',
        'a_receber',
        'custodia',
        'reservado',
        'repassado',
        'estorno'
    ));
