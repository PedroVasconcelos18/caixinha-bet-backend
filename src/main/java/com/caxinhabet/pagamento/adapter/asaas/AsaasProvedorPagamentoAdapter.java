package com.caxinhabet.pagamento.adapter.asaas;

import com.caxinhabet.pagamento.domain.CobrancaCriada;
import com.caxinhabet.pagamento.domain.DadosCliente;
import com.caxinhabet.pagamento.domain.Ganhador;
import com.caxinhabet.pagamento.domain.ProvedorPagamento;
import com.caxinhabet.pagamento.domain.ResultadoTransferencia;
import com.caxinhabet.pagamento.domain.SolicitacaoCobranca;
import com.caxinhabet.pagamento.domain.StatusCobranca;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Implementação Asaas da {@link ProvedorPagamento} (AR-3, Story 1.4).
 *
 * <p>Esta classe é a <b>única</b> fronteira do projeto que pode importar
 * tipos do Asaas — o {@link com.caxinhabet.arquitetura.ArquiteturaTest}
 * faz cumprir. Para a Story 1.4: implementação <b>gate-only</b> de
 * {@link #criarCobranca} (suficiente para a Story 1.5 validar o gate
 * sandbox PIX); demais ops lançam {@code UnsupportedOperationException}
 * — implementação completa virá nos Épicos 3/4 (FR-8/FR-13).
 */
@Component
class AsaasProvedorPagamentoAdapter implements ProvedorPagamento {

	private final RestClient http;

	AsaasProvedorPagamentoAdapter(@Qualifier("asaasRestClient") RestClient asaasRestClient) {
		this.http = asaasRestClient;
	}

	/**
	 * Registra um cliente no Asaas: {@code POST /customers} com
	 * {@code name} + {@code cpfCnpj} (campos obrigatórios — confirmado na
	 * doc Asaas, Story 3.2). Devolve o {@code id} ({@code cus_<hash>}).
	 *
	 * <p>O endpoint relativo {@code /customers} (sem {@code /v3}) porque
	 * o {@code /api/v3} já está embutido na base-url (ver {@link AsaasProperties}).
	 */
	@Override
	public String criarCliente(DadosCliente dados) {
		Map<String, Object> payload =
				Map.of("name", dados.nome(), "cpfCnpj", dados.cpf());

		@SuppressWarnings("unchecked")
		Map<String, Object> cliente =
				http.post().uri("/customers").body(payload).retrieve().body(Map.class);

		if (cliente == null || cliente.get("id") == null) {
			throw new IllegalStateException("Asaas não retornou id do cliente criado");
		}
		return String.valueOf(cliente.get("id"));
	}

	/**
	 * Cria cobrança PIX no Asaas: {@code POST /payments} com
	 * {@code billingType=PIX}, {@code value=<string decimal>},
	 * {@code dueDate=<hoje+1>}, {@code externalReference=<participanteCaixinhaRef>}.
	 *
	 * <p>O Asaas devolve o {@code id} (cobrancaId) imediatamente; o QR code
	 * vem de {@code GET /payments/{id}/pixQrCode} (segunda chamada). Aqui
	 * fazemos as duas para entregar {@link CobrancaCriada} pronto para o
	 * front. Documentação: docs.asaas.com/reference/criar-nova-cobranca.
	 *
	 * <p>Os endpoints relativos {@code /payments} (sem {@code /v3}) porque
	 * o {@code /api/v3} já faz parte da {@code base-url} em
	 * {@link AsaasProperties}.
	 */
	@Override
	public CobrancaCriada criarCobranca(SolicitacaoCobranca solicitacao) {
		// O payload é só Map<String,Object> — não criamos DTO dedicado nesta
		// story para não inflar a fundação; Épico 3 introduz DTOs tipados.
		// Campo "customer" é OBRIGATÓRIO na API v3 do Asaas — sem ele a
		// requisição retorna 400. Defeito descoberto na Story 1.5 (a 1.4
		// inicial não conhecia esse requisito).
		Map<String, Object> payload =
				Map.of(
						"customer", solicitacao.customerId(),
						"billingType", "PIX",
						"value", solicitacao.valor().toString(), // string decimal (AR-8)
						"dueDate", LocalDate.now(ZoneOffset.UTC).plusDays(1).toString(),
						"description", solicitacao.descricao(),
						"externalReference", solicitacao.participanteCaixinhaRef());

		@SuppressWarnings("unchecked")
		Map<String, Object> cobranca =
				http.post().uri("/payments").body(payload).retrieve().body(Map.class);

		if (cobranca == null || cobranca.get("id") == null) {
			throw new IllegalStateException("Asaas não retornou id da cobrança criada");
		}
		String cobrancaId = String.valueOf(cobranca.get("id"));

		@SuppressWarnings("unchecked")
		Map<String, Object> qr =
				http.get()
						.uri("/payments/{id}/pixQrCode", cobrancaId)
						.retrieve()
						.body(Map.class);

		String encoded = qr == null ? null : String.valueOf(qr.get("encodedImage"));
		String payload2 = qr == null ? null : String.valueOf(qr.get("payload"));
		String expirationDate = qr == null ? null : String.valueOf(qr.get("expirationDate"));

		// expirationDate vem como "2026-06-20 23:59:59" (sem TZ — Asaas usa
		// hora de Brasília). Para esta fundação, registramos a tentativa
		// como Instant.parse falha-soft: se não conseguir parsear, usamos
		// hoje + 1 dia em UTC (a Story 1.5 ajusta o parsing se necessário).
		Instant expira = parseExpiracaoOuFallback(expirationDate);

		return new CobrancaCriada(cobrancaId, nullSafe(encoded), nullSafe(payload2), expira);
	}

