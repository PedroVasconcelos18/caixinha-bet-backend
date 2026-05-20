package com.caxinhabet.auth.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SessaoUsuarioTest {

	@Test
	@DisplayName("estaExpirada=false antes de expiraEm; true a partir dela")
	void expira() {
		Instant criada = Instant.parse("2026-05-20T10:00:00Z");
		Instant expira = criada.plusSeconds(60);
		SessaoUsuario s = new SessaoUsuario("sid", 1L, "a@b", criada, expira);
		assertThat(s.estaExpirada(criada)).isFalse();
		assertThat(s.estaExpirada(expira)).isTrue();
		assertThat(s.estaExpirada(expira.plusSeconds(1))).isTrue();
	}

	@Test
	@DisplayName("idSessao e email obrigatórios")
	void camposObrigatorios() {
		Instant criada = Instant.parse("2026-05-20T10:00:00Z");
		Instant expira = criada.plusSeconds(60);
		assertThatThrownBy(() -> new SessaoUsuario("", 1L, "a@b", criada, expira))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new SessaoUsuario("sid", 1L, " ", criada, expira))
				.isInstanceOf(IllegalArgumentException.class);
	}
}
