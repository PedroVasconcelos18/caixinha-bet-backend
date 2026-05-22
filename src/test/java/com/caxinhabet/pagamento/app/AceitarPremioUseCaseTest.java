package com.caxinhabet.pagamento.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.caxinhabet.auth.adapter.persistence.UsuarioEntity;
import com.caxinhabet.auth.adapter.persistence.UsuarioRepository;
import com.caxinhabet.caixinha.adapter.persistence.CaixinhaEntity;
import com.caxinhabet.caixinha.adapter.persistence.CaixinhaRepository;
import com.caxinhabet.caixinha.domain.EstadoCaixinha;
import com.caxinhabet.caixinha.domain.PagamentoIndisponivelException;
import com.caxinhabet.pagamento.adapter.persistence.PayoutEntity;
import com.caxinhabet.pagamento.adapter.persistence.PayoutRepository;
import com.caxinhabet.pagamento.domain.EstadoPayout;
import com.caxinhabet.pagamento.domain.ProvedorPagamento;
import com.caxinhabet.pagamento.domain.ResultadoTransferencia;
import com.caxinhabet.pagamento.domain.ResultadoTransferencia.StatusTransferencia;
import com.caxinhabet.participante.adapter.persistence.ParticipanteEntity;
import com.caxinhabet.participante.adapter.persistence.ParticipanteRepository;
import com.caxinhabet.participante.domain.StatusParticipante;
import com.caxinhabet.participante.domain.StatusVencedor;
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
 * Story 4.6 (FR-13) — {@link AceitarPremioUseCase}: aceite, disparo do
 * PIX, idempotência, falha e transição final {@code repassada}.
 *
 * <p>{@code ProvedorPagamento} mockado — controla o resultado do PIX.
 */
@Testcontainers
@SpringBootTest
class AceitarPremioUseCaseTest {

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

	@Autowired private AceitarPremioUseCase aceitar;
	@Autowired private UsuarioRepository usuarios;
	@Autowired private CaixinhaRepository caixinhas;
	@Autowired private ParticipanteRepository participantes;
	@Autowired private PayoutRepository payouts;

	private long caixinhaId;
	private static final String GANHADOR = "ana@local";

	@BeforeEach
	void setUp() {
		payouts.deleteAll();
		participantes.deleteAll();
		caixinhas.deleteAll();
		usuarios.deleteAll();
	}

