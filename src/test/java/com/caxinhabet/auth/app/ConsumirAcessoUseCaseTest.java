package com.caxinhabet.auth.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.caxinhabet.auth.adapter.notification.LogMagicLinkSender;
import com.caxinhabet.auth.adapter.notification.LogMagicLinkSender.LinkEnviado;
import com.caxinhabet.auth.adapter.persistence.SolicitacaoAcessoEntity;
import com.caxinhabet.auth.adapter.persistence.SolicitacaoAcessoRepository;
import com.caxinhabet.auth.adapter.persistence.UsuarioEntity;
import com.caxinhabet.auth.adapter.persistence.UsuarioRepository;
import com.caxinhabet.auth.adapter.session.SessaoStore;
import com.caxinhabet.auth.domain.AcessoExpiradoException;
import com.caxinhabet.auth.domain.AcessoJaConsumidoException;
import com.caxinhabet.auth.domain.TokenAcesso;
import com.caxinhabet.auth.domain.TokenInvalidoException;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Story 2.1 Task 11.3 — propriedades do {@link ConsumirAcessoUseCase}.
 */
@Testcontainers
@SpringBootTest
class ConsumirAcessoUseCaseTest {

	@Container
	static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

	@DynamicPropertySource
	static void props(DynamicPropertyRegistry r) {
		r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
		r.add("spring.datasource.username", POSTGRES::getUsername);
		r.add("spring.datasource.password", POSTGRES::getPassword);
	}

	@Autowired private ConsumirAcessoUseCase consumir;
	@Autowired private SolicitarAcessoUseCase solicitar;
	@Autowired private UsuarioRepository usuarios;
	@Autowired private SolicitacaoAcessoRepository solicitacoes;
	@Autowired private SessaoStore sessaoStore;
	@Autowired private LogMagicLinkSender sender;

	@BeforeEach
	void setUp() {
		solicitacoes.deleteAll();
		usuarios.deleteAll();
		sessaoStore.limpar();
		sender.limpar();
	}

	@Test
	@DisplayName("Token válido: cria sessão, marca consumido, devolve redirectTo")
	void tokenValido() {
		solicitar.executar("alice@local", "/dash");
		String token = extrairToken();

		ConsumirAcessoUseCase.Resultado r = consumir.executar(token);

		assertThat(r.sessao().email()).isEqualTo("alice@local");
		assertThat(r.redirectTo()).isEqualTo("/dash");
		assertThat(sessaoStore.tamanho()).isEqualTo(1);
		assertThat(solicitacoes.findAll().get(0).getConsumidoEm()).isNotNull();
	}

	@Test
	@DisplayName("Token desconhecido → TokenInvalidoException")
	void tokenDesconhecido() {
		assertThatThrownBy(() -> consumir.executar("token-inexistente"))
				.isInstanceOf(TokenInvalidoException.class);
	}

	@Test
	@DisplayName("Token já consumido → AcessoJaConsumidoException")
	void tokenJaConsumido() {
		solicitar.executar("bob@local", null);
		String token = extrairToken();
		consumir.executar(token);

		assertThatThrownBy(() -> consumir.executar(token))
				.isInstanceOf(AcessoJaConsumidoException.class);
	}

	@Test
	@DisplayName("Token expirado → AcessoExpiradoException")
	void tokenExpirado() {
		UsuarioEntity u = usuarios.save(UsuarioEntity.criar("carol@local"));
		String tokenCru = TokenAcesso.gerar().valor();
		String hash = TokenAcesso.de(tokenCru).hash();
		Instant criado = Instant.now().minusSeconds(3600);
		Instant expira = criado.plusSeconds(60);
		solicitacoes.save(new SolicitacaoAcessoEntity(u.getId(), hash, null, criado, expira));

		assertThatThrownBy(() -> consumir.executar(tokenCru))
				.isInstanceOf(AcessoExpiradoException.class);
	}

	private String extrairToken() {
		LinkEnviado l = sender.linksEnviados().get(sender.linksEnviados().size() - 1);
		String url = l.linkAbsoluto();
		int i = url.indexOf("token=");
		String resto = url.substring(i + "token=".length());
		int amp = resto.indexOf('&');
		return amp < 0 ? resto : resto.substring(0, amp);
	}
}
