-- V8__usuario_perfil_pagamento.sql — Perfil de pagamento do Usuário (Story 3.2 v5).
--
-- PRD v5 (decisão 2026-05-21, resolve parte de OQ-7): para PAGAR um ingresso,
-- o Participante precisa de um "customer" no Asaas, que exige `name` + `cpfCnpj`.
-- Logo o Usuário cadastra nome + CPF (postergado para o 1º pagamento — o
-- acesso continua só com e-mail, FR-16).
--
-- Colunas (todas NULLABLE — cadastro postergado, acontece no 1º pagamento):
--   * nome_completo    — usado como `name` do customer Asaas.
--   * cpf              — usado como `cpfCnpj`. Armazenado só com dígitos
--                        (sem pontos/traço); normalização no use case.
--   * asaas_customer_id — id do customer criado no Asaas (cus_xxx). Salvo
--                         no 1º pagamento e reusado nas cobranças seguintes
--                         (evita criar customer duplicado — Asaas permite
--                         duplicata, então o controle é nosso).
--
-- chave_pix já foi adicionada na V7. Juntas, as 4 colunas formam o
-- "perfil de pagamento". Validação de formato (CPF válido) fica no use
-- case / borda; o DB só armazena.

ALTER TABLE usuario
    ADD COLUMN nome_completo VARCHAR(160) NULL;

ALTER TABLE usuario
    ADD COLUMN cpf VARCHAR(11) NULL;

ALTER TABLE usuario
    ADD COLUMN asaas_customer_id VARCHAR(64) NULL;
