package com.caxinhabet.auth.adapter.web;

import jakarta.validation.constraints.NotBlank;

/**
 * Payload de {@code DELETE /auth/me} (Minha Conta, 2026-05).
 *
 * <p>O front exige digitar {@code "EXCLUIR"}. O backend valida de novo
 * (defesa em profundidade contra clique acidental via curl/script).
 */
public record ExcluirContaRequest(@NotBlank String confirmacao) {}
