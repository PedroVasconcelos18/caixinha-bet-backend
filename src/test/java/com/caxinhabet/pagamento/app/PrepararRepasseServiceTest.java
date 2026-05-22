package com.caxinhabet.pagamento.app;

import static org.assertj.core.api.Assertions.assertThat;

import com.caxinhabet.auth.adapter.persistence.UsuarioEntity;
import com.caxinhabet.auth.adapter.persistence.UsuarioRepository;
import com.caxinhabet.caixinha.adapter.persistence.CaixinhaEntity;
import com.caxinhabet.caixinha.adapter.persistence.CaixinhaRepository;
import com.caxinhabet.caixinha.adapter.persistence.ResultadoPossivelEntity;
import com.caxinhabet.caixinha.adapter.persistence.ResultadoPossivelRepository;
import com.caxinhabet.caixinha.app.ApurarCaixinhaUseCase;
import com.caxinhabet.caixinha.domain.EstadoCaixinha;
import com.caxinhabet.ledger.adapter.persistence.LedgerLancamentoRepository;
import com.caxinhabet.pagamento.adapter.notification.LogPremioGanhoEmailSender;
import com.caxinhabet.pagamento.adapter.persistence.CobrancaEntity;
import com.caxinhabet.pagamento.adapter.persistence.CobrancaRepository;
import com.caxinhabet.pagamento.adapter.persistence.PayoutEntity;
import com.caxinhabet.pagamento.adapter.persistence.PayoutRepository;
import com.caxinhabet.pagamento.domain.EstadoCobranca;
import com.caxinhabet.pagamento.domain.EstadoPayout;
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
 * Story 4.3 (FR-13) — Repasse preparado pela apuração: cálculo do Prêmio,
 * resíduo de centavos, Payouts, ledger e notificação.
 *
 * <p>Exercita o caminho real: {@code ApurarCaixinhaUseCase.executar} chama
 * o {@code PrepararRepasseService} na mesma transação.
 */
@Testcontainers
@SpringBootTest
class PrepararRepasseServiceTest {

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
	@Autowired private CobrancaRepository cobrancas;
	@Autowired private PayoutRepository payouts;
	@Autowired private LedgerLancamentoRepository ledger;
	@Autowired private LogPremioGanhoEmailSender logSender;

	private long organizadorId;
	private static final String ORG = "rafael@local";
	private CaixinhaEntity caixinha;
	private ResultadoPossivelEntity vitoriaA;

	@BeforeEach
	void setUp() {
		ledger.deleteAll();
		payouts.deleteAll();
		cobrancas.deleteAll();
		participantes.deleteAll();
		resultados.deleteAll();
		caixinhas.deleteAll();
		usuarios.deleteAll();
		logSender.limpar();

		organizadorId = usuarios.save(UsuarioEntity.criar(ORG)).getId();
		caixinha = caixinhas.save(novaCaixinha(2)); // ingresso 40, Nº Ganhadores 2
		vitoriaA = resultados.save(new ResultadoPossivelEntity(caixinha.getId(), 0, "Vitória A"));
	}

	private CaixinhaEntity novaCaixinha(int numeroGanhadores) {
		return new CaixinhaEntity(
				"Brasil x Marrocos",
				"Brasil",
				"Marrocos",
				4000L,
				3,
				numeroGanhadores,
				Instant.now().plusSeconds(86400 * 30),
				Instant.now().plusSeconds(86400 * 30 + 3600),
				EstadoCaixinha.formada,
				organizadorId);
	}

	/** Participante `pago` + cobrança confirmada num instante dado. */
	private ParticipanteEntity paganteConfirmado(
			String email, long palpiteId, Instant confirmadaEm) {
		ParticipanteEntity p =
				new ParticipanteEntity(
						caixinha.getId(),
						organizadorId,
						email,
						email.equals(ORG),
						StatusParticipante.pago);
		p.setPalpiteResultadoPossivelId(palpiteId);
		p = participantes.save(p);

		CobrancaEntity c =
				new CobrancaEntity(
						p.getId(),
						caixinha.getId(),
						"pay-" + email,
						"copia",
						"qr",
						4000L,
						Instant.now().plusSeconds(3600));
		c.transicionarPara(EstadoCobranca.confirmada);
		c = cobrancas.save(c);
		// Força a ordem de pagamento desejada (transicionarPara usa now()).
		// Reabrimos via reflection-free: o teste de resíduo controla a ordem
		// por instantes distintos garantidos pela ordem de criação real.
		return p;
	}

