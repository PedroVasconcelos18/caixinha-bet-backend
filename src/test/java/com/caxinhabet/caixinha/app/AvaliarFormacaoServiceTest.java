package com.caxinhabet.caixinha.app;

import static org.assertj.core.api.Assertions.assertThat;

import com.caxinhabet.auth.adapter.persistence.UsuarioEntity;
import com.caxinhabet.auth.adapter.persistence.UsuarioRepository;
import com.caxinhabet.caixinha.adapter.notification.LogFormacaoEmailSender;
import com.caxinhabet.caixinha.adapter.persistence.CaixinhaEntity;
import com.caxinhabet.caixinha.adapter.persistence.CaixinhaRepository;
import com.caxinhabet.caixinha.domain.EstadoCaixinha;
import com.caxinhabet.caixinha.domain.FormacaoEmail;
import com.caxinhabet.caixinha.domain.ReavaliarFormacao;
import com.caxinhabet.participante.adapter.persistence.ParticipanteEntity;
import com.caxinhabet.participante.adapter.persistence.ParticipanteRepository;
import com.caxinhabet.participante.domain.StatusParticipante;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Story 3.4 (FR-9) — {@code AvaliarFormacaoService} (a impl real de
 * {@link ReavaliarFormacao}).
 *
 * <p>Caixinha-alvo: ingresso R$ 40, {@code minimoParticipantes=3}.
 * Σ na Formação = pagos × R$ 40.
 */
@Testcontainers
@SpringBootTest
class AvaliarFormacaoServiceTest {

	@Container
	static final PostgreSQLContainer<?> POSTGRES =
			new PostgreSQLContainer<>("postgres:17");

	@DynamicPropertySource
	static void props(DynamicPropertyRegistry r) {
		r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
		r.add("spring.datasource.username", POSTGRES::getUsername);
		r.add("spring.datasource.password", POSTGRES::getPassword);
	}

	@Autowired private ReavaliarFormacao service; // resolve para AvaliarFormacaoService
	@Autowired private UsuarioRepository usuarios;
	@Autowired private CaixinhaRepository caixinhas;
	@Autowired private ParticipanteRepository participantes;
	@Autowired private LogFormacaoEmailSender logSender;

	private CaixinhaEntity caixinha;
	private long organizadorId;

	@BeforeEach
	void setUp() {
		participantes.deleteAll();
		caixinhas.deleteAll();
		usuarios.deleteAll();
		logSender.limpar();

		UsuarioEntity rafael = usuarios.save(UsuarioEntity.criar("rafael@local"));
		organizadorId = rafael.getId();
		caixinha = novaCaixinha(4000L, 3, EstadoCaixinha.coletando_pagamentos);
	}

	private CaixinhaEntity novaCaixinha(
			long ingressoCentavos, int minimo, EstadoCaixinha estado) {
		return caixinhas.save(
				new CaixinhaEntity(
						"Brasil x Marrocos",
						"Brasil",
						"Marrocos",
						ingressoCentavos,
						minimo,
						1,
						Instant.now().plusSeconds(86400 * 30),
						Instant.now().plusSeconds(86400 * 30 + 3600),
						estado,
						organizadorId));
	}

	/** Cria N Participantes no status dado. */
	private void participantesComStatus(
			CaixinhaEntity c, int quantos, StatusParticipante status) {
		for (int i = 0; i < quantos; i++) {
			participantes.save(
					new ParticipanteEntity(
							c.getId(), organizadorId, "p" + i + "@local", false, status));
		}
	}

	@Test
	@DisplayName("AC-1: pagos >= mínimo E Σ > R$10 → forma")
	void formaQuandoViavel() {
		participantesComStatus(caixinha, 3, StatusParticipante.pago); // Σ = 3×40 = 120

		service.reavaliar(caixinha.getId());

		assertThat(caixinhas.findById(caixinha.getId()).orElseThrow().getEstado())
				.isEqualTo(EstadoCaixinha.formada);
		assertThat(logSender.avisosEnviados())
				.hasSize(3)
				.allSatisfy(
						a -> assertThat(a.tipo()).isEqualTo(FormacaoEmail.Tipo.FORMADA));
	}

