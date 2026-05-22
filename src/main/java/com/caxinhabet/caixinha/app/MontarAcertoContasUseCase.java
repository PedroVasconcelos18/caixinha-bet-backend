package com.caxinhabet.caixinha.app;

import com.caxinhabet.caixinha.adapter.persistence.CaixinhaEntity;
import com.caxinhabet.caixinha.adapter.persistence.CaixinhaRepository;
import com.caxinhabet.caixinha.domain.ConsultaEstadoEstorno;
import com.caxinhabet.caixinha.domain.ConsultaPayout;
import com.caxinhabet.caixinha.domain.EstadoCaixinha;
import com.caxinhabet.participante.adapter.persistence.ParticipanteEntity;
import com.caxinhabet.participante.adapter.persistence.ParticipanteRepository;
import com.caxinhabet.participante.domain.StatusParticipante;
import com.caxinhabet.shared.money.Money;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Monta o Acerto de Contas de uma Caixinha (Épico 4 v5, Story 4.4,
 * FR-14) — o desfecho público da Caixinha.
 *
 * <p>Read-only. Três modos:
 * <ul>
 *   <li>{@code INDISPONIVEL} — Caixinha ainda não apurada.
 *   <li>{@code PREMIO} — Caixinha apurada com ≥ 1 Ganhador; lista cada
 *       Ganhador com valor e estado do Repasse.
 *   <li>{@code REEMBOLSO} — Caixinha apurada com 0 palpiteiros corretos;
 *       lista cada Participante {@code pago} e o ingresso a estornar
 *       (Taxa devolvida — FR-14).
 * </ul>
 *
 * <p>Só Participantes veem o Acerto (404 anti-enumeração — padrão das
 * Stories 2.2/3.2).
 */
@Service
public class MontarAcertoContasUseCase {

	private final CaixinhaRepository caixinhas;
	private final ParticipanteRepository participantes;
	private final ConsultaPayout consultaPayout;
	private final ConsultaEstadoEstorno consultaEstadoEstorno;

	public MontarAcertoContasUseCase(
			CaixinhaRepository caixinhas,
			ParticipanteRepository participantes,
			ConsultaPayout consultaPayout,
			ConsultaEstadoEstorno consultaEstadoEstorno) {
		this.caixinhas = caixinhas;
		this.participantes = participantes;
		this.consultaPayout = consultaPayout;
		this.consultaEstadoEstorno = consultaEstadoEstorno;
	}

	public enum Modo {
		INDISPONIVEL,
		PREMIO,
		REEMBOLSO,
		/** Caixinha cancelada sem nenhum pagamento — não há o que reembolsar. */
		CANCELADA_SEM_REEMBOLSO
	}

	/** Um Ganhador no modo prêmio. */
	public record Ganhador(
			String email,
			Money valor,
			ConsultaPayout.EstadoRepasse estadoRepasse,
			String comprovante) {}

	/**
	 * Um Participante reembolsado no modo reembolso.
	 *
	 * @param email e-mail do Participante.
	 * @param valorEstorno ingresso cheio devolvido (Taxa devolvida).
	 * @param estadoEstorno estado real do estorno no Provedor (Story 5.2)
	 *     — {@code null} se a cobrança nem foi disparada ainda.
	 */
	public record Reembolso(
			String email,
			Money valorEstorno,
			ConsultaEstadoEstorno.EstadoEstorno estadoEstorno) {}

	/** Resultado completo do Acerto de Contas. */
	public record Resultado(
			Modo modo,
			EstadoCaixinha estadoCaixinha,
			Money totalCustodiado,
			List<Ganhador> ganhadores,
			List<Reembolso> reembolsos) {}

