package com.caxinhabet.pagamento.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.caxinhabet.auth.adapter.persistence.UsuarioEntity;
import com.caxinhabet.auth.adapter.persistence.UsuarioRepository;
import com.caxinhabet.caixinha.adapter.persistence.CaixinhaEntity;
import com.caxinhabet.caixinha.adapter.persistence.CaixinhaRepository;
import com.caxinhabet.caixinha.domain.EstadoCaixinha;
import com.caxinhabet.pagamento.adapter.persistence.CobrancaEntity;
import com.caxinhabet.pagamento.adapter.persistence.CobrancaRepository;
import com.caxinhabet.pagamento.app.ReconciliacaoService.ResultadoReconciliacao;
import com.caxinhabet.pagamento.domain.EstadoCobranca;
import com.caxinhabet.pagamento.domain.ProvedorPagamento;
import com.caxinhabet.pagamento.domain.StatusCobranca;
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
 * Story 3.6 (NFR-2) — {@link ReconciliacaoService}: detecta divergências
 * app↔Provedor e NÃO corrige.
 *
 * <p>O PSP ({@link ProvedorPagamento}) é mockado — controlamos o status
 * "no Provedor" para cada cobrança.
 */
@Testcontainers
@SpringBootTest
class ReconciliacaoServiceTest {

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

	@Autowired private ReconciliacaoService service;
	@Autowired private UsuarioRepository usuarios;
	@Autowired private CaixinhaRepository caixinhas;
	@Autowired private ParticipanteRepository participantes;
	@Autowired private CobrancaRepository cobrancas;

	private long caixinhaId;

	@BeforeEach
	void setUp() {
		cobrancas.deleteAll();
		participantes.deleteAll();
		caixinhas.deleteAll();
		usuarios.deleteAll();

		UsuarioEntity rafael = usuarios.save(UsuarioEntity.criar("rafael@local"));
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
								rafael.getId()));
		caixinhaId = caixinha.getId();
	}

	/**
	 * Cria uma cobrança para um Participante NOVO a cada chamada — o
	 * índice único {@code uq_pagamento_cobranca_participante_ativa} impede
	 * 2 cobranças `ativa` do mesmo Participante; cada cobrança de teste
	 * tem o seu.
	 */
	private CobrancaEntity cobranca(String cobrancaId, EstadoCobranca estado) {
		ParticipanteEntity p =
				participantes.save(
						new ParticipanteEntity(
								caixinhaId,
								null,
								cobrancaId + "@local",
								false,
								StatusParticipante.pagamento_iniciado));
		CobrancaEntity c =
				new CobrancaEntity(
						p.getId(),
						caixinhaId,
						cobrancaId,
						"copia",
						"qr",
						4000L,
						Instant.now().plusSeconds(3600));
		c.transicionarPara(estado);
		return cobrancas.save(c);
	}

	@Test
	@DisplayName("AC-2: estados coincidentes → sem divergência")
	void semDivergencia() {
		cobranca("pay_ok", EstadoCobranca.ativa);
		when(provedor.consultar(eq("pay_ok"))).thenReturn(StatusCobranca.PENDENTE);

		ResultadoReconciliacao r = service.reconciliar();

		assertThat(r.verificadas()).isEqualTo(1);
		assertThat(r.divergentes()).isZero();
		assertThat(r.erros()).isZero();
	}

	@Test
	@DisplayName("AC-3: app=ativa mas Provedor=CONFIRMADA (webhook perdido) → divergência")
	void webhookPerdidoDetectado() {
		cobranca("pay_perdido", EstadoCobranca.ativa);
		when(provedor.consultar(eq("pay_perdido")))
				.thenReturn(StatusCobranca.CONFIRMADA);

		ResultadoReconciliacao r = service.reconciliar();

		assertThat(r.verificadas()).isEqualTo(1);
		assertThat(r.divergentes()).isEqualTo(1);
		// NÃO corrige — a cobrança continua ativa no app.
		assertThat(cobrancas.findByCobrancaId("pay_perdido").orElseThrow().getEstado())
				.as("reconciliação NÃO corrige — só alerta (NFR-2)")
				.isEqualTo(EstadoCobranca.ativa);
	}

	@Test
	@DisplayName("AC-2: app=confirmada mas Provedor=ESTORNADA → divergência")
	void estornoNaoRefletido() {
		cobranca("pay_estorno", EstadoCobranca.confirmada);
		when(provedor.consultar(eq("pay_estorno")))
				.thenReturn(StatusCobranca.ESTORNADA);

		ResultadoReconciliacao r = service.reconciliar();

		assertThat(r.divergentes()).isEqualTo(1);
	}

	@Test
	@DisplayName("Falha ao consultar uma cobrança não derruba a rodada")
	void falhaDeConsultaContabilizada() {
		cobranca("pay_falha", EstadoCobranca.ativa);
		cobranca("pay_ok2", EstadoCobranca.ativa);
		when(provedor.consultar(eq("pay_falha")))
				.thenThrow(new RuntimeException("Asaas fora do ar"));
		when(provedor.consultar(eq("pay_ok2"))).thenReturn(StatusCobranca.PENDENTE);

		ResultadoReconciliacao r = service.reconciliar();

		assertThat(r.verificadas()).isEqualTo(2);
		assertThat(r.erros()).isEqualTo(1);
		assertThat(r.divergentes()).isZero(); // pay_ok2 coincide
	}

	@Test
	@DisplayName("Estados terminais (invalidada/expirada) NÃO entram na reconciliação")
	void terminaisNaoReconciliados() {
		cobranca("pay_invalidada", EstadoCobranca.invalidada);
		cobranca("pay_expirada", EstadoCobranca.expirada);

		ResultadoReconciliacao r = service.reconciliar();

		// Nenhuma "viva" → 0 verificadas.
		assertThat(r.verificadas()).isZero();
	}

	@Test
	@DisplayName("AC-5: ultimoResultado reflete a última rodada")
	void ultimoResultadoAtualizado() {
		// Nota: não asseramos jaRodou()==false no início — o job @Scheduled
		// é singleton e pode ter rodado antes (initialDelay curto em teste).
		// O que importa: após reconciliar() explícito, o resultado reflete.
		cobranca("pay_x", EstadoCobranca.ativa);
		when(provedor.consultar(eq("pay_x"))).thenReturn(StatusCobranca.PENDENTE);
		service.reconciliar();

		assertThat(service.ultimoResultado().jaRodou()).isTrue();
		assertThat(service.ultimoResultado().verificadas()).isEqualTo(1);
	}
}
