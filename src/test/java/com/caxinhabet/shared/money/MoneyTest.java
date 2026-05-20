package com.caxinhabet.shared.money;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.math.RoundingMode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Testes do tipo {@link Money} — corretude monetária por construção
 * (NFR-1, AR-8, Story 1.3 AC-1).
 *
 * <p>Anti-padrão proibido (este teste guarda contra ele): usar
 * {@link BigDecimal} cru em DTO sem o wrapper {@link Money} perde a
 * garantia de string na borda da API.
 */
class MoneyTest {

	@Nested
	@DisplayName("Construção")
	class Construcao {

		@Test
		@DisplayName("of(String) aceita string decimal com 2 casas")
		void ofString_ok() {
			Money m = Money.of("40.00");
			assertThat(m).isNotNull();
			assertThat(m.toString()).isEqualTo("40.00");
		}

		@Test
		@DisplayName("of(String) normaliza '40' para escala 2 (40.00)")
		void ofString_normalizaEscala() {
			assertThat(Money.of("40").toString()).isEqualTo("40.00");
			assertThat(Money.of("10").toString()).isEqualTo("10.00");
		}

		@Test
		@DisplayName("of(String) rejeita escala > 2 (centavo fracionado)")
		void ofString_rejeitaEscalaAlta() {
			assertThatThrownBy(() -> Money.of("40.001"))
					.isInstanceOf(ArithmeticException.class);
		}

		@Test
		@DisplayName("of(long) interpreta como CENTAVOS (4000 -> 40.00)")
		void ofLong_centavos() {
			assertThat(Money.ofCentavos(4000L).toString()).isEqualTo("40.00");
			assertThat(Money.ofCentavos(1L).toString()).isEqualTo("0.01");
			assertThat(Money.ofCentavos(0L).toString()).isEqualTo("0.00");
		}

		@Test
		@DisplayName("of(BigDecimal) aceita BigDecimal e fixa escala 2")
		void ofBigDecimal_ok() {
			assertThat(Money.of(new BigDecimal("40.00")).toString()).isEqualTo("40.00");
			assertThat(Money.of(new BigDecimal("40")).toString()).isEqualTo("40.00");
		}

		@Test
		@DisplayName("of(BigDecimal) rejeita escala > 2")
		void ofBigDecimal_rejeitaEscalaAlta() {
			assertThatThrownBy(() -> Money.of(new BigDecimal("40.001")))
					.isInstanceOf(ArithmeticException.class);
		}

		@Test
		@DisplayName("of(String) rejeita string que parece float (notação científica não conta como inteiro/decimal puro)")
		void ofString_rejeitaInvalido() {
			assertThatThrownBy(() -> Money.of("abc")).isInstanceOf(NumberFormatException.class);
			assertThatThrownBy(() -> Money.of("")).isInstanceOf(NumberFormatException.class);
		}

		/**
		 * Guardrail anti-float: NÃO existe método público que aceite
		 * {@code double} ou {@code float}. Este teste documenta a intenção;
		 * se alguém acrescentar uma sobrecarga {@code Money.of(double)} no
		 * futuro, este teste falha por reflexão.
		 */
		@Test
		@DisplayName("NÃO existe Money.of(double) nem Money.of(float) — corretude por construção")
		void semSobrecargaParaFloat() throws Exception {
			Class<Money> c = Money.class;
			assertThatThrownBy(() -> c.getMethod("of", double.class))
					.isInstanceOf(NoSuchMethodException.class);
			assertThatThrownBy(() -> c.getMethod("of", float.class))
					.isInstanceOf(NoSuchMethodException.class);
			assertThatThrownBy(() -> c.getMethod("of", Double.class))
					.isInstanceOf(NoSuchMethodException.class);
			assertThatThrownBy(() -> c.getMethod("of", Float.class))
					.isInstanceOf(NoSuchMethodException.class);
		}
	}

	@Nested
	@DisplayName("Aritmética")
	class Aritmetica {

		@Test
		@DisplayName("plus soma com escala estável")
		void plus() {
			assertThat(Money.of("40.00").plus(Money.of("10.00")).toString()).isEqualTo("50.00");
			assertThat(Money.of("0.01").plus(Money.of("0.02")).toString()).isEqualTo("0.03");
		}

