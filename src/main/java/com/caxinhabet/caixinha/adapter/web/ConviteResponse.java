package com.caxinhabet.caixinha.adapter.web;

import com.caxinhabet.caixinha.adapter.persistence.CaixinhaEntity;
import com.caxinhabet.caixinha.adapter.persistence.ResultadoPossivelEntity;
import com.caxinhabet.caixinha.domain.Caixinha;
import com.caxinhabet.participante.adapter.persistence.ParticipanteEntity;
import com.caxinhabet.shared.money.Money;
import java.time.Instant;
import java.util.List;

/**
 * Resposta de {@code GET /caixinhas/{id}/convite} (Story 2.5).
 *
 * <p>Visão restrita do convidado: dados da Caixinha + visão do PRÓPRIO
 * Participante. NÃO inclui lista completa de Participantes — privacidade.
 *
 * <p>Quem é Participante pleno (aceito+) ainda pode chamar
 * {@code GET /caixinhas/{id}} (Story 2.2) e ver tudo. Aqui é a entrada
 * do convidado, com escopo reduzido.
 */
public record ConviteResponse(
		Long caixinhaId,
		String titulo,
		String ladoA,
		String ladoB,
		Money valorIngresso,
		Money taxaServico,
		String estado,
		Instant prazoEntrada,
		List<ResultadoResponse> resultadosPossiveis,
		ParticipanteMeResponse eu) {

	public static ConviteResponse de(
			CaixinhaEntity caixinha,
			List<ResultadoPossivelEntity> resultados,
			ParticipanteEntity eu) {
		List<ResultadoResponse> rs =
				resultados.stream()
						.map(r -> new ResultadoResponse(r.getId(), r.getOrdem(), r.getRotulo()))
						.toList();
		return new ConviteResponse(
				caixinha.getId(),
				caixinha.getTitulo(),
				caixinha.getLadoA(),
				caixinha.getLadoB(),
				Money.ofCentavos(caixinha.getValorIngressoCentavos()),
				Caixinha.TAXA_SERVICO,
				caixinha.getEstado().name(),
				caixinha.getPrazoEntrada(),
				rs,
				new ParticipanteMeResponse(
						eu.getEmail(),
						eu.getStatus().name(),
						eu.getPalpiteResultadoPossivelId()));
	}
}
