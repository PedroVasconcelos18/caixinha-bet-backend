package com.caxinhabet.pagamento.adapter.web;

import com.caxinhabet.pagamento.adapter.persistence.CobrancaEntity;
import com.caxinhabet.pagamento.app.GerarCobrancaUseCase;
import com.caxinhabet.shared.money.Money;
import java.time.Instant;

/**
 * Resposta da cobrança PIX (Story 3.2 v5) — usada por
 * {@code POST /caixinhas/{id}/cobranca} e {@code GET .../cobranca}.
 *
 * <p>Sucesso = corpo direto (AR-8). {@code valor} como string decimal
 * (Money — nunca número JSON, NFR-1). {@code qrCodeBase64} é o PNG do
 * QR já encodado, pronto para {@code <img src="data:image/png;base64,...">}.
 *
 * @param cobrancaId id da cobrança no Provedor.
 * @param copiaECola código PIX copia-e-cola (BR-Code).
 * @param qrCodeBase64 imagem do QR code em base64.
 * @param expiraEm momento de expiração (ISO 8601 UTC).
 * @param valor valor do ingresso, string decimal.
 * @param estado estado da cobrança ({@code ativa}/{@code expirada}/...).
 */
public record CobrancaResponse(
		String cobrancaId,
		String copiaECola,
		String qrCodeBase64,
		Instant expiraEm,
		Money valor,
		String estado) {

	/** Monta a partir do resultado do use case de geração. */
	public static CobrancaResponse de(GerarCobrancaUseCase.Resultado r) {
		return new CobrancaResponse(
				r.cobrancaId(),
				r.copiaECola(),
				r.qrCodeBase64(),
				r.expiraEm(),
				r.valor(),
				"ativa");
	}

	/** Monta a partir da entidade persistida (GET da cobrança vigente). */
	public static CobrancaResponse de(CobrancaEntity c) {
		return new CobrancaResponse(
				c.getCobrancaId(),
				c.getCopiaECola(),
				c.getQrCodeBase64(),
				c.getExpiraEm(),
				Money.ofCentavos(c.getValorCentavos()),
				c.getEstado().name());
	}
}
