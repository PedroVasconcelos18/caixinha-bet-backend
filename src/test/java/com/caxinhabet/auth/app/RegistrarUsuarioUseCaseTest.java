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
import com.caxinhabet.caixinha.adapter.persistence.CaixinhaEntity;
import com.caxinhabet.caixinha.adapter.persistence.CaixinhaRepository;
import com.caxinhabet.caixinha.domain.EstadoCaixinha;
import com.caxinhabet.participante.adapter.persistence.ParticipanteEntity;
import com.caxinhabet.participante.adapter.persistence.ParticipanteRepository;
import com.caxinhabet.participante.domain.StatusParticipante;
import java.time.Instant;
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
    @Autowired private CaixinhaRepository caixinhas;
    @Autowired private ParticipanteRepository participantes;

    @BeforeEach
    void setUp() {
        participantes.deleteAll();
        caixinhas.deleteAll();
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
    @DisplayName(
            "Cadastro vincula convites pendentes (mesmo e-mail, usuario_id NULL) ao novo usuário"
                    + " — para aparecerem no dashboard")
    void vinculaConvitesPendentes() {
        // Convite criado por e-mail ANTES de a usuária existir: Participante com
        // usuario_id NULL. É a situação do convidado que ainda não tem conta.
        long donoId = usuarios.save(UsuarioEntity.criar("dono@local")).getId();
        CaixinhaEntity c =
                caixinhas.save(
                        new CaixinhaEntity(
                                "Copa",
                                "Brasil",
                                "Marrocos",
                                4000L,
                                3,
                                1,
                                Instant.now().plusSeconds(86400 * 30),
                                Instant.now().plusSeconds(86400 * 30 + 3600),
                                EstadoCaixinha.coletando_convites,
                                donoId));
        // O EnviarConvitesUseCase grava o e-mail já normalizado em minúsculas;
        // reproduzimos a mesma forma aqui.
        participantes.save(
                new ParticipanteEntity(
                        c.getId(), null, "alice@local", false, StatusParticipante.convidado));

        registrar.executar("Alice", "529.982.247-25", "alice@local", "senha1234", NASC);

        long aliceId = usuarios.findByEmail("alice@local").orElseThrow().getId();
        ParticipanteEntity p =
                participantes.findByCaixinhaIdAndEmail(c.getId(), "alice@local").orElseThrow();
        assertThat(p.getUsuarioId()).isEqualTo(aliceId);
        // O status do convite NÃO muda no cadastro (continua `convidado` até aceitar).
        assertThat(p.getStatus()).isEqualTo(StatusParticipante.convidado);
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
