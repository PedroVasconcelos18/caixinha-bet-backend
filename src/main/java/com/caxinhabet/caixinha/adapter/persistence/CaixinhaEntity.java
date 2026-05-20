package com.caxinhabet.caixinha.adapter.persistence;

import com.caxinhabet.caixinha.domain.EstadoCaixinha;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Entidade JPA da tabela {@code caixinha} (Story 2.2).
 *
 * <p>Dinheiro como {@code long valorIngressoCentavos} (NÃO {@code Money})
 * — conversão acontece na borda do use case (decisão da Story 2.2,
 * documentada em {@code package-info.java}).
 *
 * <p>{@code estado} como {@link EnumType#STRING} — mantém o bate-1:1 entre
 * o nome do enum em {@code snake_case} e o valor no SQL.
 */
@Entity
@Table(name = "caixinha")
public class CaixinhaEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 120)
	private String titulo;

	// @Column(name=...) explícito: `CamelCaseToUnderscoresNamingStrategy` só
	// insere underscore antes de UMA letra maiúscula que esteja entre duas
	// MINÚSCULAS. Como o `A`/`B` é a última letra do field name, a regra
	// não dispara e o nome físico vira `ladoa`/`ladob` em vez de
	// `lado_a`/`lado_b`. Forçamos o nome correto aqui — uma das raras
	// exceções permitidas pelo AGENTS.md (linha "Reservar @Column(name=...)
	// para nome legado real").
	@Column(name = "lado_a", nullable = false, length = 80)
	private String ladoA;

	@Column(name = "lado_b", nullable = false, length = 80)
	private String ladoB;

	@Column(nullable = false)
	private long valorIngressoCentavos;

	@Column(nullable = false)
	private int minimoParticipantes;

	@Column(nullable = false)
	private Instant prazoEntrada;

	@Column(nullable = false)
	private Instant dataApuracao;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 32)
	private EstadoCaixinha estado;

	@Column(nullable = false)
	private Long organizadorUsuarioId;

	@Column(nullable = false)
	private Instant criadoEm;

	protected CaixinhaEntity() {
		// JPA.
	}

	public CaixinhaEntity(
			String titulo,
			String ladoA,
			String ladoB,
			long valorIngressoCentavos,
			int minimoParticipantes,
			Instant prazoEntrada,
			Instant dataApuracao,
			EstadoCaixinha estado,
			Long organizadorUsuarioId) {
		this.titulo = titulo;
		this.ladoA = ladoA;
		this.ladoB = ladoB;
		this.valorIngressoCentavos = valorIngressoCentavos;
		this.minimoParticipantes = minimoParticipantes;
		this.prazoEntrada = prazoEntrada;
		this.dataApuracao = dataApuracao;
		this.estado = estado;
		this.organizadorUsuarioId = organizadorUsuarioId;
		this.criadoEm = Instant.now();
	}

	public Long getId() {
		return id;
	}

	public String getTitulo() {
		return titulo;
	}

	public String getLadoA() {
		return ladoA;
	}

	public String getLadoB() {
		return ladoB;
	}

	public long getValorIngressoCentavos() {
		return valorIngressoCentavos;
	}

	public int getMinimoParticipantes() {
		return minimoParticipantes;
	}

	public Instant getPrazoEntrada() {
		return prazoEntrada;
	}

	public Instant getDataApuracao() {
		return dataApuracao;
	}

	public EstadoCaixinha getEstado() {
		return estado;
	}

	public Long getOrganizadorUsuarioId() {
		return organizadorUsuarioId;
	}

	public Instant getCriadoEm() {
		return criadoEm;
	}
}
