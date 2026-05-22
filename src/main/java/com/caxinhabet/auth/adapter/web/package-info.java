/**
 * Adapter HTTP do módulo auth (auth por senha, 2026-05).
 *
 * <p>Endpoints expostos:
 * <ul>
 *   <li>{@code POST /auth/registrar}        — cadastro explícito, abre sessão
 *   <li>{@code POST /auth/login}            — login por e-mail + senha
 *   <li>{@code POST /auth/recuperar-senha}  — dispara o link de reset (204)
 *   <li>{@code POST /auth/redefinir-senha}  — consome o token, grava a nova
 *                                             senha e abre sessão
 *   <li>{@code GET  /auth/me}               — quem sou eu (autenticado)
 *   <li>{@code POST /auth/sair}             — encerra sessão atual
 * </ul>
 *
 * <p>Cookie de sessão {@code caixinhabet_sessao} é HttpOnly, SameSite=Lax,
 * Secure (quando a request veio via HTTPS), Path=/, Max-Age = 7 dias.
 * Cadastro, login e redefinição de senha respondem 200 + Set-Cookie + corpo
 * {@code MeResponse} — o usuário sai logado.
 *
 * <p>O link de recuperação enviado por e-mail aponta para o FRONT em
 * {@code ${app.public-base-url}/redefinir-senha?token=...}; a página do
 * front coleta a nova senha e chama {@code POST /auth/redefinir-senha} com
 * {@code credentials: 'include'} para receber o cookie de sessão.
 */
package com.caxinhabet.auth.adapter.web;
