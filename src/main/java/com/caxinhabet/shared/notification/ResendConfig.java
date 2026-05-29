package com.caxinhabet.shared.notification;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClient;

/**
 * Wiring do cliente Resend (HTTP). Os beans existem sempre, mas só são
 * usados quando algum {@code *.sender=resend} ativa um adapter
 * {@code Resend*EmailSender}.
 */
@Configuration
@EnableConfigurationProperties(ResendProperties.class)
public class ResendConfig {

	@Bean("resendRestClient")
	RestClient resendRestClient(ResendProperties props) {
		RestClient.Builder b = RestClient.builder().baseUrl(props.baseUrl());
		if (props.apiKey() != null && !props.apiKey().isBlank()) {
			b.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + props.apiKey());
		}
		return b.build();
	}

	@Bean
	ResendEmailClient resendEmailClient(RestClient resendRestClient, ResendProperties props) {
		boolean configurado = props.apiKey() != null && !props.apiKey().isBlank();
		return new ResendEmailClient(resendRestClient, configurado);
	}
}
