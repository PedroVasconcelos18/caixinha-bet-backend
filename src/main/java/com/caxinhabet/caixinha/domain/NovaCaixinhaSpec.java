package com.caxinhabet.caixinha.domain;

import com.caxinhabet.shared.money.Money;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Especificação de uma nova Caixinha vinda do wizard (Story 2.2).
 *
 * <p>Difere do record {@link Caixinha} porque:
 * <ul>
 *   <li>NÃO tem id/criadoEm/estado/organizadorUsuarioId (são responsabilidade
 *       do use case)
 *   <li>NÃO valida no construtor — usa {@link #validar()} para AGREGAR
 *       todos os motivos de invalidade e devolver uma lista. Melhor UX que
 *       erro a erro (uma única request → todos os problemas)
 * </ul>
 *
 * <p>Campos espelham 1:1 o payload da API ({@code CriarCaixinhaRequest}).
 * {@code emailsConvidados} é aceita aqui mas IGNORADA pelo use case da
 * Story 2.2 — Story 2.4 implementa convite por e-mail.
 */
public record NovaCaixinhaSpec(
		String titulo,
		String ladoA,
		String ladoB,
		Money valorIngresso,
		int minimoParticipantes,
		Instant prazoEntrada,
		Instant dataApuracao,
		List<String> rotulosResultados,
		List<String> emailsConvidados) {

	public NovaCaixinhaSpec {
		// Defensive copy + null safety (sem validar — fica para validar()).
		rotulosResultados = rotulosResultados == null ? List.of() : List.copyOf(rotulosResultados);
		emailsConvidados = emailsConvidados == null ? List.of() : List.copyOf(emailsConvidados);
	}

	/**
	 * Retorna a lista de motivos de invalidade. Lista vazia = válido.
	 * Verifica TUDO antes de retornar — não falha cedo.
	 */
	public List<String> validar() {
		List<String> motivos = new ArrayList<>();

		if (isBlank(titulo)) {
			motivos.add("titulo é obrigatório");
		} else if (titulo.length() > 120) {
			motivos.add("titulo excede 120 chars");
		}
		if (isBlank(ladoA)) {
			motivos.add("ladoA é obrigatório");
		} else if (ladoA.length() > 80) {
			motivos.add("ladoA excede 80 chars");
		}
		if (isBlank(ladoB)) {
			motivos.add("ladoB é obrigatório");
		} else if (ladoB.length() > 80) {
			motivos.add("ladoB excede 80 chars");
		}

		if (valorIngresso == null) {
			motivos.add("valorIngresso é obrigatório");
		} else if (valorIngresso.compareTo(Caixinha.INGRESSO_MINIMO) < 0) {
			motivos.add("valorIngresso deve ser >= R$ 5,00 (mínimo do provedor de pagamento)");
		}

		if (minimoParticipantes < 2) {
			motivos.add("minimoParticipantes deve ser >= 2");
		}

		if (prazoEntrada == null) {
			motivos.add("prazoEntrada é obrigatório");
		}
		if (dataApuracao == null) {
			motivos.add("dataApuracao é obrigatório");
		}
		if (prazoEntrada != null && dataApuracao != null && !dataApuracao.isAfter(prazoEntrada)) {
			motivos.add("dataApuracao deve ser depois de prazoEntrada");
		}

		List<String> rotulosNaoVazios =
				rotulosResultados.stream()
						.map(r -> r == null ? "" : r.trim())
						.filter(r -> !r.isEmpty())
						.toList();
		if (rotulosNaoVazios.size() < 2) {
			motivos.add(
					"resultadosPossiveis: pelo menos 2 rótulos não-vazios são obrigatórios");
		} else {
			Set<String> normalizados = new HashSet<>();
			for (String r : rotulosNaoVazios) {
				if (r.length() > 120) {
					motivos.add("resultadosPossiveis: rótulo excede 120 chars: " + r);
				}
				if (!normalizados.add(r.toLowerCase())) {
					motivos.add(
							"resultadosPossiveis: rótulos duplicados (ignorando case): " + r);
				}
			}
		}

		return List.copyOf(motivos);
	}

	private static boolean isBlank(String s) {
		return s == null || s.trim().isEmpty();
	}

	/** Rótulos normalizados (trim, sem vazios) — saída para o use case. */
	public List<String> rotulosNormalizados() {
		return rotulosResultados.stream()
				.map(r -> r == null ? null : r.trim())
				.filter(Objects::nonNull)
				.filter(r -> !r.isEmpty())
				.toList();
	}
}
