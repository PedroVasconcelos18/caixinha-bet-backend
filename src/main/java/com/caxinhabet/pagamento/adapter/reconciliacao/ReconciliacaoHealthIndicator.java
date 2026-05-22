package com.caxinhabet.pagamento.adapter.reconciliacao;

import com.caxinhabet.pagamento.app.ReconciliacaoService;
import com.caxinhabet.pagamento.app.ReconciliacaoService.ResultadoReconciliacao;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Health indicator que expõe o resultado da última reconciliação no
 * {@code /actuator/health} (Story 3.6 v5, NFR-2 / AC-5).
 *
 * <p><b>Divergência ≠ falha:</b> o status fica {@code UP} sempre que o
 * job de reconciliação <i>rodou</i> — o nº de divergências é um detalhe
 * informativo (operacional), não um {@code DOWN}. {@code DOWN} significaria
 * "o job não funciona", o que é outra coisa. O operador olha os detalhes
 * (`divergentes`) para agir; a infra olha o status para saber se o job
 * está vivo.
 *
 * <p>Antes da 1ª rodada o status continua {@code UP} (detalhe "ainda não
 * executou") — NÃO {@code OUT_OF_SERVICE}/{@code DOWN}: o app está
 * saudável, a reconciliação só ainda não teve a 1ª execução (há um atraso
 * inicial). Reportar não-UP aqui derrubaria o {@code /actuator/health}
 * agregado para 503 sem motivo real.
 */
@Component("reconciliacao")
class ReconciliacaoHealthIndicator implements HealthIndicator {

	private final ReconciliacaoService reconciliacao;

	ReconciliacaoHealthIndicator(ReconciliacaoService reconciliacao) {
		this.reconciliacao = reconciliacao;
	}

	@Override
	public Health health() {
		ResultadoReconciliacao r = reconciliacao.ultimoResultado();
		if (!r.jaRodou()) {
			return Health.up()
					.withDetail("mensagem", "reconciliação ainda não executou")
					.build();
		}
		return Health.up()
				.withDetail("ultimaExecucao", r.executadaEm().toString())
				.withDetail("cobrancasVerificadas", r.verificadas())
				.withDetail("divergencias", r.divergentes())
				.withDetail("errosDeConsulta", r.erros())
				.build();
	}
}
