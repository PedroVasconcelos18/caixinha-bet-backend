package com.caxinhabet.shared.naming;

import static org.assertj.core.api.Assertions.assertThat;

import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Guardrail da naming strategy snake_case ↔ camelCase (Story 1.3 AC-4).
 *
 * <p>A {@code CamelCaseToUnderscoresNamingStrategy} já está configurada no
 * {@code application.yml} desde a Story 1.1. Este teste garante que ela
 * <b>continua</b> funcionando: se alguém remover/trocar a config no futuro,
 * a coluna {@code nomeUsuarioCompleto} deixará de virar
 * {@code nome_usuario_completo} e este teste falha alto, em vez de o erro
 * só aparecer quando alguém modelar uma entidade real do domínio.
 *
 * <p>A entidade {@link NamingProbe} existe SÓ no escopo de teste — não
 * polui o domínio nem participa do schema versionado (Hibernate gera essa
 * tabela só aqui via {@code create-drop}; o Postgres é efêmero do
 * Testcontainers).
 */
@Testcontainers
@SpringBootTest(
		properties = {
			// Esta probe usa o Hibernate para criar SUA própria tabela (não
			// vai pelo Flyway). Desliga o validate global por este teste só.
			"spring.jpa.hibernate.ddl-auto=create-drop",
			"spring.flyway.enabled=false"
		})
@EntityScan(
		basePackages = {
			"caixinhabet.testfixtures.naming", // NamingProbe (probe deste teste)
			"com.caxinhabet.pagamento.adapter.persistence", // entidades reais (Story 1.4+)
			"com.caxinhabet.auth.adapter.persistence", // Story 2.1+
			"com.caxinhabet.caixinha.adapter.persistence", // Story 2.2+
			"com.caxinhabet.participante.adapter.persistence", // Story 2.2+
			"com.caxinhabet.ledger.adapter.persistence" // Story 3.3+
		})
class NamingStrategyTest {

	@Container
	static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

	@DynamicPropertySource
	static void datasourceProps(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
		registry.add("spring.datasource.username", POSTGRES::getUsername);
		registry.add("spring.datasource.password", POSTGRES::getPassword);
	}

	@Autowired private DataSource dataSource;

	@Test
	@DisplayName("camelCase do domínio vira snake_case no Postgres (CamelCaseToUnderscoresNamingStrategy)")
	void domainCamelCaseVirarSnakeCase() throws Exception {
		try (var conn = dataSource.getConnection();
				var st = conn.createStatement();
				var rs =
						st.executeQuery(
								"SELECT column_name FROM information_schema.columns "
										+ "WHERE table_name = 'naming_probe' ORDER BY column_name")) {
			java.util.List<String> colunas = new java.util.ArrayList<>();
			while (rs.next()) {
				colunas.add(rs.getString(1));
			}
			// A entidade tem 'id' e 'nomeUsuarioCompleto'. O guardrail é:
			//  - existe coluna 'nome_usuario_completo' (snake_case)
			//  - NÃO existe 'nomeUsuarioCompleto' (camelCase no banco = falha
			//    de naming strategy)
			assertThat(colunas)
					.as("naming strategy deve gerar snake_case no Postgres (AR-8)")
					.contains("id", "nome_usuario_completo")
					.doesNotContain("nomeUsuarioCompleto", "nomeusuariocompleto");
		}
	}

}
