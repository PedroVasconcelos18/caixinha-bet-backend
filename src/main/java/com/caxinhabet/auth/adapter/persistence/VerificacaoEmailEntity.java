package com.caxinhabet.auth.adapter.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Entidade JPA da tabela {@code verificacao_email} (Minha Conta, 2026-05).
 *
 * <p>Espelha o mesmo modelo de {@link SolicitacaoAcessoEntity}, mas vive em
 * tabela própria — tokens de verificação não devem ser intercambiáveis com
 * tokens de magic link / reset de senha.
 */
@Entity
@Table(name = "verificacao_email")
public class VerificacaoEmailEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    @Column(name = "expira_em", nullable = false)
    private Instant expiraEm;

    @Column(name = "consumido_em")
    private Instant consumidoEm;

    protected VerificacaoEmailEntity() {
        // JPA.
    }

    private VerificacaoEmailEntity(Long usuarioId, String tokenHash, Instant expiraEm) {
        this.usuarioId = usuarioId;
        this.tokenHash = tokenHash;
        this.criadoEm = Instant.now();
        this.expiraEm = expiraEm;
    }

    public static VerificacaoEmailEntity criar(Long usuarioId, String tokenHash, Instant expiraEm) {
        return new VerificacaoEmailEntity(usuarioId, tokenHash, expiraEm);
    }

    public Long getId() {
        return id;
    }

    public Long getUsuarioId() {
        return usuarioId;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public Instant getCriadoEm() {
        return criadoEm;
    }

    public Instant getExpiraEm() {
        return expiraEm;
    }

    public Instant getConsumidoEm() {
        return consumidoEm;
    }

    public void marcarConsumido(Instant agora) {
        this.consumidoEm = agora;
    }
}
