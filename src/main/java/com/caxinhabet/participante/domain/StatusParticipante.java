package com.caxinhabet.participante.domain;

/**
 * Status de um Participante (PRD §3, Glossário).
 *
 * <p>4 estados. Transições (introduzidas em Stories diferentes):
 * <ul>
 *   <li>{@link #convidado} → estado inicial. Story 2.2 (criação do dono),
 *       Story 2.4 (convite por e-mail).
 *   <li>{@link #aceito} — Story 2.5 (Participante aceita o convite).
 *   <li>{@link #pagamento_iniciado} — Story 3.2 (cobrança PIX gerada).
 *   <li>{@link #pago} — Story 3.3 (webhook Asaas confirmou — nunca
 *       autodeclaração).
 * </ul>
 *
 * <p>Nome em {@code snake_case} bate 1:1 com o valor persistido
 * ({@code @Enumerated(EnumType.STRING)}).
 */
public enum StatusParticipante {
	convidado,
	aceito,
	pagamento_iniciado,
	pago
}
