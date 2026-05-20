package com.caxinhabet.auth.adapter.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Entidade JPA da tabela {@code solicitacao_acesso} (Story 2.1).
 *
 * <p>Espelha a invariante do domínio ({@code SolicitacaoAcesso}):
 * {@code tokenHash} UNIQUE, {@code consumidoEm} nullable.
 *
 * <p>Não há FK explícita no JPA para {@code UsuarioEntity} (Lazy/Eager,
 * cascades, etc.) — usamos {@code usuarioId} como long simples. Razão:
 * o caso de uso traz o {@code UsuarioEntity} via {@code UsuarioRepository}
 * separadamente; não precisamos do grafo navegável, e essa simplicidade
 * casa com a lição da Story 1.4 (mappers manuais, sem MapStruct).
 */
@Entity
@Table(name = "solicitacao_acesso")
public class SolicitacaoAcessoEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private Long usuarioId;

	@Column(nullable = false, unique = true, length = 64)
	private String tokenHash;

	@Column(length = 512)
	private String redirectTo;

	@Column(nullable = false)
	private Instant criadoEm;

	@Column(nullable = false)
	private Instant expiraEm;

	@Column
	private Instant consumidoEm;

	protected SolicitacaoAcessoEntity() {
		// JPA.
	}

	public SolicitacaoAcessoEntity(
			Long usuarioId,
			String tokenHash,
			String redirectTo,
			Instant criadoEm,
			Instant expiraEm) {
		this.usuarioId = usuarioId;
		this.tokenHash = tokenHash;
		this.redirectTo = redirectTo;
		this.criadoEm = criadoEm;
		this.expiraEm = expiraEm;
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

	public String getRedirectTo() {
		return redirectTo;
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

	public void marcarConsumida(Instant agora) {
		this.consumidoEm = agora;
	}
}
