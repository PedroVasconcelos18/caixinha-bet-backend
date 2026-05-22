package com.caxinhabet.auth.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.caxinhabet.auth.adapter.notification.LogMagicLinkSender;
import com.caxinhabet.auth.adapter.persistence.SolicitacaoAcessoEntity;
import com.caxinhabet.auth.adapter.persistence.SolicitacaoAcessoRepository;
import com.caxinhabet.auth.adapter.persistence.UsuarioEntity;
import com.caxinhabet.auth.adapter.persistence.UsuarioRepository;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest
class RedefinirSenhaUseCaseTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        r.add("spring.datasource.username", POSTGRES::getUsername);
        r.add("spring.datasource.password", POSTGRES::getPassword);
        r.add("app.public-base-url", () -> "http://localhost:3000");
    }

    @Autowired private RedefinirSenhaUseCase redefinir;
    @Autowired private SolicitarResetSenhaUseCase solicitar;
    @Autowired private UsuarioRepository usuarios;
    @Autowired private SolicitacaoAcessoRepository solicitacoes;
    @Autowired private SessaoStore sessaoStore;
    @Autowired private LogMagicLinkSender sender;
    @Autowired private PasswordEncoder encoder;

    @BeforeEach
    void setUp() {
        solicitacoes.deleteAll();
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
    @DisplayName("Token válido: grava nova senha, marca consumido, abre sessão")
    void tokenValido() {
        usuarios.save(UsuarioEntity.criar("alice@local"));
        solicitar.executar("alice@local");
        String token = tokenDoUltimoLink();

        SessaoUsuario sessao = redefinir.executar(token, "novaSenha9");

        assertThat(sessao.email()).isEqualTo("alice@local");
        UsuarioEntity u = usuarios.findByEmail("alice@local").orElseThrow();
        assertThat(encoder.matches("novaSenha9", u.getSenhaHash())).isTrue();
        assertThat(solicitacoes.findAll().get(0).getConsumidoEm()).isNotNull();
        assertThat(sessaoStore.tamanho()).isEqualTo(1);
    }

    @Test
    @DisplayName("Token desconhecido → TokenInvalidoException")
    void tokenDesconhecido() {
        assertThatThrownBy(() -> redefinir.executar("nao-existe", "novaSenha9"))
                .isInstanceOf(TokenInvalidoException.class);
    }

    @Test
    @DisplayName("Token já consumido → AcessoJaConsumidoException")
    void tokenJaConsumido() {
        usuarios.save(UsuarioEntity.criar("bob@local"));
        solicitar.executar("bob@local");
        String token = tokenDoUltimoLink();
        redefinir.executar(token, "novaSenha9");

        assertThatThrownBy(() -> redefinir.executar(token, "outraSenha8"))
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

        assertThatThrownBy(() -> redefinir.executar(tokenCru, "novaSenha9"))
                .isInstanceOf(AcessoExpiradoException.class);
    }

    @Test
    @DisplayName("Senha nova fraca → IllegalArgumentException, token NÃO é consumido")
    void senhaNovaFraca() {
        usuarios.save(UsuarioEntity.criar("dave@local"));
        solicitar.executar("dave@local");
        String token = tokenDoUltimoLink();

        assertThatThrownBy(() -> redefinir.executar(token, "fraca"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(solicitacoes.findAll().get(0).getConsumidoEm()).isNull();
    }
}
