package com.caxinhabet.auth.adapter.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.caxinhabet.auth.adapter.notification.LogMagicLinkSender;
import com.caxinhabet.auth.adapter.notification.LogMagicLinkSender.LinkEnviado;
import com.caxinhabet.auth.adapter.persistence.SolicitacaoAcessoEntity;
import com.caxinhabet.auth.adapter.persistence.SolicitacaoAcessoRepository;
import com.caxinhabet.auth.adapter.persistence.UsuarioEntity;
import com.caxinhabet.auth.adapter.persistence.UsuarioRepository;
import com.caxinhabet.auth.adapter.session.SessaoStore;
import com.caxinhabet.auth.domain.TokenAcesso;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
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
 * Story 2.1 — fluxo HTTP completo do módulo auth contra Postgres real
 * (Testcontainers). Padrão de {@code AsaasWebhookControllerTest}.
 *
 * <p>Capturamos os links via {@link LogMagicLinkSender#linksEnviados()} —
 * adapter real, sem mock (testar wiring real).
 *
 * <p>Usamos {@link HttpURLConnection} cru porque precisamos NÃO seguir os
 * 302 do callback e inspecionar o {@code Location}/{@code Set-Cookie}
 * brutos. {@code RestClient} segue redirect automaticamente.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuthControllerIT {

	@Container
	static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

	@DynamicPropertySource
	static void datasourceProps(DynamicPropertyRegistry r) {
		r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
		r.add("spring.datasource.username", POSTGRES::getUsername);
		r.add("spring.datasource.password", POSTGRES::getPassword);
		// O magic link no log aponta para o BACK do teste (não o front).
		r.add("app.public-base-url", () -> "http://localhost:8080");
	}

	@LocalServerPort private int port;
	@Autowired private LogMagicLinkSender logSender;
	@Autowired private UsuarioRepository usuarios;
	@Autowired private SolicitacaoAcessoRepository solicitacoes;
	@Autowired private SessaoStore sessaoStore;

	@BeforeEach
	void setUp() {
		// Limpeza em ordem para respeitar FK.
		solicitacoes.deleteAll();
		usuarios.deleteAll();
		sessaoStore.limpar();
		logSender.limpar();
	}

	@Test
	@DisplayName("POST /auth/solicitar-acesso com e-mail novo → 204 + usuario criado + link enviado")
	void solicitarAcessoEmailNovo() throws Exception {
		HttpResp resp = postJson("/auth/solicitar-acesso", "{\"email\":\"alice@local\"}");

		assertThat(resp.statusCode).isEqualTo(204);
		assertThat(usuarios.findByEmail("alice@local")).isPresent();
		assertThat(solicitacoes.count()).isEqualTo(1L);
		List<LinkEnviado> links = logSender.linksEnviados();
		assertThat(links).hasSize(1);
		assertThat(links.get(0).email()).isEqualTo("alice@local");
		assertThat(links.get(0).linkAbsoluto()).contains("/auth/callback?token=");
	}

	@Test
	@DisplayName("POST /auth/solicitar-acesso 2x com mesmo e-mail → reutiliza usuario, gera 2 links")
	void emailExistenteReutiliza() throws Exception {
		postJson("/auth/solicitar-acesso", "{\"email\":\"bob@local\"}");
		postJson("/auth/solicitar-acesso", "{\"email\":\"bob@local\"}");

		assertThat(usuarios.count()).isEqualTo(1L);
		assertThat(solicitacoes.count()).isEqualTo(2L);
		assertThat(logSender.linksEnviados()).hasSize(2);
	}

	@Test
	@DisplayName("POST /auth/solicitar-acesso é case-insensitive (citext) — Alice == ALICE")
	void emailCaseInsensitive() throws Exception {
		postJson("/auth/solicitar-acesso", "{\"email\":\"Alice@Local\"}");
		postJson("/auth/solicitar-acesso", "{\"email\":\"ALICE@LOCAL\"}");
		assertThat(usuarios.count()).isEqualTo(1L);
	}

	@Test
	@DisplayName("POST /auth/solicitar-acesso com e-mail inválido → 400")
	void emailInvalidoRetorna400() throws Exception {
		HttpResp resp = postJson("/auth/solicitar-acesso", "{\"email\":\"nao-eh-email\"}");
		assertThat(resp.statusCode).isEqualTo(400);
		assertThat(solicitacoes.count()).isZero();
	}

	@Test
	@DisplayName("GET /auth/callback com token bom → 302 + Set-Cookie + sessão ativa")
	void callbackComTokenBom() throws Exception {
		postJson("/auth/solicitar-acesso", "{\"email\":\"carol@local\"}");
		String token = extrairTokenDoUltimoLink();

		HttpResp resp = get("/auth/callback?token=" + token, null);

		assertThat(resp.statusCode).isEqualTo(302);
		String setCookie = resp.header("Set-Cookie");
		assertThat(setCookie)
				.contains("caixinhabet_sessao=")
				.contains("HttpOnly")
				.contains("SameSite=Lax");
		assertThat(resp.header("Location")).isEqualTo("/");
		assertThat(sessaoStore.tamanho()).isEqualTo(1);
		assertThat(solicitacoes.findAll().get(0).getConsumidoEm()).isNotNull();
	}

	@Test
	@DisplayName("GET /auth/callback com mesmo token 2x → 1ª 302, 2ª 410 (já consumido)")
	void callbackTokenUsadoDuasVezes() throws Exception {
		postJson("/auth/solicitar-acesso", "{\"email\":\"dave@local\"}");
		String token = extrairTokenDoUltimoLink();

		HttpResp primeira = get("/auth/callback?token=" + token, null);
		HttpResp segunda = get("/auth/callback?token=" + token, null);

		assertThat(primeira.statusCode).isEqualTo(302);
		assertThat(segunda.statusCode).isEqualTo(410);
	}

	@Test
	@DisplayName("GET /auth/callback com token inexistente → 404")
	void callbackTokenInexistente() throws Exception {
		HttpResp resp = get("/auth/callback?token=token-que-nao-existe", null);
		assertThat(resp.statusCode).isEqualTo(404);
	}

	@Test
	@DisplayName("GET /auth/callback com token expirado → 410")
	void callbackTokenExpirado() throws Exception {
		// Cria diretamente no repo uma solicitação já expirada.
		UsuarioEntity usuario = usuarios.save(UsuarioEntity.criar("eva@local"));
		String tokenCru = TokenAcesso.gerar().valor();
		String hash = TokenAcesso.de(tokenCru).hash();
		Instant criado = Instant.now().minusSeconds(3600);
		Instant expira = criado.plusSeconds(60); // expirado há ~59min
		solicitacoes.save(
				new SolicitacaoAcessoEntity(usuario.getId(), hash, null, criado, expira));

		HttpResp resp = get("/auth/callback?token=" + tokenCru, null);
		assertThat(resp.statusCode).isEqualTo(410);
	}

	@Test
	@DisplayName("GET /auth/me sem cookie → 401")
	void meSemCookie() throws Exception {
		HttpResp resp = get("/auth/me", null);
		assertThat(resp.statusCode).isEqualTo(401);
	}

	@Test
	@DisplayName("GET /auth/me com cookie válido → 200 {email}")
	void meComCookie() throws Exception {
		postJson("/auth/solicitar-acesso", "{\"email\":\"fran@local\"}");
		String token = extrairTokenDoUltimoLink();
		HttpResp cb = get("/auth/callback?token=" + token, null);
		String cookieValor = cb.header("Set-Cookie").split(";", 2)[0];

		HttpResp me = get("/auth/me", cookieValor);
		assertThat(me.statusCode).isEqualTo(200);
		assertThat(me.body).contains("\"email\":\"fran@local\"");
	}

	@Test
	@DisplayName("POST /auth/sair → 204 + cookie expirado + sessão invalidada")
	void sairInvalidaSessao() throws Exception {
		postJson("/auth/solicitar-acesso", "{\"email\":\"gigi@local\"}");
		String token = extrairTokenDoUltimoLink();
		HttpResp cb = get("/auth/callback?token=" + token, null);
		String cookieValor = cb.header("Set-Cookie").split(";", 2)[0];

		assertThat(sessaoStore.tamanho()).isEqualTo(1);
		HttpResp sair = post("/auth/sair", cookieValor);
		assertThat(sair.statusCode).isEqualTo(204);
		assertThat(sair.header("Set-Cookie")).contains("Max-Age=0");
		assertThat(sessaoStore.tamanho()).isZero();
	}

	@Test
	@DisplayName("redirectTo válido é preservado no Location do 302")
	void redirectToPreservado() throws Exception {
		postJson(
				"/auth/solicitar-acesso",
				"{\"email\":\"hank@local\",\"redirectTo\":\"/caixinhas/42\"}");
		String token = extrairTokenDoUltimoLink();
		HttpResp resp = get("/auth/callback?token=" + token, null);
		assertThat(resp.statusCode).isEqualTo(302);
		assertThat(resp.header("Location")).isEqualTo("/caixinhas/42");
	}

	@Test
	@DisplayName("redirectTo malicioso (URL absoluta) cai para /")
	void redirectToMaliciosoNeutralizado() throws Exception {
		postJson(
				"/auth/solicitar-acesso",
				"{\"email\":\"ivan@local\",\"redirectTo\":\"https://evil.com/path\"}");
		String token = extrairTokenDoUltimoLink();
		HttpResp resp = get("/auth/callback?token=" + token, null);
		assertThat(resp.statusCode).isEqualTo(302);
		assertThat(resp.header("Location")).isEqualTo("/");
	}

	// --- helpers ---------------------------------------------------------

	private String extrairTokenDoUltimoLink() {
		List<LinkEnviado> links = logSender.linksEnviados();
		String url = links.get(links.size() - 1).linkAbsoluto();
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
		// case-insensitive lookup pra Set-Cookie/Location
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
