package com.caxinhabet.caixinha.app;

import static org.assertj.core.api.Assertions.assertThat;

import com.caxinhabet.auth.adapter.persistence.UsuarioEntity;
import com.caxinhabet.auth.adapter.persistence.UsuarioRepository;
import com.caxinhabet.caixinha.adapter.persistence.CaixinhaEntity;
import com.caxinhabet.caixinha.adapter.persistence.CaixinhaRepository;
import com.caxinhabet.caixinha.domain.EstadoCaixinha;
import com.caxinhabet.participante.adapter.persistence.ParticipanteEntity;
import com.caxinhabet.participante.adapter.persistence.ParticipanteRepository;
import com.caxinhabet.participante.domain.StatusParticipante;
import com.caxinhabet.shared.money.Money;
import java.time.Instant;
import java.util.List;
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
 * Story 6.1 (FR-17) — {@link ListarCaixinhasUseCase}: dashboard.
 */
@Testcontainers
@SpringBootTest
class ListarCaixinhasUseCaseTest {

	@Container
	static final PostgreSQLContainer<?> POSTGRES =
			new PostgreSQLContainer<>("postgres:17");

	@DynamicPropertySource
	static void props(DynamicPropertyRegistry r) {
		r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
		r.add("spring.datasource.username", POSTGRES::getUsername);
		r.add("spring.datasource.password", POSTGRES::getPassword);
	}

	@Autowired private ListarCaixinhasUseCase listar;
	@Autowired private UsuarioRepository usuarios;
	@Autowired private CaixinhaRepository caixinhas;
	@Autowired private ParticipanteRepository participantes;

	private long usuarioId;

	@BeforeEach
	void setUp() {
		participantes.deleteAll();
		caixinhas.deleteAll();
		usuarios.deleteAll();
		usuarioId = usuarios.save(UsuarioEntity.criar("rafael@local")).getId();
	}

	private CaixinhaEntity novaCaixinha(String titulo, EstadoCaixinha estado) {
		return caixinhas.save(
				new CaixinhaEntity(
						titulo,
						"Brasil",
						"Marrocos",
						4000L,
						3,
						1,
						Instant.now().plusSeconds(86400 * 30),
						Instant.now().plusSeconds(86400 * 30 + 3600),
						estado,
						usuarioId));
	}

	private int seqEmail = 0;

	/** Adiciona um Participante à Caixinha — e-mail único por chamada. */
	private void participanteEm(CaixinhaEntity c, long uId, StatusParticipante status) {
		participantes.save(
				new ParticipanteEntity(
						c.getId(), uId, "p" + (seqEmail++) + "@local", false, status));
	}

	@Test
	@DisplayName("AC-1: lista só as Caixinhas das quais o Usuário participa")
	void listaSoAsDoUsuario() {
		CaixinhaEntity minha = novaCaixinha("Minha", EstadoCaixinha.coletando_pagamentos);
		participanteEm(minha, usuarioId, StatusParticipante.aceito);

		// Caixinha de outro Usuário — não deve aparecer.
		long outroId = usuarios.save(UsuarioEntity.criar("outro@local")).getId();
		CaixinhaEntity alheia = novaCaixinha("Alheia", EstadoCaixinha.formada);
		participanteEm(alheia, outroId, StatusParticipante.pago);

		List<ListarCaixinhasUseCase.CaixinhaResumo> r = listar.executar(usuarioId);

		assertThat(r).hasSize(1);
		assertThat(r.get(0).titulo()).isEqualTo("Minha");
	}

	@Test
	@DisplayName("AC-1: progresso e prêmio potencial corretos")
	void progressoEPremio() {
		CaixinhaEntity c = novaCaixinha("Copa", EstadoCaixinha.coletando_pagamentos);
		participanteEm(c, usuarioId, StatusParticipante.pago);
		// + 2 pagantes (Σ = 3×40 = 120; prêmio = 110).
		participanteEm(c, usuarioId, StatusParticipante.pago);
		participanteEm(c, usuarioId, StatusParticipante.pago);

		List<ListarCaixinhasUseCase.CaixinhaResumo> r = listar.executar(usuarioId);

		assertThat(r).hasSize(1);
		assertThat(r.get(0).pagosConfirmados()).isEqualTo(3L);
		assertThat(r.get(0).minimoParticipantes()).isEqualTo(3);
		assertThat(r.get(0).premioPotencial()).isEqualTo(Money.of("110.00"));
	}

	@Test
	@DisplayName("AC-2: repassada e cancelada não são `ativa`")
	void terminaisNaoAtivas() {
		CaixinhaEntity ativa = novaCaixinha("Ativa", EstadoCaixinha.formada);
		participanteEm(ativa, usuarioId, StatusParticipante.aceito);
		CaixinhaEntity repassada = novaCaixinha("Repassada", EstadoCaixinha.repassada);
		participanteEm(repassada, usuarioId, StatusParticipante.pago);
		CaixinhaEntity cancelada = novaCaixinha("Cancelada", EstadoCaixinha.cancelada);
		participanteEm(cancelada, usuarioId, StatusParticipante.aceito);

		List<ListarCaixinhasUseCase.CaixinhaResumo> r = listar.executar(usuarioId);

		assertThat(r).hasSize(3);
		assertThat(r)
				.filteredOn(c -> c.titulo().equals("Ativa"))
				.allSatisfy(c -> assertThat(c.ativa()).isTrue());
		assertThat(r)
				.filteredOn(c -> !c.titulo().equals("Ativa"))
				.allSatisfy(c -> assertThat(c.ativa()).isFalse());
	}

	@Test
	@DisplayName("AC-3: prêmio potencial nunca negativo")
	void premioNaoNegativo() {
		// Ingresso R$ 40, 0 pagantes → Σ = 0; prêmio = max(0 − 10, 0) = 0.
		CaixinhaEntity c = novaCaixinha("Vazia", EstadoCaixinha.coletando_convites);
		participanteEm(c, usuarioId, StatusParticipante.convidado);

		List<ListarCaixinhasUseCase.CaixinhaResumo> r = listar.executar(usuarioId);

		assertThat(r.get(0).premioPotencial()).isEqualTo(Money.of("0.00"));
	}

	@Test
	@DisplayName("Usuário sem Caixinhas → lista vazia")
	void semCaixinhas() {
		assertThat(listar.executar(usuarioId)).isEmpty();
	}
}
