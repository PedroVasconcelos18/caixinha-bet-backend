package com.caxinhabet.pagamento.adapter.asaas;

import static org.assertj.core.api.Assertions.assertThat;

import com.caxinhabet.pagamento.domain.CobrancaCriada;
import com.caxinhabet.pagamento.domain.ProvedorPagamento;
import com.caxinhabet.pagamento.domain.SolicitacaoCobranca;
import com.caxinhabet.shared.money.Money;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Smoke test contra o sandbox <b>real</b> do Asaas (Story 1.5 AC-1).
 *
 * <p>Esta é a <b>única</b> bateria de testes do projeto que faz chamadas
 * HTTP saintes para fora. Por isso só roda quando o ambiente tem as
 * credenciais via {@link EnabledIfEnvironmentVariable} — sem
 * {@code ASAAS_ACCESS_TOKEN} (CI sem segredo, dev local sem chave),
 * o JUnit pula o teste silenciosamente (não falha o build).
 *
 * <p><b>Como rodar localmente:</b>
 * <pre>
 *   $env:ASAAS_ACCESS_TOKEN = "<sua-api-key-do-sandbox>"
 *   $env:ASAAS_TEST_CUSTOMER_ID = "<cus_xxx>"   # criado uma vez via POST /v3/customers
 *   .\mvnw.cmd -B test -Dtest=AsaasSandboxTest
 * </pre>
 *
 * <p>O teste imprime o {@code cobrancaId} no log. Pedro pode então abrir o
 * painel {@code sandbox.asaas.com}, simular pagamento dessa cobrança, e
 * verificar o webhook chegando (Task 2).
 */
@Testcontainers
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "ASAAS_ACCESS_TOKEN", matches = ".+")
@EnabledIfEnvironmentVariable(named = "ASAAS_TEST_CUSTOMER_ID", matches = "^cus_.+")
class AsaasSandboxTest {

	private static final Logger log = LoggerFactory.getLogger(AsaasSandboxTest.class);

	// Postgres é necessário pelo SpringBootTest (autowire de
	// PagamentoEventoRepository); mas este teste NÃO toca o DB diretamente.
	@Container @ServiceConnection
	static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

	@Autowired private ProvedorPagamento porta;

	@Test
	@DisplayName("Sandbox real: criarCobranca devolve cobrança válida com QR + copia-e-cola")
	void criaCobrancaSandboxReal() {
		String customerId = System.getenv("ASAAS_TEST_CUSTOMER_ID");
		String ref = "smoke-1-5:" + UUID.randomUUID();

		// Asaas exige mínimo R$ 5,00 por cobrança (descoberto no smoke real
		// 2026-05-19: tentar com R$ 1,00 devolve 400 "valor da cobrança ...
		// não pode ser menor que R$ 5,00"). A minha pesquisa anterior estava
		// errada. Usamos R$ 5,00 — valor simbólico para o smoke do gate.
		SolicitacaoCobranca solicitacao =
				new SolicitacaoCobranca(
						customerId,
						ref,
						Money.of("5.00"),
						"Smoke gate Asaas (Story 1.5) — ignorar");

		CobrancaCriada cobranca = porta.criarCobranca(solicitacao);

		// Evidência: logamos o cobrancaId para o operador pagar no painel.
		// Asaas usa prefixo "pay_" em IDs de pagamento.
		log.info("[GATE-ASAAS] cobrancaId = {}", cobranca.cobrancaId());
		log.info("[GATE-ASAAS] expiraEm   = {}", cobranca.expiraEm());

		assertThat(cobranca.cobrancaId()).isNotBlank().startsWith("pay_");
		// BR-Code (PIX) começa com "0002" (payload format indicator).
		assertThat(cobranca.copiaECola()).isNotBlank().startsWith("0002");
		assertThat(cobranca.qrCodeImagemBase64()).isNotBlank();
		assertThat(cobranca.expiraEm()).isNotNull();
	}
}
