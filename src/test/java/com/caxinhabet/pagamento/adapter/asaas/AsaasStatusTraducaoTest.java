package com.caxinhabet.pagamento.adapter.asaas;

import static org.assertj.core.api.Assertions.assertThat;

import com.caxinhabet.pagamento.domain.StatusCobranca;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Story 3.6 — tradução do {@code status} do Asaas para o
 * {@link StatusCobranca} de domínio ({@code AsaasProvedorPagamentoAdapter.traduzirStatus}).
 *
 * <p>Teste unitário puro — a tradução é função estática.
 */
class AsaasStatusTraducaoTest {

	@Test
	@DisplayName("CONFIRMED / RECEIVED / RECEIVED_IN_CASH → CONFIRMADA")
	void confirmados() {
		assertThat(AsaasProvedorPagamentoAdapter.traduzirStatus("CONFIRMED"))
				.isEqualTo(StatusCobranca.CONFIRMADA);
		assertThat(AsaasProvedorPagamentoAdapter.traduzirStatus("RECEIVED"))
				.isEqualTo(StatusCobranca.CONFIRMADA);
		assertThat(AsaasProvedorPagamentoAdapter.traduzirStatus("RECEIVED_IN_CASH"))
				.isEqualTo(StatusCobranca.CONFIRMADA);
	}

	@Test
	@DisplayName("Fix review: REFUND_REQUESTED / REFUND_IN_PROGRESS → CONFIRMADA")
	void estornoApenasSolicitadoSegueConfirmado() {
		// Estorno pedido/em andamento NÃO é estorno efetivado — o dinheiro
		// ainda está custodiado. Mapeá-los para ESTORNADA causaria alerta
		// de divergência falso na reconciliação (code review Épico 3).
		assertThat(AsaasProvedorPagamentoAdapter.traduzirStatus("REFUND_REQUESTED"))
				.isEqualTo(StatusCobranca.CONFIRMADA);
		assertThat(AsaasProvedorPagamentoAdapter.traduzirStatus("REFUND_IN_PROGRESS"))
				.isEqualTo(StatusCobranca.CONFIRMADA);
	}

	@Test
	@DisplayName("OVERDUE → EXPIRADA")
	void vencido() {
		assertThat(AsaasProvedorPagamentoAdapter.traduzirStatus("OVERDUE"))
				.isEqualTo(StatusCobranca.EXPIRADA);
	}

	@Test
	@DisplayName("REFUNDED (efetivado) e chargebacks → ESTORNADA")
	void estornados() {
		assertThat(AsaasProvedorPagamentoAdapter.traduzirStatus("REFUNDED"))
				.isEqualTo(StatusCobranca.ESTORNADA);
		assertThat(AsaasProvedorPagamentoAdapter.traduzirStatus("CHARGEBACK_REQUESTED"))
				.isEqualTo(StatusCobranca.ESTORNADA);
		assertThat(AsaasProvedorPagamentoAdapter.traduzirStatus("CHARGEBACK_DISPUTE"))
				.isEqualTo(StatusCobranca.ESTORNADA);
	}

	@Test
	@DisplayName("PENDING e status desconhecido → PENDENTE (conservador)")
	void pendenteEDesconhecido() {
		assertThat(AsaasProvedorPagamentoAdapter.traduzirStatus("PENDING"))
				.isEqualTo(StatusCobranca.PENDENTE);
		assertThat(AsaasProvedorPagamentoAdapter.traduzirStatus("ALGO_NOVO_DO_ASAAS"))
				.isEqualTo(StatusCobranca.PENDENTE);
		assertThat(AsaasProvedorPagamentoAdapter.traduzirStatus(null))
				.isEqualTo(StatusCobranca.PENDENTE);
	}
}
