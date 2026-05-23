package com.caxinhabet.auth.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.caxinhabet.auth.adapter.notification.LogMagicLinkSender;
import com.caxinhabet.auth.adapter.persistence.UsuarioEntity;
import com.caxinhabet.auth.adapter.persistence.UsuarioRepository;
import com.caxinhabet.auth.adapter.persistence.VerificacaoEmailEntity;
import com.caxinhabet.auth.adapter.persistence.VerificacaoEmailRepository;
import com.caxinhabet.auth.adapter.session.SessaoStore;
import com.caxinhabet.auth.domain.AcessoExpiradoException;
import com.caxinhabet.auth.domain.AcessoJaConsumidoException;
import com.caxinhabet.auth.domain.SessaoUsuario;
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

@Testcontainers
@SpringBootTest
class ConfirmarVerificacaoEmailUseCaseTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        r.add("spring.datasource.username", POSTGRES::getUsername);
        r.add("spring.datasource.password", POSTGRES::getPassword);
        r.add("app.public-base-url", () -> "http://localhost:3000");
    }

    @Autowired private ConfirmarVerificacaoEmailUseCase confirmar;
    @Autowired private SolicitarVerificacaoEmailUseCase solicitar;
    @Autowired private UsuarioRepository usuarios;
    @Autowired private VerificacaoEmailRepository verificacoes;
    @Autowired private SessaoStore sessaoStore;
    @Autowired private LogMagicLinkSender sender;

    @BeforeEach
    void setUp() {
        verificacoes.deleteAll();
        usuarios.deleteAll();
        sessaoStore.limpar();
        sender.limpar();
    }

    private String tokenDoUltimoLink() {
        String url =
                sender.linksEnviados().get(sender.linksEnviados().size() - 1).linkAbsoluto();
        int i = url.indexOf("token=");
        return url.substring(i + "token=".length());
    }

    @Test
    @DisplayName("Token válido: marca verificado, marca consumido, abre sessão")
    void tokenValido() {
        UsuarioEntity u = usuarios.save(UsuarioEntity.criar("alice@local"));
        u.marcarComoNaoVerificado();
        usuarios.save(u);

        solicitar.executar(u);
        String token = tokenDoUltimoLink();

        SessaoUsuario sessao = confirmar.executar(token);

        assertThat(sessao.email()).isEqualTo("alice@local");
        UsuarioEntity reload = usuarios.findByEmail("alice@local").orElseThrow();
        assertThat(reload.isEmailVerificado()).isTrue();
        assertThat(verificacoes.findAll().get(0).getConsumidoEm()).isNotNull();
        assertThat(sessaoStore.tamanho()).isEqualTo(1);
    }

    @Test
    @DisplayName("Token desconhecido → TokenInvalidoException")
    void tokenDesconhecido() {
        assertThatThrownBy(() -> confirmar.executar("nao-existe"))
                .isInstanceOf(TokenInvalidoException.class);
    }

    @Test
    @DisplayName("Token já consumido → AcessoJaConsumidoException")
    void tokenJaConsumido() {
        UsuarioEntity u = usuarios.save(UsuarioEntity.criar("bob@local"));
        u.marcarComoNaoVerificado();
        usuarios.save(u);
        solicitar.executar(u);
        String token = tokenDoUltimoLink();
        confirmar.executar(token);

        assertThatThrownBy(() -> confirmar.executar(token))
                .isInstanceOf(AcessoJaConsumidoException.class);
    }

    @Test
    @DisplayName("Token expirado → AcessoExpiradoException")
    void tokenExpirado() {
        UsuarioEntity u = usuarios.save(UsuarioEntity.criar("carol@local"));
        u.marcarComoNaoVerificado();
        usuarios.save(u);

        String tokenCru = TokenAcesso.gerar().valor();
        String hash = TokenAcesso.de(tokenCru).hash();
        Instant expira = Instant.now().minusSeconds(60);
        verificacoes.save(VerificacaoEmailEntity.criar(u.getId(), hash, expira));

        assertThatThrownBy(() -> confirmar.executar(tokenCru))
                .isInstanceOf(AcessoExpiradoException.class);
    }
}
