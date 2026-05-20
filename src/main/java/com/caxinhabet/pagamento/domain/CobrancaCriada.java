package com.caxinhabet.pagamento.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * Output do domínio para {@link ProvedorPagamento#criarCobranca}.
 *
 * <p>Identificadores e strings opacas — sem detalhes do PSP. O front recebe
 * QR code + copia-e-cola; o domínio guarda só o {@code cobrancaId} para
 * correlacionar com eventos futuros (FR-7/FR-8).
 *
 * @param cobrancaId identificador opaco da cobrança no PSP (usado para
 *     {@code consultar}/{@code estornar} depois).
 * @param qrCodeImagemBase64 PNG/JPEG do QR code já encodado em base64,
 *     pronto para {@code <img src="data:image/png;base64,...">}.
 * @param copiaECola string PIX copia-e-cola (texto longo, BR-Code).
 * @param expiraEm momento de expiração da cobrança (ISO 8601 UTC na borda).
 */
public record CobrancaCriada(
		String cobrancaId, String qrCodeImagemBase64, String copiaECola, Instant expiraEm) {

	public CobrancaCriada {
		Objects.requireNonNull(cobrancaId, "cobrancaId");
		Objects.requireNonNull(qrCodeImagemBase64, "qrCodeImagemBase64");
		Objects.requireNonNull(copiaECola, "copiaECola");
		Objects.requireNonNull(expiraEm, "expiraEm");
	}
}
