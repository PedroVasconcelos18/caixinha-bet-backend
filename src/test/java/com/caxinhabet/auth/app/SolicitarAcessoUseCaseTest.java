package com.caxinhabet.auth.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.caxinhabet.auth.adapter.notification.LogMagicLinkSender;
import com.caxinhabet.auth.adapter.persistence.SolicitacaoAcessoRepository;
import com.caxinhabet.auth.adapter.persistence.UsuarioRepository;
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
 * Story 2.1 Task 11.2 — propriedades do {@link SolicitarAcessoUseCase}.
 * Usa Postgres real (citext) porque parte do contrato (case-insensitive)
 * vive no banco.
 */
@Testcontainers
@SpringBootTest
class SolicitarAcessoUseCaseTest {

	@Container
	static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

	@DynamicPropertySource
	static void props(DynamicPropertyRegistry r) {
		r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
		r.add("spring.datasource.username", POSTGRES::getUsername);
		r.add("spring.datasource.password", POSTGRES::getPassword);
		r.add("app.public-base-url", () -> "http://localhost:3000");
	}

	@Autowired private SolicitarAcessoUseCase useCase;
	@Autowired private UsuarioRepository usuarios;
	@Autowired private SolicitacaoAcessoRepository solicitacoes;
	@Autowired private LogMagicLinkSender sender;

	@BeforeEach
	void setUp() {
		solicitacoes.deleteAll();
		usuarios.deleteAll();
		sender.limpar();
	}

	@Test
	@DisplayName("Email novo: cria usuario, persiste solicitacao, envia link")
	void emailNovo() {
		useCase.executar("Novo@Exemplo.com", null);
		assertThat(usuarios.findByEmail("novo@exemplo.com")).isPresent();
		assertThat(solicitacoes.count()).isEqualTo(1L);
		assertThat(sender.linksEnviados()).hasSize(1);
	}

	@Test
	@DisplayName("Email existente: reutiliza usuario, gera nova solicitacao")
	void emailExistente() {
		useCase.executar("existente@local", null);
		useCase.executar("existente@local", null);
		assertThat(usuarios.count()).isEqualTo(1L);
		assertThat(solicitacoes.count()).isEqualTo(2L);
	}

	@Test
	@DisplayName("redirectTo válido sai no link")
	void redirectToValido() {
		useCase.executar("a@b.com", "/caixinhas/42");
		String link = sender.linksEnviados().get(0).linkAbsoluto();
		assertThat(link).contains("redirectTo=%2Fcaixinhas%2F42");
	}

	@Test
	@DisplayName("redirectTo malicioso (esquema) é descartado")
	void redirectToMalicioso() {
		useCase.executar("a@b.com", "https://evil/foo");
		String link = sender.linksEnviados().get(0).linkAbsoluto();
		assertThat(link).doesNotContain("redirectTo=");
	}

	@Test
	@DisplayName("redirectTo protocolo-relativo (//foo) é descartado")
	void redirectToProtocoloRelativo() {
		useCase.executar("a@b.com", "//evil.com/foo");
		String link = sender.linksEnviados().get(0).linkAbsoluto();
		assertThat(link).doesNotContain("redirectTo=");
	}

	@Test
	@DisplayName("email null falha cedo")
	void emailNull() {
		assertThatThrownBy(() -> useCase.executar(null, null))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("token hash persistido tem 64 chars")
	void tokenHashTem64Chars() {
		useCase.executar("a@b.com", null);
		assertThat(solicitacoes.findAll().get(0).getTokenHash()).hasSize(64);
	}
}
