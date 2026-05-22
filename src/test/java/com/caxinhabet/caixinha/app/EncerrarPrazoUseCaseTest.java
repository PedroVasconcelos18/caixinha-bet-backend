package com.caxinhabet.caixinha.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.caxinhabet.auth.adapter.persistence.UsuarioEntity;
import com.caxinhabet.auth.adapter.persistence.UsuarioRepository;
import com.caxinhabet.caixinha.adapter.persistence.CaixinhaEntity;
import com.caxinhabet.caixinha.adapter.persistence.CaixinhaRepository;
import com.caxinhabet.caixinha.domain.ApuracaoInvalidaException;
import com.caxinhabet.caixinha.domain.EstadoCaixinha;
import com.caxinhabet.caixinha.domain.OperacaoNaoAutorizadaException;
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
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Story 4.5 (FR-15) — {@link EncerrarPrazoUseCase}: os 6 caminhos.
 */
@Testcontainers
@SpringBootTest
class EncerrarPrazoUseCaseTest {

	@Container
	static final PostgreSQLContainer<?> POSTGRES =
			new PostgreSQLContainer<>("postgres:17");

	@DynamicPropertySource
	static void props(DynamicPropertyRegistry r) {
		r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
		r.add("spring.datasource.username", POSTGRES::getUsername);
		r.add("spring.datasource.password", POSTGRES::getPassword);
	}

	// Story 5.1: o cancelamento dispara o Reembolso (DispararReembolsoService)
	// que invoca o PSP — mockado para não fazer HTTP real.
	@org.springframework.test.context.bean.override.mockito.MockitoBean
	private com.caxinhabet.pagamento.domain.ProvedorPagamento provedor;

	@Autowired private EncerrarPrazoUseCase encerrar;
	@Autowired private UsuarioRepository usuarios;
	@Autowired private CaixinhaRepository caixinhas;
	@Autowired private ParticipanteRepository participantes;

	private long organizadorId;
	private static final String ORG = "rafael@local";

	@BeforeEach
	void setUp() {
		participantes.deleteAll();
		caixinhas.deleteAll();
		usuarios.deleteAll();
		organizadorId = usuarios.save(UsuarioEntity.criar(ORG)).getId();
	}

	private CaixinhaEntity caixinha(EstadoCaixinha estado, int minimo) {
		return caixinhas.save(
				new CaixinhaEntity(
						"Brasil x Marrocos",
						"Brasil",
						"Marrocos",
						4000L,
						minimo,
						1,
						Instant.now().plusSeconds(86400 * 30),
						Instant.now().plusSeconds(86400 * 30 + 3600),
						estado,
						organizadorId));
	}

	private void participante(CaixinhaEntity c, String email, StatusParticipante status) {
		participantes.save(
				new ParticipanteEntity(
						c.getId(), organizadorId, email, email.equals(ORG), status));
	}

	@Test
	@DisplayName("AC-1: coletando_pagamentos com pagos >= mínimo → formada")
	void coletandoPagamentosViavelForma() {
		CaixinhaEntity c = caixinha(EstadoCaixinha.coletando_pagamentos, 3);
		participante(c, ORG, StatusParticipante.pago);
		participante(c, "ana@local", StatusParticipante.pago);
		participante(c, "beto@local", StatusParticipante.pago);

		EncerrarPrazoUseCase.Resultado r = encerrar.executar(c.getId(), organizadorId, ORG);

		assertThat(r.estadoResultante()).isEqualTo(EstadoCaixinha.formada);
		assertThat(r.reembolsoPendente()).isFalse();
	}

	@Test
	@DisplayName("AC-2: coletando_pagamentos com pagos < mínimo → cancelada + reembolso")
	void coletandoPagamentosInviavelCancela() {
		CaixinhaEntity c = caixinha(EstadoCaixinha.coletando_pagamentos, 5);
		participante(c, ORG, StatusParticipante.pago);
		participante(c, "ana@local", StatusParticipante.pago); // 2 < 5

		EncerrarPrazoUseCase.Resultado r = encerrar.executar(c.getId(), organizadorId, ORG);

		assertThat(r.estadoResultante()).isEqualTo(EstadoCaixinha.cancelada);
		assertThat(r.reembolsoPendente()).as("há pagantes — reembolso pendente").isTrue();
	}

	@Test
	@DisplayName("AC-3: coletando_convites → cancelada sem reembolso")
	void coletandoConvitesCancela() {
		CaixinhaEntity c = caixinha(EstadoCaixinha.coletando_convites, 3);
		participante(c, ORG, StatusParticipante.convidado);

		EncerrarPrazoUseCase.Resultado r = encerrar.executar(c.getId(), organizadorId, ORG);

		assertThat(r.estadoResultante()).isEqualTo(EstadoCaixinha.cancelada);
		assertThat(r.reembolsoPendente()).isFalse();
	}

	@Test
	@DisplayName("AC-4: formada → no-op idempotente")
	void formadaNoOp() {
		CaixinhaEntity c = caixinha(EstadoCaixinha.formada, 3);
		participante(c, ORG, StatusParticipante.pago);

		EncerrarPrazoUseCase.Resultado r = encerrar.executar(c.getId(), organizadorId, ORG);

		assertThat(r.estadoResultante()).isEqualTo(EstadoCaixinha.formada);
		assertThat(caixinhas.findById(c.getId()).orElseThrow().getEstado())
				.isEqualTo(EstadoCaixinha.formada);
	}

	@Test
	@DisplayName("AC-5: Caixinha apurada → rejeitado (422)")
	void apuradaRejeita() {
		CaixinhaEntity c = caixinha(EstadoCaixinha.apurada, 3);
		participante(c, ORG, StatusParticipante.pago);

		assertThatThrownBy(() -> encerrar.executar(c.getId(), organizadorId, ORG))
				.isInstanceOf(ApuracaoInvalidaException.class);
	}

	@Test
	@DisplayName("AC-6: não-Organizador → 403; não-Participante → 404")
	void autorizacao() {
		CaixinhaEntity c = caixinha(EstadoCaixinha.coletando_pagamentos, 3);
		participante(c, ORG, StatusParticipante.pago);
		UsuarioEntity bruno = usuarios.save(UsuarioEntity.criar("bruno@local"));
		participante(c, "bruno@local", StatusParticipante.pago);

		// bruno é Participante mas não Organizador → 403.
		assertThatThrownBy(() -> encerrar.executar(c.getId(), bruno.getId(), "bruno@local"))
				.isInstanceOf(OperacaoNaoAutorizadaException.class);

		// carla nem é Participante → 404.
		UsuarioEntity carla = usuarios.save(UsuarioEntity.criar("carla@local"));
		assertThatThrownBy(() -> encerrar.executar(c.getId(), carla.getId(), "carla@local"))
				.isInstanceOf(ResponseStatusException.class);
	}
}
