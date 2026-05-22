package com.caxinhabet.pagamento.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * Resposta de {@link ProvedorPagamento#transferir} ou
 * {@link ProvedorPagamento#consultarTransferencia} (FR-13 v5).
 *
 * <p>{@code transferenciaId} é o identificador retornado pelo PSP. O
 * domínio guarda esse id para reconciliação contra o extrato (NFR-2 / job
 * de reconciliação) e para anexar o comprovante ao Acerto de Contas.
 *
 * <p>{@code status} é a projeção do estado real do PSP no momento da
 * resposta. Possíveis valores (espelhando Asaas):
 * <ul>
 *   <li>{@code PENDENTE} — transferência aceita pelo PSP, aguardando
 *       liquidação.
 *   <li>{@code CONCLUIDA} — PIX confirmado na ponta destino.
 *   <li>{@code FALHA} — recusada (chave inválida, saldo insuficiente,
 *       etc.). O domínio reabre o Ganhador para correção (FR-13 v5).
 * </ul>
 *
 * @param transferenciaId id da transferência no PSP.
 * @param status estado atual da transferência.
 * @param confirmadaEm instante de confirmação na ponta destino, ou null
 *     se ainda {@code PENDENTE}/{@code FALHA}.
 */
public record ResultadoTransferencia(
		String transferenciaId, StatusTransferencia status, Instant confirmadaEm) {

	public ResultadoTransferencia {
		Objects.requireNonNull(transferenciaId, "transferenciaId");
		Objects.requireNonNull(status, "status");
		if (transferenciaId.isBlank()) {
			throw new IllegalArgumentException("transferenciaId não pode ser vazio");
		}
	}

	public enum StatusTransferencia {
		PENDENTE,
		CONCLUIDA,
		FALHA
	}
}
