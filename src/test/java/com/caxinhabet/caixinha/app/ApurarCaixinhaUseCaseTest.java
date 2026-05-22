package com.caxinhabet.caixinha.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.caxinhabet.auth.adapter.persistence.UsuarioEntity;
import com.caxinhabet.auth.adapter.persistence.UsuarioRepository;
import com.caxinhabet.caixinha.adapter.persistence.CaixinhaEntity;
import com.caxinhabet.caixinha.adapter.persistence.CaixinhaRepository;
import com.caxinhabet.caixinha.adapter.persistence.ResultadoPossivelEntity;
import com.caxinhabet.caixinha.adapter.persistence.ResultadoPossivelRepository;
import com.caxinhabet.caixinha.domain.ApuracaoInvalidaException;
import com.caxinhabet.caixinha.domain.EstadoCaixinha;
import com.caxinhabet.caixinha.domain.OperacaoNaoAutorizadaException;
import com.caxinhabet.participante.adapter.persistence.ParticipanteEntity;
import com.caxinhabet.participante.adapter.persistence.ParticipanteRepository;
import com.caxinhabet.participante.domain.StatusParticipante;
import com.caxinhabet.participante.domain.StatusVencedor;
import java.time.Instant;
import java.util.List;
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
 * Story 4.2 (FR-12) — {@link ApurarCaixinhaUseCase}: apuração, seleção de
 * Ganhadores e lock.
 */
@Testcontainers
@SpringBootTest
class ApurarCaixinhaUseCaseTest {

	@Container
	static final PostgreSQLContainer<?> POSTGRES =
			new PostgreSQLContainer<>("postgres:17");

	@DynamicPropertySource
	static void props(DynamicPropertyRegistry r) {
		r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
		r.add("spring.datasource.username", POSTGRES::getUsername);
		r.add("spring.datasource.password", POSTGRES::getPassword);
	}

	@Autowired private ApurarCaixinhaUseCase apurar;
	@Autowired private UsuarioRepository usuarios;
	@Autowired private CaixinhaRepository caixinhas;
	@Autowired private ResultadoPossivelRepository resultados;
	@Autowired private ParticipanteRepository participantes;

	private long organizadorId;
	private String organizadorEmail;
	private CaixinhaEntity caixinha;
	private ResultadoPossivelEntity vitoriaA;
	private ResultadoPossivelEntity vitoriaB;

	@BeforeEach
	void setUp() {
		participantes.deleteAll();
		resultados.deleteAll();
		caixinhas.deleteAll();
		usuarios.deleteAll();

		organizadorEmail = "rafael@local";
		UsuarioEntity rafael = usuarios.save(UsuarioEntity.criar(organizadorEmail));
		organizadorId = rafael.getId();
		caixinha = novaCaixinha(2, EstadoCaixinha.formada); // Nº Ganhadores = 2
		vitoriaA = resultados.save(new ResultadoPossivelEntity(caixinha.getId(), 0, "Vitória A"));
		vitoriaB = resultados.save(new ResultadoPossivelEntity(caixinha.getId(), 1, "Vitória B"));
	}

	private CaixinhaEntity novaCaixinha(int numeroGanhadores, EstadoCaixinha estado) {
		return caixinhas.save(
				new CaixinhaEntity(
						"Brasil x Marrocos",
						"Brasil",
						"Marrocos",
						4000L,
						3,
						numeroGanhadores,
						Instant.now().plusSeconds(86400 * 30),
						Instant.now().plusSeconds(86400 * 30 + 3600),
						estado,
						organizadorId));
	}

	/** Participante `pago` na {@link #caixinha} padrão (formada). */
	private ParticipanteEntity pagante(String email, long palpiteId) {
		return paganteEm(caixinha, email, palpiteId);
	}

	/** Participante `pago` com o palpite informado, numa Caixinha qualquer. */
	private ParticipanteEntity paganteEm(
			CaixinhaEntity cx, String email, long palpiteId) {
		ParticipanteEntity p =
				new ParticipanteEntity(
						cx.getId(),
						organizadorId,
						email,
						email.equals(organizadorEmail),
						StatusParticipante.pago);
		p.setPalpiteResultadoPossivelId(palpiteId);
		return participantes.save(p);
	}

