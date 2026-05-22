package com.caxinhabet.caixinha.domain;

/**
 * Porta de envio das notificações de Formação / Reversão da Caixinha
 * por e-mail (Story 3.4 v5, FR-9).
 *
 * <p>Espelha {@code MinimoAtingidoEmailSender} (Story 3.1) — duas
 * implementações (log/smtp) escolhidas por
 * {@code caixinha.formacao.sender=log|smtp}; {@code CaixinhaBootGuard}
 * valida no boot.
 *
 * <p><b>Falha soft:</b> exceção lançada aqui NÃO reverte a transição de
 * Formação — ela já está persistida; o e-mail é best-effort. O serviço
 * dispara em {@code afterCommit}.
 */
public interface FormacaoEmailSender {

	void enviarAvisoFormacao(FormacaoEmail email);
}
