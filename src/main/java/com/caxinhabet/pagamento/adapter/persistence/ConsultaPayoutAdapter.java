package com.caxinhabet.pagamento.adapter.persistence;

import com.caxinhabet.caixinha.domain.ConsultaPayout;
import com.caxinhabet.pagamento.domain.EstadoPayout;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Adapter da porta {@link ConsultaPayout} (Épico 4 v5, Story 4.4).
 *
 * <p>Implementa, no módulo {@code pagamento}, a leitura de Payouts que o
 * módulo {@code caixinha} consome via a porta — mantendo o isolamento
 * (caixinha não importa pagamento.adapter).
 */
@Component
class ConsultaPayoutAdapter implements ConsultaPayout {

	private final PayoutRepository payouts;

	ConsultaPayoutAdapter(PayoutRepository payouts) {
		this.payouts = payouts;
	}

	@Override
	public List<DadosPayout> porCaixinha(long caixinhaId) {
		return payouts.findByCaixinhaId(caixinhaId).stream()
				.map(
						p ->
								new DadosPayout(
										p.getParticipanteId(),
										p.getValorCentavos(),
										traduzir(p.getEstado()),
										p.getComprovante()))
				.toList();
	}

	/** {@code EstadoPayout} (pagamento) → {@code EstadoRepasse} (caixinha). */
	private static EstadoRepasse traduzir(EstadoPayout estado) {
		return switch (estado) {
			case pendente_aceite -> EstadoRepasse.aguardando_aceite;
			case transferindo -> EstadoRepasse.pix_em_andamento;
			case pago -> EstadoRepasse.pago;
			case falha -> EstadoRepasse.falha_pix;
		};
	}
}
