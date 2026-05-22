package com.caxinhabet.caixinha.domain;

import com.caxinhabet.shared.money.Money;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Caixinha — unidade central do produto (PRD §3).
 *
 * <p>Record imutável carregando o estado completo da Caixinha conforme
 * existe no banco. Validações no construtor garantem que NUNCA existe um
 * {@code Caixinha} em memória representando um estado ilegal — defesa em
 * profundidade sobre os {@code CHECK} do schema (Migration V4).
 *
 * <p>Imutabilidade pós-criação (FR-3): garantida por construção — não há
 * método mutador, nem endpoint {@code PATCH /caixinhas/{id}}. Tentativa
 * de alteração só pode acontecer via {@code UPDATE} direto no DB
 * (responsabilidade humana, fora do escopo).
 *
 * <p>{@code resultadosPossiveis} chega ordenado por {@link
 * ResultadoPossivel#ordem()} ASC (o repositório usa
 * {@code findByCaixinhaIdOrderByOrdemAsc}).
 */
public record Caixinha(
		Long id,
		String titulo,
		String ladoA,
		String ladoB,
		Money valorIngresso,
		int minimoParticipantes,
		int numeroGanhadores,
		Instant prazoEntrada,
		Instant dataApuracao,
		EstadoCaixinha estado,
		Long organizadorUsuarioId,
		Instant criadoEm,
		List<ResultadoPossivel> resultadosPossiveis) {

	/** Taxa de Serviço fixa por Caixinha (PRD §3). */
	public static final Money TAXA_SERVICO = Money.of("10.00");

	/** Valor mínimo do ingresso — regra Asaas (Story 1.5, R$ 5,00). */
	public static final Money INGRESSO_MINIMO = Money.of("5.00");

	/** Nº de Ganhadores máximo no wizard — PRD §3 v5 (FR-1 v5). */
	public static final int MAX_GANHADORES = 3;

	public Caixinha {
		if (titulo == null || titulo.isBlank()) {
			throw new IllegalArgumentException("titulo é obrigatório");
		}
		if (titulo.length() > 120) {
			throw new IllegalArgumentException("titulo excede 120 chars");
		}
		if (ladoA == null || ladoA.isBlank()) {
			throw new IllegalArgumentException("ladoA é obrigatório");
		}
		if (ladoA.length() > 80) {
			throw new IllegalArgumentException("ladoA excede 80 chars");
		}
		if (ladoB == null || ladoB.isBlank()) {
			throw new IllegalArgumentException("ladoB é obrigatório");
		}
		if (ladoB.length() > 80) {
			throw new IllegalArgumentException("ladoB excede 80 chars");
		}
		if (valorIngresso == null) {
			throw new IllegalArgumentException("valorIngresso é obrigatório");
		}
		if (valorIngresso.compareTo(INGRESSO_MINIMO) < 0) {
			throw new IllegalArgumentException(
					"valorIngresso deve ser >= " + INGRESSO_MINIMO + " (mínimo Asaas)");
		}
		if (minimoParticipantes < 2) {
			throw new IllegalArgumentException("minimoParticipantes deve ser >= 2");
		}
		// FR-1 v5: Nº de Ganhadores (1, 2 ou 3) e ≤ minimoParticipantes.
		if (numeroGanhadores < 1 || numeroGanhadores > MAX_GANHADORES) {
			throw new IllegalArgumentException(
					"numeroGanhadores deve estar entre 1 e " + MAX_GANHADORES);
		}
		if (numeroGanhadores > minimoParticipantes) {
			throw new IllegalArgumentException(
					"numeroGanhadores não pode ser maior que minimoParticipantes");
		}
		if (prazoEntrada == null) {
			throw new IllegalArgumentException("prazoEntrada é obrigatório");
		}
		if (dataApuracao == null) {
			throw new IllegalArgumentException("dataApuracao é obrigatório");
		}
		if (!dataApuracao.isAfter(prazoEntrada)) {
			throw new IllegalArgumentException(
					"dataApuracao deve ser depois de prazoEntrada");
		}
		if (estado == null) {
			throw new IllegalArgumentException("estado é obrigatório");
		}
		if (organizadorUsuarioId == null) {
			throw new IllegalArgumentException("organizadorUsuarioId é obrigatório");
		}
		if (resultadosPossiveis == null || resultadosPossiveis.size() < 2) {
			throw new IllegalArgumentException(
					"resultadosPossiveis deve ter pelo menos 2 itens");
		}
		Set<String> rotulosNormalizados =
				resultadosPossiveis.stream()
						.map(r -> r.rotulo().trim().toLowerCase())
						.collect(Collectors.toSet());
		if (rotulosNormalizados.size() != resultadosPossiveis.size()) {
			throw new IllegalArgumentException(
					"resultadosPossiveis não pode ter rótulos duplicados (ignorando case)");
		}
		// Lista defensivamente imutável.
		resultadosPossiveis = List.copyOf(resultadosPossiveis);
	}

	/**
	 * Prêmio máximo teórico exibido na revisão do wizard (AC-3): valor
	 * informativo, NÃO compromisso. Σ se todos pagarem − Taxa.
	 */
	public Money premioMaximoTeorico() {
		Money potencialTotal = valorIngresso.times(minimoParticipantes);
		return potencialTotal.minus(TAXA_SERVICO);
	}
}
