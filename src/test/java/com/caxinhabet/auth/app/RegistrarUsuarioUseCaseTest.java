package com.caxinhabet.auth.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.caxinhabet.auth.adapter.persistence.UsuarioEntity;
import com.caxinhabet.auth.adapter.persistence.UsuarioRepository;
import com.caxinhabet.auth.adapter.session.SessaoStore;
import com.caxinhabet.auth.domain.CpfJaCadastradoException;
import com.caxinhabet.auth.domain.EmailJaCadastradoException;
import com.caxinhabet.auth.domain.SessaoUsuario;
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
    }

    @Autowired private RegistrarUsuarioUseCase registrar;
    @Autowired private UsuarioRepository usuarios;
    @Autowired private SessaoStore sessaoStore;
    @Autowired private PasswordEncoder encoder;

    @BeforeEach
    void setUp() {
        usuarios.deleteAll();
        sessaoStore.limpar();
    }

    @Test
    @DisplayName("Cadastro válido cria usuário com hash, perfil e abre sessão")
    void cadastroValido() {
        SessaoUsuario sessao =
                registrar.executar("Alice", "529.982.247-25", "alice@local", "senha1234");

        assertThat(sessao.email()).isEqualTo("alice@local");
        UsuarioEntity u = usuarios.findByEmail("alice@local").orElseThrow();
        assertThat(u.getNomeCompleto()).isEqualTo("Alice");
        assertThat(u.getCpf()).isEqualTo("52998224725");
        assertThat(u.getSenhaHash()).isNotNull();
        assertThat(encoder.matches("senha1234", u.getSenhaHash())).isTrue();
        assertThat(sessaoStore.tamanho()).isEqualTo(1);
    }

    @Test
    @DisplayName("E-mail é normalizado para lowercase")
    void emailNormalizado() {
        registrar.executar("Bob", "529.982.247-25", "BOB@Local", "senha1234");
        assertThat(usuarios.findByEmail("bob@local")).isPresent();
    }

    @Test
    @DisplayName("E-mail duplicado → EmailJaCadastradoException")
    void emailDuplicado() {
        registrar.executar("Carol", "529.982.247-25", "carol@local", "senha1234");
        assertThatThrownBy(
                        () ->
                                registrar.executar(
                                        "Carol2", "168.995.350-09", "carol@local", "senha1234"))
                .isInstanceOf(EmailJaCadastradoException.class);
    }

    @Test
    @DisplayName("CPF duplicado → CpfJaCadastradoException")
    void cpfDuplicado() {
        registrar.executar("Dave", "529.982.247-25", "dave@local", "senha1234");
        assertThatThrownBy(
                        () ->
                                registrar.executar(
                                        "Dave2", "529.982.247-25", "dave2@local", "senha1234"))
                .isInstanceOf(CpfJaCadastradoException.class);
    }

    @Test
    @DisplayName("Nome em branco → IllegalArgumentException")
    void nomeVazio() {
        assertThatThrownBy(
                        () -> registrar.executar(" ", "529.982.247-25", "x@local", "senha1234"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("CPF inválido → IllegalArgumentException (do value object Cpf)")
    void cpfInvalido() {
        assertThatThrownBy(
                        () ->
                                registrar.executar(
                                        "Eva", "111.111.111-11", "eva@local", "senha1234"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Senha fraca → IllegalArgumentException (do value object Senha)")
    void senhaFraca() {
        assertThatThrownBy(
                        () -> registrar.executar("Fran", "529.982.247-25", "fran@local", "abc"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
