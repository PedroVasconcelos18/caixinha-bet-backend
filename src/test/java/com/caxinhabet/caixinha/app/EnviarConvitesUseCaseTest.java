package com.caxinhabet.caixinha.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.caxinhabet.auth.adapter.persistence.UsuarioEntity;
import com.caxinhabet.auth.adapter.persistence.UsuarioRepository;
import com.caxinhabet.caixinha.adapter.notification.LogConviteEmailSender;
import com.caxinhabet.caixinha.adapter.persistence.CaixinhaEntity;
import com.caxinhabet.caixinha.adapter.persistence.CaixinhaRepository;
import com.caxinhabet.caixinha.adapter.persistence.ResultadoPossivelRepository;
import com.caxinhabet.caixinha.domain.EstadoCaixinha;
import com.caxinhabet.caixinha.domain.OperacaoNaoAutorizadaException;
import com.caxinhabet.caixinha.domain.PrazoEncerradoException;
import com.caxinhabet.participante.adapter.persistence.ParticipanteEntity;
import com.caxinhabet.participante.adapter.persistence.ParticipanteRepository;
import com.caxinhabet.participante.domain.StatusParticipante;
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
 * Story 2.4 — {@link EnviarConvitesUseCase} contra Postgres real.
 *
 * <p>Cobre todos os ACs:
 * <ul>
 *   <li>AC-2 endpoint dedicado (convidar depois)
 *   <li>AC-3 duplicatas (não falha — reporta em jaPresentes)
 *   <li>AC-4 prazo encerrado → 422
 *   <li>AC-5 LogSender ativo (assert nos links enviados)
 *   <li>Autorização: só dono pode convidar → 403
 * </ul>
 */
@Testcontainers
@SpringBootTest
class EnviarConvitesUseCaseTest {

	@Container
	static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

	@DynamicPropertySource
	static void props(DynamicPropertyRegistry r) {
		r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
		r.add("spring.datasource.username", POSTGRES::getUsername);
		r.add("spring.datasource.password", POSTGRES::getPassword);
	}

	@Autowired private EnviarConvitesUseCase useCase;
	@Autowired private UsuarioRepository usuarios;
	@Autowired private CaixinhaRepository caixinhas;
	@Autowired private ResultadoPossivelRepository resultados;
	@Autowired private ParticipanteRepository participantes;
	@Autowired private LogConviteEmailSender logSender;

	private UsuarioEntity rafael;
	private UsuarioEntity mariana;
	private CaixinhaEntity caixinha;

	@BeforeEach
	void setUp() {
		participantes.deleteAll();
		resultados.deleteAll();
		caixinhas.deleteAll();
		usuarios.deleteAll();
		logSender.limpar();

		rafael = usuarios.save(UsuarioEntity.criar("rafael@local"));
		mariana = usuarios.save(UsuarioEntity.criar("mariana@local"));

		caixinha = criarCaixinhaComDono(rafael, Instant.now().plusSeconds(86400 * 30));
	}

	private CaixinhaEntity criarCaixinhaComDono(UsuarioEntity dono, Instant prazoEntrada) {
		CaixinhaEntity c =
				new CaixinhaEntity(
						"Brasil x Marrocos",
						"Brasil",
						"Marrocos",
						4000L,
						5,
						1, // numeroGanhadores (v5)
						prazoEntrada,
						prazoEntrada.plusSeconds(3600),
						EstadoCaixinha.coletando_convites,
						dono.getId());
		c = caixinhas.save(c);
		participantes.save(
				new ParticipanteEntity(
						c.getId(),
						dono.getId(),
						dono.getEmail(),
						true,
						StatusParticipante.convidado));
		return c;
	}

