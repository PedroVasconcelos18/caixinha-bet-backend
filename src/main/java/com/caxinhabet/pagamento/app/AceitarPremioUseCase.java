package com.caxinhabet.pagamento.app;

import com.caxinhabet.auth.domain.ChavePixObrigatoriaException;
import com.caxinhabet.auth.domain.PerfilPagamentoGateway;
import com.caxinhabet.caixinha.domain.PagamentoIndisponivelException;
import com.caxinhabet.caixinha.domain.TransicaoRepasse;
import com.caxinhabet.ledger.app.LedgerService;
import com.caxinhabet.pagamento.adapter.persistence.PayoutEntity;
import com.caxinhabet.pagamento.adapter.persistence.PayoutRepository;
import com.caxinhabet.pagamento.domain.EstadoPayout;
import com.caxinhabet.pagamento.domain.Ganhador;
import com.caxinhabet.pagamento.domain.ProvedorPagamento;
import com.caxinhabet.pagamento.domain.ResultadoTransferencia;
import com.caxinhabet.participante.adapter.persistence.ParticipanteEntity;
import com.caxinhabet.participante.adapter.persistence.ParticipanteRepository;
import com.caxinhabet.participante.domain.StatusVencedor;
import com.caxinhabet.shared.money.Money;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

/**
 * O Ganhador aceita o prêmio e o PIX é disparado (Épico 4 v5, Story 4.6,
 * FR-13 — o clímax de UJ-3).
 *
 * <p><b>Três fases, transações separadas</b> (fix code review Épico 4 —
 * NÃO disparar PIX dentro de uma transação):
 * <ol>
 *   <li><b>Fase 1 (tx curta)</b> — lê o {@code Payout} com <b>lock
 *       pessimista</b> (serializa cliques concorrentes), valida, transiciona
 *       {@code vencedor_aguardando_aceite → vencedor_aceitou} e
 *       {@code Payout → transferindo}; <b>commita</b>.
 *   <li><b>Fase 2 (sem tx)</b> — chama {@code ProvedorPagamento.transferir}
 *       (rede). Se o app cair aqui, o estado já está {@code transferindo}
 *       persistido — uma reconciliação ou novo aceite resolve, e o
 *       {@code transferir} é idempotente por {@code payout_id}.
 *   <li><b>Fase 3 (tx curta)</b> — conforme o {@link ResultadoTransferencia},
 *       grava {@code pago}+ledger+{@code repassada}, ou {@code falha}
 *       reabrindo o Ganhador.
 * </ol>
 *
 * <p>Por que separar: se o PIX é disparado dentro da mesma transação que
 * grava {@code pago}/{@code repassada}, uma falha no commit deixaria o
 * dinheiro fora do Asaas mas o estado revertido — e a retentativa
 * duplicaria o PIX. Com as fases separadas, o disparo nunca está sob uma
 * transação que pode reverter depois dele.
 */
@Service
public class AceitarPremioUseCase {

	private static final Logger log = LoggerFactory.getLogger(AceitarPremioUseCase.class);

	private final PayoutRepository payouts;
	private final ParticipanteRepository participantes;
	private final PerfilPagamentoGateway perfis;
	private final ProvedorPagamento provedor;
	private final LedgerService ledger;
	private final TransicaoRepasse transicaoRepasse;
	private final TransactionTemplate tx;

	public AceitarPremioUseCase(
			PayoutRepository payouts,
			ParticipanteRepository participantes,
			PerfilPagamentoGateway perfis,
			ProvedorPagamento provedor,
			LedgerService ledger,
			TransicaoRepasse transicaoRepasse,
			TransactionTemplate transactionTemplate) {
		this.payouts = payouts;
		this.participantes = participantes;
		this.perfis = perfis;
		this.provedor = provedor;
		this.ledger = ledger;
		this.transicaoRepasse = transicaoRepasse;
		this.tx = transactionTemplate;
	}

