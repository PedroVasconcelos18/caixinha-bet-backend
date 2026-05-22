package com.caxinhabet.ledger.adapter.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repositório dos lançamentos do ledger (Story 3.3 v5).
 */
public interface LedgerLancamentoRepository
		extends JpaRepository<LedgerLancamentoEntity, Long> {

	/** Lançamentos de uma transação (o par débito+crédito). */
	List<LedgerLancamentoEntity> findByTransacaoId(UUID transacaoId);

	/** Lançamentos de uma Caixinha — usado pela reconciliação (Story 3.6). */
	List<LedgerLancamentoEntity> findByCaixinhaId(long caixinhaId);
}
