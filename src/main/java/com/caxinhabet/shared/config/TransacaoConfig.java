package com.caxinhabet.shared.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Expõe um {@link TransactionTemplate} como bean (infra transversal).
 *
 * <p>O Spring Boot auto-configura o {@link PlatformTransactionManager},
 * mas NÃO o {@code TransactionTemplate}. Ele é necessário onde a
 * disciplina de transação precisa ser <b>explícita e fragmentada</b> —
 * ex.: o {@code AceitarPremioUseCase} (Story 4.6) divide o aceite do
 * prêmio em fases de transação separadas para que o disparo do PIX
 * (chamada de rede) nunca aconteça dentro de uma transação que possa
 * reverter depois dele.
 */
@Configuration
public class TransacaoConfig {

	@Bean
	TransactionTemplate transactionTemplate(PlatformTransactionManager tm) {
		return new TransactionTemplate(tm);
	}
}