	@Test
	@DisplayName("AC-1: Caixinha não-formada → 422")
	void naoFormadaRejeita() {
		CaixinhaEntity coletando = novaCaixinha(2, EstadoCaixinha.coletando_pagamentos);
		ResultadoPossivelEntity rc =
				resultados.save(new ResultadoPossivelEntity(coletando.getId(), 0, "X"));
		// O Organizador É Participante da Caixinha `coletando` — senão o 404
		// (anti-enumeração) dispararia antes da checagem de estado.
		paganteEm(coletando, organizadorEmail, rc.getId());

		assertThatThrownBy(
						() ->
								apurar.executar(
										coletando.getId(),
										organizadorId,
										organizadorEmail,
										rc.getId(),
										null))
				.isInstanceOf(ApuracaoInvalidaException.class)
				.hasMessageContaining("formada");
	}

	@Test
	@DisplayName("AC-2: não-Organizador → 403; não-Participante → 404")
	void autorizacao() {
		UsuarioEntity bruno = usuarios.save(UsuarioEntity.criar("bruno@local"));
		// bruno é Participante, mas não Organizador.
		pagante("bruno@local", vitoriaA.getId());
		pagante(organizadorEmail, vitoriaA.getId());

		assertThatThrownBy(
						() ->
								apurar.executar(
										caixinha.getId(),
										bruno.getId(),
										"bruno@local",
										vitoriaA.getId(),
										null))
				.isInstanceOf(OperacaoNaoAutorizadaException.class);

		// carla nem é Participante → 404.
		UsuarioEntity carla = usuarios.save(UsuarioEntity.criar("carla@local"));
		assertThatThrownBy(
						() ->
								apurar.executar(
										caixinha.getId(),
										carla.getId(),
										"carla@local",
										vitoriaA.getId(),
										null))
				.isInstanceOf(ResponseStatusException.class);
	}

	@Test
	@DisplayName("AC-3: corretos ≤ Nº Ganhadores → todos auto-marcados")
	void corretosAbaixoDoNumeroAutoMarca() {
		// Nº Ganhadores = 2; 2 acertaram Vitória A.
		ParticipanteEntity p1 = pagante(organizadorEmail, vitoriaA.getId());
		ParticipanteEntity p2 = pagante("ana@local", vitoriaA.getId());
		pagante("beto@local", vitoriaB.getId()); // errou

		ApurarCaixinhaUseCase.Resultado r =
				apurar.executar(
						caixinha.getId(), organizadorId, organizadorEmail, vitoriaA.getId(), null);

		assertThat(r.modoReembolso()).isFalse();
		assertThat(r.ganhadoresIds()).containsExactlyInAnyOrder(p1.getId(), p2.getId());
		// Com Ganhadores, a Caixinha vai direto a repasse_parcial (Story 4.3
		// roda na mesma transação — Glossário §3 v5).
		assertThat(caixinhas.findById(caixinha.getId()).orElseThrow().getEstado())
				.isEqualTo(EstadoCaixinha.repasse_parcial);
		assertThat(participantes.findById(p1.getId()).orElseThrow().getStatusVencedor())
				.isEqualTo(StatusVencedor.vencedor_aguardando_aceite);
	}

	@Test
	@DisplayName("AC-3: corretos > Nº sem seleção → 422 com candidatos")
	void corretosAcimaDoNumeroSemSelecaoRejeita() {
		// Nº = 2; 3 acertaram → precisa escolher.
		pagante(organizadorEmail, vitoriaA.getId());
		pagante("ana@local", vitoriaA.getId());
		pagante("beto@local", vitoriaA.getId());

		assertThatThrownBy(
						() ->
								apurar.executar(
										caixinha.getId(),
										organizadorId,
										organizadorEmail,
										vitoriaA.getId(),
										null))
				.isInstanceOf(ApuracaoInvalidaException.class)
				.satisfies(
						e ->
								assertThat(
												((ApuracaoInvalidaException) e).candidatos())
										.hasSize(3));
	}

