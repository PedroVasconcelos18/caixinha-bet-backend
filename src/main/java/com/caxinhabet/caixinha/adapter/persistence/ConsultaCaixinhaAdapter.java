package com.caxinhabet.caixinha.adapter.persistence;

import com.caxinhabet.caixinha.domain.ConsultaCaixinha;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Implementação da porta {@link ConsultaCaixinha} (Story 3.2 v5).
 *
 * <p>Adapter de leitura: traduz a {@code CaixinhaEntity} (JPA, interna ao
 * módulo) para o record {@code DadosCaixinha} (público). Outros módulos
 * — em especial {@code pagamento} — consomem a porta, nunca este adapter
 * nem o {@code CaixinhaRepository} direto.
 */
@Component
class ConsultaCaixinhaAdapter implements ConsultaCaixinha {

	private final CaixinhaRepository caixinhas;

	ConsultaCaixinhaAdapter(CaixinhaRepository caixinhas) {
		this.caixinhas = caixinhas;
	}

	@Override
	public Optional<DadosCaixinha> buscar(long caixinhaId) {
		return caixinhas
				.findById(caixinhaId)
				.map(
						c ->
								new DadosCaixinha(
										c.getId(),
										c.getTitulo(),
										c.getValorIngressoCentavos(),
										c.getEstado()));
	}
}
