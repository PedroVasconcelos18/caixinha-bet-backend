-- V23__usuario_perfil_e_verificacao.sql — Minha Conta (2026-05).
--
-- Estende `usuario` com os campos visíveis em /minha-conta (data de
-- nascimento, telefone, cidade, bio, foto BYTEA) + verificação de e-mail
-- + soft delete. Cria tabela `verificacao_email` (separada de
-- `solicitacao_acesso` para que tokens não sejam reutilizáveis cruzado).
--
-- Invariantes load-bearing:
--   * email_verificado NOT NULL DEFAULT TRUE → todos os usuários existentes
--     ficam "já verificados". Cadastros novos sobrescrevem para FALSE no
--     use case (RegistrarUsuario). Decisão registrada no spec.
--   * deletado_em NULL = ativo. Não há CASCADE para Caixinhas/Payouts; o
--     ledger é imutável (AR-8).
--   * foto_blob/foto_mime ficam ambos NULL ou ambos preenchidos — coerência
--     garantida no use case (AtualizarFotoUseCase).

ALTER TABLE usuario
    ADD COLUMN data_nascimento  DATE NULL,
    ADD COLUMN telefone         VARCHAR(11) NULL,
    ADD COLUMN cidade           VARCHAR(120) NULL,
    ADD COLUMN bio              VARCHAR(140) NULL,
    ADD COLUMN foto_blob        BYTEA NULL,
    ADD COLUMN foto_mime        VARCHAR(32) NULL,
    ADD COLUMN email_verificado BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN deletado_em      TIMESTAMPTZ NULL;

CREATE TABLE verificacao_email (
    id            BIGSERIAL PRIMARY KEY,
    usuario_id    BIGINT NOT NULL REFERENCES usuario(id),
    token_hash    VARCHAR(64) NOT NULL,
    criado_em     TIMESTAMPTZ NOT NULL DEFAULT now(),
    expira_em     TIMESTAMPTZ NOT NULL,
    consumido_em  TIMESTAMPTZ
);

CREATE UNIQUE INDEX uq_verificacao_email_token ON verificacao_email (token_hash);
CREATE INDEX idx_verificacao_email_expira    ON verificacao_email (expira_em);
