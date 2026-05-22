package com.caxinhabet.caixinha.app;

import com.caxinhabet.caixinha.adapter.persistence.CaixinhaEntity;
import com.caxinhabet.caixinha.adapter.persistence.CaixinhaRepository;
import com.caxinhabet.participante.adapter.persistence.ParticipanteEntity;
import com.caxinhabet.participante.adapter.persistence.ParticipanteRepository;
import com.caxinhabet.participante.domain.StatusParticipante;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Aceitar convite (Story 2.5, AC-2, AC-5, AC-6).
 *
 * <p>Idempotente: chamar 2x (ou em status superior) devolve 200 com o
 * Participante atual, sem erro. Razão: UX — convidado pode clicar em
 * "Aceitar" de outro device sem saber.
 *
 * <p>Anti-enumeração: se o usuário autenticado NÃO é Participante da
 * Caixinha (não foi convidado), responde 404 (não 403) — mesmo padrão
 * de {@code GET /caixinhas/{id}} da Story 2.2.
 *
 * <p>Vincula {@code usuario_id} no primeiro acesso autenticado (AC-6):
 * Participantes criados por convite (Story 2.4) começam com
 * {@code usuario_id=NULL}. Ao aceitar autenticado, vinculamos.
 */
@Service
public class AceitarConviteUseCase {

	private final CaixinhaRepository caixinhas;
	private final ParticipanteRepository participantes;
	private final AvaliarTransicaoAceitesService avaliarTransicao;

	public AceitarConviteUseCase(
			CaixinhaRepository caixinhas,
			ParticipanteRepository participantes,
			AvaliarTransicaoAceitesService avaliarTransicao) {
		this.caixinhas = caixinhas;
		this.participantes = participantes;
		this.avaliarTransicao = avaliarTransicao;
	}

	@Transactional
	public ParticipanteEntity executar(
			long caixinhaId, long usuarioId, String emailAutenticado) {
		// 1. Caixinha precisa existir; senão 404 (anti-enumeração)
		CaixinhaEntity caixinha =
				caixinhas
						.findById(caixinhaId)
						.orElseThrow(
								() ->
										new ResponseStatusException(
												HttpStatus.NOT_FOUND,
												"Caixinha não encontrada."));

		// 2. Usuário autenticado precisa ser Participante (convidado)
		ParticipanteEntity participante =
				participantes
						.findByCaixinhaIdAndEmail(caixinha.getId(), emailAutenticado)
						.orElseThrow(
								() ->
										new ResponseStatusException(
												HttpStatus.NOT_FOUND,
												"Caixinha não encontrada."));

		// 3. Vincula usuario_id se ainda NULL (primeiro acesso autenticado)
		if (participante.getUsuarioId() == null) {
			participante.setUsuarioId(usuarioId);
		}

		// 4. Transição idempotente: convidado → aceito. Outros status = no-op.
		boolean houveTransicaoAceite =
				participante.getStatus() == StatusParticipante.convidado;
		if (houveTransicaoAceite) {
			participante.setStatus(StatusParticipante.aceito);
		}
		ParticipanteEntity salvo = participantes.save(participante);

		// 5. Story 3.1 (FR-6): se houve transição convidado→aceito nesta
		// chamada, reavalia se a Caixinha atingiu o Mínimo de Aceites e,
		// em caso afirmativo, transiciona para coletando_pagamentos NA
		// MESMA transação. Notificação afterCommit fica para um listener
		// futuro (Task 4 / Story 3.1) — por ora, transição é o efeito visível.
		if (houveTransicaoAceite) {
			avaliarTransicao.avaliar(caixinha);
		}

		return salvo;
	}
}
