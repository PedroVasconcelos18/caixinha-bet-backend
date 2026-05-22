package com.caxinhabet.caixinha.adapter.web;

import com.caxinhabet.pagamento.app.AceitarPremioUseCase;

/**
 * Resposta de {@code POST /caixinhas/{id}/aceitar-premio} (Story 4.6, FR-13).
 *
 * @param estadoPayout estado do Repasse após o aceite: {@code transferindo}
 *     (PIX em andamento), {@code pago} (confirmado) ou {@code falha} (chave
 *     PIX recusada — corrija e aceite de novo).
 * @param comprovante comprovante do PIX ({@code null} se ainda não pago).
 */
public record AceitarPremioResponse(String estadoPayout, String comprovante) {

	public static AceitarPremioResponse de(AceitarPremioUseCase.Resultado r) {
		return new AceitarPremioResponse(
				r.estadoPayout().name(), r.comprovante());
	}
}
