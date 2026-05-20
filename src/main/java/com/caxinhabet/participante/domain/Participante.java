package com.caxinhabet.participante.domain;

import java.time.Instant;

/**
 * Participante de uma Caixinha (PRD §3).
 *
 * <p>{@code usuarioId} é {@code null} quando o Participante foi convidado
 * por e-mail (Story 2.4) e ainda não se autenticou pela primeira vez.
 * Assim que se autentica (fluxo da Story 2.1, ou Story 2.5 ao aceitar
 * convite), {@code usuarioId} é preenchido.
 *
 * <p>{@code dono} = {@code true} apenas para o Organizador (quem criou a
 * Caixinha — Story 2.2). PRD §3: "Organizador é também um Participante".
 *
 * <p>Story 2.2 só cria Participantes com {@code status=convidado, dono=true}
 * (o dono, na criação da Caixinha). Demais cenários:
 * <ul>
 *   <li>Story 2.4 — cria {@code convidado, dono=false} (convite por e-mail)
 *   <li>Story 2.5 — transição {@code convidado → aceito}
 *   <li>Story 3.2/3.3 — transições para {@code pagamento_iniciado}/{@code pago}
 * </ul>
 */
public record Participante(
		Long id,
		Long caixinhaId,
		Long usuarioId,
		String email,
		boolean dono,
		StatusParticipante status,
		Instant criadoEm) {

	public Participante {
		if (caixinhaId == null) {
			throw new IllegalArgumentException("caixinhaId é obrigatório");
		}
		if (email == null || email.isBlank()) {
			throw new IllegalArgumentException("email é obrigatório");
		}
		if (status == null) {
			throw new IllegalArgumentException("status é obrigatório");
		}
		if (criadoEm == null) {
			throw new IllegalArgumentException("criadoEm é obrigatório");
		}
	}

	/**
	 * Factory para o Organizador-como-Participante (Story 2.2, AC-4).
	 * {@code id=null, criadoEm=now}: a entidade JPA preenche na persistência.
	 */
	public static Participante dono(Long caixinhaId, Long usuarioId, String email) {
		return new Participante(
				null,
				caixinhaId,
				usuarioId,
				email,
				true,
				StatusParticipante.convidado,
				Instant.now());
	}
}
