package com.caxinhabet.auth.domain;

import java.time.Instant;

/**
 * Solicitação de acesso por magic link (Story 2.1, AC-1..AC-3).
 *
 * <p>Persistida em {@code solicitacao_acesso}. Invariantes:
 * <ul>
 *   <li>{@code tokenHash} é o SHA-256 hex (64 chars) do token cru. O token
 *       cru EXISTE apenas no momento de criar a solicitação e é enviado
 *       no link por e-mail — depois disso, NUNCA mais o conhecemos.
 *   <li>{@code consumidoEm == null} → ainda usável (uma vez, antes de
 *       {@code expiraEm}). Uma vez consumida, vira read-only auditável.
 *   <li>{@code redirectTo} é o caminho relativo para onde o front redireciona
 *       após autenticar. Validado pelo use case (precisa começar com
 *       {@code /}, sem esquema). {@code null} = redirect para {@code /}.
 * </ul>
 */
public record SolicitacaoAcesso(
		Long id,
		Long usuarioId,
		String tokenHash,
		String redirectTo,
		Instant criadoEm,
		Instant expiraEm,
		Instant consumidoEm) {

	public SolicitacaoAcesso {
		if (usuarioId == null) {
			throw new IllegalArgumentException("usuarioId é obrigatório");
		}
		if (tokenHash == null || tokenHash.length() != 64) {
			throw new IllegalArgumentException(
					"tokenHash deve ter 64 chars (SHA-256 hex)");
		}
		if (criadoEm == null || expiraEm == null) {
			throw new IllegalArgumentException("criadoEm e expiraEm são obrigatórios");
		}
		if (!expiraEm.isAfter(criadoEm)) {
			throw new IllegalArgumentException("expiraEm deve ser depois de criadoEm");
		}
	}

	/**
	 * Solicitação ainda válida = não consumida E não expirada (no instante
	 * de referência {@code agora}). O use case de consumo testa nessa
	 * ordem: {@code consumidoEm} primeiro (mensagem clara de "já usado"),
	 * depois {@code expiraEm} (mensagem clara de "expirado") — AC-3.
	 */
	public boolean estaValida(Instant agora) {
		return consumidoEm == null && agora.isBefore(expiraEm);
	}
}
