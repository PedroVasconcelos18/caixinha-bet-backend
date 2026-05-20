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

	public AceitarConviteUseCase(
			CaixinhaRepository caixinhas, ParticipanteRepository participantes) {
		this.caixinhas = caixinhas;
		this.participantes = participantes;
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
		if (participante.getStatus() == StatusParticipante.convidado) {
			participante.setStatus(StatusParticipante.aceito);
		}

		return participantes.save(participante);
	}
}
