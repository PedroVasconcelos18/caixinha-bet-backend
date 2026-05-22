package com.caxinhabet.pagamento.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.caxinhabet.auth.adapter.persistence.UsuarioEntity;
import com.caxinhabet.auth.adapter.persistence.UsuarioRepository;
import com.caxinhabet.auth.domain.ChavePixObrigatoriaException;
import com.caxinhabet.caixinha.adapter.persistence.CaixinhaEntity;
import com.caxinhabet.caixinha.adapter.persistence.CaixinhaRepository;
import com.caxinhabet.caixinha.adapter.persistence.ResultadoPossivelEntity;
import com.caxinhabet.caixinha.adapter.persistence.ResultadoPossivelRepository;
import com.caxinhabet.caixinha.domain.EstadoCaixinha;
import com.caxinhabet.caixinha.domain.PagamentoIndisponivelException;
import com.caxinhabet.pagamento.adapter.persistence.CobrancaRepository;
import com.caxinhabet.pagamento.domain.CobrancaCriada;
import com.caxinhabet.pagamento.domain.EstadoCobranca;
import com.caxinhabet.pagamento.domain.ProvedorPagamento;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Story 3.2 (FR-7) — {@link GerarCobrancaUseCase}.
 *
 * <p>O PSP ({@link ProvedorPagamento}) é mockado via {@code @MockitoBean} —
 * não tocamos o Asaas real. O resto (Postgres, portas
 * {@code ConsultaCaixinha}/{@code PerfilPagamentoGateway}) é real, via
 * Testcontainers.
 */
@Testcontainers
@SpringBootTest
class GerarCobrancaUseCaseTest {

	@Container
	static final PostgreSQLContainer<?> POSTGRES =
			new PostgreSQLContainer<>("postgres:17");

	@DynamicPropertySource
	static void props(DynamicPropertyRegistry r) {
		r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
		r.add("spring.datasource.username", POSTGRES::getUsername);
		r.add("spring.datasource.password", POSTGRES::getPassword);
	}

	@MockitoBean private ProvedorPagamento provedor;

	@Autowired private GerarCobrancaUseCase useCase;
	@Autowired private UsuarioRepository usuarios;
	@Autowired private CaixinhaRepository caixinhas;
	@Autowired private ResultadoPossivelRepository resultados;
	@Autowired private ParticipanteRepository participantes;
	@Autowired private CobrancaRepository cobrancas;

	private UsuarioEntity alice;
	private CaixinhaEntity caixinha;
	private ResultadoPossivelEntity vBrasil;
	private ParticipanteEntity participanteAlice;