	/**
	 * Cria uma Caixinha `repasse_parcial` com um Ganhador {@code ana@local}
	 * (perfil com chave PIX) e o Payout dele. Devolve o participanteId.
	 */
	private long cenarioComUmGanhador() {
		UsuarioEntity ana = usuarios.save(UsuarioEntity.criar(GANHADOR));
		ana.definirPerfilPagamento("Ana Silva", "39053344705");
		ana.definirChavePix("ana@pix.com");
		usuarios.save(ana);

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
								EstadoCaixinha.repasse_parcial,
								ana.getId()));
		caixinhaId = caixinha.getId();

		ParticipanteEntity p =
				new ParticipanteEntity(
						caixinhaId, ana.getId(), GANHADOR, true, StatusParticipante.pago);
		p.marcarComoVencedor();
		p = participantes.save(p);

		payouts.save(new PayoutEntity(caixinhaId, p.getId(), 11000L));
		return p.getId();
	}

	private long usuarioIdDe(String email) {
		return usuarios.findByEmail(email).orElseThrow().getId();
	}

	@Test
	@DisplayName("AC-2/AC-3: aceite com PIX CONCLUIDA → vencedor_pago, repassada")
	void aceiteConcluidoVaiAPago() {
		long participanteId = cenarioComUmGanhador();
		when(provedor.transferir(any()))
				.thenReturn(
						new ResultadoTransferencia(
								"transf-1", StatusTransferencia.CONCLUIDA, Instant.now()));

		AceitarPremioUseCase.Resultado r =
				aceitar.executar(caixinhaId, usuarioIdDe(GANHADOR), GANHADOR);

		assertThat(r.estadoPayout()).isEqualTo(EstadoPayout.pago);
		assertThat(r.comprovante()).isEqualTo("transf-1");
		assertThat(participantes.findById(participanteId).orElseThrow().getStatusVencedor())
				.isEqualTo(StatusVencedor.vencedor_pago);
		// Único Ganhador, todos pagos → Caixinha repassada.
		assertThat(caixinhas.findById(caixinhaId).orElseThrow().getEstado())
				.isEqualTo(EstadoCaixinha.repassada);
	}

	@Test
	@DisplayName("AC-2: aceite 2x → idempotente, PIX disparado uma vez só")
	void aceiteIdempotente() {
		cenarioComUmGanhador();
		when(provedor.transferir(any()))
				.thenReturn(
						new ResultadoTransferencia(
								"transf-1", StatusTransferencia.CONCLUIDA, Instant.now()));

		aceitar.executar(caixinhaId, usuarioIdDe(GANHADOR), GANHADOR);
		// 2º aceite — Payout já `pago`, não redispara.
		AceitarPremioUseCase.Resultado r2 =
				aceitar.executar(caixinhaId, usuarioIdDe(GANHADOR), GANHADOR);

		assertThat(r2.estadoPayout()).isEqualTo(EstadoPayout.pago);
		verify(provedor, times(1)).transferir(any());
	}

	@Test
	@DisplayName("AC-3: PIX PENDENTE → Payout transferindo (aguarda confirmação)")
	void aceitePendenteFicaTransferindo() {
		long participanteId = cenarioComUmGanhador();
		when(provedor.transferir(any()))
				.thenReturn(
						new ResultadoTransferencia(
								"transf-2", StatusTransferencia.PENDENTE, null));

		AceitarPremioUseCase.Resultado r =
				aceitar.executar(caixinhaId, usuarioIdDe(GANHADOR), GANHADOR);

		assertThat(r.estadoPayout()).isEqualTo(EstadoPayout.transferindo);
		assertThat(participantes.findById(participanteId).orElseThrow().getStatusVencedor())
				.isEqualTo(StatusVencedor.vencedor_aceitou);
		// Não repassada — PIX ainda em andamento.
		assertThat(caixinhas.findById(caixinhaId).orElseThrow().getEstado())
				.isEqualTo(EstadoCaixinha.repasse_parcial);
	}

	@Test
	@DisplayName("AC-4: PIX FALHA → Ganhador volta a aguardando_aceite")
	void aceiteFalhaReabreGanhador() {
		long participanteId = cenarioComUmGanhador();
		when(provedor.transferir(any()))
				.thenReturn(
						new ResultadoTransferencia(
								"transf-3", StatusTransferencia.FALHA, null));

		AceitarPremioUseCase.Resultado r =
				aceitar.executar(caixinhaId, usuarioIdDe(GANHADOR), GANHADOR);

		assertThat(r.estadoPayout()).isEqualTo(EstadoPayout.falha);
		assertThat(participantes.findById(participanteId).orElseThrow().getStatusVencedor())
				.as("falha reabre o Ganhador para corrigir a chave PIX")
				.isEqualTo(StatusVencedor.vencedor_aguardando_aceite);
		assertThat(caixinhas.findById(caixinhaId).orElseThrow().getEstado())
				.isEqualTo(EstadoCaixinha.repasse_parcial);
	}

	@Test
	@DisplayName("AC: não-Ganhador → rejeitado")
	void naoGanhadorRejeitado() {
		cenarioComUmGanhador();
		// beto pagou mas NÃO é Ganhador (sem status_vencedor).
		UsuarioEntity beto = usuarios.save(UsuarioEntity.criar("beto@local"));
		participantes.save(
				new ParticipanteEntity(
						caixinhaId, beto.getId(), "beto@local", false,
						StatusParticipante.pago));

		assertThatThrownBy(
						() -> aceitar.executar(caixinhaId, beto.getId(), "beto@local"))
				.isInstanceOf(PagamentoIndisponivelException.class);
	}
}
