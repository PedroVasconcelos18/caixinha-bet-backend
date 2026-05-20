package com.caxinhabet.caixinha.domain;

/**
 * Estados da Caixinha (PRD §3, Glossário). Todos os 7 estados são listados
 * porque a coluna {@code estado} tem {@code CHECK IN (...)} no DB e o
 * Hibernate valida o tipo no boot — incluir todos evita reabrir a migration.
 *
 * <p>Story 2.2 só CRIA Caixinhas em {@link #coletando_convites}. As
 * transições para os demais estados são introduzidas em:
 * <ul>
 *   <li>{@link #coletando_pagamentos} — Story 3.1 (FR-6: ao atingir Mínimo
 *       de Aceites)
 *   <li>{@link #formada} — Story 3.4 (FR-9: pagos ≥ mínimo AND Σ > R$ 10)
 *   <li>{@link #apurada} — Story 4.2 (FR-12: Organizador apura)
 *   <li>{@link #repasse_parcial} — Story 4.3 (FR-13: split com crédito retido)
 *   <li>{@link #repassada} — Story 4.3 (FR-13: split concluído)
 *   <li>{@link #cancelada} — Story 5.1 (FR-11: prazo sem mínimo)
 * </ul>
 *
 * <p>O nome em {@code snake_case} é intencional: bate 1:1 com o valor
 * persistido no DB ({@code @Enumerated(EnumType.STRING)}).
 */
public enum EstadoCaixinha {
	coletando_convites,
	coletando_pagamentos,
	formada,
	apurada,
	repasse_parcial,
	repassada,
	cancelada
}
