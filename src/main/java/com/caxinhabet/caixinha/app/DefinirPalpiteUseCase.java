package com.caxinhabet.caixinha.app;

import com.caxinhabet.caixinha.adapter.persistence.CaixinhaEntity;
import com.caxinhabet.caixinha.adapter.persistence.CaixinhaRepository;
import com.caxinhabet.caixinha.adapter.persistence.ResultadoPossivelEntity;
import com.caxinhabet.caixinha.adapter.persistence.ResultadoPossivelRepository;
import com.caxinhabet.caixinha.domain.PrazoEncerradoException;
import com.caxinhabet.participante.adapter.persistence.ParticipanteEntity;
import com.caxinhabet.participante.adapter.persistence.ParticipanteRepository;
import com.caxinhabet.participante.domain.PalpiteInvalidoException;
import com.caxinhabet.participante.domain.StatusParticipante;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Definir/alterar Palpite (Story 2.5, AC-3, AC-4).
 *
 * <p>Regras:
 * <ul>
 *   <li>Participante precisa existir e estar associado ao
 *       {@code emailAutenticado} (anti-enumeração: 404 se não).
 *   <li>{@code resultadoPossivelId} precisa pertencer à própria Caixinha
 *       (não a outra Caixinha — defesa contra enumeração de IDs).
 *   <li>{@code prazoEntrada} ainda no futuro — após, o palpite congela.
 *   <li>Status {@code convidado} → transição implícita para {@code aceito}
 *       (atalho UX: escolher palpite implica aceitar; documentado na story).
 *   <li>Vincula {@code usuario_id} se NULL (primeiro acesso, AC-6).
 *   <li>Idempotente: definir o mesmo id 2x é OK; id diferente atualiza.
 * </ul>
 */
@Service
public class DefinirPalpiteUseCase {

	private final CaixinhaRepository caixinhas;
	private final ParticipanteRepository participantes;
	private final ResultadoPossivelRepository resultados;

	public DefinirPalpiteUseCase(
			CaixinhaRepository caixinhas,
			ParticipanteRepository participantes,
			ResultadoPossivelRepository resultados) {
		this.caixinhas = caixinhas;
		this.participantes = participantes;
		this.resultados = resultados;
	}

	@Transactional
	public ParticipanteEntity executar(
			long caixinhaId,
			long usuarioId,
			String emailAutenticado,
			long resultadoPossivelId) {
		// 1. Caixinha precisa existir
		CaixinhaEntity caixinha =
				caixinhas
						.findById(caixinhaId)
						.orElseThrow(
								() ->
										new ResponseStatusException(
												HttpStatus.NOT_FOUND, "Caixinha não encontrada."));

		// 2. Autor precisa ser Participante (anti-enumeração: 404)
		ParticipanteEntity participante =
				participantes
						.findByCaixinhaIdAndEmail(caixinha.getId(), emailAutenticado)
						.orElseThrow(
								() ->
										new ResponseStatusException(
												HttpStatus.NOT_FOUND, "Caixinha não encontrada."));

		// 3. Prazo de entrada precisa estar no futuro
		if (!Instant.now().isBefore(caixinha.getPrazoEntrada())) {
			throw new PrazoEncerradoException(
					"Palpite congelado após o prazo de entrada ("
							+ caixinha.getPrazoEntrada()
							+ ").");
		}

		// 4. Resultado precisa existir E pertencer a esta Caixinha
		ResultadoPossivelEntity resultado =
				resultados
						.findById(resultadoPossivelId)
						.orElseThrow(
								() ->
										new PalpiteInvalidoException(
												"Resultado Possível não encontrado."));
		if (resultado.getCaixinhaId() != caixinha.getId()) {
			throw new PalpiteInvalidoException(
					"O Resultado Possível informado não pertence a esta Caixinha.");
		}

		// 5. Vincula usuario_id se ainda NULL (AC-6)
		if (participante.getUsuarioId() == null) {
			participante.setUsuarioId(usuarioId);
		}

		// 6. Aceite implícito (UX: escolher palpite = aceitar)
		if (participante.getStatus() == StatusParticipante.convidado) {
			participante.setStatus(StatusParticipante.aceito);
		}

		// 7. Grava palpite (idempotente — UPDATE com mesmo valor é OK)
		participante.setPalpiteResultadoPossivelId(resultadoPossivelId);

		return participantes.save(participante);
	}
}
