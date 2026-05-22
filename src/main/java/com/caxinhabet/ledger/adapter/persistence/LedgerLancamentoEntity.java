package com.caxinhabet.ledger.adapter.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Entidade JPA de um lançamento do ledger de dupla entrada
 * (Story 3.3 v5, FR-8 / AR-6).
 *
 * <p>Toda movimentação financeira é um PAR de lançamentos (débito +
 * crédito) com a mesma {@link #transacaoId}. A invariante "Σ débitos ==
 * Σ créditos por transação" é garantida pelo {@code LedgerService}.
 *
 * <p>Dinheiro como {@code long valorCentavos} (padrão do projeto).
 * {@code conta} e {@code tipo} como String — bate 1:1 com o
 * {@code CHECK IN (...)} da migration V10.
 */
@Entity
@Table(name = "ledger_lancamento")
public class LedgerLancamentoEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private UUID transacaoId;

	@Column(nullable = false, length = 16)
	private String conta;

	@Column(nullable = false, length = 8)
	private String tipo;

	@Column(nullable = false)
	private long valorCentavos;

	@Column(nullable = false)
	private long caixinhaId;

	@Column(nullable = false)
	private long participanteId;

	@Column(nullable = false)
	private String cobrancaId;

	@Column(nullable = false)
	private String eventoRef;

	@Column(nullable = false)
	private Instant criadoEm;

	protected LedgerLancamentoEntity() {
		// JPA.
	}

	public LedgerLancamentoEntity(
			UUID transacaoId,
			String conta,
			String tipo,
			long valorCentavos,
			long caixinhaId,
			long participanteId,
			String cobrancaId,
			String eventoRef) {
		this.transacaoId = transacaoId;
		this.conta = conta;
		this.tipo = tipo;
		this.valorCentavos = valorCentavos;
		this.caixinhaId = caixinhaId;
		this.participanteId = participanteId;
		this.cobrancaId = cobrancaId;
		this.eventoRef = eventoRef;
		this.criadoEm = Instant.now();
	}

	public Long getId() {
		return id;
	}

	public UUID getTransacaoId() {
		return transacaoId;
	}

	public String getConta() {
		return conta;
	}

	public String getTipo() {
		return tipo;
	}

	public long getValorCentavos() {
		return valorCentavos;
	}

	public long getCaixinhaId() {
		return caixinhaId;
	}

	public long getParticipanteId() {
		return participanteId;
	}

	public String getCobrancaId() {
		return cobrancaId;
	}

	public String getEventoRef() {
		return eventoRef;
	}

	public Instant getCriadoEm() {
		return criadoEm;
	}
}
