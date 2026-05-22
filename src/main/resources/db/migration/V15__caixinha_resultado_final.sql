-- V15__caixinha_resultado_final.sql — Resultado Final da apuração (Épico 4 v5).
--
-- Story 4.2: ao apurar, o Organizador escolhe um dos Resultados Possíveis
-- da Caixinha como Resultado Final. Fica NULL até a apuração; após
-- `apurada` é IMUTÁVEL (o use case rejeita re-apuração).
--
-- FK lógica para resultado_possivel — não declaramos FOREIGN KEY física
-- para não acoplar a ordem das migrations / permitir flexibilidade de
-- exclusão lógica; o use case valida que o id pertence a esta Caixinha.

ALTER TABLE caixinha
    ADD COLUMN resultado_final_id BIGINT;
