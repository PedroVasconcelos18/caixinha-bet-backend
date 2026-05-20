package com.caxinhabet.pagamento.adapter.asaas;

import static org.assertj.core.api.Assertions.assertThat;

import com.caxinhabet.pagamento.adapter.persistence.PagamentoEventoRepository;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Story 1.4 AC-3 + AC-4: webhook valida assinatura ANTES de persistir, e
 * persiste de forma idempotente (event_id UNIQUE). 4 cenários cobertos.
 *
 * <p>Cada teste checa a contagem real no Postgres (Testcontainers) — "sem
 * mutação" significa contagem == 0 no DB, não só "exceção lançada".
 */
@Testcontainers
@SpringBootTest(
		webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = {"asaas.webhook-auth-token=secret-de-teste-123"})
class AsaasWebhookControllerTest {

	@Container
	static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

	@DynamicPropertySource
	static void datasourceProps(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
		registry.add("spring.datasource.username", POSTGRES::getUsername);
		registry.add("spring.datasource.password", POSTGRES::getPassword);
	}

	@Autowired private PagamentoEventoRepository eventos;
	@LocalServerPort private int port;

	private RestClient http;

	@BeforeEach
	void setUp() {
		eventos.deleteAll();
		http = RestClient.builder().baseUrl("http://localhost:" + port).build();
	}

	@Test
	@DisplayName("Sem header asaas-access-token → 401 e ZERO persistência")
	void semHeader_401_semPersistir() {
		ResponseEntity<Void> resp = post(null, payloadOk("evt-1"));
		assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
		assertThat(eventos.count()).isZero();
	}

	@Test
	@DisplayName("Header inválido → 401 e ZERO persistência")
	void headerInvalido_401_semPersistir() {
		ResponseEntity<Void> resp = post("token-errado", payloadOk("evt-2"));
		assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
		assertThat(eventos.count()).isZero();
	}

	@Test
	@DisplayName("Header válido + payload inédito → 200 e 1 linha persistida")
	void headerValido_200_persiste() {
		ResponseEntity<Void> resp = post("secret-de-teste-123", payloadOk("evt-3"));
		assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(eventos.count()).isEqualTo(1L);
		assertThat(eventos.existsByEventId("evt-3")).isTrue();
	}

	@Test
	@DisplayName("Mesmo event_id duas vezes → 200 idempotente (continua 1 linha)")
	void mesmoEventIdDuasVezes_idempotente() {
		Map<String, Object> p = payloadOk("evt-4");
		ResponseEntity<Void> r1 = post("secret-de-teste-123", p);
		ResponseEntity<Void> r2 = post("secret-de-teste-123", p);
		assertThat(r1.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(r2.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(eventos.count()).as("UNIQUE event_id impede duplicata").isEqualTo(1L);
	}

	private ResponseEntity<Void> post(String token, Map<String, Object> body) {
		RestClient.RequestBodySpec req =
				http.post().uri("/webhooks/asaas").contentType(MediaType.APPLICATION_JSON);
		if (token != null) {
			req.header("asaas-access-token", token);
		}
		req.header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
		try {
			return req.body(body).retrieve().toBodilessEntity();
		} catch (org.springframework.web.client.HttpClientErrorException e) {
			return ResponseEntity.status(e.getStatusCode()).build();
		}
	}

	private static Map<String, Object> payloadOk(String eventId) {
		return Map.of(
				"id", eventId,
				"event", "PAYMENT_CONFIRMED",
				"dateCreated", "2026-05-19 12:00:00",
				"payment", Map.of("id", "cob-" + eventId, "value", "40.00"));
	}
}
