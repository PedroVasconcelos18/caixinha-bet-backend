package com.caxinhabet.pagamento.app;

import com.caxinhabet.auth.app.AppProperties;
import com.caxinhabet.caixinha.domain.ApuracaoInvalidaException;
import com.caxinhabet.caixinha.domain.Caixinha;
import com.caxinhabet.caixinha.domain.ConsultaCaixinha;
import com.caxinhabet.caixinha.domain.PrepararRepasse;
import com.caxinhabet.ledger.app.LedgerService;
import com.caxinhabet.pagamento.adapter.persistence.CobrancaEntity;
import com.caxinhabet.pagamento.adapter.persistence.CobrancaRepository;
import com.caxinhabet.pagamento.adapter.persistence.PayoutEntity;
import com.caxinhabet.pagamento.adapter.persistence.PayoutRepository;
import com.caxinhabet.pagamento.domain.EstadoCobranca;
import com.caxinhabet.pagamento.domain.PremioGanhoEmail;
import com.caxinhabet.pagamento.domain.PremioGanhoEmailSender;
import com.caxinhabet.participante.adapter.persistence.ParticipanteEntity;
import com.caxinhabet.participante.adapter.persistence.ParticipanteRepository;
import com.caxinhabet.participante.domain.StatusParticipante;
import com.caxinhabet.shared.money.Money;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Prepara o Repasse do prêmio após a apuração (Épico 4 v5, Story 4.3,
 * FR-13). Implementação da porta {@link PrepararRepasse} — chamada pelo
 * {@code ApurarCaixinhaUseCase} (Story 4.2) quando há ≥ 1 Ganhador.
 *
 * <p>O que faz, na transação da apuração:
 * <ol>
 *   <li>Calcula o Prêmio = Σ ingressos {@code pago} − Taxa de Serviço.
 *   <li>Divide igualmente entre os Ganhadores; o <b>resíduo de centavos</b>
 *       vai ao primeiro Ganhador por <b>ordem de pagamento</b> (instante
 *       de confirmação da cobrança — PRD §11, OQ-2 fechada).
 *   <li>Persiste um {@link PayoutEntity} por Ganhador ({@code payout_id}
 *       único — idempotência do PIX na Story 4.6).
 *   <li>Registra a reserva no ledger (custódia → reservado).
 *   <li>Notifica cada Ganhador por e-mail ({@code afterCommit}).
 * </ol>
 *
 * <p>A transição {@code apurada → repasse_parcial} da Caixinha é feita
 * pelo {@code ApurarCaixinhaUseCase} (módulo {@code caixinha}, dono do
 * estado) logo após esta chamada — não aqui (isolamento modular).
 *
 * <p><b>NÃO dispara PIX</b> — o disparo é a Story 4.6, após o aceite do
 * Ganhador.
 */
@Service
class PrepararRepasseService implements PrepararRepasse {

	private static final Logger log =
			LoggerFactory.getLogger(PrepararRepasseService.class);

	private final ConsultaCaixinha consultaCaixinha;
	private final ParticipanteRepository participantes;
	private final CobrancaRepository cobrancas;
	private final PayoutRepository payouts;
	private final LedgerService ledger;
	private final PremioGanhoEmailSender sender;
	private final AppProperties appProps;

	PrepararRepasseService(
			ConsultaCaixinha consultaCaixinha,
			ParticipanteRepository participantes,
			CobrancaRepository cobrancas,
			PayoutRepository payouts,
			LedgerService ledger,
			PremioGanhoEmailSender sender,
			AppProperties appProps) {
		this.consultaCaixinha = consultaCaixinha;
		this.participantes = participantes;
		this.cobrancas = cobrancas;
		this.payouts = payouts;
		this.ledger = ledger;
		this.sender = sender;
		this.appProps = appProps;
	}

