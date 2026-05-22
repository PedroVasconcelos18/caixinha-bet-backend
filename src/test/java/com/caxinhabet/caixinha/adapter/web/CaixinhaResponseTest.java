package com.caxinhabet.caixinha.adapter.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.caxinhabet.caixinha.domain.Caixinha;
import com.caxinhabet.caixinha.domain.EstadoCaixinha;
import com.caxinhabet.caixinha.domain.ResultadoPossivel;
import com.caxinhabet.participante.adapter.persistence.ParticipanteEntity;
import com.caxinhabet.participante.domain.StatusParticipante;
import com.caxinhabet.shared.money.Money;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Story 3.5 (FR-10) — {@code CaixinhaResponse.de}: total custodiado,
 * Prêmio potencial e rótulo do Palpite.
 *
 * <p>Teste unitário puro (sem Spring) — {@code CaixinhaResponse.de} é
 * função pura sobre os dados recebidos.
 */
class CaixinhaResponseTest {

	private static final Instant PRAZO = Instant.parse("2026-06-01T12:00:00Z");
	private static final Instant APURACAO = Instant.parse("2026-06-01T14:00:00Z");

	private static Caixinha caixinha(Money ingresso, int minimo) {
		return new Caixinha(
				1L,
				"Brasil x Marrocos",
				"Brasil",
				"Marrocos",
				ingresso,
				minimo,
				1,
				PRAZO,
				APURACAO,
				EstadoCaixinha.coletando_pagamentos,
				99L,
				Instant.parse("2026-05-20T10:00:00Z"),
				List.of(
						new ResultadoPossivel(10L, 1L, 0, "Vitória do Brasil"),
						new ResultadoPossivel(11L, 1L, 1, "Empate")));
	}

	private static ParticipanteEntity participante(
			String email, StatusParticipante status, Long palpiteId) {
		ParticipanteEntity p =
				new ParticipanteEntity(1L, 99L, email, false, status);
		p.setPalpiteResultadoPossivelId(palpiteId);
		return p;
	}

	@Test
	@DisplayName("AC-3: total custodiado conta só Participantes `pago`")
	void totalCustodiadoContaSoPago() {
		// Ingresso R$ 40. 2 pago, 1 pagamento_iniciado, 1 aceito.
		List<ParticipanteEntity> ps =
				List.of(
						participante("a@l", StatusParticipante.pago, 10L),
						participante("b@l", StatusParticipante.pago, 11L),
						participante("c@l", StatusParticipante.pagamento_iniciado, null),
						participante("d@l", StatusParticipante.aceito, null));

		CaixinhaResponse r = CaixinhaResponse.de(caixinha(Money.of("40.00"), 3), ps);

		// Total = 2 × 40 = 80 (só os pago).
		assertThat(r.totalCustodiado()).isEqualTo(Money.of("80.00"));
		// Prêmio potencial = 80 − 10 = 70.
		assertThat(r.premioPotencial()).isEqualTo(Money.of("70.00"));
	}

	@Test
	@DisplayName("AC-3: Prêmio potencial não-negativo na borda (capado em zero)")
	void premioPotencialNaoNegativo() {
		// Ingresso R$ 5, 1 pago → total 5; 5 − 10 = -5 → capa em 0.
		List<ParticipanteEntity> ps =
				List.of(participante("a@l", StatusParticipante.pago, 10L));

		CaixinhaResponse r = CaixinhaResponse.de(caixinha(Money.of("5.00"), 2), ps);

		assertThat(r.totalCustodiado()).isEqualTo(Money.of("5.00"));
		assertThat(r.premioPotencial())
				.as("prêmio potencial nunca é negativo no painel")
				.isEqualTo(Money.of("0.00"));
	}

	@Test
	@DisplayName("AC-4: rótulo do Palpite resolvido; sem palpite → null")
	void rotuloDoPalpiteResolvido() {
		List<ParticipanteEntity> ps =
				List.of(
						participante("a@l", StatusParticipante.pago, 10L), // Vitória do Brasil
						participante("b@l", StatusParticipante.aceito, null)); // sem palpite

		CaixinhaResponse r = CaixinhaResponse.de(caixinha(Money.of("40.00"), 2), ps);

		ParticipanteResumoResponse a =
				r.participantes().stream()
						.filter(p -> p.email().equals("a@l"))
						.findFirst()
						.orElseThrow();
		ParticipanteResumoResponse b =
				r.participantes().stream()
						.filter(p -> p.email().equals("b@l"))
						.findFirst()
						.orElseThrow();

		assertThat(a.palpiteRotulo()).isEqualTo("Vitória do Brasil");
		assertThat(b.palpiteRotulo()).isNull();
	}

	@Test
	@DisplayName("Sem nenhum pago → total e prêmio potencial zerados")
	void semPagoTudoZero() {
		List<ParticipanteEntity> ps =
				List.of(participante("a@l", StatusParticipante.aceito, null));

		CaixinhaResponse r = CaixinhaResponse.de(caixinha(Money.of("40.00"), 3), ps);

		assertThat(r.totalCustodiado()).isEqualTo(Money.of("0.00"));
		assertThat(r.premioPotencial()).isEqualTo(Money.of("0.00"));
	}
}
