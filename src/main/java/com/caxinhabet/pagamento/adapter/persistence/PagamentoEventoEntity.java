package com.caxinhabet.pagamento.adapter.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Entidade JPA do evento de pagamento (Story 1.4, AC-4).
 *
 * <p>Esta classe vive em {@code adapter.persistence} (não em {@code domain})
 * de propósito — o domínio não conhece JPA. O webhook controller usa o
 * repositório direto nesta story (gate-only); o Épico 3 introduz um caso
 * de uso de aplicação que reduz o acoplamento.
 *
 * <p>Mapeamento: nomes Java em camelCase, colunas físicas em snake_case via
 * {@code CamelCaseToUnderscoresNamingStrategy} (Story 1.1/1.3). {@code @Column}
 * só onde o nome físico não bate trivialmente (ex.: {@code event_id} bate;
 * não precisa de {@code @Column}). O {@code payload} usa {@link SqlTypes#JSON}
 * (Hibernate 6) para mapear {@code Map} ⇄ {@code JSONB} sem fricção.
 */
@Entity
@Table(name = "pagamento_evento")
public class PagamentoEventoEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, unique = true)
	private String eventId;

	@Column(nullable = false)
	private Instant providerTimestamp;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(nullable = false, columnDefinition = "jsonb")
	private Map<String, Object> payload;

	@Column(nullable = false)
	private Instant recebidoEm;

	// Story 3.3: id da cobrança do PSP (pay_xxx) que este evento afeta.
	// NULLABLE — eventos de gate (Story 1.5) e eventos não-de-cobrança
	// não preenchem. Usado pela regra de ordenação (FR-8 / NFR-2).
	@Column
	private String cobrancaId;

	protected PagamentoEventoEntity() {
		// JPA exige construtor sem-args.
	}

	public PagamentoEventoEntity(String eventId, Instant providerTimestamp, Map<String, Object> payload) {
		this(eventId, providerTimestamp, payload, null);
	}

	public PagamentoEventoEntity(
			String eventId,
			Instant providerTimestamp,
			Map<String, Object> payload,
			String cobrancaId) {
		this.eventId = eventId;
		this.providerTimestamp = providerTimestamp;
		this.payload = payload;
		this.cobrancaId = cobrancaId;
		this.recebidoEm = Instant.now();
	}

	public Long getId() {
		return id;
	}

	public String getEventId() {
		return eventId;
	}

	public Instant getProviderTimestamp() {
		return providerTimestamp;
	}

	public Map<String, Object> getPayload() {
		return payload;
	}

	public Instant getRecebidoEm() {
		return recebidoEm;
	}

	public String getCobrancaId() {
		return cobrancaId;
	}
}
