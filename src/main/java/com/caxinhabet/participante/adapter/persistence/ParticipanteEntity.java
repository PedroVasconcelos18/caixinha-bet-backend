package com.caxinhabet.participante.adapter.persistence;

import com.caxinhabet.participante.domain.StatusParticipante;
import com.caxinhabet.participante.domain.StatusVencedor;
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

	/**
	 * Sub-estado de Ganhador (Épico 4 v5, Story 4.2). NULLABLE — null = este
	 * Participante NÃO é Ganhador. Preenchido na apuração quando o
	 * Organizador o seleciona; evolui no Repasse (Story 4.6).
	 */
	@Enumerated(EnumType.STRING)
	@Column(name = "status_vencedor", length = 32)
	private StatusVencedor statusVencedor;

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

	/** Sub-estado de Ganhador (Épico 4). {@code null} = não é Ganhador. */
	public StatusVencedor getStatusVencedor() {
		return statusVencedor;
	}

	/**
	 * Story 4.2 (FR-12): marca este Participante como Ganhador, entrando em
	 * {@code vencedor_aguardando_aceite}. Story 4.6 evolui daqui.
	 */
	public void marcarComoVencedor() {
		this.statusVencedor = StatusVencedor.vencedor_aguardando_aceite;
	}

	/** Épico 4: transição do sub-estado de Repasse (Story 4.6). */
	public void setStatusVencedor(StatusVencedor statusVencedor) {
		this.statusVencedor = statusVencedor;
	}
}
