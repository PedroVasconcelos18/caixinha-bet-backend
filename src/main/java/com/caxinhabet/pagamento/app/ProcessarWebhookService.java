package com.caxinhabet.pagamento.app;

import com.caxinhabet.caixinha.domain.ConsultaCaixinha;
import com.caxinhabet.caixinha.domain.ReavaliarFormacao;
import com.caxinhabet.ledger.app.LedgerService;
import com.caxinhabet.pagamento.adapter.persistence.CobrancaEntity;
import com.caxinhabet.pagamento.adapter.persistence.CobrancaRepository;
import com.caxinhabet.pagamento.adapter.persistence.PagamentoEventoEntity;
import com.caxinhabet.pagamento.adapter.persistence.PagamentoEventoRepository;
import com.caxinhabet.pagamento.domain.EstadoCobranca;
import com.caxinhabet.pagamento.domain.TipoEventoPagamento;
import com.caxinhabet.participante.adapter.persistence.ParticipanteEntity;
import com.caxinhabet.participante.adapter.persistence.ParticipanteRepository;
import com.caxinhabet.participante.domain.StatusParticipante;
import com.caxinhabet.shared.money.Money;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Processa um evento de webhook do Provedor (Story 3.3 v5, FR-8).
 *
 * <p>Garante, numa única {@code @Transactional}:
 * <ol>
 *   <li><b>Idempotência</b> — evento com {@code event_id} já processado
 *       é no-op (AC-2).
 *   <li><b>Persistência antes do efeito</b> — {@code PagamentoEventoEntity}
 *       é gravado antes de qualquer mutação de domínio (AR-5, AC-1).
 *   <li><b>Ordenação</b> — o efeito só é aplicado se este evento tem o
 *       maior {@code provider_timestamp} entre os da mesma cobrança;
 *       eventos "do passado" são persistidos mas não aplicados (AC-3).
 *   <li><b>Efeito</b> — confirmação/expiração/estorno transicionam a
 *       {@code CobrancaEntity} + {@code ParticipanteEntity} e registram
 *       o ledger (AC-4..AC-6).
 *   <li><b>Reavaliação de Formação</b> — confirmação e estorno disparam
 *       {@link ReavaliarFormacao} (FR-9 — Story 3.4).
 * </ol>
 *
 * <p>A verificação de assinatura do webhook é feita ANTES, no
 * {@code AsaasWebhookController} — este serviço assume o payload já
 * autêntico.
 */
@Service
public class ProcessarWebhookService {

	private static final Logger log =
			LoggerFactory.getLogger(ProcessarWebhookService.class);

	private final PagamentoEventoRepository eventos;
	private final CobrancaRepository cobrancas;
	private final ParticipanteRepository participantes;
	private final LedgerService ledger;
	private final ReavaliarFormacao reavaliarFormacao;
	private final ConsultaCaixinha consultaCaixinha;

	public ProcessarWebhookService(
			PagamentoEventoRepository eventos,
			CobrancaRepository cobrancas,
			ParticipanteRepository participantes,
			LedgerService ledger,
			ReavaliarFormacao reavaliarFormacao,
			ConsultaCaixinha consultaCaixinha) {
		this.eventos = eventos;
		this.cobrancas = cobrancas;
		this.participantes = participantes;
		this.ledger = ledger;
		this.reavaliarFormacao = reavaliarFormacao;
		this.consultaCaixinha = consultaCaixinha;
	}

