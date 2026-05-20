package com.caxinhabet.caixinha.app;

import com.caxinhabet.caixinha.adapter.persistence.CaixinhaEntity;
import com.caxinhabet.caixinha.adapter.persistence.CaixinhaRepository;
import com.caxinhabet.caixinha.adapter.persistence.ResultadoPossivelEntity;
import com.caxinhabet.caixinha.adapter.persistence.ResultadoPossivelRepository;
import com.caxinhabet.participante.adapter.persistence.ParticipanteEntity;
import com.caxinhabet.participante.adapter.persistence.ParticipanteRepository;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Carrega os dados do convite para um Participante (Story 2.5, AC-1, AC-5, AC-6).
 *
 * <p>Diferente de {@code GET /caixinhas/{id}} (Story 2.2) — retorna apenas
 * a visão do PRÓPRIO Participante + dados da Caixinha, sem expor a lista
 * completa de e-mails dos outros (privacidade do convidado, que pode não
 * conhecer todo mundo).
 *
 * <p>Anti-enumeração: não-convidado → 404 (não 403), mesmo padrão da
 * Story 2.2.
 *
 * <p>Vincula {@code usuario_id} no primeiro acesso (AC-6). NÃO é
 * {@code readOnly=true} porque pode escrever neste passo.
 */
@Service
public class BuscarConviteUseCase {

	private final CaixinhaRepository caixinhas;
	private final ParticipanteRepository participantes;
	private final ResultadoPossivelRepository resultados;

	public BuscarConviteUseCase(
			CaixinhaRepository caixinhas,
			ParticipanteRepository participantes,
			ResultadoPossivelRepository resultados) {
		this.caixinhas = caixinhas;
		this.participantes = participantes;
		this.resultados = resultados;
	}

	@Transactional
	public Resultado executar(long caixinhaId, long usuarioId, String emailAutenticado) {
		CaixinhaEntity caixinha =
				caixinhas
						.findById(caixinhaId)
						.orElseThrow(
								() ->
										new ResponseStatusException(
												HttpStatus.NOT_FOUND, "Caixinha não encontrada."));

		ParticipanteEntity participante =
				participantes
						.findByCaixinhaIdAndEmail(caixinha.getId(), emailAutenticado)
						.orElseThrow(
								() ->
										new ResponseStatusException(
												HttpStatus.NOT_FOUND, "Caixinha não encontrada."));

		// Vincula usuario_id no primeiro acesso (AC-6)
		if (participante.getUsuarioId() == null) {
			participante.setUsuarioId(usuarioId);
			participante = participantes.save(participante);
		}

		List<ResultadoPossivelEntity> rps =
				resultados.findByCaixinhaIdOrderByOrdemAsc(caixinha.getId());

		return new Resultado(caixinha, rps, participante);
	}

	public record Resultado(
			CaixinhaEntity caixinha,
			List<ResultadoPossivelEntity> resultadosPossiveis,
			ParticipanteEntity eu) {}
}
