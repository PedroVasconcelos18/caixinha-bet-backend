package com.caxinhabet.caixinha.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.caxinhabet.auth.adapter.persistence.UsuarioEntity;
import com.caxinhabet.auth.adapter.persistence.UsuarioRepository;
import com.caxinhabet.caixinha.adapter.persistence.CaixinhaRepository;
import com.caxinhabet.caixinha.adapter.persistence.ResultadoPossivelRepository;
import com.caxinhabet.caixinha.domain.Caixinha;
import com.caxinhabet.caixinha.domain.CriacaoCaixinhaInvalidaException;
import com.caxinhabet.caixinha.domain.EstadoCaixinha;
import com.caxinhabet.caixinha.domain.NovaCaixinhaSpec;
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
 * Story 2.2 — {@link CriarCaixinhaUseCase} contra Postgres real.
 */
@Testcontainers
@SpringBootTest
class CriarCaixinhaUseCaseTest {

	@Container
	static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

	@DynamicPropertySource
	static void props(DynamicPropertyRegistry r) {
		r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
		r.add("spring.datasource.username", POSTGRES::getUsername);
		r.add("spring.datasource.password", POSTGRES::getPassword);
	}

	@Autowired private CriarCaixinhaUseCase useCase;
	@Autowired private CaixinhaRepository caixinhas;
	@Autowired private ResultadoPossivelRepository resultados;
	@Autowired private ParticipanteRepository participantes;
	@Autowired private UsuarioRepository usuarios;
	@Autowired private com.caxinhabet.caixinha.adapter.notification.LogConviteEmailSender logSender;

	private UsuarioEntity organizador;

	private static final Instant PRAZO = Instant.parse("2026-06-01T12:00:00Z");
	private static final Instant APURACAO = Instant.parse("2026-06-01T14:00:00Z");

	@BeforeEach
	void setUp() {
		participantes.deleteAll();
		resultados.deleteAll();
		caixinhas.deleteAll();
		usuarios.deleteAll();
		logSender.limpar();
		organizador = usuarios.save(UsuarioEntity.criar("rafael@local"));
	}

	private NovaCaixinhaSpec specValida() {
		return new NovaCaixinhaSpec(
				"Brasil x Marrocos",
				"Brasil",
				"Marrocos",
				Money.of("40.00"),
				5,
				PRAZO,
				APURACAO,
				List.of("Vitória do Brasil", "Empate", "Vitória do Marrocos"),
				List.of());
	}

	@Test
	@DisplayName("Caso feliz: cria Caixinha + 3 Resultados + 1 Participante-dono (1 txn)")
	void casoFeliz() {
		Caixinha c = useCase.executar(specValida(), organizador.getId());

		assertThat(c.id()).isNotNull();
		assertThat(c.estado()).isEqualTo(EstadoCaixinha.coletando_convites);
		assertThat(c.valorIngresso()).isEqualTo(Money.of("40.00"));
		assertThat(c.resultadosPossiveis()).hasSize(3);
		assertThat(c.resultadosPossiveis().get(0).rotulo()).isEqualTo("Vitória do Brasil");

		// Verifica DB: 1 caixinha, 3 resultados, 1 participante-dono
		assertThat(caixinhas.count()).isEqualTo(1L);
		assertThat(resultados.findByCaixinhaIdOrderByOrdemAsc(c.id())).hasSize(3);
		List<ParticipanteEntity> ps = participantes.findByCaixinhaIdOrderByCriadoEmAsc(c.id());
		assertThat(ps).hasSize(1);
		ParticipanteEntity dono = ps.get(0);
		assertThat(dono.isDono()).isTrue();
		assertThat(dono.getStatus()).isEqualTo(StatusParticipante.convidado);
		assertThat(dono.getEmail()).isEqualTo("rafael@local");
		assertThat(dono.getUsuarioId()).isEqualTo(organizador.getId());
	}

	@Test
	@DisplayName("Valor armazenado em centavos no DB (4000 para R$ 40,00)")
	void centavosNoDb() {
		Caixinha c = useCase.executar(specValida(), organizador.getId());
		long centavosNoDb = caixinhas.findById(c.id()).orElseThrow().getValorIngressoCentavos();
		assertThat(centavosNoDb).isEqualTo(4000L);
	}

	@Test
	@DisplayName("Spec inválido (vários problemas) → CriacaoCaixinhaInvalidaException agregada")
	void specInvalida() {
		NovaCaixinhaSpec spec =
				new NovaCaixinhaSpec(
						"",
						"",
						"B",
						Money.of("1.00"),
						1,
						APURACAO,
						PRAZO,
						List.of("X"),
						List.of());

		assertThatThrownBy(() -> useCase.executar(spec, organizador.getId()))
				.isInstanceOfSatisfying(
						CriacaoCaixinhaInvalidaException.class,
						ex -> assertThat(ex.motivos()).hasSizeGreaterThanOrEqualTo(4));

		// Nada persistido (transação revertida).
		assertThat(caixinhas.count()).isZero();
	}

	@Test
	@DisplayName("Premio máximo teórico = valor × minimo - taxa")
	void premioTeorico() {
		Caixinha c = useCase.executar(specValida(), organizador.getId());
		// 40 × 5 = 200; 200 - 10 = 190
		assertThat(c.premioMaximoTeorico()).isEqualTo(Money.of("190.00"));
	}

	@Test
	@DisplayName("Story 2.4: emailsConvidados gera Participantes adicionais + convites disparados")
	void criarComConvidados() {
		NovaCaixinhaSpec spec =
				new NovaCaixinhaSpec(
						"Brasil x Marrocos",
						"Brasil",
						"Marrocos",
						Money.of("40.00"),
						5,
						PRAZO,
						APURACAO,
						List.of("V Brasil", "Empate", "V Marrocos"),
						List.of("alice@local", "bob@local"));
		Caixinha c = useCase.executar(spec, organizador.getId());

		// Dono + 2 convidados = 3 participantes
		List<com.caxinhabet.participante.adapter.persistence.ParticipanteEntity> ps =
				participantes.findByCaixinhaIdOrderByCriadoEmAsc(c.id());
		assertThat(ps).hasSize(3);
		assertThat(ps).extracting(p -> p.getEmail())
				.containsExactlyInAnyOrder("rafael@local", "alice@local", "bob@local");
		assertThat(ps).filteredOn(p -> p.isDono()).hasSize(1);

		// 2 convites disparados (Log sender capturou)
		assertThat(logSender.convitesEnviados())
				.extracting(com.caxinhabet.caixinha.domain.ConviteEmail::destinatario)
				.containsExactlyInAnyOrder("alice@local", "bob@local");
	}
}
