package com.caxinhabet.pagamento.adapter.persistence;

import com.caxinhabet.pagamento.domain.EstadoCobranca;
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
 * Entidade JPA da tabela {@code pagamento_cobranca} (Story 3.2 v5, FR-7).
 *
 * <p>Uma cobrança PIX gerada para um Participante. O modelo é "máximo
 * uma {@code ativa} por Participante" (FR-7) — garantido pelo índice
 * único parcial da migration V9.
 *
 * <p>Dinheiro como {@code long valorCentavos} (padrão do projeto). Estado
 * via {@link EnumType#STRING} — bate 1:1 com o {@code CHECK IN (...)} do DB.
 */
@Entity
@Table(name = "pagamento_cobranca")
public class CobrancaEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private Long participanteId;

	@Column(nullable = false)
	private Long caixinhaId;

	@Column(nullable = false, unique = true)
	private String cobrancaId;

	// @Column(name=...) explícito: a naming strategy só insere underscore
	// antes de uma maiúscula entre DUAS minúsculas. Em "copiaECola" o 'E'
	// está entre 'a' e 'C' (maiúsculo) — a regra não dispara e o nome
	// físico viraria "copiaecola". Forçamos o nome da migration V9.
	// Mesma exceção documentada em CaixinhaEntity.ladoA.
	@Column(name = "copia_e_cola", nullable = false, columnDefinition = "text")
	private String copiaECola;

	@Column(name = "qr_code_base64", nullable = false, columnDefinition = "text")
	private String qrCodeBase64;

	@Column(nullable = false)
	private long valorCentavos;

	@Column(nullable = false)
	private Instant expiraEm;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 16)
	private EstadoCobranca estado;

	@Column(nullable = false)
	private Instant criadoEm;

	/**
	 * Instante em que a cobrança foi CONFIRMADA pelo webhook do Asaas
	 * (Épico 4 v5, Story 4.3). NULLABLE — null enquanto não confirmada.
	 * É a "ordem de pagamento" usada para o resíduo de centavos do Prêmio.
	 */
	@Column(name = "confirmada_em")
	private Instant confirmadaEm;

	protected CobrancaEntity() {
		// JPA.
	}

	public CobrancaEntity(
			Long participanteId,
			Long caixinhaId,
			String cobrancaId,
			String copiaECola,
			String qrCodeBase64,
			long valorCentavos,
			Instant expiraEm) {
		this.participanteId = participanteId;
		this.caixinhaId = caixinhaId;
		this.cobrancaId = cobrancaId;
		this.copiaECola = copiaECola;
		this.qrCodeBase64 = qrCodeBase64;
		this.valorCentavos = valorCentavos;
		this.expiraEm = expiraEm;
		this.estado = EstadoCobranca.ativa;
		this.criadoEm = Instant.now();
	}

	public Long getId() {
		return id;
	}

	public Long getParticipanteId() {
		return participanteId;
	}

	public Long getCaixinhaId() {
		return caixinhaId;
	}

	public String getCobrancaId() {
		return cobrancaId;
	}

	public String getCopiaECola() {
		return copiaECola;
	}

	public String getQrCodeBase64() {
		return qrCodeBase64;
	}

	public long getValorCentavos() {
		return valorCentavos;
	}

	public Instant getExpiraEm() {
		return expiraEm;
	}

	public EstadoCobranca getEstado() {
		return estado;
	}

	public Instant getCriadoEm() {
		return criadoEm;
	}

	/** Instante da confirmação ({@code null} se ainda não confirmada). */
	public Instant getConfirmadaEm() {
		return confirmadaEm;
	}

	/**
	 * Transiciona o estado da cobrança (Story 3.2). Setter cru não é
	 * exposto — toda mutação de estado entra por aqui.
	 *
	 * <p>Ao transicionar para {@link EstadoCobranca#confirmada}, registra
	 * {@code confirmadaEm} (Épico 4 — ordem de pagamento para o resíduo
	 * do Prêmio), se ainda não estava registrada.
	 */
	public void transicionarPara(EstadoCobranca novo) {
		if (novo == null) {
			throw new IllegalArgumentException("estado-alvo não pode ser null");
		}
		if (novo == EstadoCobranca.confirmada && this.confirmadaEm == null) {
			this.confirmadaEm = Instant.now();
		}
		this.estado = novo;
	}

	/** {@code true} se a cobrança venceu ({@code expiraEm} já passou). */
	public boolean venceu(Instant agora) {
		return agora.isAfter(expiraEm);
	}
}
