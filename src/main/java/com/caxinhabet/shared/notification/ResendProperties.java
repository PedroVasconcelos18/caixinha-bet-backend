package com.caxinhabet.shared.notification;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuração do cliente HTTP do Resend (prefix {@code resend}).
 *
 * <p>Usado pelos adapters {@code Resend*EmailSender} quando o respectivo
 * {@code *.sender=resend}. Diferente do SMTP, fala com a API HTTP do
 * Resend (porta 443) — PaaS como Railway bloqueiam SMTP de saída
 * (25/465/587), então em produção o caminho é HTTP.
 *
 * @param apiKey API key gerada em resend.com/api-keys (env {@code RESEND_API_KEY}).
 * @param baseUrl base da API; default {@code https://api.resend.com}.
 */
@ConfigurationProperties(prefix = "resend")
public record ResendProperties(String apiKey, String baseUrl) {

	public ResendProperties {
		if (baseUrl == null || baseUrl.isBlank()) {
			baseUrl = "https://api.resend.com";
		}
	}
}
