package com.caxinhabet.participante.adapter.persistence;

import com.caxinhabet.participante.domain.StatusParticipante;
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
 * Entidade JPA da tabela {@code participante} (Story 2.2).
 *
 * <p>{@code usuarioId} é nullable (PRD §3 + Story 2.4 contexto: convite
 * por e-mail cria Participante antes do convidado autenticar).
 *
 * <p>{@code email} como {@code citext} para case-insensitive automática
 * (mesmo padrão de {@code usuario.email} — Story 2.1).
 */
@Entity
@Table(name = "participante")
public class ParticipanteEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private long caixinhaId;

	@Column
	private Long usuarioId;

	@Column(nullable = false, columnDefinition = "citext")
	private String email;

	@Column(nullable = false)
	private boolean dono;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 32)
	private StatusParticipante status;

	@Column(nullable = false)
	private Instant criadoEm;

	/**
	 * Palpite escolhido pelo Participante (Story 2.4 preparando 2.5).
	 * NULLABLE — null = ainda sem palpite (status `convidado` ou `aceito`
	 * sem palpite escolhido). Story 2.5 implementa a regra de quando virar
	 * obrigatório (PRD §4.3: necessário para iniciar pagamento).
	 */
	@Column
	private Long palpiteResultadoPossivelId;

	protected ParticipanteEntity() {
		// JPA.
	}

	public ParticipanteEntity(
			long caixinhaId,
			Long usuarioId,
			String email,
			boolean dono,
			StatusParticipante status) {
		this.caixinhaId = caixinhaId;
		this.usuarioId = usuarioId;
		this.email = email;
		this.dono = dono;
		this.status = status;
		this.criadoEm = Instant.now();
	}

	public Long getId() {
		return id;
	}

	public long getCaixinhaId() {
		return caixinhaId;
	}

	public Long getUsuarioId() {
		return usuarioId;
	}

	public String getEmail() {
		return email;
	}

	public boolean isDono() {
		return dono;
	}

	public StatusParticipante getStatus() {
		return status;
	}

	public Instant getCriadoEm() {
		return criadoEm;
	}

	public Long getPalpiteResultadoPossivelId() {
		return palpiteResultadoPossivelId;
	}

	public void setPalpiteResultadoPossivelId(Long id) {
		this.palpiteResultadoPossivelId = id;
	}

	/**
	 * Story 2.5: vincula o Usuario autenticado no primeiro acesso pós-convite.
	 * Participante criado por convite (Story 2.4) começa com usuario_id=NULL
	 * até o convidado autenticar pela primeira vez.
	 */
	public void setUsuarioId(Long id) {
		this.usuarioId = id;
	}

	/** Story 2.5: transições de status (convidado → aceito, etc.). */
	public void setStatus(StatusParticipante status) {
		this.status = status;
	}
}
