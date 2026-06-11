package com.caxinhabet.participante.adapter.persistence;

import com.caxinhabet.participante.domain.StatusParticipante;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ParticipanteRepository extends JpaRepository<ParticipanteEntity, Long> {

	List<ParticipanteEntity> findByCaixinhaIdOrderByCriadoEmAsc(long caixinhaId);

	Optional<ParticipanteEntity> findByCaixinhaIdAndEmail(long caixinhaId, String email);

	List<ParticipanteEntity> findByUsuarioId(Long usuarioId);

	/**
	 * Convites ainda não vinculados a um Usuário (Participante criado por e-mail
	 * na Story 2.4, com {@code usuario_id} NULL). Usado no cadastro
	 * ({@code RegistrarUsuarioUseCase}) para vincular os convites pendentes do
	 * e-mail ao novo Usuário, de modo que apareçam no dashboard
	 * ({@link #findByUsuarioId}) assim que ele entrar — sem depender de ele
	 * abrir o link do convite. O {@code email} é {@code citext}, então casa
	 * sem diferenciar maiúsculas/minúsculas.
	 */
	List<ParticipanteEntity> findByEmailAndUsuarioIdIsNull(String email);

	/**
	 * Story 3.1 (FR-6): conta Participantes por caixinha + conjunto de status.
	 * Usado pelo {@code AvaliarTransicaoAceitesService} para decidir se a
	 * Caixinha deve transicionar para {@code coletando_pagamentos}.
	 *
	 * <p>"Aceito ou superior" = {@code {aceito, pagamento_iniciado, pago}}
	 * — alinha com PRD §FR-6 (já contempla estados que aparecem só nas
	 * Stories 3.2/3.3 sem precisar retroajustar a query).
	 */
	long countByCaixinhaIdAndStatusIn(
			long caixinhaId, Collection<StatusParticipante> status);

	/**
	 * Story 4.2 (FR-12): Participantes de uma Caixinha num dado status,
	 * em ordem cronológica de entrada. A apuração usa com {@code pago}
	 * para achar os palpiteiros (e a ordem serve de base para o resíduo
	 * de centavos — Story 4.3 refina pela confirmação da cobrança).
	 */
	List<ParticipanteEntity> findByCaixinhaIdAndStatusOrderByCriadoEmAsc(
			long caixinhaId, StatusParticipante status);
}
