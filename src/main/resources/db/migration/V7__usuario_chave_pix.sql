-- V7__usuario_chave_pix.sql — Chave PIX no perfil do Usuário (Story 2.5 v5).
--
-- PRD v5 (2026-05-21) tornou o cadastro de chave PIX **pré-requisito do
-- Palpite** (FR-5 v5). Decidido: a chave fica no Usuário (perfil global,
-- 1 por Usuário), não no Participante por Caixinha — menos fricção,
-- alinha com a UX "magic link, sem cadastro elaborado".
--
-- A coluna é NULLABLE porque o cadastro acontece DEPOIS do primeiro
-- login (UX: usuário entra, vê convite, tenta palpitar → app pede a chave).
-- Tamanho 512 é folgado para qualquer formato (CPF/email/telefone/aleatória).
-- Validação de formato fica no use case/adapter; aqui só armazenamento.

ALTER TABLE usuario
    ADD COLUMN chave_pix VARCHAR(512) NULL;
