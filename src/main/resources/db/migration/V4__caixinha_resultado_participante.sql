-- V4__caixinha_resultado_participante.sql — Núcleo da Caixinha (Story 2.2).
--
-- Suporta a criação da Caixinha pelo Organizador (FR-1) com regras imutáveis
-- pós-criação (FR-3). Tabela `participante` criada AQUI porque o Organizador
-- é registrado como Participante na mesma transação da criação (PRD §3:
-- "Organizador é também um Participante").
--
-- Invariantes load-bearing:
--   * `valor_ingresso_centavos` BIGINT (em centavos, NÃO NUMERIC).
--     Por quê: inteiro exato sem fricção de escala; padrão observável em
--     Stripe/Asaas/sistemas financeiros; `Money.centavos()` já existe
--     (Story 1.3). CHECK >= 500 = mínimo R$ 5,00 do Asaas (Story 1.5).
--   * `data_apuracao > prazo_entrada` por CHECK SQL — defesa final no DB
--     além da validação no use case.
--   * `estado` como VARCHAR com CHECK IN (...) — 7 estados do PRD §3. JPA
--     mapeia como @Enumerated(EnumType.STRING). Migrations seguintes
--     podem adicionar estados, mas REMOVER é breaking change.
--   * `status` (participante) idem — 4 status do PRD §3.
--   * `resultado_possivel.UNIQUE(caixinha_id, ordem)` — sem ordem ambígua.
--   * `participante.UNIQUE(caixinha_id, email)` — sem duplicata; lookup
--     do convite por (caixinha_id, email) é O(log n).
--   * `participante.usuario_id` NULLABLE: convite por e-mail (Story 2.4)
--     cria participante ANTES do convidado se autenticar. Quando o
--     convidado autentica (Story 2.1 reusada), `usuario_id` é preenchido.
--     Nesta story, o dono SEMPRE tem usuario_id (acabou de se autenticar).
--
-- Naming snake_case (AR-8): gerado pela JPA naming strategy automaticamente;
-- aqui em SQL escrevemos snake_case explícito.

CREATE TABLE caixinha (
    id BIGSERIAL PRIMARY KEY,
    titulo VARCHAR(120) NOT NULL,
    lado_a VARCHAR(80) NOT NULL,
    lado_b VARCHAR(80) NOT NULL,
    valor_ingresso_centavos BIGINT NOT NULL,
    minimo_participantes INTEGER NOT NULL,
    prazo_entrada TIMESTAMPTZ NOT NULL,
    data_apuracao TIMESTAMPTZ NOT NULL,
    estado VARCHAR(32) NOT NULL,
    organizador_usuario_id BIGINT NOT NULL REFERENCES usuario(id),
    criado_em TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_caixinha_valor_minimo CHECK (valor_ingresso_centavos >= 500),
    CONSTRAINT ck_caixinha_minimo_participantes CHECK (minimo_participantes >= 2),
    CONSTRAINT ck_caixinha_apuracao_apos_prazo CHECK (data_apuracao > prazo_entrada),
    CONSTRAINT ck_caixinha_estado CHECK (estado IN (
        'coletando_convites',
        'coletando_pagamentos',
        'formada',
        'apurada',
        'repasse_parcial',
        'repassada',
        'cancelada'
    ))
);

CREATE INDEX idx_caixinha_organizador ON caixinha (organizador_usuario_id);

CREATE TABLE resultado_possivel (
    id BIGSERIAL PRIMARY KEY,
    caixinha_id BIGINT NOT NULL REFERENCES caixinha(id) ON DELETE CASCADE,
    ordem INTEGER NOT NULL,
    rotulo VARCHAR(120) NOT NULL,
    CONSTRAINT ck_resultado_possivel_rotulo_nao_vazio CHECK (length(trim(rotulo)) > 0)
);

CREATE UNIQUE INDEX uq_resultado_possivel_caixinha_ordem ON resultado_possivel (caixinha_id, ordem);

CREATE TABLE participante (
    id BIGSERIAL PRIMARY KEY,
    caixinha_id BIGINT NOT NULL REFERENCES caixinha(id) ON DELETE CASCADE,
    usuario_id BIGINT REFERENCES usuario(id),
    email CITEXT NOT NULL,
    dono BOOLEAN NOT NULL DEFAULT false,
    status VARCHAR(32) NOT NULL,
    criado_em TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_participante_status CHECK (status IN (
        'convidado',
        'aceito',
        'pagamento_iniciado',
        'pago'
    ))
);

CREATE UNIQUE INDEX uq_participante_caixinha_email ON participante (caixinha_id, email);
CREATE INDEX idx_participante_usuario ON participante (usuario_id);
