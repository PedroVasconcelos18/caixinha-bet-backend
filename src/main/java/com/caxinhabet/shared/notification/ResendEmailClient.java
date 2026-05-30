package com.caxinhabet.shared.notification;

import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

/**
 * Cliente HTTP compartilhado da API do Resend (POST {@code /emails}).
 *
 * <p>Alternativa ao SMTP: PaaS como Railway bloqueiam portas SMTP de saída
 * (25/465/587), causando {@code Connection timed out}. A API HTTP roda na
 * 443 (HTTPS), que nunca é bloqueada.
 *
 * <p>Bean único, reusado pelos adapters {@code Resend*EmailSender} dos três
 * módulos (auth, caixinha, pagamento) — mesma disciplina do envio
 * best-effort: o caller é quem decide tratar/logar a exceção. Aqui
 * propagamos {@link RuntimeException} em falha para o adapter logar (e NÃO
 * propagar ao use case, que dispara em {@code afterCommit}).
 */
public class ResendEmailClient {

	private final RestClient http;
	private final boolean configurado;

	public ResendEmailClient(RestClient resendRestClient, boolean configurado) {
		this.http = resendRestClient;
		this.configurado = configurado;
	}

	/**
	 * Envia e-mail com corpo HTML e alternativa texto puro (multipart). O
	 * {@code text} é o fallback obrigatório (deliverability + leitores texto);
	 * o {@code html} é o corpo renderizado. Lança {@link RuntimeException} se
	 * a API recusar ou a key não estiver configurada — o adapter chamador
	 * captura e loga (best-effort).
	 */
	public void enviar(String from, String to, String subject, String html, String text) {
		if (!configurado) {
			throw new IllegalStateException(
					"RESEND_API_KEY não configurada — não é possível enviar via Resend HTTP");
		}
		http.post()
				.uri("/emails")
				.contentType(MediaType.APPLICATION_JSON)
				.body(new EmailRequest(from, to, subject, html, text))
				.retrieve()
				.toBodilessEntity();
	}

	/**
	 * Atalho legado (só texto). Mantido para callers fora do escopo de HTML;
	 * delega com {@code html=null}.
	 */
	public void enviar(String from, String to, String subject, String text) {
		enviar(from, to, subject, null, text);
	}

	/** Payload da API do Resend. {@code to} aceita string única ou lista. */
	private record EmailRequest(String from, String to, String subject, String html, String text) {}
}
