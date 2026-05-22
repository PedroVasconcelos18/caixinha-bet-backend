package com.caxinhabet.pagamento.adapter.persistence;

import com.caxinhabet.pagamento.domain.EstadoPayout;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Agregado JPA da tabela {@code payout} (Épico 4 v5, FR-13).
 *
 * <p>Um Payout = o Repasse do prêmio a UM Ganhador. Criado na apuração
 * (Story 4.3) com {@code valorCentavos} já calculado; o {@code payoutId}
 * (UUID) é a chave de idempotência do disparo do PIX (Story 4.6).
 *
 * <p>Dinheiro como {@code long valorCentavos} (padrão do projeto).
 */
@Entity
@Table(name = "payout")
public class PayoutEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, unique = true)
	private UUID payoutId;

	@Column(nullable = false)
	private Long caixinhaId;

	@Column(nullable = false)
	private Long participanteId;

	@Column(nullable = false)
	private long valorCentavos;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private EstadoPayout estado;

	@Column(length = 120)
	private String transferenciaId;

	@Column(columnDefinition = "text")
	private String comprovante;

	@Column(nullable = false)
	private Instant criadoEm;

	@Column(nullable = false)
	private Instant atualizadoEm;

	protected PayoutEntity() {
		// JPA.
	}

	public PayoutEntity(long caixinhaId, long participanteId, long valorCentavos) {
		this.payoutId = UUID.randomUUID();
		this.caixinhaId = caixinhaId;
		this.participanteId = participanteId;
		this.valorCentavos = valorCentavos;
		this.estado = EstadoPayout.pendente_aceite;
		this.criadoEm = Instant.now();
		this.atualizadoEm = this.criadoEm;
	}

	public Long getId() {
		return id;
	}

	public UUID getPayoutId() {
		return payoutId;
	}

	public Long getCaixinhaId() {
		return caixinhaId;
	}

	public Long getParticipanteId() {
		return participanteId;
	}

	public long getValorCentavos() {
		return valorCentavos;
	}

	public EstadoPayout getEstado() {
		return estado;
	}

	public String getTransferenciaId() {
		return transferenciaId;
	}

	public String getComprovante() {
		return comprovante;
	}

	public Instant getCriadoEm() {
		return criadoEm;
	}

	public Instant getAtualizadoEm() {
		return atualizadoEm;
	}

	/** Story 4.6: o Ganhador aceitou e o PIX foi disparado no Asaas. */
	public void marcarTransferindo(String transferenciaId) {
		this.estado = EstadoPayout.transferindo;
		this.transferenciaId = transferenciaId;
		this.atualizadoEm = Instant.now();
	}

	/** Story 4.6: o Asaas confirmou a transferência. */
	public void marcarPago(String comprovante) {
		this.estado = EstadoPayout.pago;
		this.comprovante = comprovante;
		this.atualizadoEm = Instant.now();
	}

	/** Story 4.6: o Asaas recusou (chave PIX inválida). */
	public void marcarFalha() {
		this.estado = EstadoPayout.falha;
		this.atualizadoEm = Instant.now();
	}
}
