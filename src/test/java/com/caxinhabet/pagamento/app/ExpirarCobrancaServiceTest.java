package com.caxinhabet.pagamento.app;

import static org.assertj.core.api.Assertions.assertThat;

import com.caxinhabet.auth.adapter.persistence.UsuarioEntity;
import com.caxinhabet.auth.adapter.persistence.UsuarioRepository;
import com.caxinhabet.caixinha.adapter.persistence.CaixinhaEntity;
import com.caxinhabet.caixinha.adapter.persistence.CaixinhaRepository;
import com.caxinhabet.caixinha.domain.EstadoCaixinha;
import com.caxinhabet.pagamento.adapter.persistence.CobrancaEntity;
import com.caxinhabet.pagamento.adapter.persistence.CobrancaRepository;
import com.caxinhabet.pagamento.domain.EstadoCobranca;
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
 * Story 3.2 (FR-7) — {@link ExpirarCobrancaService}, expiração lazy.
 */
@Testcontainers
@SpringBootTest
class ExpirarCobrancaServiceTest {

	@Container
	static final PostgreSQLContainer<?> POSTGRES =
			new PostgreSQLContainer<>("postgres:17");

	@DynamicPropertySource
	static void props(DynamicPropertyRegistry r) {
		r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
		r.add("spring.datasource.username", POSTGRES::getUsername);
		r.add("spring.datasource.password", POSTGRES::getPassword);
	}

	@Autowired private ExpirarCobrancaService service;
	@Autowired private CaixinhaRepository caixinhas;
	@Autowired private ParticipanteRepository participantes;
	@Autowired private CobrancaRepository cobrancas;
	@Autowired private UsuarioRepository usuarios;

	private ParticipanteEntity participante;
	private long caixinhaId;

	@BeforeEach
	void setUp() {
		cobrancas.deleteAll();
		participantes.deleteAll();
		caixinhas.deleteAll();
		usuarios.deleteAll();

		UsuarioEntity organizador = usuarios.save(UsuarioEntity.criar("rafael@local"));
		CaixinhaEntity caixinha =
				caixinhas.save(
						new CaixinhaEntity(
								"Brasil x Marrocos",
								"Brasil",
								"Marrocos",
								4000L,
								3,
								1,
								Instant.now().plusSeconds(86400 * 30),
								Instant.now().plusSeconds(86400 * 30 + 3600),
								EstadoCaixinha.coletando_pagamentos,
								organizador.getId()));
		caixinhaId = caixinha.getId();
		participante =
				participantes.save(
						new ParticipanteEntity(
								caixinha.getId(),
								organizador.getId(),
								"alice@local",
								false,
								StatusParticipante.pagamento_iniciado));
	}

	@Test
	@DisplayName("Cobrança vencida → expirada + Participante volta a aceito")
	void cobrancaVencidaExpira() {
		CobrancaEntity vencida =
				new CobrancaEntity(
						participante.getId(),
						caixinhaId,
						"pay_vencida",
						"copia",
						"qr",
						4000L,
						Instant.now().minusSeconds(60)); // já venceu
		cobrancas.save(vencida);

		boolean expirou = service.expirarSeVencida(participante.getId());

		assertThat(expirou).isTrue();
		assertThat(cobrancas.findByCobrancaId("pay_vencida").orElseThrow().getEstado())
				.isEqualTo(EstadoCobranca.expirada);
		assertThat(participantes.findById(participante.getId()).orElseThrow().getStatus())
				.isEqualTo(StatusParticipante.aceito);
	}

	@Test
	@DisplayName("Cobrança não-vencida → no-op")
	void cobrancaNaoVencidaNoOp() {
		CobrancaEntity ativa =
				new CobrancaEntity(
						participante.getId(),
						caixinhaId,
						"pay_ativa",
						"copia",
						"qr",
						4000L,
						Instant.now().plusSeconds(3600)); // ainda válida
		cobrancas.save(ativa);

		boolean expirou = service.expirarSeVencida(participante.getId());

		assertThat(expirou).isFalse();
		assertThat(cobrancas.findByCobrancaId("pay_ativa").orElseThrow().getEstado())
				.isEqualTo(EstadoCobranca.ativa);
		assertThat(participantes.findById(participante.getId()).orElseThrow().getStatus())
				.isEqualTo(StatusParticipante.pagamento_iniciado);
	}

	@Test
	@DisplayName("Sem cobrança ativa → no-op (idempotente)")
	void semCobrancaNoOp() {
		boolean expirou = service.expirarSeVencida(participante.getId());
		assertThat(expirou).isFalse();
	}

	@Test
	@DisplayName("Fix #4: cobrança vencida em Caixinha `apurada` → NÃO expira (lock)")
	void naoExpiraSeCaixinhaApurada() {
		// Cobrança vencida, mas a Caixinha já foi apurada — lock de apuração.
		CobrancaEntity vencida =
				new CobrancaEntity(
						participante.getId(),
						caixinhaId,
						"pay_lock",
						"copia",
						"qr",
						4000L,
						Instant.now().minusSeconds(60));
		cobrancas.save(vencida);

		CaixinhaEntity c = caixinhas.findById(caixinhaId).orElseThrow();
		c.transicionarPara(EstadoCaixinha.formada);
		c.transicionarPara(EstadoCaixinha.apurada);
		caixinhas.save(c);

		boolean expirou = service.expirarSeVencida(participante.getId());

		assertThat(expirou).as("lock de apuração: não expira").isFalse();
		assertThat(cobrancas.findByCobrancaId("pay_lock").orElseThrow().getEstado())
				.isEqualTo(EstadoCobranca.ativa); // intocada
		assertThat(participantes.findById(participante.getId()).orElseThrow().getStatus())
				.isEqualTo(StatusParticipante.pagamento_iniciado); // não regrediu
	}
}
