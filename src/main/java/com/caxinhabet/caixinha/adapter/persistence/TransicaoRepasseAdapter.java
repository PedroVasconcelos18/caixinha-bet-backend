package com.caxinhabet.caixinha.adapter.persistence;

import com.caxinhabet.caixinha.domain.EstadoCaixinha;
import com.caxinhabet.caixinha.domain.TransicaoRepasse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Adapter da porta {@link TransicaoRepasse} (Épico 4 v5, Story 4.6).
 *
 * <p>Implementa, no módulo {@code caixinha} (dono do estado da Caixinha),
 * a transição final {@code repasse_parcial → repassada} consumida pelo
 * módulo {@code pagamento} via a porta.
 */
@Component
class TransicaoRepasseAdapter implements TransicaoRepasse {

	private static final Logger log =
			LoggerFactory.getLogger(TransicaoRepasseAdapter.class);

	private final CaixinhaRepository caixinhas;

	TransicaoRepasseAdapter(CaixinhaRepository caixinhas) {
		this.caixinhas = caixinhas;
	}

	@Override
	public void marcarRepassada(long caixinhaId) {
		CaixinhaEntity caixinha = caixinhas.findById(caixinhaId).orElse(null);
		if (caixinha == null) {
			log.warn("marcarRepassada: Caixinha {} não encontrada", caixinhaId);
			return;
		}
		if (caixinha.getEstado() == EstadoCaixinha.repassada) {
			return; // idempotente
		}
		if (caixinha.getEstado() != EstadoCaixinha.repasse_parcial) {
			log.warn(
					"marcarRepassada: Caixinha {} em {} (esperado repasse_parcial) —"
							+ " transição ignorada",
					caixinhaId,
					caixinha.getEstado());
			return;
		}
		caixinha.transicionarPara(EstadoCaixinha.repassada);
		caixinhas.save(caixinha);
		log.info("Caixinha {} REPASSADA — todos os prêmios pagos", caixinhaId);
	}
}
