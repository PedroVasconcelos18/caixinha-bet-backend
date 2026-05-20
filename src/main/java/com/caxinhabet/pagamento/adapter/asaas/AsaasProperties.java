package com.caxinhabet.pagamento.adapter.asaas;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuração do adapter Asaas — lida de {@code application.yml}/env.
 *
 * <p>Sandbox por default ({@code https://sandbox.asaas.com}); produção fica
 * a um env-var de distância (R-7 mitigação técnica: trocabilidade de
 * ambiente sem mexer em código). Story 1.4 instala; Story 1.5 valida o gate.
 *
 * @param baseUrl URL base do Asaas (sandbox vs. produção).
 * @param accessToken API key do Asaas (header {@code access_token} em
 *     chamadas saintes — separada do token de webhook).
 * @param webhookAuthToken shared secret que o Asaas envia no header
 *     {@code asaas-access-token} de cada webhook. Validado por igualdade
 *     constante-tempo no controller (Story 1.4 Task 3).
 */
@ConfigurationProperties(prefix = "asaas")
public record AsaasProperties(String baseUrl, String accessToken, String webhookAuthToken) {

	public AsaasProperties {
		if (baseUrl == null || baseUrl.isBlank()) {
			// API real do sandbox confirmada pelo Pedro em 2026-05-19 a
			// partir da chave de homologação dele: o host atende em
			// sandbox.asaas.com/api/v3 (forma documentada em vigor; o host
			// api-sandbox.asaas.com da minha pesquisa estava obsoleto).
			baseUrl = "https://sandbox.asaas.com/api/v3";
		}
	}
}
