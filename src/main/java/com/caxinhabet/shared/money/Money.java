package com.caxinhabet.shared.money;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.io.IOException;
import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Tipo monetário único do sistema — corretude por construção (NFR-1, AR-8).
 *
 * <p>Encapsula {@link BigDecimal} com <b>escala fixa de 2</b> (centavos) e
 * proíbe construção a partir de {@code double}/{@code float} (sem sobrecargas
 * — não compila). Todo valor monetário do domínio trafega como {@code Money};
 * a fronteira JSON serializa como string decimal (ex.: {@code "40.00"}) via
 * {@link JsonValue}/{@link JsonCreator}, nunca como número JSON.
 *
 * <p><b>Anti-padrões PROIBIDOS:</b>
 * <ul>
 *   <li>Expor {@link BigDecimal} cru em DTO/API (perde a garantia de string).
 *   <li>Construir {@code Money} a partir de {@code double}/{@code float}
 *       (não há sobrecarga — não compila).
 *   <li>{@code spring.jackson.serialization.write-bigdecimal-as-plain} pensando
 *       que "serializa como string": é mentira do nome — só desliga notação
 *       científica. A garantia de string vem do {@code @JsonValue} aqui.
 * </ul>
 *
 * <p>Imutável. {@link #equals(Object)} compara <b>valor monetário</b>
 * ({@code compareTo == 0}), de modo que {@code Money.of("10")} e
 * {@code Money.of("10.00")} são iguais semanticamente.
 */
@JsonDeserialize(using = Money.MoneyDeserializer.class)
public final class Money implements Comparable<Money>, Serializable {

	private static final long serialVersionUID = 1L;

	/** Escala fixa de centavos. */
	public static final int SCALE = 2;

	/** Modo de arredondamento: nunca arredonda implicitamente. */
	public static final RoundingMode ROUNDING = RoundingMode.UNNECESSARY;

	/** Constante de conveniência: zero ({@code 0.00}). */
	public static final Money ZERO = new Money(BigDecimal.ZERO.setScale(SCALE, ROUNDING));

	private final BigDecimal valor;

	private Money(BigDecimal valorComEscala2) {
		this.valor = valorComEscala2;
	}

	/**
	 * Cria um {@code Money} a partir de string decimal (forma canônica da
	 * borda da API). Aceita {@code "40"} (normaliza para {@code "40.00"}) e
	 * {@code "40.00"} (mantém). Rejeita escala &gt; 2.
	 *
	 * @throws NumberFormatException se a string não é um número decimal válido
	 * @throws ArithmeticException se a escala for maior que 2
	 */
	@JsonCreator(mode = JsonCreator.Mode.DELEGATING)
	public static Money of(String valor) {
		Objects.requireNonNull(valor, "valor");
		BigDecimal bd = new BigDecimal(valor);
		return fromBigDecimal(bd);
	}

	/**
	 * Cria um {@code Money} a partir de {@link BigDecimal}. Aceita escala
	 * &le; 2; rejeita escala &gt; 2 (centavo fracionado).
	 *
	 * @throws ArithmeticException se a escala for maior que 2
	 */
	public static Money of(BigDecimal valor) {
		Objects.requireNonNull(valor, "valor");
		return fromBigDecimal(valor);
	}

	/**
	 * Cria um {@code Money} a partir de uma quantidade em <b>centavos</b>.
	 * {@code Money.ofCentavos(4000L) == Money.of("40.00")}.
	 */
	public static Money ofCentavos(long centavos) {
		return new Money(BigDecimal.valueOf(centavos, SCALE));
	}

	/**
	 * Quantidade exata em centavos (Story 2.2). Simétrico de
	 * {@link #ofCentavos(long)}: {@code Money.of("40.00").centavos() == 4000L}.
	 *
	 * <p>Usado pela borda do JPA para persistir como {@code BIGINT centavos}
	 * (decisão arquitetural da Story 2.2: dinheiro no DB é inteiro exato,
	 * não NUMERIC com escala). Como a escala interna é fixa em 2,
	 * {@code longValueExact} jamais lança em {@code Money} bem-formado.
	 */
	public long centavos() {
		return this.valor.movePointRight(SCALE).longValueExact();
	}

	private static Money fromBigDecimal(BigDecimal bd) {
		if (bd.scale() > SCALE) {
			throw new ArithmeticException(
					"Money não aceita escala > " + SCALE + " (recebido scale=" + bd.scale() + ")");
		}
		return new Money(bd.setScale(SCALE, ROUNDING));
	}

	// --- Aritmética ---

	/** Soma. */
	public Money plus(Money outro) {
		Objects.requireNonNull(outro, "outro");
		return new Money(this.valor.add(outro.valor));
	}

	/** Subtração. */
	public Money minus(Money outro) {
		Objects.requireNonNull(outro, "outro");
		return new Money(this.valor.subtract(outro.valor));
	}

	/**
	 * Multiplica por um inteiro não-negativo (semântica monetária — número
	 * de ingressos, número de vencedores, etc.).
	 *
	 * @throws IllegalArgumentException se {@code n < 0}
	 */
	public Money times(int n) {
		if (n < 0) {
			throw new IllegalArgumentException("Money.times(n): n deve ser >= 0, recebido " + n);
		}
		return new Money(this.valor.multiply(BigDecimal.valueOf(n)).setScale(SCALE, ROUNDING));
	}

	/**
	 * Divide por um inteiro positivo (split entre vencedores — FR-13/OQ-2),
	 * devolvendo <b>quociente truncado em centavos</b> e <b>resto explícito</b>.
	 *
	 * <p>Invariante: {@code quotient.times(n).plus(remainder).equals(this)}.
	 * O destino do resíduo (carry, plataforma, próximo vencedor) é decisão
	 * de negócio do split, não desta classe — esta classe só garante que
	 * nenhum centavo se perde ou se inventa (NFR-1).
	 *
	 * @throws IllegalArgumentException se {@code n <= 0}
	 */
	public SplitResult divideWithRemainder(int n) {
		if (n <= 0) {
			throw new IllegalArgumentException("Money.divideWithRemainder(n): n deve ser > 0, recebido " + n);
		}
		BigDecimal divisor = BigDecimal.valueOf(n);
		BigDecimal quociente = this.valor.divide(divisor, SCALE, RoundingMode.DOWN);
		BigDecimal resto = this.valor.subtract(quociente.multiply(divisor)).setScale(SCALE, ROUNDING);
		return new SplitResult(new Money(quociente), new Money(resto));
	}

	/** Resultado de {@link #divideWithRemainder(int)}: quociente + resto. */
	public record SplitResult(Money quotient, Money remainder) {}

	// --- Acesso seguro ---

	/** Expõe o valor como {@link BigDecimal} (escala 2). Não-mutante. */
	public BigDecimal toBigDecimal() {
		return this.valor;
	}

	/**
	 * String decimal usada na fronteira JSON (ex.: {@code "40.00"}). Esta é
	 * a forma <b>canônica</b> de dinheiro no contrato HTTP — o {@code @JsonValue}
	 * garante que toda serialização de {@code Money} use isso.
	 */
	@JsonValue
	@Override
	public String toString() {
		return this.valor.toPlainString();
	}

	// --- Igualdade semântica ---

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (!(o instanceof Money other)) return false;
		// compareTo == 0 trata "10" e "10.00" como iguais (mesma quantia).
		return this.valor.compareTo(other.valor) == 0;
	}

	@Override
	public int hashCode() {
		// Hash baseado no valor sem zeros à direita — coerente com equals.
		return this.valor.stripTrailingZeros().hashCode();
	}

	@Override
	public int compareTo(Money other) {
		Objects.requireNonNull(other, "other");
		return this.valor.compareTo(other.valor);
	}

	/**
	 * Desserializador estrito: <b>só aceita string JSON</b>. Rejeita número
	 * JSON (sem aspas) explicitamente — o contrato é string decimal (NFR-1).
	 * Se o cliente mandar {@code "valor": 40.00} como número, o boot da
	 * requisição falha com erro de formato, em vez de aceitar silenciosamente
	 * e propagar dinheiro impreciso.
	 */
	public static final class MoneyDeserializer extends JsonDeserializer<Money> {
		@Override
		public Money deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
			JsonToken token = p.currentToken();
			if (token != JsonToken.VALUE_STRING) {
				return (Money) ctxt.handleUnexpectedToken(
						Money.class,
						token,
						p,
						"Money deve chegar como string decimal (ex.: \"40.00\"), não como número JSON");
			}
			String text = p.getText();
			try {
				return Money.of(text);
			} catch (NumberFormatException | ArithmeticException e) {
				return (Money) ctxt.handleWeirdStringValue(
						Money.class, text, "valor monetário inválido: " + e.getMessage());
			}
		}
	}
}
