package com.caxinhabet.caixinha.domain;

import java.util.Map;

/**
 * Porta de leitura do estado do estorno de cada Participante de uma
 * Caixinha cancelada (Épico 5 v5, Story 5.2, FR-11).
 *
 * <p>O Acerto de Contas (módulo {@code caixinha}) precisa mostrar se o
 * estorno de cada Participante está "em processamento" ou "concluído" —
 * informação que vem do {@code EstadoCobranca} (módulo {@code pagamento}).
 * Esta porta mantém o isolamento: {@code caixinha} depende da interface,
 * o adapter de {@code pagamento} a implementa.
 */
public interface ConsultaEstadoEstorno {

	/**
	 * Estado do estorno de cada Participante {@code pago} de uma Caixinha.
	 *
	 * @param caixinhaId Caixinha cancelada.
	 * @return mapa {@code participanteId → EstadoEstorno}. Participante sem
	 *     cobrança não aparece.
	 */
	Map<Long, EstadoEstorno> porCaixinha(long caixinhaId);

	/**
	 * Estado do estorno exposto ao Acerto de Contas — vocabulário de
	 * domínio neutro (sem acoplar o {@code EstadoCobranca} de
	 * {@code pagamento}).
	 */
	enum EstadoEstorno {
		/** Estorno disparado, ainda não confirmado pelo Provedor. */
		em_processamento,
		/** Provedor confirmou o estorno (webhook PAYMENT_REFUNDED). */
		concluido
	}
}
