package com.caxinhabet;

import static org.assertj.core.api.Assertions.assertThat;

import com.caxinhabet.auth.adapter.persistence.SolicitacaoAcessoRepository;
import com.caxinhabet.auth.adapter.persistence.UsuarioRepository;
import com.caxinhabet.caixinha.adapter.persistence.CaixinhaRepository;
import com.caxinhabet.caixinha.adapter.persistence.ResultadoPossivelRepository;
import com.caxinhabet.ledger.adapter.persistence.LedgerLancamentoRepository;
import com.caxinhabet.pagamento.adapter.persistence.CobrancaRepository;
import com.caxinhabet.pagamento.adapter.persistence.PagamentoEventoRepository;
import com.caxinhabet.pagamento.adapter.persistence.PayoutRepository;
import com.caxinhabet.participante.adapter.persistence.ParticipanteRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Confirma que o {@code @SpringBootApplication} sobe e que o component scan
 * a partir da raiz {@code com.caxinhabet} cobre os 6 módulos de feature
 * (AC-2), SEM exigir Postgres.
 *
 * <p>A autoconfiguração de datasource/JPA é excluída de propósito: o boot
 * end-to-end contra um Postgres real é coberto pelo teste de integração com
 * Testcontainers (Task 3 / AC-3). Aqui validamos só a fundação estrutural.
 *
 * <p><b>Nota Spring Boot 4:</b> as autoconfigs migraram para pacotes por
 * módulo — {@code org.springframework.boot.jdbc.autoconfigure.*} e
 * {@code org.springframework.boot.hibernate.autoconfigure.*} (não mais
 * {@code org.springframework.boot.autoconfigure.jdbc.*} do Boot 3).
 */
@SpringBootTest(
		properties = {
			"spring.autoconfigure.exclude="
					+ "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
					+ "org.springframework.boot.jdbc.autoconfigure.DataSourceInitializationAutoConfiguration,"
					+ "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration"
		})
class CaixinhaBetApplicationContextTest {

	@Autowired private ApplicationContext context;

	/**
	 * Excluímos JPA/DataSource autoconfig (este teste valida só o boot
	 * estrutural sem Postgres), então os repositórios reais do JPA não
	 * existem. Mockamos para que os controllers (AsaasWebhookController,
	 * AuthController) e use cases possam ser instanciados pelo contexto.
	 */
	@MockitoBean private PagamentoEventoRepository pagamentoEventoRepository;

	@MockitoBean private UsuarioRepository usuarioRepository;

	@MockitoBean private SolicitacaoAcessoRepository solicitacaoAcessoRepository;

	@MockitoBean private CaixinhaRepository caixinhaRepository;

	@MockitoBean private ResultadoPossivelRepository resultadoPossivelRepository;

	@MockitoBean private ParticipanteRepository participanteRepository;

	// Story 3.2: nova tabela pagamento_cobranca.
	@MockitoBean private CobrancaRepository cobrancaRepository;

	// Story 3.3: ledger de dupla entrada.
	@MockitoBean private LedgerLancamentoRepository ledgerLancamentoRepository;

	// Story 4.3: agregado Payout (Repasse do prêmio).
	@MockitoBean private PayoutRepository payoutRepository;

	// Story 4.6: o PlatformTransactionManager vem do JPA/DataSource (aqui
	// excluído); o TransactionTemplate do AceitarPremioUseCase depende dele.
	@MockitoBean
	private org.springframework.transaction.PlatformTransactionManager
			platformTransactionManager;

	@Test
	@DisplayName("O contexto Spring sobe sem erro a partir de CaixinhaBetApplication")
	void contextLoads() {
		assertThat(context).isNotNull();
		assertThat(context.getBeansOfType(CaixinhaBetApplication.class))
				.as("a classe principal CaixinhaBetApplication deve ser um bean gerenciado")
				.isNotEmpty();
	}
}
