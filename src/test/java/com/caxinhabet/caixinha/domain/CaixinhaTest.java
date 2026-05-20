package com.caxinhabet.caixinha.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.caxinhabet.shared.money.Money;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Story 2.2 — invariantes do record {@link Caixinha}.
 */
class CaixinhaTest {

	private static final Instant PRAZO = Instant.parse("2026-06-01T12:00:00Z");
	private static final Instant APURACAO = Instant.parse("2026-06-01T14:00:00Z");

	private static List<ResultadoPossivel> doisResultados() {
		return List.of(
				new ResultadoPossivel(1L, 99L, 0, "Vitória A"),
				new ResultadoPossivel(2L, 99L, 1, "Vitória B"));
	}

	private static Caixinha valida() {
		return new Caixinha(
				99L,
				"Brasil x Marrocos",
				"Brasil",
				"Marrocos",
				Money.of("40.00"),
				5,
				PRAZO,
				APURACAO,
				EstadoCaixinha.coletando_convites,
				1L,
				Instant.parse("2026-05-20T10:00:00Z"),
				doisResultados());
	}

	@Test
	@DisplayName("caso feliz: cria sem lançar")
	void casoFeliz() {
		Caixinha c = valida();
		assertThat(c.titulo()).isEqualTo("Brasil x Marrocos");
		assertThat(c.premioMaximoTeorico()).isEqualTo(Money.of("190.00")); // 5×40 - 10
	}

	@Test
	@DisplayName("titulo vazio falha")
	void tituloVazio() {
		assertThatThrownBy(() -> new Caixinha(99L, "  ", "A", "B", Money.of("40.00"), 5, PRAZO, APURACAO, EstadoCaixinha.coletando_convites, 1L, Instant.now(), doisResultados()))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("valor < R$ 5 falha")
	void valorAbaixoMinimo() {
		assertThatThrownBy(() -> new Caixinha(99L, "T", "A", "B", Money.of("4.99"), 5, PRAZO, APURACAO, EstadoCaixinha.coletando_convites, 1L, Instant.now(), doisResultados()))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("5.00");
	}

	@Test
	@DisplayName("minimoParticipantes < 2 falha")
	void minimoMenorQueDois() {
		assertThatThrownBy(() -> new Caixinha(99L, "T", "A", "B", Money.of("10.00"), 1, PRAZO, APURACAO, EstadoCaixinha.coletando_convites, 1L, Instant.now(), doisResultados()))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("dataApuracao <= prazoEntrada falha")
	void apuracaoNaoPosteriorAoPrazo() {
		assertThatThrownBy(() -> new Caixinha(99L, "T", "A", "B", Money.of("10.00"), 2, PRAZO, PRAZO, EstadoCaixinha.coletando_convites, 1L, Instant.now(), doisResultados()))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("<2 resultados falha")
	void resultadosInsuficientes() {
		assertThatThrownBy(() -> new Caixinha(99L, "T", "A", "B", Money.of("10.00"), 2, PRAZO, APURACAO, EstadoCaixinha.coletando_convites, 1L, Instant.now(), List.of(new ResultadoPossivel(1L, 99L, 0, "Único"))))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("rótulos duplicados (case-insensitive) falham")
	void rotulosDuplicados() {
		List<ResultadoPossivel> dup =
				List.of(
						new ResultadoPossivel(1L, 99L, 0, "Empate"),
						new ResultadoPossivel(2L, 99L, 1, "EMPATE"));
		assertThatThrownBy(() -> new Caixinha(99L, "T", "A", "B", Money.of("10.00"), 2, PRAZO, APURACAO, EstadoCaixinha.coletando_convites, 1L, Instant.now(), dup))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("duplicados");
	}

	@Test
	@DisplayName("Constantes TAXA_SERVICO e INGRESSO_MINIMO")
	void constantesPRD() {
		assertThat(Caixinha.TAXA_SERVICO).isEqualTo(Money.of("10.00"));
		assertThat(Caixinha.INGRESSO_MINIMO).isEqualTo(Money.of("5.00"));
	}
}
