package com.caxinhabet.auth.adapter.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Entidade JPA da tabela {@code usuario} (Story 2.1).
 *
 * <p>O {@code email} é {@code CITEXT} no Postgres — comparação
 * case-insensitive na borda do banco. A coluna precisa de
 * {@code @Column(columnDefinition = "citext")} para o JPA não tentar
 * gerar um {@code VARCHAR} e brigar com o schema do Flyway no
 * {@code validate}.
 *
 * <p>Setters privados / construtor sem-args protegido — só o factory
 * estático {@link #criar(String)} ou JPA podem instanciar.
 */
@Entity
@Table(name = "usuario")
public class UsuarioEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, columnDefinition = "citext")
	private String email;

	@Column(nullable = false)
	private Instant criadoEm;

	protected UsuarioEntity() {
		// JPA exige construtor sem-args.
	}

	private UsuarioEntity(String email, Instant criadoEm) {
		this.email = email;
		this.criadoEm = criadoEm;
	}

	public static UsuarioEntity criar(String email) {
		return new UsuarioEntity(email, Instant.now());
	}

	public Long getId() {
		return id;
	}

	public String getEmail() {
		return email;
	}

	public Instant getCriadoEm() {
		return criadoEm;
	}
}
