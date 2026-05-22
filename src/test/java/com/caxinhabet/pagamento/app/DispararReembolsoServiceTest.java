package com.caxinhabet.pagamento.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.caxinhabet.auth.adapter.persistence.UsuarioEntity;
import com.caxinhabet.auth.adapter.persistence.UsuarioRepository;
import com.caxinhabet.caixinha.adapter.persistence.CaixinhaEntity;
import com.caxinhabet.caixinha.adapter.persistence.CaixinhaRepository;
import com.caxinhabet.caixinha.domain.DispararReembolso;
import com.caxinhabet.caixinha.domain.EstadoCaixinha;
import com.caxinhabet.pagamento.adapter.notification.LogCancelamentoEmailSender;
import com.caxinhabet.pagamento.adapter.persistence.CobrancaEntity;
import com.caxinhabet.pagamento.adapter.persistence.CobrancaRepository;
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
 * Story 5.1 (FR-11) — {@link DispararReembolso}: estorno automático de
 * Caixinha cancelada.
 *
 * <p>{@code ProvedorPagamento} mockado — verificamos QUAIS cobranças
 * tiveram {@code estornar} chamado.
 */
@Testcontainers
@SpringBootTest
class DispararReembolsoServiceTest {

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

	@Autowired private DispararReembolso dispararReembolso;
	@Autowired private UsuarioRepository usuarios;
	@Autowired private CaixinhaRepository caixinhas;
	@Autowired private ParticipanteRepository participantes;
	@Autowired private CobrancaRepository cobrancas;
	@Autowired private LogCancelamentoEmailSender logSender;

	private long caixinhaId;
	private long organizadorId;

	@BeforeEach
	void setUp() {
		cobrancas.deleteAll();
		participantes.deleteAll();
		caixinhas.deleteAll();
		usuarios.deleteAll();
		logSender.limpar();

		organizadorId = usuarios.save(UsuarioEntity.criar("rafael@local")).getId();
		CaixinhaEntity caixinha =
				caixinhas.save(
						new CaixinhaEntity(
								"Brasil x Marrocos",
								"Brasil",
								"Marrocos",
								4000L,
								5,
								1,
								Instant.now().plusSeconds(86400 * 30),
								Instant.now().plusSeconds(86400 * 30 + 3600),
								EstadoCaixinha.cancelada,
								organizadorId));
		caixinhaId = caixinha.getId();
	}

	/** Participante com cobrança no estado dado. Devolve o cobrancaId. */
	private String participanteComCobranca(String email, EstadoCobranca estado) {
		ParticipanteEntity p =
				participantes.save(
						new ParticipanteEntity(
								caixinhaId,
								organizadorId,
								email,
								false,
								StatusParticipante.pago));
		CobrancaEntity c =
				new CobrancaEntity(
						p.getId(),
						caixinhaId,
						"pay-" + email,
						"copia",
						"qr",
						4000L,
						Instant.now().plusSeconds(3600));
		c.transicionarPara(estado);
		cobrancas.save(c);
		return c.getCobrancaId();
	}

	@Test
	@DisplayName("AC-1: cada cobrança confirmada tem estorno disparado")
	void estornaCadaCobrancaConfirmada() {
		participanteComCobranca("ana@local", EstadoCobranca.confirmada);
		participanteComCobranca("beto@local", EstadoCobranca.confirmada);

		dispararReembolso.dispararReembolso(caixinhaId);

		verify(provedor).estornar(eq("pay-ana@local"));
		verify(provedor).estornar(eq("pay-beto@local"));
		// Notificação de cancelamento a cada Participante.
		assertThat(logSender.avisosEnviados()).hasSize(2);
		assertThat(logSender.avisosEnviados())
				.allSatisfy(a -> assertThat(a.houvePagamento()).isTrue());
	}

