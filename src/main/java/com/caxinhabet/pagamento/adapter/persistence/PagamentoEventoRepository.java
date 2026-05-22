package com.caxinhabet.pagamento.adapter.persistence;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repositório do evento de pagamento (Story 1.4 AC-4; ampliado na 3.3).
 *
 * <p>Idempotência por construção: {@link #existsByEventId(String)} permite
 * checar antes de inserir; o índice {@code UNIQUE event_id} no DB é a
 * defesa final (insert concorrente do mesmo {@code event_id} falha com
 * {@code DataIntegrityViolationException}, que o webhook trata como "já
 * processado" — semântica idempotente HTTP).
 */
public interface PagamentoEventoRepository extends JpaRepository<PagamentoEventoEntity, Long> {

	boolean existsByEventId(String eventId);

	/**
	 * Story 3.3 (FR-8 / NFR-2): o evento de maior {@code provider_timestamp}
	 * já registrado para uma cobrança. Usado para a regra de ordenação —
	 * um evento que chega com timestamp anterior a este NÃO aplica efeito.
	 *
	 * <p>Nota: chamar ANTES de persistir o evento atual, senão o próprio
	 * evento entra na conta.
	 */
	Optional<PagamentoEventoEntity>
			findTopByCobrancaIdOrderByProviderTimestampDesc(String cobrancaId);
}
