package com.caxinhabet.auth.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.caxinhabet.auth.adapter.notification.LogMagicLinkSender;
import com.caxinhabet.auth.adapter.persistence.UsuarioEntity;
import com.caxinhabet.auth.adapter.persistence.UsuarioRepository;
import com.caxinhabet.auth.adapter.persistence.VerificacaoEmailRepository;
import com.caxinhabet.auth.adapter.session.SessaoStore;
import com.caxinhabet.auth.domain.CpfJaCadastradoException;
import com.caxinhabet.auth.domain.EmailJaCadastradoException;
import java.time.LocalDate;
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
class RegistrarUsuarioUseCaseTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        r.add("spring.datasource.username", POSTGRES::getUsername);
        r.add("spring.datasource.password", POSTGRES::getPassword);
        r.add("app.public-base-url", () -> "http://localhost:3000");
    }

    private static final LocalDate NASC = LocalDate.of(2000, 1, 1);

    @Autowired private RegistrarUsuarioUseCase registrar;
    @Autowired private UsuarioRepository usuarios;
    @Autowired private VerificacaoEmailRepository verificacoes;
    @Autowired private SessaoStore sessaoStore;
    @Autowired private LogMagicLinkSender sender;
    @Autowired private PasswordEncoder encoder;

    @BeforeEach
    void setUp() {
        verificacoes.deleteAll();
        usuarios.deleteAll();
        sessaoStore.limpar();
        sender.limpar();
    }

    @Test
    @DisplayName("Cadastro válido cria usuário com hash + email_verificado=false + dispara verificação; NÃO abre sessão")
    void cadastroValido() {
        registrar.executar("Alice", "529.982.247-25", "alice@local", "senha1234", NASC);

        UsuarioEntity u = usuarios.findByEmail("alice@local").orElseThrow();
        assertThat(u.getNomeCompleto()).isEqualTo("Alice");
        assertThat(u.getCpf()).isEqualTo("52998224725");
        assertThat(u.getSenhaHash()).isNotNull();
        assertThat(encoder.matches("senha1234", u.getSenhaHash())).isTrue();
        assertThat(u.getDataNascimento()).isEqualTo(NASC);
        assertThat(u.isEmailVerificado()).isFalse();

        assertThat(verificacoes.count()).isEqualTo(1L);
        assertThat(sender.linksEnviados()).hasSize(1);
        assertThat(sessaoStore.tamanho()).isZero();
    }

    @Test
    @DisplayName("E-mail é normalizado para lowercase")
    void emailNormalizado() {
        registrar.executar("Bob", "529.982.247-25", "BOB@Local", "senha1234", NASC);
        assertThat(usuarios.findByEmail("bob@local")).isPresent();
    }

    @Test
    @DisplayName("E-mail duplicado → EmailJaCadastradoException")
    void emailDuplicado() {
        registrar.executar("Carol", "529.982.247-25", "carol@local", "senha1234", NASC);
        assertThatThrownBy(
                        () ->
                                registrar.executar(
                                        "Carol2",
                                        "168.995.350-09",
                                        "carol@local",
                                        "senha1234",
                                        NASC))
                .isInstanceOf(EmailJaCadastradoException.class);
    }

    @Test
    @DisplayName("CPF duplicado → CpfJaCadastradoException")
    void cpfDuplicado() {
        registrar.executar("Dave", "529.982.247-25", "dave@local", "senha1234", NASC);
        assertThatThrownBy(
                        () ->
                                registrar.executar(
                                        "Dave2",
                                        "529.982.247-25",
                                        "dave2@local",
                                        "senha1234",
                                        NASC))
                .isInstanceOf(CpfJaCadastradoException.class);
    }

    @Test
    @DisplayName("Nome em branco → IllegalArgumentException")
    void nomeVazio() {
        assertThatThrownBy(
                        () ->
                                registrar.executar(
                                        " ", "529.982.247-25", "x@local", "senha1234", NASC))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("CPF inválido → IllegalArgumentException (do value object Cpf)")
    void cpfInvalido() {
        assertThatThrownBy(
                        () ->
                                registrar.executar(
                                        "Eva",
                                        "111.111.111-11",
                                        "eva@local",
                                        "senha1234",
                                        NASC))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Senha fraca → IllegalArgumentException (do value object Senha)")
    void senhaFraca() {
        assertThatThrownBy(
                        () ->
                                registrar.executar(
                                        "Fran",
                                        "529.982.247-25",
                                        "fran@local",
                                        "abc",
                                        NASC))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Menor de 18 → IllegalArgumentException (do value object DataNascimento)")
    void menorDeIdade() {
        assertThatThrownBy(
                        () ->
                                registrar.executar(
                                        "Gigi",
                                        "529.982.247-25",
                                        "gigi@local",
                                        "senha1234",
                                        LocalDate.now().minusYears(17)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
