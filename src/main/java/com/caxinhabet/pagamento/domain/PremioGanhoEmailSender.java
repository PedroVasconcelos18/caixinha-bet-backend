package com.caxinhabet.pagamento.domain;

/**
 * Porta de envio da notificação "você ganhou" ao Ganhador
 * (Épico 4 v5, Story 4.3, FR-13).
 *
 * <p>Duas implementações (log/smtp) escolhidas por
 * {@code caixinha.premio-ganho.sender}. Falha de envio NÃO reverte a
 * apuração nem o Repasse — é best-effort, disparada {@code afterCommit}.
 */
public interface PremioGanhoEmailSender {

	void enviarAvisoPremio(PremioGanhoEmail email);
}