	@Test
	@DisplayName("AC-1: pagos < mínimo → NÃO forma")
	void naoFormaAbaixoDoMinimo() {
		participantesComStatus(caixinha, 2, StatusParticipante.pago); // 2 < 3

		service.reavaliar(caixinha.getId());

		assertThat(caixinhas.findById(caixinha.getId()).orElseThrow().getEstado())
				.isEqualTo(EstadoCaixinha.coletando_pagamentos);
		assertThat(logSender.avisosEnviados()).isEmpty();
	}

	@Test
	@DisplayName("AC-1 borda: Σ == R$10 (não > R$10) → NÃO forma")
	void naoFormaNaBordaExataDaTaxa() {
		// Ingresso R$ 5, mínimo 2 → Σ = 2×5 = R$ 10 (NÃO é > 10).
		CaixinhaEntity borda = novaCaixinha(500L, 2, EstadoCaixinha.coletando_pagamentos);
		participantesComStatus(borda, 2, StatusParticipante.pago);

		service.reavaliar(borda.getId());

		assertThat(caixinhas.findById(borda.getId()).orElseThrow().getEstado())
				.as("Σ = R$10 não é > R$10 — não forma (borda OQ-2)")
				.isEqualTo(EstadoCaixinha.coletando_pagamentos);
	}

	@Test
	@DisplayName("AC-3: formada + estorno derruba abaixo do mínimo → reverte")
	void reverteQuandoCaiAbaixoDoMinimo() {
		// Caixinha já formada, mas só 2 pagos agora (um estornou).
		CaixinhaEntity formada = novaCaixinha(4000L, 3, EstadoCaixinha.formada);
		participantesComStatus(formada, 2, StatusParticipante.pago);

		service.reavaliar(formada.getId());

		assertThat(caixinhas.findById(formada.getId()).orElseThrow().getEstado())
				.isEqualTo(EstadoCaixinha.coletando_pagamentos);
		assertThat(logSender.avisosEnviados())
				.hasSize(2)
				.allSatisfy(
						a -> assertThat(a.tipo()).isEqualTo(FormacaoEmail.Tipo.REVERTIDA));
	}

	@Test
	@DisplayName("AC-4 lock: apurada + estorno → NÃO reverte")
	void naoReverteSeApurada() {
		CaixinhaEntity apurada = novaCaixinha(4000L, 3, EstadoCaixinha.apurada);
		participantesComStatus(apurada, 1, StatusParticipante.pago); // bem abaixo

		service.reavaliar(apurada.getId());

		assertThat(caixinhas.findById(apurada.getId()).orElseThrow().getEstado())
				.as("lock de apuração: estado apurada não regride")
				.isEqualTo(EstadoCaixinha.apurada);
		assertThat(logSender.avisosEnviados()).isEmpty();
	}

	@Test
	@DisplayName("AC-5: reavaliar 2x já formada → idempotente, sem 2ª notificação")
	void idempotente() {
		participantesComStatus(caixinha, 3, StatusParticipante.pago);

		service.reavaliar(caixinha.getId()); // forma
		int aposPrimeira = logSender.avisosEnviados().size();
		service.reavaliar(caixinha.getId()); // já formada — no-op

		assertThat(caixinhas.findById(caixinha.getId()).orElseThrow().getEstado())
				.isEqualTo(EstadoCaixinha.formada);
		assertThat(logSender.avisosEnviados()).hasSize(aposPrimeira);
	}

	@Test
	@DisplayName("coletando_convites → no-op (pagamento ainda não liberado)")
	void noOpEmColetandoConvites() {
		CaixinhaEntity convites =
				novaCaixinha(4000L, 3, EstadoCaixinha.coletando_convites);
		participantesComStatus(convites, 5, StatusParticipante.pago);

		service.reavaliar(convites.getId());

		assertThat(caixinhas.findById(convites.getId()).orElseThrow().getEstado())
				.isEqualTo(EstadoCaixinha.coletando_convites);
	}
}
