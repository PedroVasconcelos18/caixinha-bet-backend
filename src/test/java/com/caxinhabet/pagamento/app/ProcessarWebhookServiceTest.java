package com.caxinhabet.pagamento.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.caxinhabet.auth.adapter.persistence.UsuarioEntity;
import com.caxinhabet.auth.adapter.persistence.UsuarioRepository;
import com.caxinhabet.caixinha.adapter.persistence.CaixinhaEntity;
import com.caxinhabet.caixinha.adapter.persistence.CaixinhaRepository;
import com.caxinhabet.caixinha.domain.EstadoCaixinha;
import com.caxinhabet.ledger.adapter.persistence.LedgerLancamentoRepository;
import com.caxinhabet.pagamento.adapter.persistence.CobrancaEntity;
import com.caxinhabet.pagamento.adapter.persistence.CobrancaRepository;
import com.caxinhabet.pagamento.adapter.persistence.PagamentoEventoRepository;
import com.caxinhabet.pagamento.domain.EstadoCobranca;
import com.caxinhabet.participante.adapter.persistence.ParticipanteEntity;
import com.caxinhabet.participante.adapter.persistence.ParticipanteRepository;
import com.caxinhabet.participante.domain.StatusParticipante;
import java.time.Instant;
import java.util.Map;
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
 * Story 3.3 (FR-8) — {@link ProcessarWebhookService}: idempotência,
 * ordenação, transições e ledger.
 */
@Testcontainers
@SpringBootTest
class ProcessarWebhookServiceTest {

	@Container
	static final PostgreSQLContainer<?> POSTGRES =
			new PostgreSQLContainer<>("postgres:17");

	@DynamicPropertySource
	static void props(DynamicPropertyRegistry r) {
		r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
		r.add("spring.datasource.username", POSTGRES::getUsername);
		r.add("spring.datasource.password", POSTGRES::getPassword);
	}

	@Autowired private ProcessarWebhookService service;
	@Autowired private UsuarioRepository usuarios;
	@Autowired private CaixinhaRepository caixinhas;
	@Autowired private ParticipanteRepository participantes;
	@Autowired private CobrancaRepository cobrancas;
	@Autowired private PagamentoEventoRepository eventos;
	@Autowired private LedgerLancamentoRepository ledger;

	private ParticipanteEntity participante;
	private CobrancaEntity cobranca;

	@BeforeEach
	void setUp() {
		ledger.deleteAll();
		eventos.deleteAll();
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
		participante =
				participantes.save(
						new ParticipanteEntity(
								caixinha.getId(),
								rafael.getId(),
								"alice@local",
								false,
								StatusParticipante.pagamento_iniciado));
		cobranca =
				cobrancas.save(
						new CobrancaEntity(
								participante.getId(),
								caixinha.getId(),
								"pay_abc",
								"copia",
								"qr",
								4000L,
								Instant.now().plusSeconds(3600)));
	}

	private Map<String, Object> evento(
			String eventId, String tipo, String dateCreated) {
		return Map.of(
				"id", eventId,
				"event", tipo,
				"dateCreated", dateCreated,
				"payment", Map.of("id", "pay_abc", "value", "40.00"));
	}

	@Test
	@DisplayName("AC-4: PAYMENT_CONFIRMED → cobrança confirmada, Participante pago, ledger")
	void confirmadoVaiPraPago() {
		service.processar(evento("evt-1", "PAYMENT_CONFIRMED", "2026-05-21 12:00:00"));

		assertThat(cobrancas.findByCobrancaId("pay_abc").orElseThrow().getEstado())
				.isEqualTo(EstadoCobranca.confirmada);
		assertThat(participantes.findById(participante.getId()).orElseThrow().getStatus())
				.isEqualTo(StatusParticipante.pago);
		// Ledger: 2 lançamentos (débito a_receber + crédito custodia).
		assertThat(ledger.findAll()).hasSize(2);
	}

	@Test
	@DisplayName("AC-2: mesmo event_id 2x → idempotente (1 evento, ledger não duplica)")
	void idempotente() {
		Map<String, Object> e = evento("evt-2", "PAYMENT_CONFIRMED", "2026-05-21 12:00:00");
		service.processar(e);
		service.processar(e);

		assertThat(eventos.count()).isEqualTo(1L);
		assertThat(ledger.findAll()).hasSize(2); // não duplicou
	}

