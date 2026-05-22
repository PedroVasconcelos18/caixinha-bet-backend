package com.caxinhabet.pagamento.adapter.asaas;

import com.caxinhabet.pagamento.app.ProcessarWebhookService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Endpoint do webhook do Asaas (AR-10).
 *
 * <p><b>Fora do contrato público:</b> esta rota nunca aparece em OpenAPI.
 * Só o Asaas conhece a URL + o {@code asaas-access-token} esperado.
 *
 * <p><b>Segurança por construção:</b> a <b>primeira</b> coisa que o método
 * faz é verificar o header {@code asaas-access-token} contra
 * {@link AsaasProperties#webhookAuthToken()} em tempo constante. Se
 * falhar, lança 401 e <b>nada</b> mais acontece.
 *
 * <p><b>Story 1.4 → 3.3:</b> a Story 1.4 deixou este controller
 * "gate-only" (verificava assinatura + persistia o evento, sem efeito de
 * domínio). A Story 3.3 cumpre a promessa: depois da verificação de
 * assinatura, delega ao {@link ProcessarWebhookService}, que aplica
 * idempotência, ordenação, transições e ledger (FR-8).
 *
 * <p>Responde sempre 200 quando a assinatura é válida — inclusive para
 * evento duplicado ou ignorado. Semântica idempotente: o Asaas não deve
 * re-tentar um evento que já foi aceito.
 */
@RestController
class AsaasWebhookController {

	private static final Logger log = LoggerFactory.getLogger(AsaasWebhookController.class);

	private final AsaasProperties props;
	private final ProcessarWebhookService processar;

	AsaasWebhookController(AsaasProperties props, ProcessarWebhookService processar) {
		this.props = props;
		this.processar = processar;
	}

	@PostMapping("/webhooks/asaas")
	ResponseEntity<Void> receber(
			@RequestHeader(name = "asaas-access-token", required = false) String tokenRecebido,
			@RequestBody(required = false) Map<String, Object> payload) {

		// (1) Assinatura — falha aqui = ZERO efeito/persistência.
		verificarAssinaturaOu401(tokenRecebido);

		if (payload == null || payload.get("id") == null) {
			throw new ResponseStatusException(
					HttpStatus.BAD_REQUEST, "Payload sem 'id' (event_id).");
		}

		// (2) Delega o processamento (idempotência, ordenação, efeito, ledger).
		processar.processar(payload);

		// 200 sempre que a assinatura é válida — idempotente.
		return ResponseEntity.ok().build();
	}

	/**
	 * Falha com 401 se o token não bater. Comparação de tempo constante
	 * para evitar inferência por timing.
	 */
	private void verificarAssinaturaOu401(String tokenRecebido) {
		String esperado = props.webhookAuthToken();
		if (esperado == null || esperado.isBlank()) {
			throw new ResponseStatusException(
					HttpStatus.UNAUTHORIZED,
					"Webhook não configurado (ASAAS_WEBHOOK_AUTH_TOKEN ausente).");
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
		log.debug("Webhook Asaas: assinatura verificada.");
	}
}
