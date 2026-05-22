package com.caxinhabet.ledger.app;

import static org.assertj.core.api.Assertions.assertThat;

import com.caxinhabet.auth.adapter.persistence.UsuarioEntity;
import com.caxinhabet.auth.adapter.persistence.UsuarioRepository;
import com.caxinhabet.caixinha.adapter.persistence.CaixinhaEntity;
import com.caxinhabet.caixinha.adapter.persistence.CaixinhaRepository;
import com.caxinhabet.caixinha.domain.EstadoCaixinha;
import com.caxinhabet.ledger.adapter.persistence.LedgerLancamentoEntity;
import com.caxinhabet.ledger.adapter.persistence.LedgerLancamentoRepository;
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
 * Story 3.3 (AR-6) — {@link LedgerService}: dupla entrada balanceada.
 */
@Testcontainers
@SpringBootTest
class LedgerServiceTest {

	@Container
	static final PostgreSQLContainer<?> POSTGRES =
			new PostgreSQLContainer<>("postgres:17");

	@DynamicPropertySource
	static void props(DynamicPropertyRegistry r) {
		r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
		r.add("spring.datasource.username", POSTGRES::getUsername);
		r.add("spring.datasource.password", POSTGRES::getPassword);
	}

	@Autowired private LedgerService service;
	@Autowired private LedgerLancamentoRepository lancamentos;
	@Autowired private UsuarioRepository usuarios;
	@Autowired private CaixinhaRepository caixinhas;
	@Autowired private ParticipanteRepository participantes;

	private long caixinhaId;
	private long participanteId;

	@BeforeEach
	void setUp() {
		lancamentos.deleteAll();
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
		ParticipanteEntity p =
				participantes.save(
						new ParticipanteEntity(
								caixinhaId,
								rafael.getId(),
								"alice@local",
								false,
								StatusParticipante.pago));
		participanteId = p.getId();
	}

	@Test
	@DisplayName("registrarCustodia grava par débito+crédito balanceado")
	void custodiaBalanceada() {
		service.registrarCustodia(
				caixinhaId, participanteId, "pay_1", Money.of("40.00"), "evt-1");

		List<LedgerLancamentoEntity> ls = lancamentos.findByCaixinhaId(caixinhaId);
		assertThat(ls).hasSize(2);
		// Mesma transação, valores iguais, tipos opostos.
		assertThat(ls).extracting(LedgerLancamentoEntity::getTransacaoId)
				.containsOnly(ls.get(0).getTransacaoId());
		assertThat(ls).extracting(LedgerLancamentoEntity::getTipo)
				.containsExactlyInAnyOrder("debito", "credito");
		assertThat(ls).allSatisfy(l -> assertThat(l.getValorCentavos()).isEqualTo(4000L));
		assertThat(service.transacaoBalanceada(ls.get(0).getTransacaoId())).isTrue();
	}

	@Test
	@DisplayName("registrarEstorno grava par débito+crédito balanceado")
	void estornoBalanceado() {
		service.registrarEstorno(
				caixinhaId, participanteId, "pay_1", Money.of("40.00"), "evt-2");

		List<LedgerLancamentoEntity> ls = lancamentos.findByCaixinhaId(caixinhaId);
		assertThat(ls).hasSize(2);
		assertThat(ls).extracting(LedgerLancamentoEntity::getConta)
				.containsExactlyInAnyOrder("custodia", "estorno");
		assertThat(service.transacaoBalanceada(ls.get(0).getTransacaoId())).isTrue();
	}

	@Test
	@DisplayName("custódia + estorno = 4 lançamentos, 2 transações distintas balanceadas")
	void custodiaEstornoDuasTransacoes() {
		service.registrarCustodia(
				caixinhaId, participanteId, "pay_1", Money.of("40.00"), "evt-1");
		service.registrarEstorno(
				caixinhaId, participanteId, "pay_1", Money.of("40.00"), "evt-2");

		List<LedgerLancamentoEntity> ls = lancamentos.findByCaixinhaId(caixinhaId);
		assertThat(ls).hasSize(4);
		// 4 lançamentos, 2 transações distintas (cada par débito+crédito
		// compartilha uma transacaoId).
		assertThat(ls.stream().map(LedgerLancamentoEntity::getTransacaoId).distinct().count())
				.isEqualTo(2L);
	}

	@Test
	@DisplayName("Fix review: registrarCobrancaGerada credita `a_receber` (par débito expectativa)")
	void cobrancaGeradaCreditaAReceber() {
		service.registrarCobrancaGerada(
				caixinhaId, participanteId, "pay_1", Money.of("40.00"), "pay_1");

		List<LedgerLancamentoEntity> ls = lancamentos.findByCaixinhaId(caixinhaId);
		assertThat(ls).hasSize(2);
		assertThat(ls).extracting(LedgerLancamentoEntity::getConta)
				.containsExactlyInAnyOrder("expectativa", "a_receber");
		assertThat(ls).extracting(LedgerLancamentoEntity::getTipo)
				.containsExactlyInAnyOrder("debito", "credito");
		assertThat(service.transacaoBalanceada(ls.get(0).getTransacaoId())).isTrue();
	}

	@Test
	@DisplayName("Fix review: ciclo geração→custódia fecha `a_receber` (crédito na geração, débito na confirmação)")
	void cicloAReceberFecha() {
		// Geração credita a_receber; custódia debita a_receber. O saldo
		// líquido de `a_receber` zera — o ciclo do AC-7 fecha.
		service.registrarCobrancaGerada(
				caixinhaId, participanteId, "pay_1", Money.of("40.00"), "pay_1");
		service.registrarCustodia(
				caixinhaId, participanteId, "pay_1", Money.of("40.00"), "evt-1");

		List<LedgerLancamentoEntity> aReceber =
				lancamentos.findByCaixinhaId(caixinhaId).stream()
						.filter(l -> l.getConta().equals("a_receber"))
						.toList();
		// Um crédito (geração) + um débito (custódia) = saldo líquido zero.
		assertThat(aReceber).hasSize(2);
		long creditos =
				aReceber.stream()
						.filter(l -> l.getTipo().equals("credito"))
						.mapToLong(LedgerLancamentoEntity::getValorCentavos)
						.sum();
		long debitos =
				aReceber.stream()
						.filter(l -> l.getTipo().equals("debito"))
						.mapToLong(LedgerLancamentoEntity::getValorCentavos)
						.sum();
		assertThat(creditos)
				.as("a_receber: crédito da geração == débito da confirmação")
				.isEqualTo(debitos);
	}
}