	/**
	 * Resultado do aceite.
	 *
	 * @param estadoPayout estado do Payout após o aceite.
	 * @param comprovante comprovante do PIX ({@code null} se ainda não pago).
	 */
	public record Resultado(EstadoPayout estadoPayout, String comprovante) {}

	/** Dados levados da Fase 1 para a Fase 2/3. */
	private record DadosAceite(
			long payoutEntityId,
			long ganhadorParticipanteId,
			String payoutId,
			String chavePix,
			Money valor) {}

	/**
	 * @param caixinhaId Caixinha do prêmio.
	 * @param usuarioId Usuário autenticado (deve ser o Ganhador).
	 * @param emailAutenticado e-mail do autor (anti-enumeração).
	 */
	public Resultado executar(
			long caixinhaId, long usuarioId, String emailAutenticado) {

		// ───── Fase 1: validação + reserva do aceite (tx curta, com lock) ─────
		Object faseUm = tx.execute(status -> faseReservar(caixinhaId, usuarioId, emailAutenticado));

		// Idempotência: a Fase 1 pode devolver um Resultado pronto (Payout já
		// em andamento/pago) — nesse caso não há PIX a disparar.
		if (faseUm instanceof Resultado jaResolvido) {
			return jaResolvido;
		}
		DadosAceite dados = (DadosAceite) faseUm;

		// ───── Fase 2: dispara o PIX FORA de qualquer transação ─────
		ResultadoTransferencia resultado;
		try {
			resultado =
					provedor.transferir(
							new Ganhador(dados.payoutId(), dados.chavePix(), dados.valor()));
		} catch (RuntimeException e) {
			// Falha de rede/Asaas no disparo — o Payout fica `transferindo`
			// (persistido na Fase 1). Não sabemos se o PIX saiu; a
			// reconciliação (Story 3.6) ou um novo aceite (idempotente por
			// payout_id) resolve. Propaga para o caller saber.
			log.error(
					"[PAYOUT] Falha ao disparar PIX do payout {} — fica `transferindo`"
							+ " para reconciliação: {}",
					dados.payoutId(),
					e.getMessage(),
					e);
			throw new ResponseStatusException(
					HttpStatus.BAD_GATEWAY,
					"Não foi possível concluir o PIX agora. O prêmio segue em"
							+ " processamento — acompanhe o Acerto de Contas.");
		}

		// ───── Fase 3: aplica o resultado (tx curta) ─────
		return tx.execute(status -> faseAplicarResultado(caixinhaId, dados, resultado));
	}

	/**
	 * Fase 1: lê o Payout com lock pessimista, valida e transiciona para
	 * {@code transferindo}. Devolve {@link DadosAceite} para a Fase 2, OU
	 * um {@link Resultado} pronto se o aceite já foi feito (idempotência).
	 */
	private Object faseReservar(
			long caixinhaId, long usuarioId, String emailAutenticado) {
		ParticipanteEntity ganhador =
				participantes
						.findByCaixinhaIdAndEmail(caixinhaId, emailAutenticado)
						.orElseThrow(
								() ->
										new ResponseStatusException(
												HttpStatus.NOT_FOUND, "Caixinha não encontrada."));

		if (ganhador.getStatusVencedor() == null) {
			throw new PagamentoIndisponivelException(
					"Você não é um Ganhador desta Caixinha.");
		}

		// Lock pessimista: serializa cliques concorrentes no botão "aceitar".
		PayoutEntity payout =
				payouts
						.findParaAceiteComLock(caixinhaId, ganhador.getId())
						.orElseThrow(
								() ->
										new IllegalStateException(
												"Ganhador sem Payout — caixinha "
														+ caixinhaId
														+ ", participante "
														+ ganhador.getId()));

		// Idempotência: Payout já em andamento ou concluído — não redispara.
		if (payout.getEstado() == EstadoPayout.transferindo
				|| payout.getEstado() == EstadoPayout.pago) {
			return new Resultado(payout.getEstado(), payout.getComprovante());
		}

		// Chave PIX cadastrada (pré-requisito FR-5; defesa em profundidade).
		PerfilPagamentoGateway.PerfilPagamento perfil =
				perfis.buscar(usuarioId)
						.orElseThrow(
								() ->
										new ResponseStatusException(
												HttpStatus.NOT_FOUND, "Usuário não encontrado."));
		if (perfil.chavePix() == null || perfil.chavePix().isBlank()) {
			throw new ChavePixObrigatoriaException(
					"Cadastre sua chave PIX antes de aceitar o prêmio.");
		}

		// Reserva o aceite: vencedor_aceitou + Payout transferindo. Commitado
		// ao fim desta fase — ANTES de qualquer chamada de rede.
		ganhador.setStatusVencedor(StatusVencedor.vencedor_aceitou);
		payout.marcarTransferindo(null);
		participantes.save(ganhador);
		payouts.save(payout);

		return new DadosAceite(
				payout.getId(),
				ganhador.getId(),
				payout.getPayoutId().toString(),
				perfil.chavePix(),
				Money.ofCentavos(payout.getValorCentavos()));
	}

