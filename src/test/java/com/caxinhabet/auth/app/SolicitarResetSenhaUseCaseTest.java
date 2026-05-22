package com.caxinhabet.auth.app;

import static org.assertj.core.api.Assertions.assertThat;

import com.caxinhabet.auth.adapter.notification.LogMagicLinkSender;
import com.caxinhabet.auth.adapter.persistence.SolicitacaoAcessoRepository;
import com.caxinhabet.auth.adapter.persistence.UsuarioEntity;
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

@Testcontainers
@SpringBootTest
class SolicitarResetSenhaUseCaseTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        r.add("spring.datasource.username", POSTGRES::getUsername);
        r.add("spring.datasource.password", POSTGRES::getPassword);
        r.add("app.public-base-url", () -> "http://localhost:3000");
    }

    @Autowired private SolicitarResetSenhaUseCase solicitar;
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
    @DisplayName("E-mail com conta → gera token e envia link de /redefinir-senha")
    void emailComConta() {
        usuarios.save(UsuarioEntity.criar("alice@local"));

        solicitar.executar("alice@local");

        assertThat(solicitacoes.count()).isEqualTo(1L);
        assertThat(sender.linksEnviados()).hasSize(1);
        assertThat(sender.linksEnviados().get(0).linkAbsoluto())
                .contains("/redefinir-senha?token=");
    }

    @Test
    @DisplayName("E-mail sem conta → não cria usuário, não envia link (silencioso)")
    void emailSemConta() {
        solicitar.executar("ninguem@local");

        assertThat(usuarios.count()).isZero();
        assertThat(solicitacoes.count()).isZero();
        assertThat(sender.linksEnviados()).isEmpty();
    }

    @Test
    @DisplayName("E-mail é case-insensitive")
    void caseInsensitive() {
        usuarios.save(UsuarioEntity.criar("bob@local"));
        solicitar.executar("BOB@LOCAL");
        assertThat(solicitacoes.count()).isEqualTo(1L);
    }
}
