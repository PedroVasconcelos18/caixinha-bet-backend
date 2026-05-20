package com.caxinhabet.auth.domain;

import java.time.Instant;

/**
 * Sessão de usuário autenticado (Story 2.1, AC-2).
 *
 * <p>Vive em {@code SessaoStore} (in-memory). O cookie HTTP carrega apenas
 * o {@code idSessao} (opaco, mesmo formato do {@link TokenAcesso} — 43 chars
 * Base64URL). O {@code email} desta sessão é a identidade exposta como
 * {@code Authentication.principal} no {@code SecurityContext}.
 *
 * <p>Expiração: {@code Instant.now().isAfter(expiraEm)} → invalida. O cookie
 * {@code Max-Age} casa com {@code ttl-dias} da config; o store ignora
 * sessões expiradas mesmo que o cookie chegue.
 */
public record SessaoUsuario(
		String idSessao,
		Long usuarioId,
		String email,
		Instant criadaEm,
		Instant expiraEm) {

	public SessaoUsuario {
		if (idSessao == null || idSessao.isBlank()) {
			throw new IllegalArgumentException("idSessao é obrigatório");
		}
		if (usuarioId == null) {
			throw new IllegalArgumentException("usuarioId é obrigatório");
		}
		if (email == null || email.isBlank()) {
			throw new IllegalArgumentException("email é obrigatório");
		}
		if (criadaEm == null || expiraEm == null) {
			throw new IllegalArgumentException("criadaEm e expiraEm são obrigatórios");
		}
	}

	public boolean estaExpirada(Instant agora) {
		return !agora.isBefore(expiraEm);
	}
}
