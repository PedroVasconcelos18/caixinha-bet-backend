package com.caxinhabet.caixinha.adapter.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Entidade JPA da tabela {@code resultado_possivel} (Story 2.2).
 *
 * <p>Sem {@code @ManyToOne} para {@code CaixinhaEntity} — guardamos
 * {@code caixinhaId} como {@code long} simples (padrão Story 2.1 com
 * {@code SolicitacaoAcessoEntity} → {@code UsuarioEntity}). O use case
 * lê via {@code findByCaixinhaIdOrderByOrdemAsc} no
 * {@code ResultadoPossivelRepository}.
 *
 * <p>{@code ordem} é 0-indexed; UNIQUE(caixinha_id, ordem) no DB garante
 * unicidade (defesa final). O wizard sempre envia rótulos numerados.
 */
@Entity
@Table(name = "resultado_possivel")
public class ResultadoPossivelEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private long caixinhaId;

	@Column(nullable = false)
	private int ordem;

	@Column(nullable = false, length = 120)
	private String rotulo;

	protected ResultadoPossivelEntity() {
		// JPA.
	}

	public ResultadoPossivelEntity(long caixinhaId, int ordem, String rotulo) {
		this.caixinhaId = caixinhaId;
		this.ordem = ordem;
		this.rotulo = rotulo;
	}

	public Long getId() {
		return id;
	}

	public long getCaixinhaId() {
		return caixinhaId;
	}

	public int getOrdem() {
		return ordem;
	}

	public String getRotulo() {
		return rotulo;
	}
}
