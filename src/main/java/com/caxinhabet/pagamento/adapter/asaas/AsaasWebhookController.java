package com.caxinhabet.pagamento.adapter.asaas;

import com.caxinhabet.pagamento.adapter.persistence.PagamentoEventoEntity;
import com.caxinhabet.pagamento.adapter.persistence.PagamentoEventoRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Endpoint do webhook do Asaas (AR-10, Story 1.4 AC-3).
 *
 * <p><b>Fora do contrato público:</b> esta rota nunca aparece em OpenAPI/
 * documentação de API. Só o Asaas conhece a URL + o {@code asaas-access-token}
 * que o nosso servidor espera.
 *
 * <p><b>Segurança por construção:</b> a <b>primeira</b> coisa que o método
 * faz é verificar o header {@code asaas-access-token} contra
 * {@link AsaasProperties#webhookAuthToken()} usando comparação de tempo
 * constante ({@link MessageDigest#isEqual}) — sem isso, um atacante poderia
 * inferir o token por timing attack. Se falhar, lança 401 e <b>nada</b>
 * mais acontece (sem persistência, sem log de payload, sem efeito).
 *
 * <p>Story 1.4: validação + 200 sem corpo (Asaas considera processado).
 * Persistência do evento (Task 4) entra a seguir; efeito de domínio
 * (atualizar status de pagamento) é Épico 3.
 */
@RestController
class AsaasWebhookController {

	private static final Logger log = LoggerFactory.getLogger(AsaasWebhookController.class);

	private final AsaasProperties props;
	private final PagamentoEventoRepository eventos;

	AsaasWebhookController(AsaasProperties props, PagamentoEventoRepository eventos) {
		this.props = props;
		this.eventos = eventos;
	}

	@PostMapping("/webhooks/asaas")
	ResponseEntity<Void> receber(
			@RequestHeader(name = "asaas-access-token", required = false) String tokenRecebido,
			@RequestBody(required = false) Map<String, Object> payload) {

		// (1) Assinatura — falha aqui = ZERO mutação/persistência.
		verificarAssinaturaOu401(tokenRecebido);

		// (2) Persistência do evento ANTES de qualquer efeito (AR-5). Em
		// Story 1.4 "qualquer efeito" é zero (gate-only); a invariante já
		// fica no código para o Épico 3 herdar.
		String eventId = extractEventId(payload);
		if (eventId == null) {
			// Sem event_id não há como garantir idempotência — recusa.
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Payload sem 'id' (event_id).");
		}

		if (eventos.existsByEventId(eventId)) {
			// Idempotente: já processado, devolver 200 sem duplicar.
			log.info("Webhook Asaas duplicado ignorado (eventId={})", eventId);
			return ResponseEntity.ok().build();
		}

		try {
			eventos.save(
					new PagamentoEventoEntity(
							eventId, extractProviderTimestamp(payload), payload));
		} catch (DataIntegrityViolationException race) {
			// Corrida: outro thread inseriu o mesmo eventId entre o
			// existsBy e o save. UNIQUE index no DB é a defesa final;
			// tratamos como idempotência (200, sem propagar 500).
			log.info(
					"Webhook Asaas race condition resolvida por UNIQUE (eventId={})", eventId);
		}

		log.info(
				"Webhook Asaas persistido (event={}, paymentId={})",
				payload == null ? "null" : payload.get("event"),
				extractPaymentId(payload));
		return ResponseEntity.ok().build();
	}

	private static String extractEventId(Map<String, Object> payload) {
		if (payload == null) return null;
		Object id = payload.get("id");
		return id == null ? null : String.valueOf(id);
	}

	private static Instant extractProviderTimestamp(Map<String, Object> payload) {
		if (payload != null) {
			Object dt = payload.get("dateCreated");
			if (dt != null) {
				try {
					String s = String.valueOf(dt).replace(' ', 'T');
					if (!s.endsWith("Z") && !s.contains("+")) s = s + "Z";
					return Instant.parse(s);
				} catch (DateTimeParseException ignored) {
					// Falha-soft no parse → usa now() (gate-only; Épico 3 endurece).
				}
			}
		}
		return Instant.now();
	}

	/**
	 * Falha com 401 (mapeado para RFC 9457 pelo handler global da Story 1.3)
	 * se o token não bater. Comparação de tempo constante para evitar
	 * inferência por timing.
	 */
	private void verificarAssinaturaOu401(String tokenRecebido) {
		String esperado = props.webhookAuthToken();
		if (esperado == null || esperado.isBlank()) {
			// Boot sem ASAAS_WEBHOOK_AUTH_TOKEN configurado = recusa tudo.
			// Sem isso, deixaríamos o webhook aberto sem perceber.
			throw new ResponseStatusException(
					HttpStatus.UNAUTHORIZED, "Webhook não configurado (ASAAS_WEBHOOK_AUTH_TOKEN ausente).");
		}
		if (tokenRecebido == null) {
			throw new ResponseStatusException(
					HttpStatus.UNAUTHORIZED, "Header asaas-access-token ausente.");
		}
		byte[] a = esperado.getBytes(StandardCharsets.UTF_8);
		byte[] b = tokenRecebido.getBytes(StandardCharsets.UTF_8);
		if (!MessageDigest.isEqual(a, b)) {
			throw new ResponseStatusException(
					HttpStatus.UNAUTHORIZED, "Header asaas-access-token inválido.");
		}
	}

	private static String extractPaymentId(Map<String, Object> payload) {
		if (payload == null) return "null";
		Object p = payload.get("payment");
		if (p instanceof Map<?, ?> m) return String.valueOf(m.get("id"));
		return "null";
	}
}
