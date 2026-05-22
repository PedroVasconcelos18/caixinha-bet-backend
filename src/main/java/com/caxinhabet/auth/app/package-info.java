/**
 * Camada de aplicação do módulo auth (auth por senha, 2026-05).
 *
 * <p>Use cases: {@code RegistrarUsuarioUseCase} (cadastro explícito);
 * {@code AutenticarUseCase} (login por e-mail + senha);
 * {@code SolicitarResetSenhaUseCase} (dispara o link de reset);
 * {@code RedefinirSenhaUseCase} (consome o token e grava a nova senha).
 * Padrão da Story 1.4: lógica de aplicação fina, acesso direto ao
 * repositório, sem camada de service genérica.
 */
package com.caxinhabet.auth.app;
