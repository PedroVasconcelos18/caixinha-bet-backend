package com.caxinhabet.caixinha.domain;

import java.util.List;

/**
 * Levantada quando uma {@code NovaCaixinhaSpec} falha em
 * {@link NovaCaixinhaSpec#validar()} (Story 2.2).
 *
 * <p>Mapeada para HTTP 422 + RFC 9457 com extensão {@code violations}
 * pelo {@code GlobalExceptionHandler}. Carrega a lista completa de
 * motivos — exibir todos de uma vez é melhor UX que erro a erro.
 *
 * <p>Distinta de {@code MethodArgumentNotValidException} (Bean
 * Validation), que mapeia para 400. 422 = "sintaxe OK, semântica
 * inválida" (RFC 9110 §15.5.21).
 */
public class CriacaoCaixinhaInvalidaException extends RuntimeException {

	private final List<String> motivos;

	public CriacaoCaixinhaInvalidaException(List<String> motivos) {
		super("Caixinha inválida: " + String.join("; ", motivos));
		this.motivos = List.copyOf(motivos);
	}

	public List<String> motivos() {
		return motivos;
	}
}
