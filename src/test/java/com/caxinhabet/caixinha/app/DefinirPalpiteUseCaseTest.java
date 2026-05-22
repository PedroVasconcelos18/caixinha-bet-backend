package com.caxinhabet.caixinha.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.caxinhabet.auth.adapter.persistence.UsuarioEntity;
import com.caxinhabet.auth.adapter.persistence.UsuarioRepository;
import com.caxinhabet.auth.domain.ChavePixObrigatoriaException;
import com.caxinhabet.caixinha.adapter.persistence.CaixinhaEntity;
import com.caxinhabet.caixinha.adapter.persistence.CaixinhaRepository;
import com.caxinhabet.caixinha.adapter.persistence.ResultadoPossivelEntity;
import com.caxinhabet.caixinha.adapter.persistence.ResultadoPossivelRepository;
import com.caxinhabet.caixinha.domain.EstadoCaixinha;
import com.caxinhabet.caixinha.domain.PrazoEncerradoException;
import com.caxinhabet.participante.adapter.persistence.ParticipanteEntity;
import com.caxinhabet.participante.adapter.persistence.ParticipanteRepository;
import com.caxinhabet.participante.domain.PalpiteInvalidoException;
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
class DefinirPalpiteUseCaseTest {

	@Container
	static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

	@DynamicPropertySource
	static void props(DynamicPropertyRegistry r) {
		r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
		r.add("spring.datasource.username", POSTGRES::getUsername);
		r.add("spring.datasource.password", POSTGRES::getPassword);
	}

	@Autowired private DefinirPalpiteUseCase useCase;
	@Autowired private UsuarioRepository usuarios;
	@Autowired private CaixinhaRepository caixinhas;
	@Autowired private ResultadoPossivelRepository resultados;
	@Autowired private ParticipanteRepository participantes;

	private UsuarioEntity alice;
	private CaixinhaEntity caixinha;
	private ResultadoPossivelEntity vBrasil;
	private ResultadoPossivelEntity empate;

	@BeforeEach
	void setUp() {
		participantes.deleteAll();
		resultados.deleteAll();
		caixinhas.deleteAll();
		usuarios.deleteAll();

		alice = usuarios.save(UsuarioEntity.criar("alice@local"));
		// v5 (FR-5): chave PIX no perfil é pré-requisito do Palpite.
		// O setUp default cadastra; testes específicos podem limpar para
		// exercitar o cenário "sem chave".
		alice.definirChavePix("alice@pix");
		alice = usuarios.save(alice);
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
		vBrasil =
				resultados.save(new ResultadoPossivelEntity(caixinha.getId(), 0, "Vitória Brasil"));
		empate = resultados.save(new ResultadoPossivelEntity(caixinha.getId(), 1, "Empate"));

		participantes.save(
				new ParticipanteEntity(
						caixinha.getId(),
						null,
						"alice@local",
						false,
						StatusParticipante.convidado));
	}

	@Test
	@DisplayName("Convidado escolhe palpite: aceite implícito + palpite gravado")
	void aceiteImplicito() {
		ParticipanteEntity p =
				useCase.executar(
						caixinha.getId(), alice.getId(), "alice@local", vBrasil.getId());

		assertThat(p.getStatus()).isEqualTo(StatusParticipante.aceito);
		assertThat(p.getPalpiteResultadoPossivelId()).isEqualTo(vBrasil.getId());
		assertThat(p.getUsuarioId()).isEqualTo(alice.getId());
	}

	@Test
	@DisplayName("Alterar palpite múltiplas vezes funciona")
	void alterarPalpite() {
		useCase.executar(caixinha.getId(), alice.getId(), "alice@local", vBrasil.getId());
		ParticipanteEntity p2 =
				useCase.executar(
						caixinha.getId(), alice.getId(), "alice@local", empate.getId());
		assertThat(p2.getPalpiteResultadoPossivelId()).isEqualTo(empate.getId());
	}

