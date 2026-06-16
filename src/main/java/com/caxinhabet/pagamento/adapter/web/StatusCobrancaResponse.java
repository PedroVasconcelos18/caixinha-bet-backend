package com.caxinhabet.pagamento.adapter.web;

import com.caxinhabet.pagamento.adapter.persistence.CobrancaEntity;

/**
 * Resposta enxuta do polling de status da cobrança
 * (GET {@code /caixinhas/{id}/cobranca/status}).
 *
 * <p>Existe separada do {@link CobrancaResponse} de propósito: o polling
 * só precisa do <b>estado</b> (a tela já tem o QR/copia-e-cola da geração);
 * trafegar o QR base64 a cada 4s seria desperdício. Sucesso = corpo direto
 * (AR-8).
 *
 * <p>Ao contrário do {@code GET .../cobranca} (que dá 404 quando não há
 * cobrança {@code ativa}), este endpoint <b>nunca</b> retorna 404 por
 * ausência: sem cobrança gerada → {@code estado = "nenhuma"}. Assim o
 * front faz polling sem ter que tratar 404 como caso normal.
 *
 * @param estado estado da cobrança mais recente do Participante
 *     ({@code ativa}/{@code confirmada}/{@code expirada}/{@code invalidada}/
 *     {@code estornada}) ou {@code "nenhuma"} se ele ainda não gerou nenhuma.
 */
public record StatusCobrancaResponse(String estado) {

	/** Estado sentinela quando o Participante não tem cobrança alguma. */
	public static final String NENHUMA = "nenhuma";

	static StatusCobrancaResponse de(CobrancaEntity c) {
		return new StatusCobrancaResponse(c.getEstado().name());
	}

	static StatusCobrancaResponse nenhuma() {
		return new StatusCobrancaResponse(NENHUMA);
	}
}
