package com.caxinhabet.caixinha.adapter.web;

import com.caxinhabet.caixinha.domain.Caixinha;
import com.caxinhabet.participante.adapter.persistence.ParticipanteEntity;
import com.caxinhabet.shared.money.Money;
import java.time.Instant;
import java.util.List;

/**
 * Resposta de {@code POST /caixinhas} e {@code GET /caixinhas/{id}}
 * (Story 2.2).
 *
 * <p>{@code estado} como string ({@code snake_case} do enum). {@code taxaServico}
 * é eco da regra ({@code R$ 10,00} fixo), informativo para o cliente.
 * {@code premioMaximoTeorico} = {@code valorIngresso × minimoParticipantes − taxa}
 * — informativo, NÃO compromisso (o Prêmio real depende de quantos
 * pagarem; FR-9).
 */
public record CaixinhaResponse(
		Long id,
		String titulo,
		String ladoA,
		String ladoB,
		Money valorIngresso,
		int minimoParticipantes,
		Instant prazoEntrada,
		Instant dataApuracao,
		String estado,
		Money taxaServico,
		Money premioMaximoTeorico,
		Instant criadoEm,
		List<ResultadoResponse> resultadosPossiveis,
		List<ParticipanteResumoResponse> participantes) {

	public static CaixinhaResponse de(
			Caixinha caixinha, List<ParticipanteEntity> participantesEntities) {
		List<ResultadoResponse> resultados =
				caixinha.resultadosPossiveis().stream()
						.map(r -> new ResultadoResponse(r.id(), r.ordem(), r.rotulo()))
						.toList();
		List<ParticipanteResumoResponse> participantes =
				participantesEntities.stream()
						.map(
								p ->
										new ParticipanteResumoResponse(
												p.getEmail(),
												p.isDono(),
												p.getStatus().name(),
												p.getPalpiteResultadoPossivelId()))
						.toList();
		return new CaixinhaResponse(
				caixinha.id(),
				caixinha.titulo(),
				caixinha.ladoA(),
				caixinha.ladoB(),
				caixinha.valorIngresso(),
				caixinha.minimoParticipantes(),
				caixinha.prazoEntrada(),
				caixinha.dataApuracao(),
				caixinha.estado().name(),
				Caixinha.TAXA_SERVICO,
				caixinha.premioMaximoTeorico(),
				caixinha.criadoEm(),
				resultados,
				participantes);
	}
}
