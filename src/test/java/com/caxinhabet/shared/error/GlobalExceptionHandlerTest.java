package com.caxinhabet.shared.error;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Contrato de erro/sucesso (Story 1.3 AC-3) — valida o
 * {@link GlobalExceptionHandler} com MockMvc <b>standalone</b> (sem subir
 * contexto Spring completo).
 *
 * <p>Por que standalone: o Boot 4 modularizou as autoconfigs, e a fatia
 * mínima necessária para validar o contrato de erro/sucesso (controller +
 * @ControllerAdvice + Jackson converters) sobe sem fricção via
 * {@link MockMvcBuilders#standaloneSetup} e fica isolada de Security,
 * DataSource, JPA, Flyway etc. (que não têm nada a ver com este AC).
 *
 * <p>Stub controllers {@code /__test/sucesso} e {@code /__test/erro} são
 * só de teste. NÃO há rota pública do projeto até o Épico 2.
 */
class GlobalExceptionHandlerTest {

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		mockMvc =
				MockMvcBuilders.standaloneSetup(new TestStubController())
						.setControllerAdvice(new GlobalExceptionHandler())
						.build();
	}

	@Test
	@DisplayName("GET /__test/sucesso retorna corpo direto sem envelope")
	void sucessoCorpoDireto() throws Exception {
		mockMvc
				.perform(get("/__test/sucesso").accept(MediaType.APPLICATION_JSON))
				.andExpect(status().isOk())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				// Corpo direto: {"hello":"world"} sem wrapper "data"/"result"/"payload"
				.andExpect(jsonPath("$.hello").value("world"))
				.andExpect(jsonPath("$.data").doesNotExist())
				.andExpect(jsonPath("$.result").doesNotExist())
				.andExpect(jsonPath("$.payload").doesNotExist());
	}

	@Test
	@DisplayName("GET /__test/erro retorna RFC 9457 application/problem+json")
	void erroRfc9457() throws Exception {
		mockMvc
				.perform(
						get("/__test/erro")
								.accept(MediaType.APPLICATION_JSON, MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(status().isInternalServerError())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				// RFC 9457: type, title, status, detail (instance opcional)
				.andExpect(jsonPath("$.type").exists())
				.andExpect(jsonPath("$.title").exists())
				.andExpect(jsonPath("$.status").value(500))
				.andExpect(jsonPath("$.detail").exists())
				// NÃO expor stack trace nem campos do BasicErrorController antigo
				.andExpect(jsonPath("$.trace").doesNotExist())
				.andExpect(jsonPath("$.exception").doesNotExist())
				.andExpect(jsonPath("$.timestamp").doesNotExist())
				.andExpect(jsonPath("$.path").doesNotExist());
	}

	public record Resposta(String hello) {}

	@RestController
	@RequestMapping("/__test")
	public static class TestStubController {

		@GetMapping("/sucesso")
		public Resposta sucesso() {
			return new Resposta("world");
		}

		@GetMapping("/erro")
		public Resposta erro() {
			throw new RuntimeException("erro simulado para teste");
		}
	}
}
