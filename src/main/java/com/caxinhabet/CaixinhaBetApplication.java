package com.caxinhabet;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Ponto de entrada da aplicação Caixinha Bet.
 *
 * <p>Reside na raiz do pacote {@code com.caxinhabet} para que o component scan
 * cubra todos os módulos de feature ({@code caixinha}, {@code participante},
 * {@code pagamento}, {@code apuracao}, {@code ledger}, {@code auth}) sem
 * configuração adicional — ver estrutura package-by-feature na architecture.
 */
@SpringBootApplication
public class CaixinhaBetApplication {

	public static void main(String[] args) {
		SpringApplication.run(CaixinhaBetApplication.class, args);
	}

}
