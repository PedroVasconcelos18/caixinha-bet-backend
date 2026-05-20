package com.caxinhabet.auth.app;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Propriedades do app (prefix {@code app} no application.yml, Story 2.1).
 *
 * <p>Hoje contém apenas o {@code publicBaseUrl} (URL do front).
 * Consumido por {@code auth} (Story 2.1 — magic link) e
 * {@code caixinha} (Story 2.4 — link do convite). Ainda vive aqui;
 * promover para {@code shared/config} se um 3º módulo passar a usar.
 */
@Component
@ConfigurationProperties(prefix = "app")
public class AppProperties {

	/**
	 * URL pública do FRONT — ex.: {@code http://localhost:3000} em dev,
	 * domínio HTTPS em produção. O magic link envia para
	 * {@code ${publicBaseUrl}/auth/callback?token=...}.
	 */
	private String publicBaseUrl = "http://localhost:3000";

	public String getPublicBaseUrl() {
		return publicBaseUrl;
	}

	public void setPublicBaseUrl(String publicBaseUrl) {
		this.publicBaseUrl = publicBaseUrl;
	}
}