	@Test
	@DisplayName("Resultado de OUTRA Caixinha → PalpiteInvalidoException (422)")
	void resultadoDeOutraCaixinha() {
		UsuarioEntity rafael = usuarios.findByEmail("rafael@local").orElseThrow();
		CaixinhaEntity outra =
				caixinhas.save(
						new CaixinhaEntity(
								"Outra",
								"X",
								"Y",
								4000L,
								2,
								1, // numeroGanhadores (v5)
								Instant.now().plusSeconds(3600),
								Instant.now().plusSeconds(7200),
								EstadoCaixinha.coletando_convites,
								rafael.getId()));
		ResultadoPossivelEntity outroR =
				resultados.save(new ResultadoPossivelEntity(outra.getId(), 0, "Z"));

		assertThatThrownBy(
						() ->
								useCase.executar(
										caixinha.getId(),
										alice.getId(),
										"alice@local",
										outroR.getId()))
				.isInstanceOf(PalpiteInvalidoException.class);
	}

	@Test
	@DisplayName("Resultado inexistente → PalpiteInvalidoException")
	void resultadoInexistente() {
		assertThatThrownBy(
						() ->
								useCase.executar(
										caixinha.getId(), alice.getId(), "alice@local", 999999L))
				.isInstanceOf(PalpiteInvalidoException.class);
	}

	@Test
	@DisplayName("Palpite após prazo expirado → PrazoEncerradoException (422)")
	void prazoExpirado() {
		// Cria nova Caixinha com prazo no passado
		UsuarioEntity rafael = usuarios.findByEmail("rafael@local").orElseThrow();
		CaixinhaEntity expirada =
				caixinhas.save(
						new CaixinhaEntity(
								"Expirada",
								"A",
								"B",
								4000L,
								2,
								1, // numeroGanhadores (v5)
								Instant.now().minusSeconds(3600),
								Instant.now().minusSeconds(1800),
								EstadoCaixinha.coletando_convites,
								rafael.getId()));
		ResultadoPossivelEntity r =
				resultados.save(new ResultadoPossivelEntity(expirada.getId(), 0, "X"));
		participantes.save(
				new ParticipanteEntity(
						expirada.getId(),
						alice.getId(),
						"alice@local",
						false,
						StatusParticipante.aceito));

		assertThatThrownBy(
						() ->
								useCase.executar(
										expirada.getId(),
										alice.getId(),
										"alice@local",
										r.getId()))
				.isInstanceOf(PrazoEncerradoException.class);
	}

	@Test
	@DisplayName("Não-convidado → 404 (anti-enumeração)")
	void naoConvidado() {
		UsuarioEntity bob = usuarios.save(UsuarioEntity.criar("bob@local"));
		assertThatThrownBy(
						() ->
								useCase.executar(
										caixinha.getId(), bob.getId(), "bob@local", vBrasil.getId()))
				.isInstanceOf(ResponseStatusException.class);
	}

	@Test
	@DisplayName("v5: Usuário sem chave PIX no perfil → ChavePixObrigatoriaException (422)")
	void semChavePix() {
		// alice começa com chave no setUp; limpamos para exercitar o cenário
		alice.definirChavePix(null);
		alice = usuarios.save(alice);

		assertThatThrownBy(
						() ->
								useCase.executar(
										caixinha.getId(), alice.getId(), "alice@local", vBrasil.getId()))
				.isInstanceOf(ChavePixObrigatoriaException.class)
				.hasMessageContaining("chave PIX");
	}

	@Test
	@DisplayName("v5: chave PIX em branco também é tratada como ausente")
	void chavePixEmBranco() {
		alice.definirChavePix("   ");
		alice = usuarios.save(alice);

		assertThatThrownBy(
						() ->
								useCase.executar(
										caixinha.getId(), alice.getId(), "alice@local", vBrasil.getId()))
				.isInstanceOf(ChavePixObrigatoriaException.class);
	}
}