	/** Fase 3: aplica o {@link ResultadoTransferencia} numa tx própria. */
	private Resultado faseAplicarResultado(
			long caixinhaId, DadosAceite dados, ResultadoTransferencia resultado) {
		PayoutEntity payout =
				payouts.findById(dados.payoutEntityId()).orElseThrow();
		ParticipanteEntity ganhador =
				participantes.findById(dados.ganhadorParticipanteId()).orElseThrow();

		return switch (resultado.status()) {
			case CONCLUIDA -> {
				payout.marcarPago(resultado.transferenciaId());
				ganhador.setStatusVencedor(StatusVencedor.vencedor_pago);
				participantes.save(ganhador);
				payouts.save(payout);
				ledger.registrarSaidaPremio(
						caixinhaId, ganhador.getId(), dados.valor(), dados.payoutId());
				log.info(
						"Payout {} PAGO — transferência {}",
						dados.payoutId(),
						resultado.transferenciaId());
				avaliarRepassada(caixinhaId);
				yield new Resultado(EstadoPayout.pago, resultado.transferenciaId());
			}
			case PENDENTE -> {
				// PIX aceito, aguardando liquidação. Fica `transferindo`; a
				// confirmação final virá de polling/reconciliação.
				payout.marcarTransferindo(resultado.transferenciaId());
				payouts.save(payout);
				log.info(
						"Payout {} em transferência (PENDENTE) — {}",
						dados.payoutId(),
						resultado.transferenciaId());
				yield new Resultado(EstadoPayout.transferindo, null);
			}
			case FALHA -> {
				// Chave inválida / recusa — reabre o Ganhador para corrigir.
				payout.marcarFalha();
				ganhador.setStatusVencedor(StatusVencedor.vencedor_aguardando_aceite);
				participantes.save(ganhador);
				payouts.save(payout);
				log.error(
						"[PAYOUT-FALHA] Payout {} (caixinha {}, participante {})"
								+ " — PIX recusado pelo Asaas. Ganhador reaberto para"
								+ " corrigir a chave PIX.",
						dados.payoutId(),
						caixinhaId,
						ganhador.getId());
				yield new Resultado(EstadoPayout.falha, null);
			}
		};
	}

	/**
	 * Se TODOS os Payouts da Caixinha estão {@code pago}, transiciona a
	 * Caixinha {@code repasse_parcial → repassada} (via a porta).
	 */
	private void avaliarRepassada(long caixinhaId) {
		boolean todosPagos =
				payouts.findByCaixinhaId(caixinhaId).stream()
						.allMatch(p -> p.getEstado() == EstadoPayout.pago);
		if (todosPagos) {
			transicaoRepasse.marcarRepassada(caixinhaId);
		}
	}
}
