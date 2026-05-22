-- V14__participante_status_vencedor.sql — sub-estado de Ganhador (Épico 4 v5).
--
-- Story 4.2: ao apurar a Caixinha, o Organizador marca os Ganhadores. O
-- sub-estado do Repasse do prêmio é ORTOGONAL ao `status` (pagamento do
-- ingresso): só Participantes que são Ganhadores têm `status_vencedor`;
-- os demais ficam NULL.
--
-- Valores (ver enum StatusVencedor):
--   vencedor_aguardando_aceite — marcado Ganhador (Story 4.2)
--   vencedor_aceitou           — aceitou o prêmio, PIX disparado (Story 4.6)
--   vencedor_pago              — PIX confirmado pelo Asaas (Story 4.6)

ALTER TABLE participante
    ADD COLUMN status_vencedor VARCHAR(32);

ALTER TABLE participante
    ADD CONSTRAINT ck_participante_status_vencedor CHECK (
        status_vencedor IS NULL OR status_vencedor IN (
            'vencedor_aguardando_aceite',
            'vencedor_aceitou',
            'vencedor_pago'
        )
    );

-- Índice parcial: as queries do Repasse (Stories 4.3/4.4/4.6) filtram só
-- os Ganhadores de uma Caixinha. NULL fica fora do índice.
CREATE INDEX ix_participante_vencedor
    ON participante (caixinha_id)
    WHERE status_vencedor IS NOT NULL;
