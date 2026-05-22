package com.caxinhabet.pagamento.adapter.persistence;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Repositório do agregado {@code Payout} (Épico 4 v5, FR-13).
 */
public interface PayoutRepository extends JpaRepository<PayoutEntity, Long> {

	/** Payouts de uma Caixinha — usado pelo Acerto de Contas (Story 4.4). */
	List<PayoutEntity> findByCaixinhaId(long caixinhaId);

	/** Payout de um Ganhador específico — leitura simples (Story 4.4). */
	Optional<PayoutEntity> findByCaixinhaIdAndParticipanteId(
			long caixinhaId, long participanteId);

	/**
	 * Payout de um Ganhador com <b>lock pessimista</b> de escrita
	 * ({@code SELECT ... FOR UPDATE}) — usado no aceite do prêmio
	 * (Story 4.6, fix code review Épico 4).
	 *
	 * <p>Sem o lock, dois cliques simultâneos do mesmo Ganhador no botão
	 * "aceitar" leem ambos o Payout em {@code pendente_aceite}, passam o
	 * gate de idempotência e disparam DOIS PIX. O lock serializa: o 2º
	 * request espera o 1º commitar e então vê o Payout já {@code transferindo}.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query(
			"SELECT p FROM PayoutEntity p"
					+ " WHERE p.caixinhaId = :caixinhaId"
					+ " AND p.participanteId = :participanteId")
	Optional<PayoutEntity> findParaAceiteComLock(
			@Param("caixinhaId") long caixinhaId,
			@Param("participanteId") long participanteId);
}
