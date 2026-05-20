package com.caxinhabet.shared.money;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Contrato JSON do {@link Money} — Story 1.3 AC-2.
 *
 * <p>Garantia load-bearing: dinheiro vai para o JSON como <b>string decimal</b>
 * (ex.: {@code "40.00"}), <b>nunca</b> como número JSON. Sem isso, clientes
 * JavaScript leem como {@code Number} (IEEE 754) e perdem precisão.
 */
class MoneyJsonTest {

	private ObjectMapper mapper;

	@BeforeEach
	void setUp() {
		// ObjectMapper padrão do Spring Boot — o @JsonValue/@JsonCreator do
		// Money funciona sem nenhuma config global. É exatamente isso que
		// queremos: a garantia mora no TIPO, não em config dispersa.
		mapper = new ObjectMapper();
	}

	@Test
	@DisplayName("Money serializa como STRING JSON (\"40.00\"), nunca como número")
	void serializaComoString() throws JsonProcessingException {
		Money m = Money.of("40.00");
		String json = mapper.writeValueAsString(m);
		// Aspas em volta: é string JSON, não número
		assertThat(json).isEqualTo("\"40.00\"");
		assertThat(json).doesNotMatch("^-?\\d+(\\.\\d+)?$"); // não é um número JSON cru
	}

	@Test
	@DisplayName("Money normaliza escala '40' -> '40.00' no JSON")
	void serializaNormalizandoEscala() throws JsonProcessingException {
		assertThat(mapper.writeValueAsString(Money.of("40"))).isEqualTo("\"40.00\"");
		assertThat(mapper.writeValueAsString(Money.ZERO)).isEqualTo("\"0.00\"");
		assertThat(mapper.writeValueAsString(Money.ofCentavos(1L))).isEqualTo("\"0.01\"");
	}

	@Test
	@DisplayName("Money serializado dentro de um objeto pai continua string")
	void serializaDentroDeObjeto() throws JsonProcessingException {
		// Garantia que @JsonValue funciona como campo de POJO (não só raiz)
		record Cobranca(String id, Money valor) {}
		Cobranca c = new Cobranca("cob-1", Money.of("40.00"));
		String json = mapper.writeValueAsString(c);
		assertThat(json).isEqualTo("{\"id\":\"cob-1\",\"valor\":\"40.00\"}");
		// O valor JAMAIS pode aparecer como número:
		assertThat(json).doesNotContain(":40.00,");
		assertThat(json).doesNotContain(":40,");
	}

	@Test
	@DisplayName("Money desserializa de string JSON exatamente (sem passar por double)")
	void desserializaDeString() throws JsonProcessingException {
		Money m = mapper.readValue("\"40.00\"", Money.class);
		assertThat(m).isEqualTo(Money.of("40.00"));
		assertThat(m.toString()).isEqualTo("40.00");
	}

	@Test
	@DisplayName("Money desserializa '40' normalizando para 40.00")
	void desserializaNormalizandoEscala() throws JsonProcessingException {
		Money m = mapper.readValue("\"40\"", Money.class);
		assertThat(m).isEqualTo(Money.of("40.00"));
	}

	@Test
	@DisplayName("Money REJEITA desserializar de número JSON (sem aspas)")
	void rejeitaDesserializacaoDeNumeroJson() {
		// Se alguém mandar { "valor": 40.00 } (número), TEM que falhar — não
		// aceitar silenciosamente. O contrato é string.
		assertThatThrownBy(() -> mapper.readValue("40.00", Money.class))
				.isInstanceOfAny(InvalidFormatException.class, JsonProcessingException.class);
	}

	@Test
	@DisplayName("Money REJEITA escala > 2 vinda do JSON (ex.: \"40.001\")")
	void rejeitaEscalaInvalidaNoJson() {
		assertThatThrownBy(() -> mapper.readValue("\"40.001\"", Money.class))
				.isInstanceOf(JsonProcessingException.class);
	}
}
