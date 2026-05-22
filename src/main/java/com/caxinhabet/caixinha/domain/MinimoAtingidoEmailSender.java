package com.caxinhabet.caixinha.domain;

/**
 * Porta de envio do aviso "Mínimo atingido — hora de pagar" por e-mail
 * (Story 3.1, FR-6).
 *
 * <p>Espelha {@link ConviteEmailSender} da Story 2.4 — duas implementações
 * (log/smtp) escolhidas por {@code caixinha.minimo-atingido.sender=log|smtp};
 * {@code CaixinhaBootGuard} valida no boot.
 *
 * <p><b>Falha soft obrigatória:</b> uma exceção lançada aqui NÃO reverte
 * a transação do aceite que disparou a transição. A Caixinha JÁ ESTÁ em
 * {@code coletando_pagamentos} no DB — dinheiro de Caixinha não pode
 * depender de SMTP. O use case dispara em {@code afterCommit}.
 */
public interface MinimoAtingidoEmailSender {

	void enviarAvisoMinimoAtingido(MinimoAtingidoEmail email);
}
