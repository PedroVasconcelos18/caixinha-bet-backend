package com.caxinhabet.pagamento.adapter.persistence;

import com.caxinhabet.pagamento.domain.EstadoCobranca;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repositório das cobranças PIX (Story 3.2 v5; ampliado na 3.6).
 */
public interface CobrancaRepository extends JpaRepository<CobrancaEntity, Long> {

	/**
	 * Cobrança {@code ativa} de um Participante, se houver. O índice único
	 * parcial da V9 garante no máximo uma — daí o {@link Optional}.
	 */
	Optional<CobrancaEntity> findByParticipanteIdAndEstado(
			long participanteId, EstadoCobranca estado);

	/** Cobrança pelo id do PSP — usado pelo webhook (Story 3.3). */
	Optional<CobrancaEntity> findByCobrancaId(String cobrancaId);

	/**
	 * Story 3.6 (reconciliação): cobranças nos estados informados — o job
	 * reconcilia as "vivas" ({@code ativa}, {@code confirmada}) contra o
	 * Provedor; estados terminais antigos ({@code invalidada}, etc.) não
	 * precisam de reconciliação contínua.
	 */
	List<CobrancaEntity> findByEstadoIn(Collection<EstadoCobranca> estados);
}
