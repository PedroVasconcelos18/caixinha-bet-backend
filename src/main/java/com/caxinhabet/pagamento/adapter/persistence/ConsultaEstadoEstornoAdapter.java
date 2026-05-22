package com.caxinhabet.pagamento.adapter.persistence;

import com.caxinhabet.caixinha.domain.ConsultaEstadoEstorno;
import com.caxinhabet.pagamento.domain.EstadoCobranca;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Adapter da porta {@link ConsultaEstadoEstorno} (Épico 5 v5, Story 5.2).
 *
 * <p>Implementa, no módulo {@code pagamento}, a leitura do estado do
 * estorno consumida pelo Acerto de Contas (módulo {@code caixinha}).
 *
 * <p>Mapeamento {@code EstadoCobranca} → {@code EstadoEstorno}:
 * <ul>
 *   <li>{@code confirmada} — paga; o Reembolso ainda não foi disparado
 *       (ou está prestes a ser) → {@code em_processamento}.
 *   <li>{@code estorno_solicitado} — estorno disparado no Provedor
 *       (Story 5.1), webhook {@code PAYMENT_REFUNDED} ainda não chegou
 *       → {@code em_processamento}.
 *   <li>{@code estornada} — webhook confirmou o estorno → {@code concluido}.
 * </ul>
 * Cobranças {@code ativa}/{@code expirada}/{@code invalidada} não
 * representam dinheiro a reembolsar — ficam fora do mapa.
 */
@Component
class ConsultaEstadoEstornoAdapter implements ConsultaEstadoEstorno {

	private final CobrancaRepository cobrancas;

	ConsultaEstadoEstornoAdapter(CobrancaRepository cobrancas) {
		this.cobrancas = cobrancas;
	}

	@Override
	public Map<Long, EstadoEstorno> porCaixinha(long caixinhaId) {
		Map<Long, EstadoEstorno> resultado = new HashMap<>();
		for (CobrancaEntity c :
				cobrancas.findByEstadoIn(
						List.of(
								EstadoCobranca.confirmada,
								EstadoCobranca.estorno_solicitado,
								EstadoCobranca.estornada))) {
			if (c.getCaixinhaId() != caixinhaId) {
				continue;
			}
			EstadoEstorno estado =
					c.getEstado() == EstadoCobranca.estornada
							? EstadoEstorno.concluido
							: EstadoEstorno.em_processamento;
			// Se o Participante tiver mais de uma cobrança (regerada), a
			// `estornada` (concluído) prevalece sobre a `confirmada`.
			resultado.merge(
					c.getParticipanteId(),
					estado,
					(a, b) ->
							a == EstadoEstorno.concluido || b == EstadoEstorno.concluido
									? EstadoEstorno.concluido
									: EstadoEstorno.em_processamento);
		}
		return resultado;
	}
}
