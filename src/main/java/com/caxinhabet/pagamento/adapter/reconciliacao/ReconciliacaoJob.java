package com.caxinhabet.pagamento.adapter.reconciliacao;

import com.caxinhabet.pagamento.app.ReconciliacaoService;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Job agendado que dispara a reconciliação periodicamente
 * (Story 3.6 v5, NFR-2 / AR-6).
 *
 * <p>{@code @EnableScheduling} é ativado aqui — a Story 3.2 evitou
 * scheduler de propósito (expiração de cobrança é lazy); a reconciliação
 * é o caso legítimo de job periódico (não há trigger natural além do
 * tempo).
 *
 * <p>Intervalo configurável por {@code caixinha.reconciliacao.intervalo-ms}
 * (default 300000 = 5 min). {@code fixedDelay} (não {@code fixedRate}):
 * a próxima rodada só começa depois que a anterior termina — evita
 * rodadas se empilharem se o Provedor estiver lento.
 *
 * <p>O job apenas delega ao {@link ReconciliacaoService} — toda a lógica
 * (consultar, comparar, alertar) vive no service, testável sem o
 * scheduler.
 */
@Component
@EnableScheduling
class ReconciliacaoJob {

	private final ReconciliacaoService reconciliacao;

	ReconciliacaoJob(ReconciliacaoService reconciliacao) {
		this.reconciliacao = reconciliacao;
	}

	@Scheduled(
			fixedDelayString = "${caixinha.reconciliacao.intervalo-ms:300000}",
			initialDelayString = "${caixinha.reconciliacao.atraso-inicial-ms:60000}")
	void executar() {
		reconciliacao.reconciliar();
	}
}
