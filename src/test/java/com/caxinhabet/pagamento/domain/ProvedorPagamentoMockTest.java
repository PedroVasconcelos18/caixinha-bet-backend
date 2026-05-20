package com.caxinhabet.pagamento.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.caxinhabet.shared.money.Money;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * AC-2 da Story 1.4: os testes de domínio mockam a porta
 * {@link ProvedorPagamento} sem dependência do Asaas real.
 *
 * <p>Este teste é a <b>prova viva</b> de que a porta foi modelada com tipos
 * de domínio (não Asaas): conseguimos exercitá-la com Mockito sem importar
 * nada de {@code pagamento.adapter} nem do PSP. Quando o Épico 3 escrever
 * casos de uso reais (FR-7), seguirá esse mesmo padrão.
 */
class ProvedorPagamentoMockTest {

	@Test
	@DisplayName("Domínio invoca a porta criarCobranca com tipos de domínio (sem Asaas)")
	void mockaCriarCobrancaSemAsaas() {
		ProvedorPagamento porta = mock(ProvedorPagamento.class);

		SolicitacaoCobranca solicitacao =
				new SolicitacaoCobranca(
						"cus_test_123",
						"caixinha-1:participante-1",
						Money.of("40.00"),
						"Caixinha Copa — Brasil x Marrocos");

		CobrancaCriada stub =
				new CobrancaCriada(
						"cob-stub-1",
						"base64-stub",
						"00020126...",
						Instant.parse("2026-06-20T23:59:59Z"));
		when(porta.criarCobranca(any(SolicitacaoCobranca.class))).thenReturn(stub);

		CobrancaCriada out = porta.criarCobranca(solicitacao);

		assertThat(out.cobrancaId()).isEqualTo("cob-stub-1");
		assertThat(out.copiaECola()).startsWith("0002");
		verify(porta).criarCobranca(solicitacao);
	}
}
