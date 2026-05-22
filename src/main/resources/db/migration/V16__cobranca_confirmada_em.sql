-- V16__cobranca_confirmada_em.sql — instante da confirmação (Épico 4 v5).
--
-- Story 4.3: o resíduo de centavos da divisão do Prêmio vai ao "primeiro
-- Ganhador por ORDEM DE PAGAMENTO" (cronológico de quando ficou `pago`).
-- A ordem de pagamento real é o instante em que o webhook PAYMENT_CONFIRMED
-- foi processado — registrado aqui em `confirmada_em`.
--
-- NULL enquanto a cobrança não foi confirmada; preenchido pelo
-- ProcessarWebhookService no momento da confirmação.

ALTER TABLE pagamento_cobranca
    ADD COLUMN confirmada_em TIMESTAMPTZ;