	@Override
	public void prepararRepasse(
			long caixinhaId, List<Long> ganhadoresParticipanteIds) {
		if (ganhadoresParticipanteIds == null || ganhadoresParticipanteIds.isEmpty()) {
			log.warn("prepararRepasse chamado sem Ganhadores — caixinha {}", caixinhaId);
			return;
		}

		ConsultaCaixinha.DadosCaixinha caixinha =
				consultaCaixinha
						.buscar(caixinhaId)
						.orElseThrow(
								() ->
										new IllegalStateException(
												"Caixinha " + caixinhaId + " não encontrada"));

		// Σ dos ingressos `pago` (todos pagam o mesmo valor — FR-1).
		long totalPagos =
				participantes.countByCaixinhaIdAndStatusIn(
						caixinhaId, List.of(StatusParticipante.pago));
		Money soma =
				Money.ofCentavos(caixinha.valorIngressoCentavos()).times((int) totalPagos);
		Money premio = soma.minus(Caixinha.TAXA_SERVICO);

		// Ordena os Ganhadores por ordem de pagamento (confirmação da
		// cobrança). O primeiro recebe o resíduo de centavos.
		List<Long> ordenados = ordenarPorPagamento(caixinhaId, ganhadoresParticipanteIds);

		List<ValorGanhador> valores = dividir(premio, ordenados);

		// Cria os Payouts + reserva no ledger.
		for (ValorGanhador v : valores) {
			PayoutEntity payout =
					new PayoutEntity(caixinhaId, v.participanteId(), v.valor().centavos());
			payouts.save(payout);
			ledger.registrarReservaPremio(
					caixinhaId,
					v.participanteId(),
					v.valor(),
					payout.getPayoutId().toString());
		}

		// Notifica cada Ganhador (afterCommit — best-effort).
		String link = appProps.getPublicBaseUrl() + "/caixinhas/" + caixinhaId;
		List<PremioGanhoEmail> avisos = new ArrayList<>(valores.size());
		for (ValorGanhador v : valores) {
			participantes
					.findById(v.participanteId())
					.map(ParticipanteEntity::getEmail)
					.filter(e -> e != null && !e.isBlank())
					.ifPresent(
							email ->
									avisos.add(
											new PremioGanhoEmail(
													email,
													caixinha.titulo(),
													v.valor().toString(),
													link)));
		}
		agendarNotificacoes(avisos);

		log.info(
				"Repasse preparado — caixinha {}, Prêmio R$ {}, {} Payout(s)",
				caixinhaId,
				premio,
				valores.size());
	}

	/**
	 * Ordena os ids de Ganhador pela ordem de pagamento — instante de
	 * {@code confirmada_em} da cobrança {@code confirmada} do Participante.
	 * Quem confirmou primeiro vem primeiro (recebe o resíduo de centavos).
	 * Ganhador sem cobrança confirmada (não deveria ocorrer) vai ao fim.
	 */
	private List<Long> ordenarPorPagamento(long caixinhaId, List<Long> ids) {
		Map<Long, Instant> confirmadaEmPorParticipante = new HashMap<>();
		for (CobrancaEntity c :
				cobrancas.findByEstadoIn(List.of(EstadoCobranca.confirmada))) {
			if (c.getCaixinhaId() == caixinhaId && c.getConfirmadaEm() != null) {
				confirmadaEmPorParticipante.merge(
						c.getParticipanteId(),
						c.getConfirmadaEm(),
						(a, b) -> a.isBefore(b) ? a : b);
			}
		}
		List<Long> ordenados = new ArrayList<>(ids);
		ordenados.sort(
				Comparator.comparing(
						pid ->
								confirmadaEmPorParticipante.getOrDefault(pid, Instant.MAX)));
		return ordenados;
	}

	/**
	 * Divide o Prêmio igualmente entre os Ganhadores; o resíduo de centavos
	 * vai ao primeiro (já ordenado por ordem de pagamento).
	 *
	 * <p>Usa {@link Money#divideWithRemainder} — a API monetária canônica
	 * do projeto (NFR-1/AR-8), com a invariante
	 * {@code quotient.times(n).plus(remainder) == premio} garantida.
	 *
	 * <p><b>Fix code review Épico 4:</b> se o quociente base for R$ 0,00
	 * (Prêmio pequeno demais para o nº de Ganhadores — ex.: 3 centavos ÷ 3),
	 * algum Payout teria valor zero e violaria o {@code CHECK
	 * (valor_centavos > 0)} da migration V17, travando a apuração. Falhamos
	 * cedo, com {@link ApuracaoInvalidaException} (422) e mensagem clara —
	 * em vez de deixar a constraint estourar e travar a Caixinha.
	 */
	private List<ValorGanhador> dividir(Money premio, List<Long> ganhadoresOrdenados) {
		int n = ganhadoresOrdenados.size();
		Money.SplitResult split = premio.divideWithRemainder(n);
		Money base = split.quotient();
		Money residuo = split.remainder();

		if (base.centavos() <= 0) {
			throw new ApuracaoInvalidaException(
					"Prêmio de R$ "
							+ premio
							+ " é pequeno demais para ser dividido entre "
							+ n
							+ " Ganhador(es) — cada um receberia menos de R$ 0,01.");
		}

		List<ValorGanhador> valores = new ArrayList<>(n);
		for (int i = 0; i < n; i++) {
			// O 1º Ganhador (índice 0) acumula o resíduo de centavos.
			Money valor = (i == 0) ? base.plus(residuo) : base;
			valores.add(new ValorGanhador(ganhadoresOrdenados.get(i), valor));
		}
		return valores;
	}

	private void agendarNotificacoes(List<PremioGanhoEmail> avisos) {
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
						avisos.forEach(PrepararRepasseService.this::tentarEnviar);
					}
				});
	}

	private void tentarEnviar(PremioGanhoEmail e) {
		try {
			sender.enviarAvisoPremio(e);
		} catch (Exception ex) {
			log.error(
					"Falha no envio do aviso de prêmio para {} (caixinha={}): {}",
					e.destinatario(),
					e.tituloCaixinha(),
					ex.getMessage(),
					ex);
		}
	}

	private record ValorGanhador(long participanteId, Money valor) {}
}
