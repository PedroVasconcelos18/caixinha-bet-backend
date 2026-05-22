package com.caxinhabet.caixinha.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.caxinhabet.auth.adapter.persistence.UsuarioEntity;
import com.caxinhabet.auth.adapter.persistence.UsuarioRepository;
import com.caxinhabet.caixinha.adapter.persistence.CaixinhaEntity;
import com.caxinhabet.caixinha.adapter.persistence.CaixinhaRepository;
import com.caxinhabet.caixinha.adapter.persistence.ResultadoPossivelRepository;
import com.caxinhabet.caixinha.domain.EstadoCaixinha;
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

@Testcontainers
@SpringBootTest
class AceitarConviteUseCaseTest {

	@Container
	static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

	@DynamicPropertySource
	static void props(DynamicPropertyRegistry r) {
		r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
		r.add("spring.datasource.username", POSTGRES::getUsername);
		r.add("spring.datasource.password", POSTGRES::getPassword);
	}

	@Autowired private AceitarConviteUseCase useCase;
	@Autowired private UsuarioRepository usuarios;
	@Autowired private CaixinhaRepository caixinhas;
	@Autowired private ResultadoPossivelRepository resultados;
	@Autowired private ParticipanteRepository participantes;

	private UsuarioEntity alice;
	private CaixinhaEntity caixinha;
	private ParticipanteEntity convidada; // alice como convidada sem usuario_id

	@BeforeEach
	void setUp() {
		participantes.deleteAll();
		resultados.deleteAll();
		caixinhas.deleteAll();
		usuarios.deleteAll();

		alice = usuarios.save(UsuarioEntity.criar("alice@local"));
		UsuarioEntity rafael = usuarios.save(UsuarioEntity.criar("rafael@local"));
		caixinha =
				caixinhas.save(
						new CaixinhaEntity(
								"Brasil x Marrocos",
								"Brasil",
								"Marrocos",
								4000L,
								3,
								1, // numeroGanhadores (v5)
								Instant.now().plusSeconds(86400 * 30),
								Instant.now().plusSeconds(86400 * 30 + 3600),
								EstadoCaixinha.coletando_convites,
								rafael.getId()));
		// Convite criado por Story 2.4 — usuario_id ainda NULL
		convidada =
				participantes.save(
						new ParticipanteEntity(
								caixinha.getId(),
								null,
								"alice@local",
								false,
								StatusParticipante.convidado));
	}

	@Test
	@DisplayName("Caso feliz: convidado → aceito, usuario_id vinculado")
	void casoFeliz() {
		ParticipanteEntity p =
				useCase.executar(caixinha.getId(), alice.getId(), "alice@local");

		assertThat(p.getStatus()).isEqualTo(StatusParticipante.aceito);
		assertThat(p.getUsuarioId()).isEqualTo(alice.getId());
		assertThat(p.getId()).isEqualTo(convidada.getId());
	}

	@Test
	@DisplayName("Idempotente: aceitar 2x devolve aceito sem erro")
	void idempotente() {
		useCase.executar(caixinha.getId(), alice.getId(), "alice@local");
		ParticipanteEntity p2 =
				useCase.executar(caixinha.getId(), alice.getId(), "alice@local");
		assertThat(p2.getStatus()).isEqualTo(StatusParticipante.aceito);
	}

	@Test
	@DisplayName("Não-convidado → 404 (anti-enumeração)")
	void naoConvidado() {
		UsuarioEntity bob = usuarios.save(UsuarioEntity.criar("bob@local"));
		assertThatThrownBy(() -> useCase.executar(caixinha.getId(), bob.getId(), "bob@local"))
				.isInstanceOf(ResponseStatusException.class);
	}

	@Test
	@DisplayName("Caixinha inexistente → 404")
	void caixinhaInexistente() {
		assertThatThrownBy(() -> useCase.executar(999999L, alice.getId(), "alice@local"))
				.isInstanceOf(ResponseStatusException.class);
	}

	@Test
	@DisplayName("Aceitar status superior (aceito) é no-op idempotente")
	void aceitarStatusSuperior() {
		convidada.setStatus(StatusParticipante.aceito);
		participantes.save(convidada);

		ParticipanteEntity p =
				useCase.executar(caixinha.getId(), alice.getId(), "alice@local");
		assertThat(p.getStatus()).isEqualTo(StatusParticipante.aceito);
	}
}