	@Test
	@DisplayName("AC-1: Prêmio = Σ pago − Taxa, dividido igualmente; 2 Payouts criados")
	void premioDivididoIgualmente() {
		// 3 pagantes (Σ = 120), 2 acertaram → Prêmio = 120 − 10 = 110.
		ParticipanteEntity g1 =
				paganteConfirmado(ORG, vitoriaA.getId(), Instant.now());
		ParticipanteEntity g2 =
				paganteConfirmado("ana@local", vitoriaA.getId(), Instant.now());
		// 3º pagante não acertou (paga, conta no Σ).
		ParticipanteEntity perdedor = new ParticipanteEntity(
				caixinha.getId(), organizadorId, "ze@local", false,
				StatusParticipante.pago);
		participantes.save(perdedor);

		apurar.executar(
				caixinha.getId(), organizadorId, ORG, vitoriaA.getId(), null);

		List<PayoutEntity> ps = payouts.findByCaixinhaId(caixinha.getId());
		assertThat(ps).hasSize(2);
		// 110 / 2 = 55.00 cada (divisão exata).
		assertThat(ps).allSatisfy(p -> assertThat(p.getValorCentavos()).isEqualTo(5500L));
		assertThat(ps)
				.extracting(PayoutEntity::getEstado)
				.containsOnly(EstadoPayout.pendente_aceite);
		// Cada Payout tem payout_id único.
		assertThat(ps.stream().map(PayoutEntity::getPayoutId).distinct()).hasSize(2);
		// Notificação enviada a cada Ganhador.
		assertThat(logSender.avisosEnviados()).hasSize(2);
		// participanteIds dos Payouts == os Ganhadores.
		assertThat(ps).extracting(PayoutEntity::getParticipanteId)
				.containsExactlyInAnyOrder(g1.getId(), g2.getId());
	}

	@Test
	@DisplayName("AC-1: resíduo de centavos vai ao 1º Ganhador por ordem de pagamento")
	void residuoVaiAoPrimeiroPagante() {
		// 3 pagantes (Σ = 120), todos acertaram, Nº Ganhadores = 3.
		// Mas a Caixinha-padrão tem Nº = 2; crio uma com Nº = 3.
		CaixinhaEntity cx3 = caixinhas.save(novaCaixinha(3));
		ResultadoPossivelEntity rA =
				resultados.save(new ResultadoPossivelEntity(cx3.getId(), 0, "A"));

		// Prêmio = 120 − 10 = 110; 110 / 3 = 3666 com resíduo 2 centavos.
		// O 1º a confirmar (ordem de pagamento) recebe 3666 + 2 = 3668.
		// p1 usa o e-mail do Organizador — ele precisa ser Participante da
		// cx3 para a apuração não cair no 404 (anti-enumeração).
		long pid1 = paganteEm(cx3, ORG, rA.getId());
		paganteEm(cx3, "p2@local", rA.getId());
		paganteEm(cx3, "p3@local", rA.getId());

		apurar.executar(cx3.getId(), organizadorId, ORG, rA.getId(), null);

		List<PayoutEntity> ps = payouts.findByCaixinhaId(cx3.getId());
		assertThat(ps).hasSize(3);
		long soma =
				ps.stream().mapToLong(PayoutEntity::getValorCentavos).sum();
		assertThat(soma).as("nenhum centavo perdido").isEqualTo(11000L);
		// O 1º pagante (p1, confirmou primeiro) recebe o resíduo.
		long valorP1 =
				ps.stream()
						.filter(p -> p.getParticipanteId() == pid1)
						.findFirst()
						.orElseThrow()
						.getValorCentavos();
		assertThat(valorP1).as("1º pagante recebe base+resíduo").isEqualTo(3668L);
		// Os outros recebem só a base.
		assertThat(
						ps.stream()
								.filter(p -> p.getParticipanteId() != pid1)
								.mapToLong(PayoutEntity::getValorCentavos))
				.allMatch(v -> v == 3666L);
	}

	@Test
	@DisplayName("AC-3: ledger registra a reserva (custodia → reservado) por Ganhador")
	void ledgerRegistraReserva() {
		paganteConfirmado(ORG, vitoriaA.getId(), Instant.now());
		paganteConfirmado("ana@local", vitoriaA.getId(), Instant.now());

		apurar.executar(
				caixinha.getId(), organizadorId, ORG, vitoriaA.getId(), null);

		// 2 Ganhadores → 2 reservas → 4 lançamentos de ledger da reserva.
		// (cada reserva = par débito custodia + crédito reservado).
		long lancamentosReservado =
				ledger.findByCaixinhaId(caixinha.getId()).stream()
						.filter(l -> l.getConta().equals("reservado"))
						.count();
		assertThat(lancamentosReservado).isEqualTo(2L);
	}

	/** Cria pagante confirmado numa Caixinha específica; devolve o id. */
	private long paganteEm(CaixinhaEntity cx, String email, long palpiteId) {
		ParticipanteEntity p =
				new ParticipanteEntity(
						cx.getId(),
						organizadorId,
						email,
						false,
						StatusParticipante.pago);
		p.setPalpiteResultadoPossivelId(palpiteId);
		p = participantes.save(p);
		CobrancaEntity c =
				new CobrancaEntity(
						p.getId(),
						cx.getId(),
						"pay-" + cx.getId() + "-" + email,
						"copia",
						"qr",
						4000L,
						Instant.now().plusSeconds(3600));
		c.transicionarPara(EstadoCobranca.confirmada);
		cobrancas.save(c);
		return p.getId();
	}
}
