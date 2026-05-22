package com.caxinhabet.caixinha.adapter.web;

import com.caxinhabet.caixinha.domain.Caixinha;
import com.caxinhabet.participante.adapter.persistence.ParticipanteEntity;
import com.caxinhabet.participante.domain.StatusParticipante;
import com.caxinhabet.shared.money.Money;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Resposta de {@code POST /caixinhas} e {@code GET /caixinhas/{id}}
 * (Story 2.2 → 3.5).
 *
 * <p>{@code estado} como string ({@code snake_case} do enum).
 * {@code taxaServico} é eco da regra (R$ 10 fixo).
 * {@code premioMaximoTeorico} = {@code valorIngresso × minimoParticipantes − taxa}
 * — informativo, NÃO compromisso.
 *
 * <p><b>Story 3.5 (painel de transparência, FR-10):</b>
 * <ul>
 *   <li>{@code totalCustodiado} = {@code valorIngresso × nº de Participantes
 *       em status `pago`} — só pagamentos confirmados contam.
 *   <li>{@code premioPotencial} = {@code max(totalCustodiado − taxa, R$ 0)}
 *       — o Prêmio que o conjunto atual de pagantes geraria; capado em
 *       zero (não exibe valor negativo).
 *   <li>cada {@code ParticipanteResumoResponse} ganha {@code palpiteRotulo}
 *       (rótulo legível do Resultado Possível escolhido).
 * </ul>
 */
public record CaixinhaResponse(
		Long id,
		String titulo,
		String ladoA,
		String ladoB,
		Money valorIngresso,
		int minimoParticipantes,
		int numeroGanhadores,
		Instant prazoEntrada,
		Instant dataApuracao,
		String estado,
		Money taxaServico,
		Money premioMaximoTeorico,
		Money totalCustodiado,
		Money premioPotencial,
		Instant criadoEm,
		List<ResultadoResponse> resultadosPossiveis,
		List<ParticipanteResumoResponse> participantes) {

	public static CaixinhaResponse de(
			Caixinha caixinha, List<ParticipanteEntity> participantesEntities) {
		List<ResultadoResponse> resultados =
				caixinha.resultadosPossiveis().stream()
						.map(r -> new ResultadoResponse(r.id(), r.ordem(), r.rotulo()))
						.toList();

		// Lookup id → rótulo do Resultado Possível, para resolver o Palpite
		// de cada Participante em texto legível (Story 3.5 AC-4).
		Map<Long, String> rotuloPorId =
				resultados.stream()
						.collect(
								Collectors.toMap(
										ResultadoResponse::id, ResultadoResponse::rotulo));

		List<ParticipanteResumoResponse> participantes =
				participantesEntities.stream()
						.map(
								p ->
										new ParticipanteResumoResponse(
												p.getEmail(),
												p.isDono(),
												p.getStatus().name(),
												p.getPalpiteResultadoPossivelId(),
												rotuloDoPalpite(p, rotuloPorId)))
						.toList();

		// Story 3.5 AC-3: total custodiado e Prêmio potencial.
		long pagos =
				participantesEntities.stream()
						.filter(p -> p.getStatus() == StatusParticipante.pago)
						.count();
		Money totalCustodiado = caixinha.valorIngresso().times((int) pagos);
		Money premioPotencial = capadoEmZero(totalCustodiado.minus(Caixinha.TAXA_SERVICO));

		return new CaixinhaResponse(
				caixinha.id(),
				caixinha.titulo(),
				caixinha.ladoA(),
				caixinha.ladoB(),
				caixinha.valorIngresso(),
				caixinha.minimoParticipantes(),
				caixinha.numeroGanhadores(),
				caixinha.prazoEntrada(),
				caixinha.dataApuracao(),
				caixinha.estado().name(),
				Caixinha.TAXA_SERVICO,
				caixinha.premioMaximoTeorico(),
				totalCustodiado,
				premioPotencial,
				caixinha.criadoEm(),
				resultados,
				participantes);
	}

	private static String rotuloDoPalpite(
			ParticipanteEntity p, Map<Long, String> rotuloPorId) {
		Long id = p.getPalpiteResultadoPossivelId();
		return id == null ? null : rotuloPorId.get(id);
	}

	/** {@code Money} nunca exibe valor negativo no painel — capa em zero. */
	private static Money capadoEmZero(Money valor) {
		return valor.compareTo(Money.of("0.00")) < 0 ? Money.of("0.00") : valor;
	}
}
