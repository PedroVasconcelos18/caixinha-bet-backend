package com.caxinhabet.pagamento.domain;

/**
 * Estado de uma cobrança PIX persistida (Story 3.2 v5, FR-7).
 *
 * <p>NÃO confundir com {@link StatusCobranca} — aquele é o record que a
 * porta {@code ProvedorPagamento} devolve com o estado consultado no PSP.
 * Este enum é o estado interno da nossa entidade {@code pagamento_cobranca}.
 *
 * <p>O nome em {@code snake_case}... na verdade aqui são minúsculas
 * simples; bate 1:1 com o {@code CHECK IN (...)} da migration V9 via
 * {@code @Enumerated(EnumType.STRING)}.
 *
 * <ul>
 *   <li>{@link #ativa} — cobrança vigente; Participante em {@code pagamento_iniciado}.
 *   <li>{@link #invalidada} — substituída por cobrança nova (FR-7: máx 1 ativa).
 *   <li>{@link #expirada} — venceu sem pagamento; Participante voltou a {@code aceito}.
 *   <li>{@link #confirmada} — pagamento confirmado pelo Provedor (Story 3.3).
 *   <li>{@link #estornada} — pagamento confirmado e depois estornado pelo
 *       Provedor (evento {@code PAYMENT_REFUNDED} — Story 3.3).
 * </ul>
 */
public enum EstadoCobranca {
	ativa,
	invalidada,
	expirada,
	confirmada,
	estornada
}
