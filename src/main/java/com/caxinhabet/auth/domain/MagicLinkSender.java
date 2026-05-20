package com.caxinhabet.auth.domain;

import java.time.Instant;

/**
 * Porta de envio do magic link (Story 2.1, AC-1).
 *
 * <p>O domínio do auth não sabe como o link é entregue (SMTP, log, fila,
 * SaaS, etc.) — apenas que existe um endereço {@code email} e um
 * {@code linkAbsoluto} a entregar. Os adapters concretos vivem em
 * {@code com.caxinhabet.auth.adapter.notification}.
 *
 * <p><b>No MVP (Story 2.1):</b> o adapter ativo é
 * {@code LogMagicLinkSender} (loga URL no console do backend para teste
 * manual local). <b>Envio real por SMTP fica para a Story 2.4</b> (convite
 * por e-mail), que ganha o adapter SMTP de produção e troca a config
 * {@code auth.magic-link.sender}.
 *
 * <p>A porta intencionalmente não devolve resultado nem dispara exceção
 * checked: o adapter síncrono que falha estoura unchecked e a transação
 * do {@code SolicitarAcessoUseCase} desfaz a {@code solicitacao_acesso}
 * persistida — não fica "solicitação fantasma" sem link entregue.
 */
public interface MagicLinkSender {

	void enviar(String email, String linkAbsoluto, Instant expiraEm);
}
