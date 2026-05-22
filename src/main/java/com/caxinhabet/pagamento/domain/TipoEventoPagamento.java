package com.caxinhabet.pagamento.domain;

/**
 * Classificação de um evento de webhook do Provedor (Story 3.3 v5, FR-8).
 *
 * <p>Traduz os {@code event} strings do Asaas para o vocabulário do
 * domínio — o resto do código não conhece os nomes do Asaas.
 *
 * <ul>
 *   <li>{@link #CONFIRMADO} — {@code PAYMENT_CONFIRMED} / {@code PAYMENT_RECEIVED}.
 *       Para o MVP, ambos significam "o pagamento ocorreu" → Participante
 *       vai para {@code pago}.
 *   <li>{@link #EXPIRADO} — {@code PAYMENT_OVERDUE}. Cobrança venceu sem
 *       pagamento → Participante volta a {@code aceito}.
 *   <li>{@link #ESTORNADO} — {@code PAYMENT_REFUNDED}. Pagamento confirmado
 *       e depois devolvido → rebobina {@code pago} → {@code aceito}.
 *   <li>{@link #IGNORADO} — qualquer outro evento do Asaas que não afeta
 *       o estado de pagamento do MVP (ex.: {@code PAYMENT_UPDATED}).
 * </ul>
 */
public enum TipoEventoPagamento {
	CONFIRMADO,
	EXPIRADO,
	ESTORNADO,
	IGNORADO;

	/**
	 * Classifica o {@code event} string do Asaas.
	 *
	 * @param eventoAsaas valor do campo {@code event} do webhook (pode ser
	 *     {@code null} — vira {@link #IGNORADO}).
	 */
	public static TipoEventoPagamento doEventoAsaas(String eventoAsaas) {
		if (eventoAsaas == null) {
			return IGNORADO;
		}
		return switch (eventoAsaas) {
			case "PAYMENT_CONFIRMED", "PAYMENT_RECEIVED" -> CONFIRMADO;
			case "PAYMENT_OVERDUE" -> EXPIRADO;
			case "PAYMENT_REFUNDED" -> ESTORNADO;
			default -> IGNORADO;
		};
	}
}
