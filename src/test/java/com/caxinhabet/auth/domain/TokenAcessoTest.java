package com.caxinhabet.auth.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Story 2.1 Task 11.1 — propriedades do {@link TokenAcesso}.
 */
class TokenAcessoTest {

	@Test
	@DisplayName("gerar() produz tokens distintos a cada chamada")
	void geraTokensDistintos() {
		TokenAcesso a = TokenAcesso.gerar();
		TokenAcesso b = TokenAcesso.gerar();
		assertThat(a.valor()).isNotEqualTo(b.valor());
	}

	@Test
	@DisplayName("valor() tem 43 chars Base64URL sem padding (32 bytes de entropia)")
	void valorTem43Chars() {
		assertThat(TokenAcesso.gerar().valor())
				.hasSize(43)
				.matches("^[A-Za-z0-9_-]+$");
	}

	@Test
	@DisplayName("hash() é determinístico (mesmo token → mesmo hash)")
	void hashDeterministico() {
		TokenAcesso t = TokenAcesso.gerar();
		assertThat(t.hash()).isEqualTo(t.hash());
	}

	@Test
	@DisplayName("hash() tem 64 chars hex")
	void hashTem64Hex() {
		assertThat(TokenAcesso.gerar().hash()).hasSize(64).matches("^[0-9a-f]+$");
	}

	@Test
	@DisplayName("tokens diferentes geram hashes diferentes")
	void hashesDiferentes() {
		assertThat(TokenAcesso.gerar().hash()).isNotEqualTo(TokenAcesso.gerar().hash());
	}

	@Test
	@DisplayName("TokenAcesso.de(valor) reconstrói com o mesmo hash")
	void deReconstroi() {
		TokenAcesso original = TokenAcesso.gerar();
		TokenAcesso recriado = TokenAcesso.de(original.valor());
		assertThat(recriado.hash()).isEqualTo(original.hash());
	}

	@Test
	@DisplayName("TokenAcesso.de(null) e de(\"\") falham")
	void deRejeitaInvalido() {
		assertThatThrownBy(() -> TokenAcesso.de(null))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> TokenAcesso.de(""))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> TokenAcesso.de("  "))
				.isInstanceOf(IllegalArgumentException.class);
	}
}
