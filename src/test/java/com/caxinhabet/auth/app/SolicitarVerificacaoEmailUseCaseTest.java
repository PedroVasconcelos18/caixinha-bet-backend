package com.caxinhabet.auth.app;

import static org.assertj.core.api.Assertions.assertThat;

import com.caxinhabet.auth.adapter.notification.LogMagicLinkSender;
import com.caxinhabet.auth.adapter.persistence.UsuarioEntity;
import com.caxinhabet.auth.adapter.persistence.UsuarioRepository;
import com.caxinhabet.auth.adapter.persistence.VerificacaoEmailRepository;
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
class SolicitarVerificacaoEmailUseCaseTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        r.add("spring.datasource.username", POSTGRES::getUsername);
        r.add("spring.datasource.password", POSTGRES::getPassword);
        r.add("app.public-base-url", () -> "http://localhost:3000");
    }

    @Autowired private SolicitarVerificacaoEmailUseCase solicitar;
    @Autowired private UsuarioRepository usuarios;
    @Autowired private VerificacaoEmailRepository verificacoes;
    @Autowired private LogMagicLinkSender sender;

    @BeforeEach
    void setUp() {
        verificacoes.deleteAll();
        usuarios.deleteAll();
        sender.limpar();
    }

    @Test
    @DisplayName("Persiste token + envia link de /verificar-email")
    void persisteEEnvia() {
        UsuarioEntity u = usuarios.save(UsuarioEntity.criar("alice@local"));

        solicitar.executar(u);

        assertThat(verificacoes.count()).isEqualTo(1L);
        assertThat(sender.linksEnviados()).hasSize(1);
        assertThat(sender.linksEnviados().get(0).linkAbsoluto())
                .contains("/verificar-email?token=");
    }
}
