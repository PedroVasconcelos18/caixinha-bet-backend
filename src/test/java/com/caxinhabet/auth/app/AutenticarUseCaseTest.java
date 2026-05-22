package com.caxinhabet.auth.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.caxinhabet.auth.adapter.persistence.UsuarioEntity;
import com.caxinhabet.auth.adapter.persistence.UsuarioRepository;
import com.caxinhabet.auth.adapter.session.SessaoStore;
import com.caxinhabet.auth.domain.CredenciaisInvalidasException;
import com.caxinhabet.auth.domain.SessaoUsuario;
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
class AutenticarUseCaseTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        r.add("spring.datasource.username", POSTGRES::getUsername);
        r.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired private AutenticarUseCase autenticar;
    @Autowired private RegistrarUsuarioUseCase registrar;
    @Autowired private UsuarioRepository usuarios;
    @Autowired private SessaoStore sessaoStore;

    @BeforeEach
    void setUp() {
        usuarios.deleteAll();
        sessaoStore.limpar();
    }

    @Test
    @DisplayName("E-mail e senha corretos → abre sessão")
    void loginValido() {
        registrar.executar("Alice", "529.982.247-25", "alice@local", "senha1234");
        sessaoStore.limpar(); // descarta a sessão do cadastro

        SessaoUsuario sessao = autenticar.executar("alice@local", "senha1234");

        assertThat(sessao.email()).isEqualTo("alice@local");
        assertThat(sessaoStore.tamanho()).isEqualTo(1);
    }

    @Test
    @DisplayName("Login é case-insensitive no e-mail")
    void loginCaseInsensitive() {
        registrar.executar("Bob", "529.982.247-25", "bob@local", "senha1234");
        assertThat(autenticar.executar("BOB@LOCAL", "senha1234").email())
                .isEqualTo("bob@local");
    }

    @Test
    @DisplayName("Senha errada → CredenciaisInvalidasException")
    void senhaErrada() {
        registrar.executar("Carol", "529.982.247-25", "carol@local", "senha1234");
        assertThatThrownBy(() -> autenticar.executar("carol@local", "errada999"))
                .isInstanceOf(CredenciaisInvalidasException.class);
    }

    @Test
    @DisplayName("E-mail inexistente → CredenciaisInvalidasException (mensagem genérica)")
    void emailInexistente() {
        assertThatThrownBy(() -> autenticar.executar("ninguem@local", "senha1234"))
                .isInstanceOf(CredenciaisInvalidasException.class);
    }

    @Test
    @DisplayName("Usuário legado sem senha_hash → CredenciaisInvalidasException")
    void usuarioLegadoSemSenha() {
        usuarios.save(UsuarioEntity.criar("legado@local")); // sem senha
        assertThatThrownBy(() -> autenticar.executar("legado@local", "senha1234"))
                .isInstanceOf(CredenciaisInvalidasException.class);
    }
}