	@Test
	@DisplayName("AC-4: cobrança NÃO confirmada (já estornada/ativa) não é estornada")
	void naoEstornaCobrancaNaoConfirmada() {
		participanteComCobranca("ana@local", EstadoCobranca.confirmada);
		participanteComCobranca("beto@local", EstadoCobranca.estornada); // já estornada
		participanteComCobranca("ze@local", EstadoCobranca.ativa); // nunca pagou
		// Estorno já disparado (janela antes do webhook) — NÃO re-estorna.
		participanteComCobranca("ju@local", EstadoCobranca.estorno_solicitado);

		dispararReembolso.dispararReembolso(caixinhaId);

		// Só a `confirmada` é estornada.
		verify(provedor).estornar(eq("pay-ana@local"));
		verify(provedor, never()).estornar(eq("pay-beto@local"));
		verify(provedor, never()).estornar(eq("pay-ze@local"));
		verify(provedor, never()).estornar(eq("pay-ju@local"));
	}

	@Test
	@DisplayName("Fix review: estorno bem-sucedido marca cobrança `estorno_solicitado`")
	void estornoMarcaEstornoSolicitado() {
		participanteComCobranca("ana@local", EstadoCobranca.confirmada);

		dispararReembolso.dispararReembolso(caixinhaId);

		// A cobrança foi marcada — um re-disparo não a estornaria de novo.
		assertThat(cobrancas.findByCobrancaId("pay-ana@local").orElseThrow().getEstado())
				.isEqualTo(EstadoCobranca.estorno_solicitado);
	}

	@Test
	@DisplayName("Fix review: falha do estorno reverte a marca para `confirmada` (retry)")
	void falhaReverteMarcaParaConfirmada() {
		participanteComCobranca("ana@local", EstadoCobranca.confirmada);
		org.mockito.Mockito.doThrow(new RuntimeException("Asaas fora do ar"))
				.when(provedor)
				.estornar(eq("pay-ana@local"));

		dispararReembolso.dispararReembolso(caixinhaId);

		// A transação curta reverteu — a cobrança volta a `confirmada`,
		// disponível para um novo disparo.
		assertThat(cobrancas.findByCobrancaId("pay-ana@local").orElseThrow().getEstado())
				.isEqualTo(EstadoCobranca.confirmada);
	}

	@Test
	@DisplayName("AC-4: falha de um estorno não impede os demais")
	void falhaDeUmNaoImpedeOutros() {
		participanteComCobranca("ana@local", EstadoCobranca.confirmada);
		participanteComCobranca("beto@local", EstadoCobranca.confirmada);
		org.mockito.Mockito.doThrow(new RuntimeException("Asaas fora do ar"))
				.when(provedor)
				.estornar(eq("pay-ana@local"));

		dispararReembolso.dispararReembolso(caixinhaId);

		// beto foi estornado mesmo com a falha de ana.
		verify(provedor).estornar(eq("pay-beto@local"));
	}

	@Test
	@DisplayName("Caixinha sem cobranças confirmadas → nenhum estorno")
	void semCobrancasNenhumEstorno() {
		dispararReembolso.dispararReembolso(caixinhaId);
		verify(provedor, never()).estornar(org.mockito.ArgumentMatchers.anyString());
	}

	@Test
	@DisplayName("Notificação distingue quem pagou de quem não pagou")
	void notificacaoDistinguePagantes() {
		participanteComCobranca("ana@local", EstadoCobranca.confirmada);
		participanteComCobranca("ze@local", EstadoCobranca.ativa); // não pagou

		dispararReembolso.dispararReembolso(caixinhaId);

		assertThat(logSender.avisosEnviados()).hasSize(2);
		assertThat(logSender.avisosEnviados())
				.filteredOn(a -> a.destinatario().equals("ana@local"))
				.allSatisfy(a -> assertThat(a.houvePagamento()).isTrue());
		assertThat(logSender.avisosEnviados())
				.filteredOn(a -> a.destinatario().equals("ze@local"))
				.allSatisfy(a -> assertThat(a.houvePagamento()).isFalse());
	}
}
