package com.caxinhabet.auth.domain;

import java.time.Instant;

/**
 * Usuário autenticado pelo e-mail (FR-16). Identidade = e-mail.
 *
 * <p>Não existe senha, login social, nem qualquer credencial além do e-mail
 * (PRD §6.2 / OQ-5). O {@code id} é cosmético/operacional; o que importa
 * para o resto do sistema é o {@code email} — futuro {@code Participante}
 * referencia por e-mail (Stories 2.4/2.5).
 *
 * <p>O {@code email} chega aqui já normalizado (lowercase) pelo use case;
 * defesa em profundidade — o tipo {@code citext} no Postgres também faz
 * comparação case-insensitive na borda do banco.
 */
public record Usuario(Long id, String email, Instant criadoEm) {

	public Usuario {
		if (email == null || email.isBlank()) {
			throw new IllegalArgumentException("email é obrigatório");
		}
		if (criadoEm == null) {
			throw new IllegalArgumentException("criadoEm é obrigatório");
		}
	}
}
