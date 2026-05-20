package com.caxinhabet.caixinha.adapter.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.caxinhabet.auth.adapter.persistence.UsuarioEntity;
import com.caxinhabet.auth.adapter.persistence.UsuarioRepository;
import com.caxinhabet.auth.adapter.session.SessaoStore;
import com.caxinhabet.auth.domain.SessaoUsuario;
import com.caxinhabet.auth.domain.TokenAcesso;
import com.caxinhabet.caixinha.adapter.persistence.CaixinhaRepository;
import com.caxinhabet.caixinha.adapter.persistence.ResultadoPossivelRepository;
import com.caxinhabet.participante.adapter.persistence.ParticipanteRepository;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
 * Story 2.2 — IT de {@code POST/GET /caixinhas}.
 *
 * <p>Padrão da Story 2.1: {@link HttpURLConnection} cru (para inspecionar
 * Set-Cookie/Location quando relevante) e {@link SessaoStore} para
 * autenticar sem precisar percorrer o magic link inteiro.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CaixinhaControllerIT {

	@Container
	static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

	@DynamicPropertySource
	static void props(DynamicPropertyRegistry r) {
		r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
		r.add("spring.datasource.username", POSTGRES::getUsername);
		r.add("spring.datasource.password", POSTGRES::getPassword);
	}

	@LocalServerPort private int port;

	@Autowired private UsuarioRepository usuarios;
	@Autowired private CaixinhaRepository caixinhas;
	@Autowired private ResultadoPossivelRepository resultados;
	@Autowired private ParticipanteRepository participantes;
	@Autowired private SessaoStore sessaoStore;

	private String cookieRafael;
	private String cookieMariana;

	@BeforeEach
	void setUp() {
		participantes.deleteAll();
		resultados.deleteAll();
		caixinhas.deleteAll();
		usuarios.deleteAll();
		sessaoStore.limpar();

		UsuarioEntity rafael = usuarios.save(UsuarioEntity.criar("rafael@local"));
		UsuarioEntity mariana = usuarios.save(UsuarioEntity.criar("mariana@local"));
		cookieRafael = sessao(rafael);
		cookieMariana = sessao(mariana);
	}

	private String sessao(UsuarioEntity u) {
		String idSessao = TokenAcesso.gerar().valor();
		Instant agora = Instant.now();
		Instant exp = agora.plus(7, ChronoUnit.DAYS);
		sessaoStore.criar(new SessaoUsuario(idSessao, u.getId(), u.getEmail(), agora, exp));
		return "caixinhabet_sessao=" + idSessao;
	}

	private static final String JSON_OK =
			"{"
					+ "\"titulo\":\"Brasil x Marrocos\","
					+ "\"ladoA\":\"Brasil\","
					+ "\"ladoB\":\"Marrocos\","
					+ "\"valorIngresso\":\"40.00\","
					+ "\"minimoParticipantes\":5,"
					+ "\"prazoEntrada\":\"2026-06-01T12:00:00Z\","
					+ "\"dataApuracao\":\"2026-06-01T14:00:00Z\","
					+ "\"rotulosResultados\":[\"Vitória do Brasil\",\"Empate\",\"Vitória do Marrocos\"],"
					+ "\"emailsConvidados\":[]"
					+ "}";

	// ---------- AC-1/AC-4: criar ----------

	@Test
	@DisplayName("POST /caixinhas sem autenticação → 401")
	void postSemAuth() throws Exception {
		HttpResp r = postJson("/caixinhas", JSON_OK, null);
		assertThat(r.statusCode).isEqualTo(401);
	}

	@Test
	@DisplayName("POST /caixinhas autenticado válido → 201 + Location + corpo")
	void postFeliz() throws Exception {
		HttpResp r = postJson("/caixinhas", JSON_OK, cookieRafael);
		assertThat(r.statusCode).isEqualTo(201);
		assertThat(r.header("Location")).matches("/caixinhas/\\d+");
		assertThat(r.body)
				.contains("\"titulo\":\"Brasil x Marrocos\"")
				.contains("\"estado\":\"coletando_convites\"")
				.contains("\"taxaServico\":\"10.00\"")
				.contains("\"premioMaximoTeorico\":\"190.00\"")
				.contains("\"valorIngresso\":\"40.00\"");

		// DB: 1 caixinha, 3 resultados, 1 participante-dono
		assertThat(caixinhas.count()).isEqualTo(1L);
		assertThat(resultados.count()).isEqualTo(3L);
		assertThat(participantes.count()).isEqualTo(1L);
	}

	// ---------- AC-2: validação ----------

	@Test
	@DisplayName("POST /caixinhas com valorIngresso < R$ 5 → 422 + violations")
	void valorMenorQueMinimo() throws Exception {
		String json = JSON_OK.replace("\"40.00\"", "\"4.99\"");
		HttpResp r = postJson("/caixinhas", json, cookieRafael);
		assertThat(r.statusCode).isEqualTo(422);
		assertThat(r.body).contains("violations").contains("valorIngresso");
	}

	@Test
	@DisplayName("POST com dataApuracao <= prazoEntrada → 422")
	void apuracaoAntesDoPrazo() throws Exception {
		String json =
				JSON_OK
						.replace("\"2026-06-01T14:00:00Z\"", "\"2026-06-01T12:00:00Z\""); // ambos == prazo
		HttpResp r = postJson("/caixinhas", json, cookieRafael);
		assertThat(r.statusCode).isEqualTo(422);
		assertThat(r.body).contains("dataApuracao");
	}

	@Test
	@DisplayName("POST com Bean Validation falhando (titulo vazio) → 400")
	void tituloVazio400() throws Exception {
		String json = JSON_OK.replace("Brasil x Marrocos", "");
		HttpResp r = postJson("/caixinhas", json, cookieRafael);
		assertThat(r.statusCode).isEqualTo(400);
	}

	@Test
	@DisplayName("POST com 1 só resultado possível → 400 (Bean Validation @Size(min=2))")
	void umSoResultado() throws Exception {
		String json = JSON_OK.replace(
				"[\"Vitória do Brasil\",\"Empate\",\"Vitória do Marrocos\"]",
				"[\"Único\"]");
		HttpResp r = postJson("/caixinhas", json, cookieRafael);
		// Bean Validation @Size(min=2) já falha em 400 antes do use case ver
		assertThat(r.statusCode).isEqualTo(400);
	}

	// ---------- GET /caixinhas/{id} ----------

	@Test
	@DisplayName("GET /caixinhas/{id} como participante (organizador) → 200")
	void getComoParticipante() throws Exception {
		HttpResp created = postJson("/caixinhas", JSON_OK, cookieRafael);
		String location = created.header("Location");
		HttpResp got = get(location, cookieRafael);
		assertThat(got.statusCode).isEqualTo(200);
		assertThat(got.body).contains("\"titulo\":\"Brasil x Marrocos\"");
	}

	@Test
	@DisplayName("GET como NÃO-participante → 404 (anti-enumeração, não 403)")
	void getComoNaoParticipante() throws Exception {
		HttpResp created = postJson("/caixinhas", JSON_OK, cookieRafael);
		String location = created.header("Location");
		HttpResp got = get(location, cookieMariana);
		assertThat(got.statusCode).isEqualTo(404);
	}

	@Test
	@DisplayName("GET /caixinhas/{id} sem autenticação → 401")
	void getSemAuth() throws Exception {
		HttpResp created = postJson("/caixinhas", JSON_OK, cookieRafael);
		String location = created.header("Location");
		HttpResp got = get(location, null);
		assertThat(got.statusCode).isEqualTo(401);
	}

	@Test
	@DisplayName("GET /caixinhas/{id} inexistente → 404")
	void getInexistente() throws Exception {
		HttpResp got = get("/caixinhas/9999999", cookieRafael);
		assertThat(got.statusCode).isEqualTo(404);
	}

	// ---------- AC-5: imutabilidade por construção ----------

	@Test
	@DisplayName("PUT /caixinhas/{id} → 405")
	void putNaoExiste() throws Exception {
		HttpResp created = postJson("/caixinhas", JSON_OK, cookieRafael);
		String location = created.header("Location");
		HttpResp put = put(location, "{}", cookieRafael);
		assertThat(put.statusCode).isEqualTo(405);
	}

	@Test
	@DisplayName("DELETE /caixinhas/{id} → 405")
	void deleteNaoExiste() throws Exception {
		HttpResp created = postJson("/caixinhas", JSON_OK, cookieRafael);
		String location = created.header("Location");
		HttpResp del = delete(location, cookieRafael);
		assertThat(del.statusCode).isEqualTo(405);
	}

	// ---------- Story 2.4: POST /caixinhas/{id}/convites ----------

	@Test
	@DisplayName("POST /convites como dono → 200 + convidados + jaPresentes")
	void convitesFelizes() throws Exception {
		HttpResp created = postJson("/caixinhas", JSON_OK, cookieRafael);
		String location = created.header("Location");
		HttpResp resp =
				postJson(
						location + "/convites",
						"{\"emails\":[\"alice@local\",\"bob@local\"]}",
						cookieRafael);
		assertThat(resp.statusCode).isEqualTo(200);
		assertThat(resp.body).contains("\"convidados\":");
		assertThat(resp.body).contains("alice@local").contains("bob@local");
		assertThat(resp.body).contains("\"jaPresentes\":[]");
	}

	@Test
	@DisplayName("POST /convites como NÃO-dono → 403")
	void convitesNaoDono() throws Exception {
		HttpResp created = postJson("/caixinhas", JSON_OK, cookieRafael);
		String location = created.header("Location");
		HttpResp resp =
				postJson(
						location + "/convites",
						"{\"emails\":[\"alice@local\"]}",
						cookieMariana);
		assertThat(resp.statusCode).isEqualTo(403);
	}

	@Test
	@DisplayName("POST /convites sem autenticação → 401")
	void convitesSemAuth() throws Exception {
		HttpResp created = postJson("/caixinhas", JSON_OK, cookieRafael);
		String location = created.header("Location");
		HttpResp resp =
				postJson(location + "/convites", "{\"emails\":[\"alice@local\"]}", null);
		assertThat(resp.statusCode).isEqualTo(401);
	}

	@Test
	@DisplayName("POST /convites com e-mail inválido → 400 (Bean Validation)")
	void convitesEmailInvalido() throws Exception {
		HttpResp created = postJson("/caixinhas", JSON_OK, cookieRafael);
		String location = created.header("Location");
		HttpResp resp =
				postJson(location + "/convites", "{\"emails\":[\"nao-eh-email\"]}", cookieRafael);
		assertThat(resp.statusCode).isEqualTo(400);
	}

	@Test
	@DisplayName("POST /convites com lista vazia → 400 (Bean Validation @Size(min=1))")
	void convitesListaVazia() throws Exception {
		HttpResp created = postJson("/caixinhas", JSON_OK, cookieRafael);
		String location = created.header("Location");
		HttpResp resp = postJson(location + "/convites", "{\"emails\":[]}", cookieRafael);
		assertThat(resp.statusCode).isEqualTo(400);
	}

	@Test
	@DisplayName("POST /convites em Caixinha inexistente → 403 (mascarado, anti-enumeração)")
	void convitesCaixinhaInexistente() throws Exception {
		HttpResp resp =
				postJson(
						"/caixinhas/999999/convites",
						"{\"emails\":[\"alice@local\"]}",
						cookieRafael);
		assertThat(resp.statusCode).isEqualTo(403);
	}

	// ---------- Story 2.5: convite ----------

	@Test
	@DisplayName("GET /convite como convidado → 200 com dados restritos")
	void verConviteFeliz() throws Exception {
		HttpResp created = postJson("/caixinhas", JSON_OK_COM_CONVIDADO, cookieRafael);
		String location = created.header("Location");

		// alice (convidada) acessa autenticada
		String cookieAlice = sessao(garantirUsuario("alice@local"));
		HttpResp resp = get(location + "/convite", cookieAlice);
		assertThat(resp.statusCode).isEqualTo(200);
		assertThat(resp.body)
				.contains("\"titulo\":\"Brasil x Marrocos\"")
				.contains("\"taxaServico\":\"10.00\"")
				.contains("\"eu\":")
				.contains("\"status\":\"convidado\"");
	}

	@Test
	@DisplayName("GET /convite como não-convidado → 404 (anti-enumeração)")
	void verConviteNaoConvidado() throws Exception {
		HttpResp created = postJson("/caixinhas", JSON_OK, cookieRafael);
		String location = created.header("Location");
		HttpResp resp = get(location + "/convite", cookieMariana);
		assertThat(resp.statusCode).isEqualTo(404);
	}

	@Test
	@DisplayName("POST /aceitar transiciona convidado → aceito")
	void aceitarFeliz() throws Exception {
		HttpResp created = postJson("/caixinhas", JSON_OK_COM_CONVIDADO, cookieRafael);
		String location = created.header("Location");
		String cookieAlice = sessao(garantirUsuario("alice@local"));

		HttpResp resp = postJson(location + "/aceitar", "{}", cookieAlice);
		assertThat(resp.statusCode).isEqualTo(200);
		assertThat(resp.body).contains("\"status\":\"aceito\"");
	}

	@Test
	@DisplayName("POST /aceitar como não-convidado → 404")
	void aceitarNaoConvidado() throws Exception {
		HttpResp created = postJson("/caixinhas", JSON_OK, cookieRafael);
		String location = created.header("Location");
		HttpResp resp = postJson(location + "/aceitar", "{}", cookieMariana);
		assertThat(resp.statusCode).isEqualTo(404);
	}

	@Test
	@DisplayName("POST /aceitar sem autenticação → 401")
	void aceitarSemAuth() throws Exception {
		HttpResp created = postJson("/caixinhas", JSON_OK, cookieRafael);
		String location = created.header("Location");
		HttpResp resp = postJson(location + "/aceitar", "{}", null);
		assertThat(resp.statusCode).isEqualTo(401);
	}

	@Test
	@DisplayName("PUT /palpite com resultado válido → 200 + status aceito (aceite implícito)")
	void palpitarFeliz() throws Exception {
		HttpResp created = postJson("/caixinhas", JSON_OK_COM_CONVIDADO, cookieRafael);
		String location = created.header("Location");
		String cookieAlice = sessao(garantirUsuario("alice@local"));

		// Buscar id do primeiro resultado
		HttpResp convite = get(location + "/convite", cookieAlice);
		long resultadoId = extrairPrimeiroResultadoId(convite.body);

		HttpResp resp =
				put(
						location + "/palpite",
						"{\"resultadoPossivelId\":" + resultadoId + "}",
						cookieAlice);
		assertThat(resp.statusCode).isEqualTo(200);
		assertThat(resp.body)
				.contains("\"status\":\"aceito\"")
				.contains("\"palpiteResultadoPossivelId\":" + resultadoId);
	}

	@Test
	@DisplayName("PUT /palpite com id de outra Caixinha → 422")
	void palpitarResultadoDeOutraCaixinha() throws Exception {
		HttpResp c1 = postJson("/caixinhas", JSON_OK_COM_CONVIDADO, cookieRafael);
		String loc1 = c1.header("Location");
		HttpResp c2 = postJson("/caixinhas", JSON_OK, cookieRafael);
		String loc2 = c2.header("Location");

		String cookieAlice = sessao(garantirUsuario("alice@local"));
		// Pega id de resultado da Caixinha 2 (alice NÃO foi convidada nela — mas isso não importa pro teste de validação do id)
		HttpResp conviteC2Resp = get(loc2 + "/convite", cookieRafael);
		long resultadoIdDeOutra = extrairPrimeiroResultadoId(conviteC2Resp.body);

		HttpResp resp =
				put(
						loc1 + "/palpite",
						"{\"resultadoPossivelId\":" + resultadoIdDeOutra + "}",
						cookieAlice);
		assertThat(resp.statusCode).isEqualTo(422);
		assertThat(resp.body).contains("não pertence");
	}

	@Test
	@DisplayName("PUT /palpite como não-convidado → 404")
	void palpitarNaoConvidado() throws Exception {
		HttpResp created = postJson("/caixinhas", JSON_OK, cookieRafael);
		String location = created.header("Location");
		HttpResp resp =
				put(
						location + "/palpite",
						"{\"resultadoPossivelId\":1}",
						cookieMariana);
		assertThat(resp.statusCode).isEqualTo(404);
	}

	@Test
	@DisplayName("PUT /palpite sem body válido → 400 (Bean Validation @NotNull)")
	void palpitarSemBody() throws Exception {
		HttpResp created = postJson("/caixinhas", JSON_OK_COM_CONVIDADO, cookieRafael);
		String location = created.header("Location");
		String cookieAlice = sessao(garantirUsuario("alice@local"));
		HttpResp resp = put(location + "/palpite", "{}", cookieAlice);
		assertThat(resp.statusCode).isEqualTo(400);
	}

	private UsuarioEntity garantirUsuario(String email) {
		return usuarios
				.findByEmail(email)
				.orElseGet(() -> usuarios.save(UsuarioEntity.criar(email)));
	}

	private long extrairPrimeiroResultadoId(String body) {
		// Regex simples: encontra primeiro "id":N dentro de resultadosPossiveis
		java.util.regex.Matcher m =
				java.util.regex.Pattern.compile("\"resultadosPossiveis\":\\[\\{\"id\":(\\d+)")
						.matcher(body);
		if (!m.find()) {
			throw new IllegalStateException("Não achou id no body: " + body);
		}
		return Long.parseLong(m.group(1));
	}

	private static final String JSON_OK_COM_CONVIDADO =
			"{\"titulo\":\"Brasil x Marrocos\",\"ladoA\":\"Brasil\",\"ladoB\":\"Marrocos\","
					+ "\"valorIngresso\":\"40.00\",\"minimoParticipantes\":3,"
					+ "\"prazoEntrada\":\"2027-06-01T12:00:00Z\",\"dataApuracao\":\"2027-06-01T14:00:00Z\","
					+ "\"rotulosResultados\":[\"V Brasil\",\"Empate\",\"V Marrocos\"],"
					+ "\"emailsConvidados\":[\"alice@local\"]}";

	// ---------- helpers ----------

	private HttpResp postJson(String path, String json, String cookie) throws IOException {
		HttpURLConnection c = open(path);
		c.setRequestMethod("POST");
		c.setDoOutput(true);
		c.setRequestProperty("Content-Type", "application/json");
		if (cookie != null) c.setRequestProperty("Cookie", cookie);
		c.getOutputStream().write(json.getBytes(StandardCharsets.UTF_8));
		return finalizar(c);
	}

	private HttpResp get(String path, String cookie) throws IOException {
		HttpURLConnection c = open(path);
		c.setRequestMethod("GET");
		if (cookie != null) c.setRequestProperty("Cookie", cookie);
		return finalizar(c);
	}

	private HttpResp put(String path, String body, String cookie) throws IOException {
		HttpURLConnection c = open(path);
		c.setRequestMethod("PUT");
		c.setDoOutput(true);
		c.setRequestProperty("Content-Type", "application/json");
		if (cookie != null) c.setRequestProperty("Cookie", cookie);
		c.getOutputStream().write(body.getBytes(StandardCharsets.UTF_8));
		return finalizar(c);
	}

	private HttpResp delete(String path, String cookie) throws IOException {
		HttpURLConnection c = open(path);
		c.setRequestMethod("DELETE");
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

	@Test
	@DisplayName("Lista de Participantes na response inclui o dono com status 'convidado'")
	void participantesNaResponse() throws Exception {
		HttpResp r = postJson("/caixinhas", JSON_OK, cookieRafael);
		assertThat(r.body)
				.contains("\"participantes\":")
				.contains("\"email\":\"rafael@local\"")
				.contains("\"dono\":true")
				.contains("\"status\":\"convidado\"");
	}

}
