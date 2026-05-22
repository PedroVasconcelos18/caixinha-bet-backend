package com.caxinhabet.caixinha.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.caxinhabet.auth.adapter.persistence.UsuarioEntity;
import com.caxinhabet.auth.adapter.persistence.UsuarioRepository;
import com.caxinhabet.caixinha.adapter.persistence.CaixinhaEntity;
import com.caxinhabet.caixinha.adapter.persistence.CaixinhaRepository;
import com.caxinhabet.caixinha.adapter.persistence.ResultadoPossivelEntity;
import com.caxinhabet.caixinha.adapter.persistence.ResultadoPossivelRepository;
import com.caxinhabet.caixinha.domain.ConsultaPayout;
import com.caxinhabet.caixinha.domain.EstadoCaixinha;
import com.caxinhabet.pagamento.adapter.persistence.CobrancaEntity;
import com.caxinhabet.pagamento.adapter.persistence.CobrancaRepository;
import com.caxinhabet.pagamento.domain.EstadoCobranca;
import com.caxinhabet.participante.adapter.persistence.ParticipanteEntity;
import com.caxinhabet.participante.adapter.persistence.ParticipanteRepository;
import com.caxinhabet.participante.domain.StatusParticipante;
import com.caxinhabet.shared.money.Money;
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

/**
 * Story 4.4 (FR-14) — {@link MontarAcertoContasUseCase}: modos prêmio,
 * reembolso e indisponível.
 *
 * <p>Usa o {@code ApurarCaixinhaUseCase} real para chegar aos estados —
 * exercita a integração 4.2 → 4.3 → 4.4.
 */
@Testcontainers
@SpringBootTest
class MontarAcertoContasUseCaseTest {

	@Container
	static final PostgreSQLContainer<?> POSTGRES =
			new PostgreSQLContainer<>("postgres:17");

	@DynamicPropertySource
	static void props(DynamicPropertyRegistry r) {
		r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
		r.add("spring.datasource.username", POSTGRES::getUsername);
		r.add("spring.datasource.password", POSTGRES::getPassword);
	}

	@Autowired private MontarAcertoContasUseCase montar;
	@Autowired private ApurarCaixinhaUseCase apurar;
	@Autowired private UsuarioRepository usuarios;
	@Autowired private CaixinhaRepository caixinhas;
	@Autowired private ResultadoPossivelRepository resultados;
	@Autowired private ParticipanteRepository participantes;
	@Autowired private CobrancaRepository cobrancas;

	private long organizadorId;
	private static final String ORG = "rafael@local";
	private CaixinhaEntity caixinha;
	private ResultadoPossivelEntity vitoriaA;
	private ResultadoPossivelEntity vitoriaB;

