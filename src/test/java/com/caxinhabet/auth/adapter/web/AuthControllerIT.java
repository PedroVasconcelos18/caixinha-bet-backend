package com.caxinhabet.auth.adapter.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.caxinhabet.auth.adapter.notification.LogMagicLinkSender;
import com.caxinhabet.auth.adapter.persistence.SolicitacaoAcessoRepository;
import com.caxinhabet.auth.adapter.persistence.UsuarioRepository;
import com.caxinhabet.auth.adapter.session.SessaoStore;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Fluxo HTTP completo do módulo auth (auth por senha, 2026-05) contra
 * Postgres real (Testcontainers).
 *
 * <p>Usamos {@link HttpURLConnection} cru — sem seguir redirects — herdado
 * do IT do magic link; aqui não há 302, mas o helper continua útil para
 * inspecionar {@code Set-Cookie} bruto.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuthControllerIT {

	private static final String CPF_VALIDO_A = "529.982.247-25";
	private static final String CPF_VALIDO_B = "168.995.350-09";

	@Container
	static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

	@DynamicPropertySource
	static void datasourceProps(DynamicPropertyRegistry r) {
		r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
		r.add("spring.datasource.username", POSTGRES::getUsername);
		r.add("spring.datasource.password", POSTGRES::getPassword);
		r.add("app.public-base-url", () -> "http://localhost:3000");
	}

	@LocalServerPort private int port;
	@Autowired private LogMagicLinkSender logSender;
	@Autowired private UsuarioRepository usuarios;
	@Autowired private SolicitacaoAcessoRepository solicitacoes;
	@Autowired private SessaoStore sessaoStore;

	@BeforeEach
	void setUp() {
		solicitacoes.deleteAll();
		usuarios.deleteAll();
		sessaoStore.limpar();
		logSender.limpar();
	}

	@Test
	@DisplayName("POST /auth/registrar válido → 200 + Set-Cookie + usuário criado")
	void registrarValido() throws Exception {
		HttpResp resp =
				postJson(
						"/auth/registrar",
						"{\"nomeCompleto\":\"Alice\",\"cpf\":\""
								+ CPF_VALIDO_A
								+ "\",\"email\":\"alice@local\",\"senha\":\"senha1234\"}");

		assertThat(resp.statusCode).isEqualTo(200);
		assertThat(resp.header("Set-Cookie")).contains("caixinhabet_sessao=");
		assertThat(resp.body).contains("\"email\":\"alice@local\"");
		assertThat(usuarios.findByEmail("alice@local")).isPresent();
		assertThat(sessaoStore.tamanho()).isEqualTo(1);
	}

	@Test
	@DisplayName("POST /auth/registrar com e-mail duplicado → 409")
	void registrarEmailDuplicado() throws Exception {
		postJson(
				"/auth/registrar",
				"{\"nomeCompleto\":\"Bob\",\"cpf\":\""
						+ CPF_VALIDO_A
						+ "\",\"email\":\"bob@local\",\"senha\":\"senha1234\"}");
		HttpResp resp =
				postJson(
						"/auth/registrar",
						"{\"nomeCompleto\":\"Bob2\",\"cpf\":\""
								+ CPF_VALIDO_B
								+ "\",\"email\":\"bob@local\",\"senha\":\"senha1234\"}");
		assertThat(resp.statusCode).isEqualTo(409);
		assertThat(resp.body).contains("email-ja-cadastrado");
	}

	@Test
	@DisplayName("POST /auth/registrar com CPF duplicado → 409")
	void registrarCpfDuplicado() throws Exception {
		postJson(
				"/auth/registrar",
				"{\"nomeCompleto\":\"Carol\",\"cpf\":\""
						+ CPF_VALIDO_A
						+ "\",\"email\":\"carol@local\",\"senha\":\"senha1234\"}");
		HttpResp resp =
				postJson(
						"/auth/registrar",
						"{\"nomeCompleto\":\"Carol2\",\"cpf\":\""
								+ CPF_VALIDO_A
								+ "\",\"email\":\"carol2@local\",\"senha\":\"senha1234\"}");
		assertThat(resp.statusCode).isEqualTo(409);
		assertThat(resp.body).contains("cpf-ja-cadastrado");
	}

	@Test
	@DisplayName("POST /auth/registrar com CPF inválido → 400")
	void registrarCpfInvalido() throws Exception {
		HttpResp resp =
				postJson(
						"/auth/registrar",
						"{\"nomeCompleto\":\"Dave\",\"cpf\":\"111.111.111-11\","
								+ "\"email\":\"dave@local\",\"senha\":\"senha1234\"}");
		assertThat(resp.statusCode).isEqualTo(400);
	}

	@Test
	@DisplayName("POST /auth/login com senha correta → 200 + Set-Cookie")
	void loginValido() throws Exception {
		postJson(
				"/auth/registrar",
				"{\"nomeCompleto\":\"Eva\",\"cpf\":\""
						+ CPF_VALIDO_A
						+ "\",\"email\":\"eva@local\",\"senha\":\"senha1234\"}");
		sessaoStore.limpar();

		HttpResp resp =
				postJson("/auth/login", "{\"email\":\"eva@local\",\"senha\":\"senha1234\"}");
		assertThat(resp.statusCode).isEqualTo(200);
		assertThat(resp.header("Set-Cookie")).contains("caixinhabet_sessao=");
		assertThat(sessaoStore.tamanho()).isEqualTo(1);
	}

	@Test
	@DisplayName("POST /auth/login com senha errada → 401")
	void loginSenhaErrada() throws Exception {
		postJson(
				"/auth/registrar",
				"{\"nomeCompleto\":\"Fran\",\"cpf\":\""
						+ CPF_VALIDO_A
						+ "\",\"email\":\"fran@local\",\"senha\":\"senha1234\"}");
		HttpResp resp =
				postJson("/auth/login", "{\"email\":\"fran@local\",\"senha\":\"errada999\"}");
		assertThat(resp.statusCode).isEqualTo(401);
	}

	@Test
	@DisplayName("POST /auth/recuperar-senha → sempre 204 (e-mail com ou sem conta)")
	void recuperarSenhaSempre204() throws Exception {
		postJson(
				"/auth/registrar",
				"{\"nomeCompleto\":\"Gigi\",\"cpf\":\""
						+ CPF_VALIDO_A
						+ "\",\"email\":\"gigi@local\",\"senha\":\"senha1234\"}");

		HttpResp comConta = postJson("/auth/recuperar-senha", "{\"email\":\"gigi@local\"}");
		HttpResp semConta = postJson("/auth/recuperar-senha", "{\"email\":\"ninguem@local\"}");

		assertThat(comConta.statusCode).isEqualTo(204);
		assertThat(semConta.statusCode).isEqualTo(204);
		assertThat(logSender.linksEnviados()).hasSize(1);
		assertThat(logSender.linksEnviados().get(0).linkAbsoluto())
				.contains("/redefinir-senha?token=");
	}

	@Test
	@DisplayName("POST /auth/redefinir-senha com token bom → 200 + nova senha funciona no login")
	void redefinirSenhaFluxoCompleto() throws Exception {
		postJson(
				"/auth/registrar",
				"{\"nomeCompleto\":\"Hank\",\"cpf\":\""
						+ CPF_VALIDO_A
						+ "\",\"email\":\"hank@local\",\"senha\":\"senhaVelha1\"}");
		postJson("/auth/recuperar-senha", "{\"email\":\"hank@local\"}");
		String token = extrairTokenDoUltimoLink();

		HttpResp redef =
				postJson(
						"/auth/redefinir-senha",
						"{\"token\":\"" + token + "\",\"senha\":\"senhaNova9\"}");
		assertThat(redef.statusCode).isEqualTo(200);
		assertThat(redef.header("Set-Cookie")).contains("caixinhabet_sessao=");

		assertThat(
						postJson(
										"/auth/login",
										"{\"email\":\"hank@local\",\"senha\":\"senhaNova9\"}")
								.statusCode)
				.isEqualTo(200);
		assertThat(
						postJson(
										"/auth/login",
										"{\"email\":\"hank@local\",\"senha\":\"senhaVelha1\"}")
								.statusCode)
				.isEqualTo(401);
	}

	@Test
	@DisplayName("POST /auth/redefinir-senha com token usado 2x → 1ª 200, 2ª 410")
	void redefinirTokenUsadoDuasVezes() throws Exception {
		postJson(
				"/auth/registrar",
				"{\"nomeCompleto\":\"Ivan\",\"cpf\":\""
						+ CPF_VALIDO_A
						+ "\",\"email\":\"ivan@local\",\"senha\":\"senhaVelha1\"}");
		postJson("/auth/recuperar-senha", "{\"email\":\"ivan@local\"}");
		String token = extrairTokenDoUltimoLink();

		HttpResp primeira =
				postJson(
						"/auth/redefinir-senha",
						"{\"token\":\"" + token + "\",\"senha\":\"senhaNova9\"}");
		HttpResp segunda =
				postJson(
						"/auth/redefinir-senha",
						"{\"token\":\"" + token + "\",\"senha\":\"outraNova8\"}");
		assertThat(primeira.statusCode).isEqualTo(200);
		assertThat(segunda.statusCode).isEqualTo(410);
	}

	@Test
	@DisplayName("POST /auth/redefinir-senha com token inexistente → 404")
	void redefinirTokenInexistente() throws Exception {
		HttpResp resp =
				postJson(
						"/auth/redefinir-senha",
						"{\"token\":\"nao-existe\",\"senha\":\"senhaNova9\"}");
		assertThat(resp.statusCode).isEqualTo(404);
	}

	@Test
	@DisplayName("GET /auth/me sem cookie → 401")
	void meSemCookie() throws Exception {
		assertThat(get("/auth/me", null).statusCode).isEqualTo(401);
	}

	@Test
	@DisplayName("GET /auth/me com cookie do registro → 200 {email}")
	void meComCookie() throws Exception {
		HttpResp reg =
				postJson(
						"/auth/registrar",
						"{\"nomeCompleto\":\"Jana\",\"cpf\":\""
								+ CPF_VALIDO_A
								+ "\",\"email\":\"jana@local\",\"senha\":\"senha1234\"}");
		String cookie = reg.header("Set-Cookie").split(";", 2)[0];

		HttpResp me = get("/auth/me", cookie);
		assertThat(me.statusCode).isEqualTo(200);
		assertThat(me.body).contains("\"email\":\"jana@local\"");
	}

	@Test
	@DisplayName("POST /auth/sair → 204 + cookie expirado + sessão invalidada")
	void sairInvalidaSessao() throws Exception {
		HttpResp reg =
				postJson(
						"/auth/registrar",
						"{\"nomeCompleto\":\"Kim\",\"cpf\":\""
								+ CPF_VALIDO_A
								+ "\",\"email\":\"kim@local\",\"senha\":\"senha1234\"}");
		String cookie = reg.header("Set-Cookie").split(";", 2)[0];
		assertThat(sessaoStore.tamanho()).isEqualTo(1);

		HttpResp sair = post("/auth/sair", cookie);
		assertThat(sair.statusCode).isEqualTo(204);
		assertThat(sair.header("Set-Cookie")).contains("Max-Age=0");
		assertThat(sessaoStore.tamanho()).isZero();
	}

	// --- helpers ---------------------------------------------------------

	private String extrairTokenDoUltimoLink() {
		String url =
				logSender
						.linksEnviados()
						.get(logSender.linksEnviados().size() - 1)
						.linkAbsoluto();
		int i = url.indexOf("token=");
		String resto = url.substring(i + "token=".length());
		int amp = resto.indexOf('&');
		return amp < 0 ? resto : resto.substring(0, amp);
	}

	private HttpResp postJson(String path, String json) throws IOException {
		HttpURLConnection c = open(path);
		c.setRequestMethod("POST");
		c.setDoOutput(true);
		c.setRequestProperty("Content-Type", "application/json");
		c.getOutputStream().write(json.getBytes(StandardCharsets.UTF_8));
		return finalizar(c);
	}

	private HttpResp post(String path, String cookie) throws IOException {
		HttpURLConnection c = open(path);
		c.setRequestMethod("POST");
		c.setDoOutput(true);
		if (cookie != null) c.setRequestProperty("Cookie", cookie);
		c.getOutputStream().write(new byte[0]);
		return finalizar(c);
	}

	private HttpResp get(String path, String cookie) throws IOException {
		HttpURLConnection c = open(path);
		c.setRequestMethod("GET");
		if (cookie != null) c.setRequestProperty("Cookie", cookie);
		return finalizar(c);
	}

	private HttpURLConnection open(String path) throws IOException {
		HttpURLConnection c =
				(HttpURLConnection)
						URI.create("http://localhost:" + port + path).toURL().openConnection();
		c.setInstanceFollowRedirects(false);
		c.setRequestProperty("Accept", "application/json,*/*");
		return c;
	}

	private static HttpResp finalizar(HttpURLConnection c) throws IOException {
		c.connect();
		int code = c.getResponseCode();
		String body = "";
		try {
			if (c.getInputStream() != null) body = new String(c.getInputStream().readAllBytes());
		} catch (IOException ignored) {
			if (c.getErrorStream() != null) body = new String(c.getErrorStream().readAllBytes());
		}
		Map<String, String> headers = new HashMap<>();
		c.getHeaderFields()
				.forEach(
						(k, v) -> {
							if (k != null && v != null && !v.isEmpty()) {
								headers.put(k.toLowerCase(), v.get(0));
							}
						});
		return new HttpResp(code, headers, body);
	}

	private record HttpResp(int statusCode, Map<String, String> headers, String body) {
		String header(String name) {
			return headers.get(name.toLowerCase());
		}
	}
}
