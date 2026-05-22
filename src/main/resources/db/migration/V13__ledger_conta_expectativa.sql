-- V13__ledger_conta_expectativa.sql — conta `expectativa` no ledger
-- (fix code review Épico 3, 2026-05-21).
--
-- O code review do Épico 3 apontou: a V10 descreve `a_receber` como "valor
-- que o Participante deve (cobrança gerada)", mas nenhum lançamento CREDITA
-- `a_receber` na geração da cobrança. O `registrarCustodia` (confirmação)
-- DEBITA `a_receber` — debitando uma conta nunca creditada → o saldo
-- agregado de `a_receber` fica estruturalmente negativo e a "reconstrução
-- do estado financeiro" (AC-7 da Story 3.3) não fecha.
--
-- Correção: ao gerar a cobrança, registra-se o par
--   débito `expectativa`  / crédito `a_receber`
-- — "criou-se a expectativa de receber este ingresso". Na confirmação, o
-- par já existente
--   débito `a_receber`    / crédito `custodia`
-- agora debita uma conta que foi previamente creditada — o ciclo fecha.
--
-- `expectativa` é a conta de contrapartida da geração (conceito contábil:
-- o "outro lado" do compromisso que ainda não virou dinheiro real).

ALTER TABLE ledger_lancamento
    DROP CONSTRAINT ck_ledger_conta;

ALTER TABLE ledger_lancamento
    ADD CONSTRAINT ck_ledger_conta CHECK (conta IN (
        'expectativa',
        'a_receber',
        'custodia',
        'estorno'
    ));
