package com.caxinhabet.caixinha.domain;

import java.util.List;

/**
 * Apuração rejeitada por violar uma pré-condição (Épico 4 v5, Story 4.2,
 * FR-12).
 *
 * <p>Mapeada para HTTP 422 (Unprocessable Entity / problem+json) pelo
 * {@code GlobalExceptionHandler}. Casos:
 * <ul>
 *   <li>Caixinha não está em {@code formada} (estado errado, ou já apurada
 *       — imutabilidade do Resultado Final).
 *   <li>Resultado Final escolhido não pertence à Caixinha.
 *   <li>Há mais palpiteiros corretos que o Nº de Ganhadores e o request
 *       não trouxe a seleção exata — neste caso a exceção carrega a lista
 *       de candidatos ({@link #candidatos()}) para o front montar a tela
 *       de seleção sem uma chamada extra.
 * </ul>
 */
public class ApuracaoInvalidaException extends RuntimeException {

	/**
	 * Candidatos a Ganhador (palpiteiros corretos) — preenchido SÓ no caso
	 * "mais corretos que vagas". Vazio nos demais casos.
	 */
	private final transient List<Candidato> candidatos;

	/** Palpiteiro correto candidato a Ganhador. */
	public record Candidato(long participanteId, String email) {}

	public ApuracaoInvalidaException(String message) {
		super(message);
		this.candidatos = List.of();
	}

	public ApuracaoInvalidaException(String message, List<Candidato> candidatos) {
		super(message);
		this.candidatos = candidatos == null ? List.of() : List.copyOf(candidatos);
	}

	public List<Candidato> candidatos() {
		return candidatos;
	}
}
