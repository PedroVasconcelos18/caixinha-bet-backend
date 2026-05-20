package com.caxinhabet;

import static org.assertj.core.api.Assertions.assertThat;

import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Teste de integração ponta a ponta da fundação (AC-3): sobe um Postgres 17
 * real via Testcontainers, deixa o Flyway aplicar a migration baseline e
 * confirma que (1) o contexto sobe contra Postgres real, (2) o Flyway
 * registrou a baseline e (3) o Actuator health responde {@code UP} com o
 * componente {@code db} também {@code UP}.
 *
 * <p>Não depende de Postgres instalado no runner de CI — o container é
 * efêmero. A versão da imagem casa com o {@code docker-compose.yml} (17).
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PostgresIntegracaoTest {

	@Container
	static final PostgreSQLContainer<?> POSTGRES =
			new PostgreSQLContainer<>("postgres:17");

	@DynamicPropertySource
	static void datasourceProps(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
		registry.add("spring.datasource.username", POSTGRES::getUsername);
		registry.add("spring.datasource.password", POSTGRES::getPassword);
	}

	@Autowired private DataSource dataSource;
	@LocalServerPort private int port;

	@Test
	@DisplayName("Contexto sobe contra Postgres real e a conexão é válida")
	void contextoSobeComPostgresReal() throws Exception {
		assertThat(dataSource).isNotNull();
		try (var conn = dataSource.getConnection()) {
			assertThat(conn.isValid(2)).isTrue();
		}
	}

	@Test
	@DisplayName("Flyway aplicou a migration baseline V1 (schema versionado)")
	void flywayAplicouBaseline() throws Exception {
		try (var conn = dataSource.getConnection();
				var st = conn.createStatement();
				var rs =
						st.executeQuery(
								"SELECT COUNT(*) FROM flyway_schema_history WHERE version = '1' AND success = true")) {
			assertThat(rs.next()).isTrue();
			assertThat(rs.getInt(1)).as("migration V1 deve estar aplicada com sucesso").isEqualTo(1);
		}
	}

	@Test
	@DisplayName("GET /actuator/health responde UP com o componente db UP")
	void actuatorHealthUp() {
		RestTestClient client =
				RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
		client
				.get()
				.uri("/actuator/health")
				.exchange()
				.expectStatus()
				.isOk()
				.expectBody()
				.jsonPath("$.status")
				.isEqualTo("UP")
				.jsonPath("$.components.db.status")
				.isEqualTo("UP");
	}
}
