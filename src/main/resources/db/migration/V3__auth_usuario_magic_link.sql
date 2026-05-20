-- V3__auth_usuario_magic_link.sql — Tabelas do módulo auth (Story 2.1).
--
-- Suporta a autenticação por e-mail via magic link (FR-16, OQ-5 resolvida
-- como "magic link"). Schema MÍNIMO para esta story: nenhuma coluna além
-- do estritamente necessário para AC-1..AC-5. Sessão fica IN-MEMORY
-- (SessaoStore) — não há tabela `sessao` aqui, decisão consciente da
-- Story 2.1 (trade-off: restart do back invalida sessões; aceitável MVP,
-- revisto na retrospectiva do Épico 2).
--
-- Invariantes load-bearing nestas tabelas:
--   * usuario.email CITEXT UNIQUE → case-insensitive por construção, sem
--     normalização manual em código (defesa em profundidade: o use case
--     também normaliza para lowercase). citext é módulo padrão do Postgres
--     (incluído na imagem oficial postgres:17), idempotente via
--     CREATE EXTENSION IF NOT EXISTS.
--   * solicitacao_acesso.token_hash UNIQUE → o token CRU nunca é
--     armazenado; só o SHA-256 hex (64 chars). Roubo do banco não dá ao
--     atacante o token usável.
--   * solicitacao_acesso.expira_em + consumido_em → uso único + expiração
--     determinístico. O UseCase de consumo faz UPDATE com WHERE
--     consumido_em IS NULL para resistir a race (duplo-clique no link).
--
-- Naming snake_case (AR-8) é gerado automaticamente pela JPA naming
-- strategy; aqui em SQL escrevemos snake_case explícito.

CREATE EXTENSION IF NOT EXISTS citext;

CREATE TABLE usuario (
    id BIGSERIAL PRIMARY KEY,
    email CITEXT NOT NULL,
    criado_em TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uq_usuario_email ON usuario (email);

CREATE TABLE solicitacao_acesso (
    id BIGSERIAL PRIMARY KEY,
    usuario_id BIGINT NOT NULL REFERENCES usuario(id),
    token_hash VARCHAR(64) NOT NULL,
    redirect_to VARCHAR(512),
    criado_em TIMESTAMPTZ NOT NULL DEFAULT now(),
    expira_em TIMESTAMPTZ NOT NULL,
    consumido_em TIMESTAMPTZ
);

CREATE UNIQUE INDEX uq_solicitacao_acesso_token_hash ON solicitacao_acesso (token_hash);
CREATE INDEX idx_solicitacao_acesso_expira_em ON solicitacao_acesso (expira_em);
