package com.caxinhabet.caixinha.adapter.web;

import com.caxinhabet.caixinha.app.MontarAcertoContasUseCase;
import com.caxinhabet.caixinha.domain.Caixinha;
import com.caxinhabet.shared.money.Money;
import java.util.List;

/**
 * Resposta de {@code GET /caixinhas/{id}/acerto} (Épico 4 v5, Story 4.4,
 * FR-14) — o desfecho da Caixinha.
 *
 * @param modo {@code "premio"} (há Ganhadores), {@code "reembolso"} (0
 *     palpiteiros corretos) ou {@code "indisponivel"} (ainda não apurada).
 * @param estadoCaixinha estado atual da Caixinha (snake_case).
 * @param taxaServico Taxa de Serviço (R$ 10) — eco da regra.
 * @param totalCustodiado total custodiado confirmado (Money string).
 * @param ganhadores itens do modo prêmio — vazio nos outros modos.
 * @param reembolsos itens do modo reembolso — vazio nos outros modos.
 */
public record AcertoContasResponse(
		String modo,
		String estadoCaixinha,
		Money taxaServico,
		Money totalCustodiado,
		List<ItemGanhador> ganhadores,
		List<ItemReembolso> reembolsos) {

	/**
	 * Um Ganhador no modo prêmio.
	 *
	 * @param email e-mail do Ganhador.
	 * @param valor valor do prêmio (Money string).
	 * @param estadoRepasse {@code aguardando_aceite} / {@code pix_em_andamento}
	 *     / {@code pago} / {@code falha_pix}.
	 * @param comprovante comprovante do PIX ({@code null} enquanto não pago).
	 */
	public record ItemGanhador(
			String email, Money valor, String estadoRepasse, String comprovante) {}

	/**
	 * Um Participante reembolsado no modo reembolso.
	 *
	 * @param email e-mail do Participante.
	 * @param valorEstorno valor a estornar — o ingresso cheio (Taxa devolvida).
	 */
	public record ItemReembolso(String email, Money valorEstorno) {}

	/** Traduz o {@code Resultado} do use case para o contrato HTTP. */
	public static AcertoContasResponse de(MontarAcertoContasUseCase.Resultado r) {
		List<ItemGanhador> ganhadores =
				r.ganhadores().stream()
						.map(
								g ->
										new ItemGanhador(
												g.email(),
												g.valor(),
												g.estadoRepasse().name(),
												g.comprovante()))
						.toList();
		List<ItemReembolso> reembolsos =
				r.reembolsos().stream()
						.map(rb -> new ItemReembolso(rb.email(), rb.valorEstorno()))
						.toList();
		return new AcertoContasResponse(
				r.modo().name().toLowerCase(),
				r.estadoCaixinha().name(),
				Caixinha.TAXA_SERVICO,
				r.totalCustodiado(),
				ganhadores,
				reembolsos);
	}
}
