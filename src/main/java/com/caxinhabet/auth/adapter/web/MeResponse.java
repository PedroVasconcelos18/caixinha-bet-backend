package com.caxinhabet.auth.adapter.web;

/**
 * Resposta de {@code GET /auth/me} (Story 2.1).
 *
 * <p>Sucesso = corpo direto (sem envelope) — regra dura AR-8. Hoje
 * carrega apenas o {@code email}; Stories futuras podem adicionar mais
 * campos do {@code Usuario} sem quebrar contrato (campos novos não
 * removem garantias dos existentes).
 */
public record MeResponse(String email) {}
