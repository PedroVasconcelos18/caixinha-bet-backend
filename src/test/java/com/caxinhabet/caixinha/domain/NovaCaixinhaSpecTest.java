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
				1, // numeroGanhadores (v5)
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
						0, // numeroGanhadores inválido (v5)
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
				.contains("numeroGanhadores")
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
						1, // numeroGanhadores (v5)
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
						1, // numeroGanhadores (v5)
						PRAZO,
						APURACAO,
						List.of("Um", "  ", ""),
						List.of());
		assertThat(spec.validar()).anyMatch(m -> m.contains("resultadosPossiveis"));
	}

	@Test
	@DisplayName("v5: numeroGanhadores fora de 1..3 → motivo registrado")
	void numeroGanhadoresForaIntervalo() {
		NovaCaixinhaSpec spec =
				new NovaCaixinhaSpec(
						"T",
						"A",
						"B",
						Money.of("10.00"),
						5,
						4, // > 3
						PRAZO,
						APURACAO,
						List.of("X", "Y"),
						List.of());
		assertThat(spec.validar())
				.anyMatch(m -> m.contains("numeroGanhadores"));
	}

	@Test
	@DisplayName("v5: numeroGanhadores > minimoParticipantes → motivo registrado")
	void numeroGanhadoresAcimaDoMinimo() {
		NovaCaixinhaSpec spec =
				new NovaCaixinhaSpec(
						"T",
						"A",
						"B",
						Money.of("10.00"),
						2,
						3, // > minimo 2
						PRAZO,
						APURACAO,
						List.of("X", "Y"),
						List.of());
		assertThat(spec.validar())
				.anyMatch(m -> m.contains("numeroGanhadores"));
	}

	@Test
	@DisplayName("prazoEntrada no passado → motivo 'data futura' registrado")
	void prazoEntradaNoPassado() {
		Instant passado = Instant.now().minusSeconds(3600);
		NovaCaixinhaSpec spec =
				new NovaCaixinhaSpec(
						"T",
						"A",
						"B",
						Money.of("10.00"),
						2,
						1,
						passado,
						passado.plusSeconds(7200), // apuração depois do prazo
						List.of("X", "Y"),
						List.of());
		assertThat(spec.validar())
				.anyMatch(m -> m.contains("prazoEntrada") && m.contains("futura"));
	}

	@Test
	@DisplayName("prazoEntrada no futuro → nenhum motivo de 'data futura'")
	void prazoEntradaNoFuturo() {
		Instant futuro = Instant.now().plusSeconds(3600);
		NovaCaixinhaSpec spec =
				new NovaCaixinhaSpec(
						"T",
						"A",
						"B",
						Money.of("10.00"),
						2,
						1,
						futuro,
						futuro.plusSeconds(7200),
						List.of("X", "Y"),
						List.of());
		assertThat(spec.validar())
				.noneMatch(m -> m.contains("futura"));
	}
}