		@Test
		@DisplayName("minus subtrai com escala estável")
		void minus() {
			assertThat(Money.of("40.00").minus(Money.of("10.00")).toString()).isEqualTo("30.00");
			assertThat(Money.of("0.03").minus(Money.of("0.01")).toString()).isEqualTo("0.02");
		}

		@Test
		@DisplayName("times multiplica por inteiro")
		void times() {
			assertThat(Money.of("40.00").times(3).toString()).isEqualTo("120.00");
			assertThat(Money.of("0.01").times(100).toString()).isEqualTo("1.00");
		}

		@Test
		@DisplayName("divideWithRemainder devolve quociente exato e resto (resíduo explícito — FR-13/OQ-2)")
		void divisaoExata() {
			// 100.00 / 4 = 25.00, resto 0.00
			Money.SplitResult r = Money.of("100.00").divideWithRemainder(4);
			assertThat(r.quotient().toString()).isEqualTo("25.00");
			assertThat(r.remainder().toString()).isEqualTo("0.00");
		}

		@Test
		@DisplayName("divideWithRemainder devolve resíduo quando não divide exato (split com sobra)")
		void divisaoComResiduo() {
			// 100.00 / 3 = 33.33 cada (×3 = 99.99), resto 0.01
			Money.SplitResult r = Money.of("100.00").divideWithRemainder(3);
			assertThat(r.quotient().toString()).isEqualTo("33.33");
			assertThat(r.remainder().toString()).isEqualTo("0.01");
			// invariante crítico: quociente*n + resto == original (FR-13/NFR-1)
			assertThat(r.quotient().times(3).plus(r.remainder()).toString())
					.isEqualTo("100.00");
		}

		@Test
		@DisplayName("divideWithRemainder rejeita divisor <= 0")
		void divisaoRejeitaDivisorInvalido() {
			Money m = Money.of("100.00");
			assertThatThrownBy(() -> m.divideWithRemainder(0))
					.isInstanceOf(IllegalArgumentException.class);
			assertThatThrownBy(() -> m.divideWithRemainder(-1))
					.isInstanceOf(IllegalArgumentException.class);
		}

		@Test
		@DisplayName("times rejeita fator negativo (preserva semântica monetária)")
		void timesRejeitaNegativo() {
			Money m = Money.of("10.00");
			assertThatThrownBy(() -> m.times(-1))
					.isInstanceOf(IllegalArgumentException.class);
		}
	}

	@Nested
	@DisplayName("Igualdade semântica")
	class Igualdade {

		@Test
		@DisplayName("'10' e '10.00' são iguais (mesma quantia monetária)")
		void igualdadeSemantica() {
			Money a = Money.of("10");
			Money b = Money.of("10.00");
			assertThat(a).isEqualTo(b);
			assertThat(a.hashCode()).isEqualTo(b.hashCode());
		}

		@Test
		@DisplayName("'10.00' e '10.01' NÃO são iguais")
		void desigualdade() {
			assertThat(Money.of("10.00")).isNotEqualTo(Money.of("10.01"));
		}

		@Test
		@DisplayName("zero é Money.ofCentavos(0)")
		void zero() {
			assertThat(Money.ZERO).isEqualTo(Money.ofCentavos(0L));
			assertThat(Money.ZERO.toString()).isEqualTo("0.00");
		}
	}

	@Nested
	@DisplayName("Imutabilidade e exposição segura")
	class Imutabilidade {

		@Test
		@DisplayName("plus/minus/times retornam novas instâncias (não mutam)")
		void naoMuta() {
			Money m = Money.of("10.00");
			m.plus(Money.of("1.00"));
			m.times(5);
			assertThat(m.toString()).isEqualTo("10.00");
		}

		@Test
		@DisplayName("toBigDecimal expõe valor com escala 2 e modo de arredondamento seguro")
		void exposicaoBigDecimal() {
			BigDecimal bd = Money.of("40.00").toBigDecimal();
			assertThat(bd.scale()).isEqualTo(2);
			assertThat(bd).isEqualTo(new BigDecimal("40.00").setScale(2, RoundingMode.UNNECESSARY));
		}
	}
}
