package com.caxinhabet.pagamento.adapter.asaas;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * Wiring do adapter Asaas. Mora <b>dentro</b> de
 * {@code pagamento.adapter.asaas} (junto com o adapter e o webhook
 * controller) para não vazar a dependência do Asaas para o resto do
 * projeto — o {@link com.caxinhabet.arquitetura.ArquiteturaTest} faz cumprir.
 */
@Configuration
@EnableConfigurationProperties(AsaasProperties.class)
class AsaasConfig {

	/**
	 * {@link RestClient} pré-configurado para o Asaas: base URL (sandbox
	 * por default) + header {@code access_token} em toda chamada saindo. O
	 * {@code @Qualifier("asaasRestClient")} no adapter garante que o bean
	 * certo seja injetado mesmo que apareçam outros {@link RestClient}
	 * futuros.
	 */
	@Bean("asaasRestClient")
	RestClient asaasRestClient(AsaasProperties props) {
		RestClient.Builder b = RestClient.builder().baseUrl(props.baseUrl());
		if (props.accessToken() != null && !props.accessToken().isBlank()) {
			b.defaultHeader("access_token", props.accessToken());
		}
		return b.build();
	}
}