	/**
	 * Processa o payload de um webhook já autenticado.
	 *
	 * @param payload corpo do webhook (estrutura Asaas: {@code id},
	 *     {@code event}, {@code dateCreated}, {@code payment.id}, ...).
	 */
	@Transactional
	public void processar(Map<String, Object> payload) {
		String eventId = str(payload.get("id"));
		if (eventId == null) {
			// Sem event_id não há como garantir idempotência — recusa.
			throw new IllegalArgumentException("Webhook sem 'id' (event_id).");
		}

		// (1) Idempotência: já processado? no-op.
		if (eventos.existsByEventId(eventId)) {
			log.info("Webhook duplicado ignorado (eventId={})", eventId);
			return;
		}

		String cobrancaId = extrairCobrancaId(payload);
		Instant providerTs = extrairProviderTimestamp(payload);
		TipoEventoPagamento tipo =
				TipoEventoPagamento.doEventoAsaas(str(payload.get("event")));

		// (2) Ordenação: o efeito só vale se este é o evento mais recente
		// da cobrança. Captura ANTES de persistir o evento atual.
		boolean maisRecente = ehMaisRecente(cobrancaId, providerTs);

		// (3) Persiste o evento ANTES de qualquer efeito (AR-5).
		try {
			eventos.save(
					new PagamentoEventoEntity(eventId, providerTs, payload, cobrancaId));
		} catch (DataIntegrityViolationException corrida) {
			// Corrida no UNIQUE event_id: outro thread inseriu primeiro.
			// Idempotente — trata como já processado.
			log.info("Corrida no event_id resolvida por UNIQUE (eventId={})", eventId);
			return;
		}

		// (4) Aplica o efeito — só se for o evento mais recente da cobrança
		// e o tipo for relevante.
		if (cobrancaId == null || tipo == TipoEventoPagamento.IGNORADO) {
			log.info(
					"Evento {} sem efeito (cobrancaId={}, tipo={})",
					eventId,
					cobrancaId,
					tipo);
			return;
		}
		if (!maisRecente) {
			log.warn(
					"Evento {} (cobranca={}) é mais antigo que o último aplicado —"
							+ " persistido para auditoria, efeito IGNORADO (NFR-2 ordenação).",
					eventId,
					cobrancaId);
			return;
		}

		aplicarEfeito(tipo, cobrancaId, eventId);
	}

	private void aplicarEfeito(
			TipoEventoPagamento tipo, String cobrancaId, String eventId) {
		CobrancaEntity cobranca =
				cobrancas.findByCobrancaId(cobrancaId).orElse(null);
		if (cobranca == null) {
			// Cobrança desconhecida — pode ser de outro ambiente / cobrança de
			// teste do gate. Persistimos o evento (auditoria) mas não há o que
			// transicionar.
			log.warn("Evento {} para cobrança desconhecida {}", eventId, cobrancaId);
			return;
		}
		ParticipanteEntity participante =
				participantes.findById(cobranca.getParticipanteId()).orElse(null);
		if (participante == null) {
			log.warn("Cobrança {} sem Participante {}", cobrancaId, cobranca.getParticipanteId());
			return;
		}

		switch (tipo) {
			case CONFIRMADO -> aplicarConfirmacao(cobranca, participante, eventId);
			case EXPIRADO -> aplicarExpiracao(cobranca, participante);
			case ESTORNADO -> aplicarEstorno(cobranca, participante, eventId);
			case IGNORADO -> {
				/* já tratado antes */
			}
		}
	}

	private void aplicarConfirmacao(
			CobrancaEntity cobranca, ParticipanteEntity participante, String eventId) {
		// GUARDA (fix Épico 3 #1/#2): confirmação só age sobre cobrança `ativa`.
		// Aceitar outros estados (invalidada/expirada/estornada/confirmada)
		// duplicaria custódia ou re-confirmaria um estorno:
		//  - `invalidada`: o usuário pagou uma cobrança antiga já substituída
		//    — não pode entrar como custódia válida (haveria 2 custódias p/ o
		//    mesmo Participante).
		//  - `estornada`: empate de provider_timestamp poderia re-aplicar a
		//    confirmação sobre uma cobrança já estornada.
		//  - `confirmada`: idempotência — já processada.
		// Em qualquer um desses casos: registra (auditoria via evento) e sai.
		if (cobranca.getEstado() != EstadoCobranca.ativa) {
			log.warn(
					"Evento {} CONFIRMADO ignorado: cobrança {} está {} (esperado `ativa`)."
							+ " Sem efeito de custódia — investigar se necessário.",
					eventId,
					cobranca.getCobrancaId(),
					cobranca.getEstado());
			return;
		}
		// LOCK DE APURAÇÃO (fix Épico 3 #4): se a Caixinha já foi apurada, o
		// conjunto `pago` está congelado — uma confirmação atrasada NÃO pode
		// mais entrar no rateio. Registra como exceção, não aplica.
		if (caixinhaApuradaOuAlem(cobranca.getCaixinhaId())) {
			log.error(
					"[LOCK-APURACAO] Evento {} CONFIRMADO chegou após a apuração da"
							+ " Caixinha {} — cobrança {} NÃO entra no rateio. Exceção"
							+ " pós-apuração registrada, sem custódia.",
					eventId,
					cobranca.getCaixinhaId(),
					cobranca.getCobrancaId());
			return;
		}
		cobranca.transicionarPara(EstadoCobranca.confirmada);
		// Participante → pago (só sobe de pagamento_iniciado/aceito; não regride).
		if (participante.getStatus() == StatusParticipante.pagamento_iniciado
				|| participante.getStatus() == StatusParticipante.aceito) {
			participante.setStatus(StatusParticipante.pago);
		}
		// FLUSH explícito (fix code review Épico 3, 2026-05-21): o
		// `reavaliarFormacao` abaixo faz `count(status=pago)`. Sem este
		// flush, o `setStatus(pago)` recém-feito ainda é dirty no contexto
		// de persistência e o COUNT pode NÃO incluir este Participante —
		// a Caixinha não formaria no evento que deveria formá-la. O flush
		// garante que o estado `pago` está visível à query da formação.
		participantes.saveAndFlush(participante);

		Money valor = Money.ofCentavos(cobranca.getValorCentavos());
		ledger.registrarCustodia(
				cobranca.getCaixinhaId(),
				participante.getId(),
				cobranca.getCobrancaId(),
				valor,
				eventId);
		// FR-9: pagamento confirmado pode formar a Caixinha (Story 3.4).
		reavaliarFormacao.reavaliar(cobranca.getCaixinhaId());
	}

