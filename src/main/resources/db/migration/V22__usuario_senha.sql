-- V22__usuario_senha.sql — Senha do Usuário (auth por senha, 2026-05).
--
-- Substitui a autenticação por magic link (Story 2.1) por login com senha.
--
--   * senha_hash — hash BCrypt (sempre 60 chars). NULLABLE de propósito:
--     usuários legados (criados pelo cadastro implícito do magic link) não
--     têm senha; eles definem uma pelo fluxo "esqueceu a senha". Cadastros
--     novos gravam senha_hash sempre — invariante garantida no use case,
--     não no schema (não há como exigir NOT NULL sem quebrar os legados).
--
--   * uq_usuario_cpf — índice único PARCIAL. Impede dois cadastros com o
--     mesmo CPF, mas o WHERE cpf IS NOT NULL preserva os usuários legados
--     de CPF nulo (cadastro de perfil postergado, migration V8).

ALTER TABLE usuario
    ADD COLUMN senha_hash VARCHAR(60) NULL;

CREATE UNIQUE INDEX uq_usuario_cpf ON usuario (cpf) WHERE cpf IS NOT NULL;
