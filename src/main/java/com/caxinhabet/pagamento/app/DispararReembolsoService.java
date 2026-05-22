package com.caxinhabet.pagamento.app;

import com.caxinhabet.caixinha.domain.ConsultaCaixinha;
import com.caxinhabet.caixinha.domain.DispararReembolso;
import com.caxinhabet.pagamento.adapter.persistence.CobrancaEntity;
import com.caxinhabet.pagamento.adapter.persistence.CobrancaRepository;
import com.caxinhabet.pagamento.domain.CancelamentoEmail;
import com.caxinhabet.pagamento.domain.CancelamentoEmailSender;
import com.caxinhabet.pagamento.domain.EstadoCobranca;
import com.caxinhabet.pagamento.domain.ProvedorPagamento;
import com.caxinhabet.participante.adapter.persistence.ParticipanteEntity;
import com.caxinhabet.participante.adapter.persistence.ParticipanteRepository;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Dispara o Reembolso de uma Caixinha cancelada (Épico 5 v5, Story 5.1,
 * FR-11). Implementação da porta {@link DispararReembolso} — chamada pelo
 * {@code EncerrarPrazoUseCase} (Story 4.5) <b>após o commit</b> do
 * cancelamento.
 *
 * <p><b>Fora de transação (fix code review Épico 5):</b> o
 * {@code EncerrarPrazoUseCase} é {@code @Transactional}; chamar
 * {@code provedor.estornar} (rede ao Asaas) dentro dessa transação
 * arriscaria mandar dinheiro de volta e depois a transação reverter,
 * deixando a Caixinha não-cancelada. Por isso o {@code EncerrarPrazoUseCase}
 * registra esta chamada em {@code afterCommit}; aqui cada estorno roda em
 * sua própria transação curta.
 *
 * <p><b>Idempotência por estado (fix code review Épico 5):</b> cada
 * cobrança a estornar é primeiro marcada {@code confirmada →
 * estorno_solicitado} e só então o {@code estornar} é chamado. Se
 * {@code estornar} falha, a transação curta reverte a marca — a cobrança
 * volta a {@code confirmada} e pode ser re-tentada. Uma cobrança já em
 * {@code estorno_solicitado}/{@code estornada} é PULADA — um re-disparo
 * do reembolso (na janela antes do webhook {@code PAYMENT_REFUNDED}) não
 * estorna duas vezes.
 */
@Service
class DispararReembolsoService implements DispararReembolso {

	private static final Logger log =
			LoggerFactory.getLogger(DispararReembolsoService.class);

	private final ConsultaCaixinha consultaCaixinha;
	private final CobrancaRepository cobrancas;
	private final ParticipanteRepository participantes;
	private final ProvedorPagamento provedor;
	private final CancelamentoEmailSender sender;
	private final TransactionTemplate tx;

	DispararReembolsoService(
			ConsultaCaixinha consultaCaixinha,
			CobrancaRepository cobrancas,
			ParticipanteRepository participantes,
			ProvedorPagamento provedor,
			CancelamentoEmailSender sender,
			TransactionTemplate transactionTemplate) {
		this.consultaCaixinha = consultaCaixinha;
		this.cobrancas = cobrancas;
		this.participantes = participantes;
		this.provedor = provedor;
		this.sender = sender;
		this.tx = transactionTemplate;
	}

	@Override
	public void dispararReembolso(long caixinhaId) {
		ConsultaCaixinha.DadosCaixinha caixinha =
				consultaCaixinha
						.buscar(caixinhaId)
						.orElseThrow(
								() ->
										new IllegalStateException(
												"Caixinha " + caixinhaId + " não encontrada"));

		// Ids das cobranças `confirmada` desta Caixinha — só essas valem
		// estorno. Cobranças já em `estorno_solicitado`/`estornada` ficam
		// de fora (idempotência: não re-estorna).
		List<Long> cobrancaIds = new ArrayList<>();
		for (CobrancaEntity c :
				cobrancas.findByEstadoIn(List.of(EstadoCobranca.confirmada))) {
			if (c.getCaixinhaId() == caixinhaId) {
				cobrancaIds.add(c.getId());
			}
		}

		int estornadas = 0;
		int falhas = 0;
		Set<Long> participantesReembolsados = new HashSet<>();
		for (Long cobrancaId : cobrancaIds) {
			Long participanteEstornado = estornarUma(cobrancaId);
			if (participanteEstornado != null) {
				participantesReembolsados.add(participanteEstornado);
				estornadas++;
			} else {
				falhas++;
			}
		}
		log.info(
				"Reembolso disparado — caixinha {}: {} estorno(s) ok, {} falha(s)",
				caixinhaId,
				estornadas,
				falhas);

		notificarCancelamento(caixinhaId, caixinha.titulo(), participantesReembolsados);
	}

	/**
	 * Estorna UMA cobrança numa transação curta própria: marca
	 * {@code estorno_solicitado}, chama {@code estornar}; se o
	 * {@code estornar} falhar, a transação reverte a marca (a cobrança
	 * volta a {@code confirmada} para retry futuro).
	 *
	 * @return o {@code participanteId} se o estorno foi disparado com
	 *     sucesso; {@code null} em caso de falha.
	 */
	private Long estornarUma(long cobrancaEntityId) {
		try {
			return tx.execute(
					status -> {
						CobrancaEntity c =
								cobrancas.findById(cobrancaEntityId).orElseThrow();
						// Defesa: outra thread pode ter mudado o estado.
						if (c.getEstado() != EstadoCobranca.confirmada) {
							return c.getParticipanteId();
						}
						// Marca ANTES de chamar o PSP — se o `estornar` lançar,
						// esta transação reverte e a marca não persiste.
						c.transicionarPara(EstadoCobranca.estorno_solicitado);
						cobrancas.saveAndFlush(c);
						provedor.estornar(c.getCobrancaId());
						return c.getParticipanteId();
					});
		} catch (Exception e) {
			// Falha de um estorno não derruba o reembolso dos demais; a
			// transação curta já reverteu a marca → cobrança volta a
			// `confirmada`. Alerta operacional (NFR-2); a reconciliação
			// (Story 3.6) acompanha.
			log.error(
					"[REEMBOLSO] Falha ao estornar cobrança id={} — revertida para"
							+ " `confirmada`, será re-tentada: {}",
					cobrancaEntityId,
					e.getMessage(),
					e);
			return null;
		}
	}

	/** Agenda o aviso de cancelamento a todos os Participantes (tx própria). */
	private void notificarCancelamento(
			long caixinhaId, String titulo, Set<Long> reembolsados) {
		List<CancelamentoEmail> avisos = new ArrayList<>();
		for (ParticipanteEntity p :
				participantes.findByCaixinhaIdOrderByCriadoEmAsc(caixinhaId)) {
			if (p.getEmail() == null || p.getEmail().isBlank()) {
				continue;
			}
			avisos.add(
					new CancelamentoEmail(
							p.getEmail(), titulo, reembolsados.contains(p.getId())));
		}
		// Já estamos fora da transação do EncerrarPrazoUseCase (afterCommit);
		// envio direto, best-effort.
		avisos.forEach(this::tentarEnviar);
	}

	private void tentarEnviar(CancelamentoEmail e) {
		try {
			sender.enviarAvisoCancelamento(e);
		} catch (Exception ex) {
			log.error(
					"Falha no envio do aviso de cancelamento para {} (caixinha={}): {}",
					e.destinatario(),
					e.tituloCaixinha(),
					ex.getMessage(),
					ex);
		}
	}
}
