package com.caxinhabet.auth.adapter.web;

import com.caxinhabet.auth.adapter.persistence.UsuarioEntity;

/**
 * Resposta de {@code GET /auth/me} (Story 2.1; v5 acrescentou perfil de
 * pagamento).
 *
 * <p>Sucesso = corpo direto (sem envelope) — regra dura AR-8.
 *
 * @param email identidade do usuário (e-mail do magic link).
 * @param chavePix chave PIX cadastrada, ou {@code null} (FR-5 v5).
 * @param nomeCompleto nome do perfil de pagamento, ou {@code null} (FR-16 v5).
 * @param cpf CPF do perfil de pagamento (só dígitos), ou {@code null}.
 * @param perfilPagamentoCompleto {@code true} se nome+CPF+chave PIX estão
 *     todos presentes — o front usa para decidir se mostra o form de
 *     perfil antes de permitir o pagamento (Story 3.2).
 */
public record MeResponse(
		String email,
		String chavePix,
		String nomeCompleto,
		String cpf,
		boolean perfilPagamentoCompleto) {

	/** Monta a partir da entidade — concentra o mapeamento num lugar só. */
	public static MeResponse de(UsuarioEntity u) {
		return new MeResponse(
				u.getEmail(),
				u.getChavePix(),
				u.getNomeCompleto(),
				u.getCpf(),
				u.perfilPagamentoCompleto());
	}
}
