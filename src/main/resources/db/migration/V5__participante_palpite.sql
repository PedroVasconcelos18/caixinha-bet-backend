-- V5__participante_palpite.sql — Coluna palpite na tabela participante (Story 2.4 preparando 2.5).
--
-- Story 2.5 (aceitar convite + escolher palpite) vai PRECISAR desta coluna.
-- Adicionamos AGORA como NULLABLE (zero risk para linhas existentes) para
-- evitar uma migration V6 imediatamente em sequência. O Status do
-- Participante (PRD §3) inclui `convidado` (sem palpite), depois `aceito`
-- (palpite opcional), depois `pago` (palpite ainda opcional até o prazo
-- de entrada, congelando depois — Story 2.5 implementa a regra).
--
-- A coluna armazena o ID do Resultado Possível escolhido. FK para
-- `resultado_possivel(id)` garante integridade (você não pode palpitar
-- num resultado que não pertence à própria Caixinha — Story 2.5 valida
-- isso no use case também, mas o DB ajuda).
--
-- ON DELETE da Caixinha já CASCADE em `resultado_possivel`, então este
-- FK não precisa CASCADE — o ramo já é coberto.

ALTER TABLE participante
    ADD COLUMN palpite_resultado_possivel_id BIGINT NULL
        REFERENCES resultado_possivel(id);

CREATE INDEX idx_participante_palpite ON participante (palpite_resultado_possivel_id);