	@Test
	@DisplayName("AC-5: PAYMENT_OVERDUE → cobrança expirada, Participante volta a aceito")
	void expiradoVoltaPraAceito() {
		service.processar(evento("evt-3", "PAYMENT_OVERDUE", "2026-05-21 12:00:00"));

		assertThat(cobrancas.findByCobrancaId("pay_abc").orElseThrow().getEstado())
				.isEqualTo(EstadoCobranca.expirada);
		assertThat(participantes.findById(participante.getId()).orElseThrow().getStatus())
				.isEqualTo(StatusParticipante.aceito);
	}

	@Test
	@DisplayName("AC-6: PAYMENT_REFUNDED após confirmado → rebobina pago→aceito + ledger estorno")
	void estornoRebobina() {
		service.processar(evento("evt-4", "PAYMENT_CONFIRMED", "2026-05-21 12:00:00"));
		service.processar(evento("evt-5", "PAYMENT_REFUNDED", "2026-05-21 13:00:00"));

		assertThat(cobrancas.findByCobrancaId("pay_abc").orElseThrow().getEstado())
				.isEqualTo(EstadoCobranca.estornada);
		assertThat(participantes.findById(participante.getId()).orElseThrow().getStatus())
				.isEqualTo(StatusParticipante.aceito);
		// Ledger: 2 da custódia + 2 do estorno = 4.
		assertThat(ledger.findAll()).hasSize(4);
	}

	@Test
	@DisplayName("AC-3: estorno que chega ANTES do confirmado (timestamp menor) NÃO rebaixa")
	void ordenacaoEventoAntigoNaoAplica() {
		// Confirmado com timestamp 13:00.
		service.processar(evento("evt-6", "PAYMENT_CONFIRMED", "2026-05-21 13:00:00"));
		// Estorno com timestamp 12:00 (ANTERIOR) — chega depois mas é "do passado".
		service.processar(evento("evt-7", "PAYMENT_REFUNDED", "2026-05-21 12:00:00"));

		// O estorno antigo foi persistido (auditoria) mas NÃO aplicado.
		assertThat(eventos.count()).isEqualTo(2L);
		assertThat(cobrancas.findByCobrancaId("pay_abc").orElseThrow().getEstado())
				.isEqualTo(EstadoCobranca.confirmada); // continua confirmada
		assertThat(participantes.findById(participante.getId()).orElseThrow().getStatus())
				.isEqualTo(StatusParticipante.pago); // não regrediu
	}

	@Test
	@DisplayName("Evento de cobrança desconhecida → persiste mas não quebra")
	void cobrancaDesconhecida() {
		Map<String, Object> e =
				Map.of(
						"id", "evt-8",
						"event", "PAYMENT_CONFIRMED",
						"dateCreated", "2026-05-21 12:00:00",
						"payment", Map.of("id", "pay_inexistente", "value", "40.00"));
		service.processar(e);

		assertThat(eventos.existsByEventId("evt-8")).isTrue();
		assertThat(ledger.findAll()).isEmpty(); // nada a movimentar
	}

	@Test
	@DisplayName("Evento IGNORADO (tipo não-relevante) → persiste, sem efeito")
	void eventoIgnorado() {
		service.processar(evento("evt-9", "PAYMENT_UPDATED", "2026-05-21 12:00:00"));

		assertThat(eventos.existsByEventId("evt-9")).isTrue();
		assertThat(cobrancas.findByCobrancaId("pay_abc").orElseThrow().getEstado())
				.isEqualTo(EstadoCobranca.ativa); // intocada
	}

	// ───────── Fixes do code review do Épico 3 (2026-05-21) ─────────

	@Test
	@DisplayName("Fix #1: CONFIRMADO sobre cobrança `invalidada` NÃO custodia (dinheiro fantasma)")
	void confirmadoSobreInvalidadaNaoCustodia() {
		// Cenário: usuário regerou a cobrança (esta virou invalidada) e depois
		// pagou a antiga. O webhook NÃO pode confirmá-la como custódia válida.
		CobrancaEntity c =
				cobrancas.findByCobrancaId("pay_abc").orElseThrow();
		c.transicionarPara(EstadoCobranca.invalidada);
		cobrancas.save(c);

		service.processar(evento("evt-inv", "PAYMENT_CONFIRMED", "2026-05-21 12:00:00"));

		// Evento persistido (auditoria), mas SEM custódia e SEM transição.
		assertThat(eventos.existsByEventId("evt-inv")).isTrue();
		assertThat(cobrancas.findByCobrancaId("pay_abc").orElseThrow().getEstado())
				.isEqualTo(EstadoCobranca.invalidada); // não virou confirmada
		assertThat(ledger.findAll()).as("nenhuma custódia registrada").isEmpty();
		assertThat(participantes.findById(participante.getId()).orElseThrow().getStatus())
				.isEqualTo(StatusParticipante.pagamento_iniciado); // não virou pago
	}