	@BeforeEach
	void setUp() {
		cobrancas.deleteAll();
		participantes.deleteAll();
		resultados.deleteAll();
		caixinhas.deleteAll();
		usuarios.deleteAll();

		// Alice com perfil de pagamento completo.
		alice = UsuarioEntity.criar("alice@local");
		alice.definirPerfilPagamento("Alice Silva", "39053344705"); // CPF válido
		alice.definirChavePix("alice@pix");
		alice = usuarios.save(alice);
		UsuarioEntity rafael = usuarios.save(UsuarioEntity.criar("rafael@local"));

		// Caixinha já em coletando_pagamentos.
		caixinha =
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
								rafael.getId()));
		vBrasil =
				resultados.save(
						new ResultadoPossivelEntity(caixinha.getId(), 0, "Vitória Brasil"));

		// Alice = Participante aceito com palpite.
		participanteAlice =
				new ParticipanteEntity(
						caixinha.getId(),
						alice.getId(),
						"alice@local",
						false,
						StatusParticipante.aceito);
		participanteAlice.setPalpiteResultadoPossivelId(vBrasil.getId());
		participanteAlice = participantes.save(participanteAlice);

		// Mock do PSP: criarCliente devolve um id; criarCobranca devolve cobrança.
		when(provedor.criarCliente(any()))
				.thenReturn("cus_test_alice");
		when(provedor.criarCobranca(any()))
				.thenReturn(
						new CobrancaCriada(
								"pay_test_1",
								"base64-qr",
								"00020126-copia-e-cola",
								Instant.now().plusSeconds(3600)));
	}

	@Test
	@DisplayName("AC-2: caso feliz — cobrança gerada, Participante vira pagamento_iniciado")
	void casoFeliz() {
		GerarCobrancaUseCase.Resultado r =
				useCase.executar(caixinha.getId(), "alice@local");

		assertThat(r.cobrancaId()).isEqualTo("pay_test_1");
		assertThat(r.copiaECola()).startsWith("0002");

		// Participante transicionou.
		ParticipanteEntity reload =
				participantes.findById(participanteAlice.getId()).orElseThrow();
		assertThat(reload.getStatus())
				.isEqualTo(StatusParticipante.pagamento_iniciado);

		// Cobrança persistida e ativa.
		assertThat(
						cobrancas.findByParticipanteIdAndEstado(
								participanteAlice.getId(), EstadoCobranca.ativa))
				.isPresent();

		// Customer Asaas registrado no perfil (lazy).
		assertThat(usuarios.findById(alice.getId()).orElseThrow().getAsaasCustomerId())
				.isEqualTo("cus_test_alice");
	}

	@Test
	@DisplayName("AC-3: gerar nova cobrança invalida a anterior")
	void invalidaCobrancaAnterior() {
		useCase.executar(caixinha.getId(), "alice@local");
		// Participante agora está pagamento_iniciado — para gerar de novo,
		// volta para aceito (simula o que a expiração/UX faria) e re-tenta.
		ParticipanteEntity p =
				participantes.findById(participanteAlice.getId()).orElseThrow();
		p.setStatus(StatusParticipante.aceito);
		participantes.save(p);

		when(provedor.criarCobranca(any()))
				.thenReturn(
						new CobrancaCriada(
								"pay_test_2",
								"base64-qr-2",
								"00020126-copia-e-cola-2",
								Instant.now().plusSeconds(3600)));

		useCase.executar(caixinha.getId(), "alice@local");

		// Só uma ativa (a nova); a antiga ficou invalidada.
		assertThat(
						cobrancas.findByCobrancaId("pay_test_1").orElseThrow().getEstado())
				.isEqualTo(EstadoCobranca.invalidada);
		assertThat(
						cobrancas.findByCobrancaId("pay_test_2").orElseThrow().getEstado())
				.isEqualTo(EstadoCobranca.ativa);
	}

	@Test
	@DisplayName("AC-1: Caixinha em coletando_convites → PagamentoIndisponivelException")
	void caixinhaAindaColetandoConvites() {
		caixinha.transicionarPara(EstadoCaixinha.coletando_convites);
		caixinhas.save(caixinha);

		assertThatThrownBy(() -> useCase.executar(caixinha.getId(), "alice@local"))
				.isInstanceOf(PagamentoIndisponivelException.class);
	}

	@Test
	@DisplayName("AC-1: Participante sem palpite → PagamentoIndisponivelException")
	void semPalpite() {
		participanteAlice.setPalpiteResultadoPossivelId(null);
		participantes.save(participanteAlice);

		assertThatThrownBy(() -> useCase.executar(caixinha.getId(), "alice@local"))
				.isInstanceOf(PagamentoIndisponivelException.class)
				.hasMessageContaining("palpite");
	}

	@Test
	@DisplayName("AC-1: perfil de pagamento incompleto → ChavePixObrigatoriaException")
	void perfilIncompleto() {
		alice.definirChavePix(null); // remove a chave PIX
		usuarios.save(alice);

		assertThatThrownBy(() -> useCase.executar(caixinha.getId(), "alice@local"))
				.isInstanceOf(ChavePixObrigatoriaException.class);
	}
}