	private void aplicarExpiracao(
			CobrancaEntity cobranca, ParticipanteEntity participante) {
		// Só expira cobrança que ainda está ativa; se já foi confirmada/
		// estornada/invalidada, ordenação/timestamp já decidiu — não regride.
		if (cobranca.getEstado() != EstadoCobranca.ativa) {
			return;
		}
		cobranca.transicionarPara(EstadoCobranca.expirada);
		if (participante.getStatus() == StatusParticipante.pagamento_iniciado) {
			participante.setStatus(StatusParticipante.aceito);
		}
	}

	private void aplicarEstorno(
			CobrancaEntity cobranca, ParticipanteEntity participante, String eventId) {
		// Estorno só faz sentido sobre cobrança confirmada (fix #1/#2: nunca
		// sobre ativa/invalidada/expirada/estornada).
		if (cobranca.getEstado() != EstadoCobranca.confirmada) {
			return;
		}
		cobranca.transicionarPara(EstadoCobranca.estornada);
		// Rebobina: pago → aceito.
		if (participante.getStatus() == StatusParticipante.pago) {
			participante.setStatus(StatusParticipante.aceito);
		}
		Money valor = Money.ofCentavos(cobranca.getValorCentavos());
		ledger.registrarEstorno(
				cobranca.getCaixinhaId(),
				participante.getId(),
				cobranca.getCobrancaId(),
				valor,
				eventId);
		// FR-9: estorno pode reverter Formação (Story 3.4).
		reavaliarFormacao.reavaliar(cobranca.getCaixinhaId());
	}

	/**
	 * {@code true} se {@code providerTs} é maior (ou igual, na ausência de
	 * histórico) que o maior {@code provider_timestamp} já registrado para
	 * a cobrança. Chamado ANTES de persistir o evento atual.
	 */
	private boolean ehMaisRecente(String cobrancaId, Instant providerTs) {
		if (cobrancaId == null) {
			return true; // sem cobrança, ordenação não se aplica
		}
		return eventos
				.findTopByCobrancaIdOrderByProviderTimestampDesc(cobrancaId)
				.map(ultimo -> !providerTs.isBefore(ultimo.getProviderTimestamp()))
				.orElse(true);
	}

	private static String extrairCobrancaId(Map<String, Object> payload) {
		Object payment = payload.get("payment");
		if (payment instanceof Map<?, ?> m) {
			return str(m.get("id"));
		}
		return null;
	}

	/** Tolerância de relógio: timestamp do Provedor não pode estar muito à frente. */
	private static final java.time.Duration MARGEM_FUTURO =
			java.time.Duration.ofHours(1);