	@BeforeEach
	void setUp() {
		cobrancas.deleteAll();
		participantes.deleteAll();
		resultados.deleteAll();
		caixinhas.deleteAll();
		usuarios.deleteAll();

		organizadorId = usuarios.save(UsuarioEntity.criar(ORG)).getId();
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
								EstadoCaixinha.formada,
								organizadorId));
		vitoriaA = resultados.save(new ResultadoPossivelEntity(caixinha.getId(), 0, "Vitória A"));
		vitoriaB = resultados.save(new ResultadoPossivelEntity(caixinha.getId(), 1, "Vitória B"));
	}

	private ParticipanteEntity pagante(String email, long palpiteId) {
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
		cobrancas.save(c);
		return p;
	}

	@Test
	@DisplayName("AC: Caixinha formada (não apurada) → modo indisponível")
	void naoApuradaIndisponivel() {
		pagante(ORG, vitoriaA.getId());

		MontarAcertoContasUseCase.Resultado r = montar.executar(caixinha.getId(), ORG);

		assertThat(r.modo()).isEqualTo(MontarAcertoContasUseCase.Modo.INDISPONIVEL);
		assertThat(r.ganhadores()).isEmpty();
		assertThat(r.reembolsos()).isEmpty();
	}

	@Test
	@DisplayName("AC-1: Caixinha apurada com Ganhador → modo prêmio")
	void apuradaComGanhadorModoPremio() {
		pagante(ORG, vitoriaA.getId());
		pagante("ana@local", vitoriaB.getId()); // pagou, errou
		pagante("beto@local", vitoriaB.getId());

		apurar.executar(caixinha.getId(), organizadorId, ORG, vitoriaA.getId(), null);

		MontarAcertoContasUseCase.Resultado r = montar.executar(caixinha.getId(), ORG);

		assertThat(r.modo()).isEqualTo(MontarAcertoContasUseCase.Modo.PREMIO);
		assertThat(r.ganhadores()).hasSize(1);
		// Prêmio = 3×40 − 10 = 110; 1 Ganhador → R$ 110.00.
		assertThat(r.ganhadores().get(0).valor()).isEqualTo(Money.of("110.00"));
		assertThat(r.ganhadores().get(0).estadoRepasse())
				.isEqualTo(ConsultaPayout.EstadoRepasse.aguardando_aceite);
		assertThat(r.ganhadores().get(0).email()).isEqualTo(ORG);
	}

	@Test
	@DisplayName("AC-2: Caixinha apurada com 0 corretos → modo reembolso, Taxa devolvida")
	void apuradaSemCorretoModoReembolso() {
		pagante(ORG, vitoriaB.getId()); // todos erraram (final = A)
		pagante("ana@local", vitoriaB.getId());

		apurar.executar(caixinha.getId(), organizadorId, ORG, vitoriaA.getId(), null);

		MontarAcertoContasUseCase.Resultado r = montar.executar(caixinha.getId(), ORG);

		assertThat(r.modo()).isEqualTo(MontarAcertoContasUseCase.Modo.REEMBOLSO);
		assertThat(r.reembolsos()).hasSize(2);
		// Taxa devolvida: estorno é o ingresso CHEIO (R$ 40), não 40 − taxa.
		assertThat(r.reembolsos())
				.allSatisfy(
						rb -> assertThat(rb.valorEstorno()).isEqualTo(Money.of("40.00")));
	}

	@Test
	@DisplayName("AC: não-Participante → 404 (anti-enumeração)")
	void naoParticipante404() {
		pagante(ORG, vitoriaA.getId());
		usuarios.save(UsuarioEntity.criar("intruso@local"));

		assertThatThrownBy(() -> montar.executar(caixinha.getId(), "intruso@local"))
				.isInstanceOf(ResponseStatusException.class);
	}

	// ───────── Story 5.2: estado real do estorno ─────────

	@Test
	@DisplayName("5.2 AC-1: Caixinha cancelada, estorno disparado → em_processamento")
	void canceladaEstornoEmProcessamento() {
		// pagante() cria cobrança `confirmada` — estorno disparado mas o
		// webhook PAYMENT_REFUNDED ainda não chegou.
		pagante(ORG, vitoriaA.getId());
		caixinha.transicionarPara(EstadoCaixinha.cancelada);
		caixinhas.save(caixinha);

		MontarAcertoContasUseCase.Resultado r = montar.executar(caixinha.getId(), ORG);

		assertThat(r.modo()).isEqualTo(MontarAcertoContasUseCase.Modo.REEMBOLSO);
		assertThat(r.reembolsos()).hasSize(1);
		assertThat(r.reembolsos().get(0).estadoEstorno())
				.isEqualTo(
						com.caxinhabet.caixinha.domain.ConsultaEstadoEstorno.EstadoEstorno
								.em_processamento);
	}

	@Test
	@DisplayName("5.2 AC-2: cobrança estornada (webhook confirmou) → concluido")
	void canceladaEstornoConcluido() {
		ParticipanteEntity p = pagante(ORG, vitoriaA.getId());
		// Simula o webhook PAYMENT_REFUNDED: a cobrança foi a `estornada`.
		com.caxinhabet.pagamento.adapter.persistence.CobrancaEntity cob =
				cobrancas.findByCobrancaId("pay-" + ORG).orElseThrow();
		cob.transicionarPara(
				com.caxinhabet.pagamento.domain.EstadoCobranca.estornada);
		cobrancas.save(cob);
		caixinha.transicionarPara(EstadoCaixinha.cancelada);
		caixinhas.save(caixinha);

		MontarAcertoContasUseCase.Resultado r = montar.executar(caixinha.getId(), ORG);

		assertThat(r.reembolsos()).hasSize(1);
		assertThat(r.reembolsos().get(0).estadoEstorno())
				.isEqualTo(
						com.caxinhabet.caixinha.domain.ConsultaEstadoEstorno.EstadoEstorno
								.concluido);
		assertThat(p.getId()).isNotNull();
	}

	@Test
	@DisplayName("5.2 AC-3: cancelada sem pagamento → modo CANCELADA_SEM_REEMBOLSO")
	void canceladaSemPagamento() {
		// Participante criado mas SEM cobrança (não pagou).
		participantes.save(
				new ParticipanteEntity(
						caixinha.getId(),
						organizadorId,
						ORG,
						true,
						StatusParticipante.aceito));
		caixinha.transicionarPara(EstadoCaixinha.cancelada);
		caixinhas.save(caixinha);

		MontarAcertoContasUseCase.Resultado r = montar.executar(caixinha.getId(), ORG);

		assertThat(r.modo())
				.isEqualTo(MontarAcertoContasUseCase.Modo.CANCELADA_SEM_REEMBOLSO);
		assertThat(r.reembolsos()).isEmpty();
	}
}