	@Test
	@DisplayName("AC-3: corretos > Nº com seleção válida → marca os escolhidos")
	void corretosAcimaDoNumeroComSelecaoValida() {
		ParticipanteEntity p1 = pagante(organizadorEmail, vitoriaA.getId());
		ParticipanteEntity p2 = pagante("ana@local", vitoriaA.getId());
		ParticipanteEntity p3 = pagante("beto@local", vitoriaA.getId());

		ApurarCaixinhaUseCase.Resultado r =
				apurar.executar(
						caixinha.getId(),
						organizadorId,
						organizadorEmail,
						vitoriaA.getId(),
						List.of(p1.getId(), p3.getId()));

		assertThat(r.ganhadoresIds()).containsExactlyInAnyOrder(p1.getId(), p3.getId());
		// p2 NÃO foi escolhido — não é vencedor.
		assertThat(participantes.findById(p2.getId()).orElseThrow().getStatusVencedor())
				.isNull();
	}

	@Test
	@DisplayName("AC-3: seleção com participante que não acertou → 422")
	void selecaoComNaoCorretoRejeita() {
		ParticipanteEntity p1 = pagante(organizadorEmail, vitoriaA.getId());
		pagante("ana@local", vitoriaA.getId());
		pagante("beto@local", vitoriaA.getId());
		ParticipanteEntity errou = pagante("ze@local", vitoriaB.getId());

		assertThatThrownBy(
						() ->
								apurar.executar(
										caixinha.getId(),
										organizadorId,
										organizadorEmail,
										vitoriaA.getId(),
										List.of(p1.getId(), errou.getId())))
				.isInstanceOf(ApuracaoInvalidaException.class)
				.hasMessageContaining("não é palpiteiro correto");
	}

	@Test
	@DisplayName("AC-3: 0 palpiteiros corretos → apurada em modo reembolso")
	void zeroCorretosModoReembolso() {
		pagante(organizadorEmail, vitoriaB.getId()); // todos erraram
		pagante("ana@local", vitoriaB.getId());

		ApurarCaixinhaUseCase.Resultado r =
				apurar.executar(
						caixinha.getId(), organizadorId, organizadorEmail, vitoriaA.getId(), null);

		assertThat(r.modoReembolso()).isTrue();
		assertThat(r.ganhadoresIds()).isEmpty();
		assertThat(caixinhas.findById(caixinha.getId()).orElseThrow().getEstado())
				.isEqualTo(EstadoCaixinha.apurada);
	}

	@Test
	@DisplayName("AC-5: Caixinha já apurada → 422 (imutabilidade)")
	void jaApuradaRejeita() {
		pagante(organizadorEmail, vitoriaA.getId());
		apurar.executar(
				caixinha.getId(), organizadorId, organizadorEmail, vitoriaA.getId(), null);

		assertThatThrownBy(
						() ->
								apurar.executar(
										caixinha.getId(),
										organizadorId,
										organizadorEmail,
										vitoriaB.getId(),
										null))
				.isInstanceOf(ApuracaoInvalidaException.class)
				.hasMessageContaining("já foi apurada");
	}

	@Test
	@DisplayName("AC-5: Resultado Final de outra Caixinha → 422")
	void resultadoDeOutraCaixinhaRejeita() {
		CaixinhaEntity outra = novaCaixinha(1, EstadoCaixinha.formada);
		ResultadoPossivelEntity alheio =
				resultados.save(new ResultadoPossivelEntity(outra.getId(), 0, "X"));
		pagante(organizadorEmail, vitoriaA.getId());

		assertThatThrownBy(
						() ->
								apurar.executar(
										caixinha.getId(),
										organizadorId,
										organizadorEmail,
										alheio.getId(),
										null))
				.isInstanceOf(ApuracaoInvalidaException.class)
				.hasMessageContaining("não pertence");
	}

	@Test
	@DisplayName("AC-5: Resultado Final fica registrado na Caixinha")
	void resultadoFinalRegistrado() {
		pagante(organizadorEmail, vitoriaA.getId());
		apurar.executar(
				caixinha.getId(), organizadorId, organizadorEmail, vitoriaA.getId(), null);

		assertThat(caixinhas.findById(caixinha.getId()).orElseThrow().getResultadoFinalId())
				.isEqualTo(vitoriaA.getId());
	}
}
