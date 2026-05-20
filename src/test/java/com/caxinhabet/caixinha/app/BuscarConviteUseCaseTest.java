package com.caxinhabet.caixinha.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.caxinhabet.auth.adapter.persistence.UsuarioEntity;
import com.caxinhabet.auth.adapter.persistence.UsuarioRepository;
import com.caxinhabet.caixinha.adapter.persistence.CaixinhaEntity;
import com.caxinhabet.caixinha.adapter.persistence.CaixinhaRepository;
import com.caxinhabet.caixinha.adapter.persistence.ResultadoPossivelEntity;
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
class BuscarConviteUseCaseTest {

	@Container
	static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

	@DynamicPropertySource
	static void props(DynamicPropertyRegistry r) {
		r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
		r.add("spring.datasource.username", POSTGRES::getUsername);
		r.add("spring.datasource.password", POSTGRES::getPassword);
	}

	@Autowired private BuscarConviteUseCase useCase;
	@Autowired private UsuarioRepository usuarios;
	@Autowired private CaixinhaRepository caixinhas;
	@Autowired private ResultadoPossivelRepository resultados;
	@Autowired private ParticipanteRepository participantes;

	private UsuarioEntity alice;
	private CaixinhaEntity caixinha;

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
								Instant.now().plusSeconds(86400 * 30),
								Instant.now().plusSeconds(86400 * 30 + 3600),
								EstadoCaixinha.coletando_convites,
								rafael.getId()));
		resultados.save(new ResultadoPossivelEntity(caixinha.getId(), 0, "Vitória Brasil"));
		resultados.save(new ResultadoPossivelEntity(caixinha.getId(), 1, "Empate"));

		participantes.save(
				new ParticipanteEntity(
						caixinha.getId(),
						null,
						"alice@local",
						false,
						StatusParticipante.convidado));
	}

	@Test
	@DisplayName("Convidado vê seu próprio status; usuario_id é vinculado")
	void conviteFeliz() {
		BuscarConviteUseCase.Resultado r =
				useCase.executar(caixinha.getId(), alice.getId(), "alice@local");

		assertThat(r.caixinha().getId()).isEqualTo(caixinha.getId());
		assertThat(r.resultadosPossiveis()).hasSize(2);
		assertThat(r.eu().getEmail()).isEqualTo("alice@local");
		assertThat(r.eu().getStatus()).isEqualTo(StatusParticipante.convidado);
		assertThat(r.eu().getUsuarioId()).isEqualTo(alice.getId()); // vinculado!
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
}
