package com.caxinhabet.pagamento.adapter.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repositório do evento de pagamento (Story 1.4 AC-4).
 *
 * <p>Idempotência por construção: {@link #existsByEventId(String)} permite
 * checar antes de inserir; o índice {@code UNIQUE event_id} no DB é a
 * defesa final (insert concorrente do mesmo {@code event_id} falha com
 * {@code DataIntegrityViolationException}, que o webhook trata como "já
 * processado" — semântica idempotente HTTP).
 */
public interface PagamentoEventoRepository extends JpaRepository<PagamentoEventoEntity, Long> {

	boolean existsByEventId(String eventId);
}