	@Test
	@DisplayName("Feliz: convida 2 e-mails novos → 2 Participantes + 2 convites")
	void feliz() {
		EnviarConvitesUseCase.Resultado r =
				useCase.executar(
						caixinha.getId(), rafael.getId(), List.of("alice@local", "bob@local"));

		assertThat(r.convidados()).containsExactlyInAnyOrder("alice@local", "bob@local");
		assertThat(r.jaPresentes()).isEmpty();
		assertThat(participantes.findByCaixinhaIdOrderByCriadoEmAsc(caixinha.getId()))
				.hasSize(3); // dono + 2
		// Pós-commit dispara os e-mails (a tx do @Transactional do useCase commitou ao fim do método)
		assertThat(logSender.convitesEnviados()).hasSize(2);
	}

	@Test
	@DisplayName("Duplicata (case-insensitive) cai em jaPresentes, sem nova linha nem envio")
	void duplicataNaoEnvia() {
		// Pré-condição: alice já está
		useCase.executar(caixinha.getId(), rafael.getId(), List.of("alice@local"));
		logSender.limpar();

		EnviarConvitesUseCase.Resultado r =
				useCase.executar(
						caixinha.getId(), rafael.getId(), List.of("ALICE@LOCAL", "bob@local"));

		assertThat(r.convidados()).containsExactly("bob@local");
		assertThat(r.jaPresentes()).contains("alice@local");
		assertThat(logSender.convitesEnviados()).hasSize(1); // só bob
	}

	@Test
	@DisplayName("E-mail do dono nos convidados é descartado (já é Participante)")
	void emailDoDonoEhDuplicata() {
		EnviarConvitesUseCase.Resultado r =
				useCase.executar(
						caixinha.getId(), rafael.getId(), List.of("rafael@local"));

		assertThat(r.convidados()).isEmpty();
		assertThat(r.jaPresentes()).contains("rafael@local");
	}

	@Test
	@DisplayName("Não-dono → OperacaoNaoAutorizadaException (403)")
	void naoDono() {
		assertThatThrownBy(
						() ->
								useCase.executar(
										caixinha.getId(), mariana.getId(), List.of("alice@local")))
				.isInstanceOf(OperacaoNaoAutorizadaException.class);
		assertThat(logSender.convitesEnviados()).isEmpty();
	}

	@Test
	@DisplayName("Caixinha inexistente → OperacaoNaoAutorizadaException (404 mascarado como 403)")
	void caixinhaInexistente() {
		assertThatThrownBy(
						() -> useCase.executar(999999L, rafael.getId(), List.of("alice@local")))
				.isInstanceOf(OperacaoNaoAutorizadaException.class);
	}

	@Test
	@DisplayName("Prazo expirado → PrazoEncerradoException (422)")
	void prazoExpirado() {
		CaixinhaEntity expirada =
				criarCaixinhaComDono(rafael, Instant.now().minusSeconds(3600));

		assertThatThrownBy(
						() ->
								useCase.executar(
										expirada.getId(),
										rafael.getId(),
										List.of("alice@local")))
				.isInstanceOf(PrazoEncerradoException.class);
		assertThat(logSender.convitesEnviados()).isEmpty();
	}

	@Test
	@DisplayName("E-mails são normalizados (trim + lowercase) e duplicados na lista entrante deduplicam")
	void normalizaEDeduplica() {
		EnviarConvitesUseCase.Resultado r =
				useCase.executar(
						caixinha.getId(),
						rafael.getId(),
						List.of("  Alice@Local  ", "ALICE@LOCAL", "alice@local"));

		assertThat(r.convidados()).containsExactly("alice@local");
		assertThat(participantes.findByCaixinhaIdOrderByCriadoEmAsc(caixinha.getId()))
				.hasSize(2); // dono + alice
	}

	@Test
	@DisplayName("Lista vazia → não faz nada (sem erro, sem envio)")
	void listaVazia() {
		EnviarConvitesUseCase.Resultado r =
				useCase.executar(caixinha.getId(), rafael.getId(), List.of());

		assertThat(r.convidados()).isEmpty();
		assertThat(r.jaPresentes()).isEmpty();
		assertThat(logSender.convitesEnviados()).isEmpty();
	}
}