	/**
	 * Extrai o {@code provider_timestamp} do payload (campo {@code dateCreated}
	 * do Asaas).
	 *
	 * <p><b>NÃO faz falha-soft para {@code now()}.</b> O timestamp é o eixo
	 * da ordenação financeira (FR-8/NFR-2) — inventar {@code now()} para um
	 * evento com parse falho o tornaria artificialmente "o mais recente" e
	 * aplicaria efeito fora de ordem. Parse falho → lança; o Asaas re-tenta
	 * e o problema fica visível.
	 *
	 * <p><b>Formatos aceitos (fix code review Épico 3, 2026-05-21):</b>
	 * <ul>
	 *   <li>{@code "2024-06-12 16:45:03"} — sem fuso; assume UTC.
	 *   <li>{@code "2024-06-12T16:45:03Z"} — UTC explícito.
	 *   <li>{@code "2024-06-12T16:45:03-03:00"} — com offset; respeitado.
	 *       (O código anterior anexava {@code Z} ao final de uma string com
	 *       offset → {@code ...-03:00Z} inválido → webhook em loop de 500.)
	 * </ul>
	 *
	 * <p><b>Rejeita timestamp no futuro</b> além de {@link #MARGEM_FUTURO}:
	 * um evento com {@code dateCreated} futuro (skew de relógio, replay)
	 * viraria permanentemente "o mais recente" e bloquearia todos os
	 * eventos legítimos seguintes daquela cobrança.
	 *
	 * @throws IllegalArgumentException se {@code dateCreated} ausente,
	 *     inválido, ou no futuro além da margem.
	 */
	private static Instant extrairProviderTimestamp(Map<String, Object> payload) {
		Object dt = payload.get("dateCreated");
		if (dt == null) {
			throw new IllegalArgumentException(
					"Webhook sem 'dateCreated' — provider_timestamp é obrigatório"
							+ " para a ordenação de eventos (FR-8).");
		}
		Instant ts = parsear(String.valueOf(dt));
		if (ts.isAfter(Instant.now().plus(MARGEM_FUTURO))) {
			throw new IllegalArgumentException(
					"Webhook com 'dateCreated' no futuro: '" + dt + "' — recusado"
							+ " (corromperia a ordenação de eventos).");
		}
		return ts;
	}

	private static Instant parsear(String raw) {
		String s = raw.trim().replace(' ', 'T');
		try {
			// Já tem fuso explícito (termina em Z, ou tem offset +hh:mm /
			// -hh:mm na parte de hora)? Parse direto preservando o offset.
			if (temFusoExplicito(s)) {
				return java.time.OffsetDateTime.parse(s).toInstant();
			}
			// Sem fuso → Asaas usa horário sem TZ; assume UTC.
			return java.time.LocalDateTime.parse(s)
					.toInstant(java.time.ZoneOffset.UTC);
		} catch (DateTimeParseException e) {
			throw new IllegalArgumentException(
					"Webhook com 'dateCreated' inválido: '" + raw + "'", e);
		}
	}

	/**
	 * {@code true} se a string ISO tem fuso explícito — termina em {@code Z}
	 * ou tem {@code +hh:mm}/{@code -hh:mm} APÓS a parte de tempo (o índice
	 * 10 separa data de hora; offset só conta a partir daí, para não
	 * confundir com os hífens da data {@code yyyy-MM-dd}).
	 */
	private static boolean temFusoExplicito(String iso) {
		if (iso.endsWith("Z")) {
			return true;
		}
		int inicioHora = iso.indexOf('T');
		if (inicioHora < 0) {
			return false;
		}
		String parteHora = iso.substring(inicioHora);
		return parteHora.contains("+") || parteHora.contains("-");
	}

	/**
	 * {@code true} se a Caixinha já foi apurada (ou está em estado posterior
	 * — {@code repasse_parcial}/{@code repassada}). Lock de apuração (FR-9):
	 * o conjunto {@code pago} está congelado, eventos de pagamento atrasados
	 * não entram mais no rateio.
	 */
	private boolean caixinhaApuradaOuAlem(long caixinhaId) {
		return consultaCaixinha
				.buscar(caixinhaId)
				.map(
						c ->
								switch (c.estado()) {
									case apurada, repasse_parcial, repassada -> true;
									default -> false;
								})
				.orElse(false);
	}

	private static String str(Object o) {
		return o == null ? null : String.valueOf(o);
	}
}
