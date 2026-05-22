package com.caxinhabet.caixinha.app;

import com.caxinhabet.caixinha.adapter.persistence.CaixinhaEntity;
import com.caxinhabet.caixinha.adapter.persistence.CaixinhaRepository;
import com.caxinhabet.caixinha.domain.ApuracaoInvalidaException;
import com.caxinhabet.caixinha.domain.DispararReembolso;
import com.caxinhabet.caixinha.domain.EstadoCaixinha;
import com.caxinhabet.caixinha.domain.OperacaoNaoAutorizadaException;
import com.caxinhabet.caixinha.domain.ReavaliarFormacao;
import com.caxinhabet.participante.adapter.persistence.ParticipanteRepository;
import com.caxinhabet.participante.domain.StatusParticipante;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

/**
 * Encerra manualmente o prazo de entrada de uma Caixinha (Épico 4 v5,
 * Story 4.5, FR-15).
 *
 * <p>O encerramento força a avaliação que normalmente aconteceria por
 * webhook/tempo:
 * <ul>
 *   <li>{@code coletando_pagamentos} com pagos ≥ Mínimo (e Σ > R$ 10) →
 *       {@code formada} (delega à porta {@link ReavaliarFormacao} —
 *       Story 3.4 — para não duplicar a regra).
 *   <li>{@code coletando_pagamentos} com pagos &lt; Mínimo → {@code cancelada}
 *       (o Reembolso é disparado pela Story 5.1).
 *   <li>{@code coletando_convites} → {@code cancelada} (sem pagamento, sem
 *       reembolso).
 *   <li>{@code formada} → no-op idempotente.
 *   <li>{@code apurada}/{@code repasse_parcial}/{@code repassada}/
 *       {@code cancelada} → rejeitado (422).
 * </ul>
 *
 * <p>Esta story transiciona o estado; o estorno efetivo do Reembolso
 * (chamar {@code ProvedorPagamento.estornar}) é a Story 5.1.
 */
@Service
public class EncerrarPrazoUseCase {

	private static final Logger log = LoggerFactory.getLogger(EncerrarPrazoUseCase.class);

	private final CaixinhaRepository caixinhas;
	private final ParticipanteRepository participantes;
	private final ReavaliarFormacao reavaliarFormacao;
	private final DispararReembolso dispararReembolso;

	public EncerrarPrazoUseCase(
			CaixinhaRepository caixinhas,
			ParticipanteRepository participantes,
			ReavaliarFormacao reavaliarFormacao,
			DispararReembolso dispararReembolso) {
		this.caixinhas = caixinhas;
		this.participantes = participantes;
		this.reavaliarFormacao = reavaliarFormacao;
		this.dispararReembolso = dispararReembolso;
	}

	/**
	 * Resultado do encerramento.
	 *
	 * @param estadoResultante estado da Caixinha após o encerramento.
	 * @param reembolsoPendente {@code true} se a Caixinha foi cancelada com
	 *     Participantes {@code pago} — o Reembolso (Story 5.1) deve rodar.
	 */
	public record Resultado(EstadoCaixinha estadoResultante, boolean reembolsoPendente) {}

	@Transactional
	public Resultado executar(
			long caixinhaId, long usuarioId, String emailAutenticado) {
		CaixinhaEntity caixinha =
				caixinhas
						.findById(caixinhaId)
						.orElseThrow(
								() ->
										new ResponseStatusException(
												HttpStatus.NOT_FOUND, "Caixinha não encontrada."));

		// Anti-enumeração: não-Participante → 404.
		participantes
				.findByCaixinhaIdAndEmail(caixinhaId, emailAutenticado)
				.orElseThrow(
						() ->
								new ResponseStatusException(
										HttpStatus.NOT_FOUND, "Caixinha não encontrada."));

		// Participante, mas não Organizador → 403.
		if (!caixinha.getOrganizadorUsuarioId().equals(usuarioId)) {
			throw new OperacaoNaoAutorizadaException(
					"Apenas o Organizador pode encerrar o prazo da Caixinha.");
		}

		return switch (caixinha.getEstado()) {
			case formada -> {
				// Já formada — encerrar de novo é no-op (idempotente, AC-4).
				yield new Resultado(EstadoCaixinha.formada, false);
			}
			case coletando_convites -> {
				// Mínimo de Aceites nunca atingido — cancela sem reembolso.
				caixinha.transicionarPara(EstadoCaixinha.cancelada);
				caixinhas.save(caixinha);
				log.info(
						"Caixinha {} CANCELADA por encerramento de prazo (coletando_convites)",
						caixinhaId);
				yield new Resultado(EstadoCaixinha.cancelada, false);
			}
			case coletando_pagamentos -> encerrarColetandoPagamentos(caixinha);
			case apurada, repasse_parcial, repassada, cancelada ->
					throw new ApuracaoInvalidaException(
							"Não é possível encerrar o prazo: a Caixinha está em "
									+ caixinha.getEstado()
									+ ".");
		};
	}

	private Resultado encerrarColetandoPagamentos(CaixinhaEntity caixinha) {
		long pagos =
				participantes.countByCaixinhaIdAndStatusIn(
						caixinha.getId(), List.of(StatusParticipante.pago));

		if (pagos >= caixinha.getMinimoParticipantes()) {
			// Viável — força a Formação. A porta ReavaliarFormacao (Story 3.4)
			// aplica a MESMA regra (pagos ≥ mínimo E Σ > R$ 10): se formar,
			// o estado já estará `formada` ao retornar.
			reavaliarFormacao.reavaliar(caixinha.getId());
			EstadoCaixinha depois =
					caixinhas.findById(caixinha.getId()).orElseThrow().getEstado();
			log.info(
					"Caixinha {} encerramento de prazo — reavaliada, estado={}",
					caixinha.getId(),
					depois);
			// Se a borda Σ ≤ R$ 10 impediu a Formação mesmo com pagos ≥ mínimo
			// (ingresso muito baixo), a Caixinha continua coletando_pagamentos;
			// nesse caso raro o encerramento não muda nada — retorna o estado.
			return new Resultado(depois, false);
		}

		// pagos < mínimo — cancela.
		caixinha.transicionarPara(EstadoCaixinha.cancelada);
		caixinhas.save(caixinha);
		log.info(
				"Caixinha {} CANCELADA por encerramento de prazo (pagos {} < mínimo {})",
				caixinha.getId(),
				pagos,
				caixinha.getMinimoParticipantes());

		// Story 5.1 (FR-11): se há Participantes que pagaram, dispara o
		// Reembolso automático — APÓS O COMMIT (fix code review Épico 5).
		// O `dispararReembolso` chama `provedor.estornar` (rede ao Asaas);
		// rodar isso dentro desta transação arriscaria mandar dinheiro de
		// volta e depois a transação reverter, deixando a Caixinha
		// não-cancelada. O `afterCommit` garante que o estorno só dispara
		// quando o cancelamento já está persistido.
		boolean houvePagantes = pagos > 0;
		if (houvePagantes) {
			long caixinhaId = caixinha.getId();
			TransactionSynchronizationManager.registerSynchronization(
					new TransactionSynchronization() {
						@Override
						public void afterCommit() {
							dispararReembolso.dispararReembolso(caixinhaId);
						}
					});
		}
		return new Resultado(EstadoCaixinha.cancelada, houvePagantes);
	}
}
