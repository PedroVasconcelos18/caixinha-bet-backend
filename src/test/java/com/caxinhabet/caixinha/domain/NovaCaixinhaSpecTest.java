package com.caxinhabet.caixinha.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.caxinhabet.shared.money.Money;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Story 2.2 — {@link NovaCaixinhaSpec#validar()} agrega todos os motivos.
 */
class NovaCaixinhaSpecTest {

	private static final Instant PRAZO = Instant.parse("2026-06-01T12:00:00Z");
	private static final Instant APURACAO = Instant.parse("2026-06-01T14:00:00Z");

	private static NovaCaixinhaSpec valida() {
		return new NovaCaixinhaSpec(
				"Brasil x Marrocos",
				"Brasil",
				"Marrocos",
				Money.of("40.00"),
				5,
				PRAZO,
				APURACAO,
				List.of("Vitória A", "Empate", "Vitória B"),
				List.of());
	}

	@Test
	@DisplayName("Spec válido → lista vazia de motivos")
	void valido() {
		assertThat(valida().validar()).isEmpty();
	}

	@Test
	@DisplayName("Vários campos inválidos → motivos AGREGADOS (não falha cedo)")
	void agregaTodos() {
		NovaCaixinhaSpec spec =
				new NovaCaixinhaSpec(
						"",
						"",
						"",
						Money.of("1.00"), // < 5
						0, // < 2
						APURACAO,
						PRAZO, // apuracao <= prazo
						List.of("X"), // < 2 rótulos
						List.of());

		List<String> motivos = spec.validar();
		assertThat(motivos).hasSizeGreaterThanOrEqualTo(5);
		assertThat(String.join("|", motivos))
				.contains("titulo")
				.contains("ladoA")
				.contains("ladoB")
				.contains("valorIngresso")
				.contains("minimoParticipantes")
				.contains("dataApuracao")
				.contains("resultadosPossiveis");
	}

	@Test
	@DisplayName("rotulosNormalizados: trim + remove vazios + preserva ordem")
	void rotulosNormalizados() {
		NovaCaixinhaSpec spec =
				new NovaCaixinhaSpec(
						"T",
						"A",
						"B",
						Money.of("10.00"),
						2,
						PRAZO,
						APURACAO,
						List.of("  Um  ", "", "Dois", "  ", "Três"),
						List.of());
		assertThat(spec.rotulosNormalizados()).containsExactly("Um", "Dois", "Três");
	}

	@Test
	@DisplayName("rótulos vazios após trim NÃO contam (precisam de 2+ não-vazios)")
	void rotulosVaziosAposTrim() {
		NovaCaixinhaSpec spec =
				new NovaCaixinhaSpec(
						"T",
						"A",
						"B",
						Money.of("10.00"),
						2,
						PRAZO,
						APURACAO,
						List.of("Um", "  ", ""),
						List.of());
		assertThat(spec.validar()).anyMatch(m -> m.contains("resultadosPossiveis"));
	}
}