	@Transactional(readOnly = true)
	public Resultado executar(long caixinhaId, String emailAutenticado) {
		CaixinhaEntity caixinha =
				caixinhas
						.findById(caixinhaId)
						.orElseThrow(
								() ->
										new ResponseStatusException(
												HttpStatus.NOT_FOUND, "Caixinha não encontrada."));

		// Anti-enumeração: só Participante vê.
		participantes
				.findByCaixinhaIdAndEmail(caixinhaId, emailAutenticado)
				.orElseThrow(
						() ->
								new ResponseStatusException(
										HttpStatus.NOT_FOUND, "Caixinha não encontrada."));

		List<ParticipanteEntity> todos =
				participantes.findByCaixinhaIdOrderByCriadoEmAsc(caixinhaId);
		long pagos =
				todos.stream()
						.filter(p -> p.getStatus() == StatusParticipante.pago)
						.count();
		Money totalCustodiado =
				Money.ofCentavos(caixinha.getValorIngressoCentavos()).times((int) pagos);

		// Caixinha CANCELADA (Story 5.2): se houve pagamentos, é modo
		// reembolso com o estado real de cada estorno; se não, deixa claro
		// que não há reembolso a fazer.
		if (caixinha.getEstado() == EstadoCaixinha.cancelada) {
			List<Reembolso> reembolsos =
					montarReembolsos(caixinhaId, todos, caixinha.getValorIngressoCentavos());
			return new Resultado(
					reembolsos.isEmpty() ? Modo.CANCELADA_SEM_REEMBOLSO : Modo.REEMBOLSO,
					caixinha.getEstado(),
					totalCustodiado,
					List.of(),
					reembolsos);
		}

		// Caixinha ainda não apurada → modo indisponível.
		if (caixinha.getEstado() == EstadoCaixinha.coletando_convites
				|| caixinha.getEstado() == EstadoCaixinha.coletando_pagamentos
				|| caixinha.getEstado() == EstadoCaixinha.formada) {
			return new Resultado(
					Modo.INDISPONIVEL,
					caixinha.getEstado(),
					totalCustodiado,
					List.of(),
					List.of());
		}

		// Modo decidido pela presença de GANHADORES MARCADOS (status_vencedor
		// não-nulo), NÃO por "tem Payout?" (fix code review Épico 4): a
		// contagem de Ganhadores é registrada pela apuração (Story 4.2) no
		// mesmo passo da transição para `apurada` — é a fonte canônica do
		// modo. Uma Caixinha `apurada` sem nenhum Ganhador marcado é
		// inequivocamente modo reembolso (0 palpiteiros corretos).
		boolean temGanhadores =
				todos.stream().anyMatch(p -> p.getStatusVencedor() != null);

		if (!temGanhadores) {
			return new Resultado(
					Modo.REEMBOLSO,
					caixinha.getEstado(),
					totalCustodiado,
					List.of(),
					montarReembolsos(caixinhaId, todos, caixinha.getValorIngressoCentavos()));
		}

		List<ConsultaPayout.DadosPayout> payouts = consultaPayout.porCaixinha(caixinhaId);

		// Modo prêmio — cruza Payout (participanteId) com o e-mail.
		Map<Long, String> emailPorParticipante =
				todos.stream()
						.collect(
								Collectors.toMap(
										ParticipanteEntity::getId,
										ParticipanteEntity::getEmail,
										(a, b) -> a));
		List<Ganhador> ganhadores =
				payouts.stream()
						.map(
								p ->
										new Ganhador(
												emailPorParticipante.getOrDefault(
														p.participanteId(), "—"),
												Money.ofCentavos(p.valorCentavos()),
												p.estado(),
												p.comprovante()))
						.toList();

		return new Resultado(
				Modo.PREMIO,
				caixinha.getEstado(),
				totalCustodiado,
				ganhadores,
				List.of());
	}

	/**
	 * Monta a lista de reembolsos — um por Participante {@code pago} — com
	 * o estado real do estorno (Story 5.2). Vazia se ninguém pagou.
	 */
	private List<Reembolso> montarReembolsos(
			long caixinhaId,
			List<ParticipanteEntity> todos,
			long valorIngressoCentavos) {
		Map<Long, ConsultaEstadoEstorno.EstadoEstorno> estados =
				consultaEstadoEstorno.porCaixinha(caixinhaId);
		return todos.stream()
				.filter(p -> p.getStatus() == StatusParticipante.pago)
				.map(
						p ->
								new Reembolso(
										p.getEmail(),
										// Taxa devolvida — estorno é o ingresso cheio.
										Money.ofCentavos(valorIngressoCentavos),
										estados.get(p.getId())))
				.toList();
	}
}

