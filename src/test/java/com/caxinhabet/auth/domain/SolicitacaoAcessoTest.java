package com.caxinhabet.auth.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Story 2.1 — invariantes do {@link SolicitacaoAcesso}.
 */
class SolicitacaoAcessoTest {

	private static final String HASH_VALIDO =
			"0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

	@Test
	@DisplayName("válida quando consumidoEm=null e agora < expiraEm")
	void valida() {
		Instant criado = Instant.parse("2026-05-20T10:00:00Z");
		Instant expira = criado.plusSeconds(900);
		SolicitacaoAcesso s = new SolicitacaoAcesso(1L, 7L, HASH_VALIDO, null, criado, expira, null);
		assertThat(s.estaValida(criado.plusSeconds(60))).isTrue();
	}

	@Test
	@DisplayName("inválida quando expirada")
	void expirada() {
		Instant criado = Instant.parse("2026-05-20T10:00:00Z");
		Instant expira = criado.plusSeconds(900);
		SolicitacaoAcesso s = new SolicitacaoAcesso(1L, 7L, HASH_VALIDO, null, criado, expira, null);
		assertThat(s.estaValida(expira.plusSeconds(1))).isFalse();
		assertThat(s.estaValida(expira)).isFalse();
	}

	@Test
	@DisplayName("inválida quando já consumida")
	void consumida() {
		Instant criado = Instant.parse("2026-05-20T10:00:00Z");
		Instant expira = criado.plusSeconds(900);
		SolicitacaoAcesso s =
				new SolicitacaoAcesso(
						1L, 7L, HASH_VALIDO, null, criado, expira, criado.plusSeconds(30));
		assertThat(s.estaValida(criado.plusSeconds(60))).isFalse();
	}

	@Test
	@DisplayName("tokenHash com tamanho diferente de 64 falha")
	void hashTamanhoErrado() {
		Instant criado = Instant.parse("2026-05-20T10:00:00Z");
		Instant expira = criado.plusSeconds(900);
		assertThatThrownBy(
						() ->
								new SolicitacaoAcesso(
										1L, 7L, "muito-curto", null, criado, expira, null))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("expiraEm <= criadoEm falha")
	void janelaInvertida() {
		Instant criado = Instant.parse("2026-05-20T10:00:00Z");
		assertThatThrownBy(
						() ->
								new SolicitacaoAcesso(
										1L, 7L, HASH_VALIDO, null, criado, criado, null))
				.isInstanceOf(IllegalArgumentException.class);
	}
}
