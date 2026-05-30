package com.caxinhabet.shared.notification;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** ResendEmailClient com a API HTTP mockada (MockRestServiceServer). */
class ResendEmailClientTest {

	@Test
	@DisplayName("enviar(html,text) faz POST /emails com html e text no JSON")
	void enviaComHtmlETexto() {
		RestClient.Builder builder = RestClient.builder().baseUrl("https://api.resend.com");
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		RestClient client = builder.build();
		ResendEmailClient resend = new ResendEmailClient(client, true);

		server.expect(requestTo("https://api.resend.com/emails"))
				.andExpect(method(HttpMethod.POST))
				.andExpect(content().string(org.hamcrest.Matchers.containsString("\"html\"")))
				.andExpect(content().string(org.hamcrest.Matchers.containsString("<p>oi</p>")))
				.andExpect(content().string(org.hamcrest.Matchers.containsString("\"text\"")))
				.andRespond(withSuccess("{\"id\":\"abc\"}", org.springframework.http.MediaType.APPLICATION_JSON));

		resend.enviar("from@x", "to@y", "Assunto", "<p>oi</p>", "oi (texto)");
		server.verify();
	}

	@Test
	@DisplayName("enviar legado (só text) ainda funciona")
	void enviaLegadoSoTexto() {
		RestClient.Builder builder = RestClient.builder().baseUrl("https://api.resend.com");
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		RestClient client = builder.build();
		ResendEmailClient resend = new ResendEmailClient(client, true);

		server.expect(requestTo("https://api.resend.com/emails"))
				.andExpect(method(HttpMethod.POST))
				.andExpect(content().string(org.hamcrest.Matchers.containsString("oi (texto)")))
				.andRespond(withSuccess("{\"id\":\"abc\"}", org.springframework.http.MediaType.APPLICATION_JSON));

		resend.enviar("from@x", "to@y", "Assunto", "oi (texto)");
		server.verify();
	}

	@Test
	@DisplayName("key não configurada lança IllegalStateException")
	void semKeyLanca() {
		RestClient client = RestClient.builder().baseUrl("https://api.resend.com").build();
		ResendEmailClient resend = new ResendEmailClient(client, false);

		assertThatThrownBy(() -> resend.enviar("from@x", "to@y", "S", "<p>h</p>", "t"))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("RESEND_API_KEY");
	}
}
