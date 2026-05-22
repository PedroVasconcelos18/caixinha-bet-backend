package com.caxinhabet.pagamento.domain;

/**
 * Porta de envio da notificação de cancelamento da Caixinha
 * (Épico 5 v5, Story 5.1, FR-11).
 *
 * <p>Duas implementações (log/smtp) escolhidas por
 * {@code caixinha.cancelamento.sender}. Falha de envio NÃO reverte o
 * cancelamento — best-effort, disparada {@code afterCommit}.
 */
public interface CancelamentoEmailSender {

	void enviarAvisoCancelamento(CancelamentoEmail email);
}
