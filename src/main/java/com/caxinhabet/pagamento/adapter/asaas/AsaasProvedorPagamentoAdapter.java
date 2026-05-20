package com.caxinhabet.pagamento.adapter.asaas;

import com.caxinhabet.pagamento.domain.CobrancaCriada;
import com.caxinhabet.pagamento.domain.ProvedorPagamento;
import com.caxinhabet.pagamento.domain.SolicitacaoCobranca;
import com.caxinhabet.pagamento.domain.StatusCobranca;
import com.caxinhabet.pagamento.domain.Vencedor;
import com.caxinhabet.shared.money.Money;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
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

	@Override
	public StatusCobranca consultar(String cobrancaId) {
		throw new UnsupportedOperationException(
				"consultar() implementado no Épico 3 (FR-8). Story 1.4 = gate-only.");
	}

	@Override
	public void estornar(String cobrancaId) {
		throw new UnsupportedOperationException(
				"estornar() implementado no Épico 3/5 (FR-11). Story 1.4 = gate-only.");
	}

	@Override
	public void split(List<Vencedor> vencedores, Money taxaPlataforma) {
		throw new UnsupportedOperationException(
				"split() implementado no Épico 4 (FR-13). Story 1.4 = gate-only.");
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
