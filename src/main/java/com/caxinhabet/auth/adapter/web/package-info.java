/**
 * Adapter HTTP do módulo auth (Story 2.1).
 *
 * <p>Endpoints expostos:
 * <ul>
 *   <li>{@code POST /auth/solicitar-acesso} — gera magic link
 *   <li>{@code GET  /auth/callback}         — consome token, abre sessão
 *   <li>{@code GET  /auth/me}               — quem sou eu (autenticado)
 *   <li>{@code POST /auth/sair}             — encerra sessão atual
 * </ul>
 *
 * <p>Cookie de sessão {@code caixinhabet_sessao} é HttpOnly, SameSite=Lax,
 * Secure (quando a request veio via HTTPS), Path=/, Max-Age = 7 dias.
 *
 * <p>Fluxo cross-origin (dev front 3000 + back 8080):
 * <ol>
 *   <li>O magic link no e-mail aponta para
 *       {@code ${app.public-base-url}/auth/callback?token=...} — ou seja,
 *       o FRONT;
 *   <li>A página do front em {@code /auth/callback} faz {@code fetch}
 *       para {@code ${API_BASE_URL}/auth/callback?...} com
 *       {@code credentials: 'include'};
 *   <li>O back responde 302 + Set-Cookie no domínio do back; o navegador
 *       grava o cookie e o front faz client-side navigation para
 *       {@code redirectTo}.
 * </ol>
 */
package com.caxinhabet.auth.adapter.web;
