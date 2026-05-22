package com.caxinhabet.caixinha.app;

import com.caxinhabet.auth.app.AppProperties;
import com.caxinhabet.caixinha.adapter.persistence.CaixinhaEntity;
import com.caxinhabet.caixinha.adapter.persistence.CaixinhaRepository;
import com.caxinhabet.caixinha.domain.Caixinha;
import com.caxinhabet.caixinha.domain.EstadoCaixinha;
import com.caxinhabet.caixinha.domain.FormacaoEmail;
import com.caxinhabet.caixinha.domain.FormacaoEmailSender;
import com.caxinhabet.caixinha.domain.ReavaliarFormacao;
import com.caxinhabet.participante.adapter.persistence.ParticipanteEntity;
import com.caxinhabet.participante.adapter.persistence.ParticipanteRepository;
import com.caxinhabet.participante.domain.StatusParticipante;
import com.caxinhabet.shared.money.Money;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Avalia e aplica a Formação / Reversão de uma Caixinha (Story 3.4 v5,
 * FR-9). É a implementação REAL da porta {@link ReavaliarFormacao}
 * (substitui o {@code ReavaliarFormacaoNoOp} da Story 3.3 — que foi
 * deletado).
 *
 * <p>Disparada pelo {@code ProcessarWebhookService} (Story 3.3) sempre
 * que um pagamento é confirmado ou estornado — eventos que mudam o
 * conjunto de Participantes {@code pago}.
 *
 * <p><b>Regras (FR-9 / PRD §11 v5):</b>
 * <ul>
 *   <li>{@code coletando_pagamentos → formada}: {@code pago ≥ Mínimo}
 *       <b>E</b> {@code Σ ingressos pago > R$ 10} (sem a 2ª condição não
 *       haveria Prêmio positivo — borda OQ-2 fechada: bloquear formação).
 *   <li>{@code formada → coletando_pagamentos} (reversão): um estorno
 *       derrubou {@code pago} abaixo do mínimo (ou Σ ≤ R$ 10), e a
 *       Caixinha ainda NÃO foi {@code apurada}.
 *   <li>{@code apurada}/{@code repasse_parcial}/{@code repassada}/
 *       {@code cancelada}: no-op — o conjunto {@code pago} está
 *       congelado (lock de apuração — AC-4). Estorno pós-apuração é
 *       logado como exceção, não reverte.
 * </ul>
 *
 * <p>Idempotente: reavaliar uma Caixinha cujo estado já é coerente com a
 * contagem atual não dispara transição nem notificação.
 */
@Service
class AvaliarFormacaoService implements ReavaliarFormacao {

	private static final Logger log =
			LoggerFactory.getLogger(AvaliarFormacaoService.class);

	private static final Set<StatusParticipante> PAGO = Set.of(StatusParticipante.pago);

	private final CaixinhaRepository caixinhas;
	private final ParticipanteRepository participantes;
	private final FormacaoEmailSender sender;
	private final AppProperties appProps;

	AvaliarFormacaoService(
			CaixinhaRepository caixinhas,
			ParticipanteRepository participantes,
			FormacaoEmailSender sender,
			AppProperties appProps) {
		this.caixinhas = caixinhas;
		this.participantes = participantes;
		this.sender = sender;
		this.appProps = appProps;
	}

	@Override
	public void reavaliar(long caixinhaId) {
		CaixinhaEntity caixinha = caixinhas.findById(caixinhaId).orElse(null);
		if (caixinha == null) {
			log.warn("ReavaliarFormacao: Caixinha {} não encontrada", caixinhaId);
			return;
		}

		long pagos =
				participantes.countByCaixinhaIdAndStatusIn(caixinhaId, PAGO);
		// Σ dos ingressos pago: todos pagam o mesmo valor (FR-1) → count × valor.
		Money soma =
				Money.ofCentavos(caixinha.getValorIngressoCentavos()).times((int) pagos);
		boolean atingiuMinimo = pagos >= caixinha.getMinimoParticipantes();
		boolean premioPositivo = soma.compareTo(Caixinha.TAXA_SERVICO) > 0;
		boolean viavel = atingiuMinimo && premioPositivo;

		switch (caixinha.getEstado()) {
			case coletando_pagamentos -> {
				if (viavel) {
					caixinha.transicionarPara(EstadoCaixinha.formada);
					caixinhas.save(caixinha);
					agendarNotificacao(caixinha, FormacaoEmail.Tipo.FORMADA);
					log.info("Caixinha {} FORMADA (pagos={}, Σ={})", caixinhaId, pagos, soma);
				}
			}
			case formada -> {
				if (!viavel) {
					caixinha.transicionarPara(EstadoCaixinha.coletando_pagamentos);
					caixinhas.save(caixinha);
					agendarNotificacao(caixinha, FormacaoEmail.Tipo.REVERTIDA);
					log.info(
							"Caixinha {} REVERTIDA formada→coletando_pagamentos"
									+ " (pagos={}, Σ={})",
							caixinhaId,
							pagos,
							soma);
				}
			}
			case apurada, repasse_parcial, repassada -> {
				// Lock de apuração (AC-4): o conjunto pago está congelado.
				// Se um estorno chegou aqui, registramos como exceção — não
				// reverte, não recalcula. A Story 4.x trata o efeito real.
				log.warn(
						"ReavaliarFormacao em Caixinha {} no estado {} — lock de apuração"
								+ " ativo, sem reversão (pagos={}, Σ={}). Exceção pós-apuração"
								+ " registrada.",
						caixinhaId,
						caixinha.getEstado(),
						pagos,
						soma);
			}
			case coletando_convites, cancelada -> {
				// coletando_convites: pagamento ainda não liberado — nada a formar.
				// cancelada: terminal de exceção — nada a fazer.
			}
		}
	}

	private void agendarNotificacao(
			CaixinhaEntity caixinha, FormacaoEmail.Tipo tipo) {
		String confronto = caixinha.getLadoA() + " × " + caixinha.getLadoB();
		String link = appProps.getPublicBaseUrl() + "/caixinhas/" + caixinha.getId();
		List<FormacaoEmail> avisos =
				participantes
						.findByCaixinhaIdOrderByCriadoEmAsc(caixinha.getId())
						.stream()
						.map(ParticipanteEntity::getEmail)
						.filter(e -> e != null && !e.isBlank())
						.distinct()
						.map(
								email ->
										new FormacaoEmail(
												email,
												caixinha.getTitulo(),
												confronto,
												link,
												tipo))
						.toList();
		if (avisos.isEmpty()) {
			return;
		}
		if (!TransactionSynchronizationManager.isSynchronizationActive()) {
			avisos.forEach(this::tentarEnviar);
			return;
		}
		TransactionSynchronizationManager.registerSynchronization(
				new TransactionSynchronization() {
					@Override
					public void afterCommit() {
						avisos.forEach(AvaliarFormacaoService.this::tentarEnviar);
					}
				});
	}

	private void tentarEnviar(FormacaoEmail e) {
		try {
			sender.enviarAvisoFormacao(e);
		} catch (Exception ex) {
			log.error(
					"Falha no envio do aviso de Formação ({}) para {} (caixinha={}): {}",
					e.tipo(),
					e.destinatario(),
					e.tituloCaixinha(),
					ex.getMessage(),
					ex);
		}
	}
}