	@Test
	@DisplayName("Fix #1/#2: CONFIRMADO sobre cobrança já `estornada` NÃO re-confirma")
	void confirmadoSobreEstornadaNaoReConfirma() {
		// Empate de timestamp poderia re-aplicar confirmação sobre estornada.
		CobrancaEntity c = cobrancas.findByCobrancaId("pay_abc").orElseThrow();
		c.transicionarPara(EstadoCobranca.estornada);
		cobrancas.save(c);

		service.processar(evento("evt-rc", "PAYMENT_CONFIRMED", "2026-05-21 12:00:00"));

		assertThat(cobrancas.findByCobrancaId("pay_abc").orElseThrow().getEstado())
				.isEqualTo(EstadoCobranca.estornada); // permanece estornada
		assertThat(ledger.findAll()).isEmpty();
	}

	@Test
	@DisplayName("Fix #3: webhook sem `dateCreated` → rejeita (não inventa timestamp)")
	void semDateCreatedRejeita() {
		Map<String, Object> e =
				Map.of(
						"id", "evt-semdata",
						"event", "PAYMENT_CONFIRMED",
						"payment", Map.of("id", "pay_abc", "value", "40.00"));

		assertThatThrownBy(() -> service.processar(e))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("dateCreated");
	}

	@Test
	@DisplayName("Fix #4: CONFIRMADO após Caixinha `apurada` → lock, sem custódia")
	void confirmadoAposApuracaoNaoCustodia() {
		// Caixinha já apurada — conjunto `pago` congelado.
		CaixinhaEntity cx =
				caixinhas.findById(cobrancas.findByCobrancaId("pay_abc").orElseThrow()
						.getCaixinhaId())
						.orElseThrow();
		cx.transicionarPara(EstadoCaixinha.formada);
		cx.transicionarPara(EstadoCaixinha.apurada);
		caixinhas.save(cx);

		service.processar(evento("evt-tarde", "PAYMENT_CONFIRMED", "2026-05-21 12:00:00"));

		// Lock: evento persistido, mas sem custódia e cobrança intocada.
		assertThat(eventos.existsByEventId("evt-tarde")).isTrue();
		assertThat(cobrancas.findByCobrancaId("pay_abc").orElseThrow().getEstado())
				.isEqualTo(EstadoCobranca.ativa);
		assertThat(ledger.findAll()).isEmpty();
	}

	// ───────── Fixes do BMAD code review do Épico 3 (2026-05-21) ─────────

	@Test
	@DisplayName("Fix review: `dateCreated` com offset -03:00 é aceito (não vira string inválida)")
	void dateCreatedComOffsetAceito() {
		// O parsing antigo anexava 'Z' ao final de uma string com offset →
		// "...03-03:00Z" inválido → webhook em loop de 500.
		service.processar(
				evento("evt-offset", "PAYMENT_CONFIRMED", "2026-05-21T09:00:00-03:00"));

		// Processou sem lançar — confirmação aplicada (09:00 BRT = 12:00 UTC).
		assertThat(eventos.existsByEventId("evt-offset")).isTrue();
		assertThat(cobrancas.findByCobrancaId("pay_abc").orElseThrow().getEstado())
				.isEqualTo(EstadoCobranca.confirmada);
	}

	@Test
	@DisplayName("Fix review: `dateCreated` no futuro → rejeitado (não vira 'mais recente' permanente)")
	void dateCreatedNoFuturoRejeitado() {
		// Um evento com timestamp futuro bloquearia todos os eventos
		// legítimos seguintes daquela cobrança.
		assertThatThrownBy(
						() ->
								service.processar(
										evento(
												"evt-futuro",
												"PAYMENT_CONFIRMED",
												"2099-01-01 00:00:00")))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("futuro");
	}
}