	/**
	 * Consulta o status de uma cobrança no Asaas: {@code GET /payments/{id}}
	 * (Story 3.6, FR-NFR2 — reconciliação).
	 *
	 * <p>Traduz o {@code status} do Asaas para o {@link StatusCobranca} de
	 * domínio. Status Asaas conhecidos:
	 * <ul>
	 *   <li>{@code PENDING} → {@link StatusCobranca#PENDENTE}
	 *   <li>{@code CONFIRMED}, {@code RECEIVED}, {@code RECEIVED_IN_CASH},
	 *       {@code REFUND_REQUESTED}, {@code REFUND_IN_PROGRESS} →
	 *       {@link StatusCobranca#CONFIRMADA} — ver nota abaixo
	 *   <li>{@code OVERDUE} → {@link StatusCobranca#EXPIRADA}
	 *   <li>{@code REFUNDED}, {@code CHARGEBACK_*} →
	 *       {@link StatusCobranca#ESTORNADA}
	 * </ul>
	 * Status desconhecido → {@code PENDENTE} (conservador — não afirma
	 * pagamento sem certeza).
	 *
	 * <p><b>{@code REFUND_REQUESTED}/{@code REFUND_IN_PROGRESS} → CONFIRMADA</b>
	 * (decisão code review Épico 3, 2026-05-21): um estorno apenas
	 * <i>solicitado</i> ou <i>em andamento</i> NÃO é um estorno efetivado —
	 * o dinheiro ainda está custodiado. Mapeá-los para {@code ESTORNADA}
	 * faria a reconciliação (Story 3.6) gritar divergência falsa para uma
	 * cobrança que está legitimamente {@code confirmada} localmente. Só
	 * {@code REFUNDED} (efetivado) e chargebacks contam como estorno.
	 */
	@Override
	public StatusCobranca consultar(String cobrancaId) {
		@SuppressWarnings("unchecked")
		Map<String, Object> cobranca =
				http.get().uri("/payments/{id}", cobrancaId).retrieve().body(Map.class);

		if (cobranca == null || cobranca.get("status") == null) {
			throw new IllegalStateException(
					"Asaas não retornou status da cobrança " + cobrancaId);
		}
		return traduzirStatus(String.valueOf(cobranca.get("status")));
	}

	/** Tradução status Asaas → domínio (Story 3.6). Pacote-visível p/ teste. */
	static StatusCobranca traduzirStatus(String statusAsaas) {
		if (statusAsaas == null) {
			return StatusCobranca.PENDENTE;
		}
		return switch (statusAsaas) {
			case "CONFIRMED",
					"RECEIVED",
					"RECEIVED_IN_CASH",
					// Estorno pedido/em andamento: dinheiro ainda custodiado —
					// só o REFUNDED efetivado abaixo conta como estorno.
					"REFUND_REQUESTED",
					"REFUND_IN_PROGRESS" ->
					StatusCobranca.CONFIRMADA;
			case "OVERDUE" -> StatusCobranca.EXPIRADA;
			case "REFUNDED", "CHARGEBACK_REQUESTED", "CHARGEBACK_DISPUTE" ->
					StatusCobranca.ESTORNADA;
			default -> StatusCobranca.PENDENTE; // PENDING e desconhecidos
		};
	}

	@Override
	public void estornar(String cobrancaId) {
		throw new UnsupportedOperationException(
				"estornar() implementado no Épico 3/5 (FR-11). Story 1.4 = gate-only.");
	}

	@Override
	public ResultadoTransferencia transferir(Ganhador ganhador) {
		throw new UnsupportedOperationException(
				"transferir() implementado no Épico 4 (FR-13 v5). Story 1.4 v5 = gate-only.");
	}

	@Override
	public ResultadoTransferencia consultarTransferencia(String transferenciaId) {
		throw new UnsupportedOperationException(
				"consultarTransferencia() implementado no Épico 4 (FR-13 v5). Story 1.4 v5 = gate-only.");
	}

	private static String nullSafe(String s) {
		return s == null ? "" : s;
	}

	private static Instant parseExpiracaoOuFallback(String raw) {
		if (raw == null || raw.isBlank() || "null".equals(raw)) {
			return Instant.now().plusSeconds(86_400L); // +1 dia
		}
		try {
			// "2026-06-20 23:59:59" -> assume UTC (gate-only; Épico 3 trata TZ real)
			return Instant.parse(raw.replace(' ', 'T') + "Z");
		} catch (Exception ignored) {
			return Instant.now().plusSeconds(86_400L);
		}
	}
}
