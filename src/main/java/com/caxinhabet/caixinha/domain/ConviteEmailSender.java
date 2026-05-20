package com.caxinhabet.caixinha.domain;

/**
 * Porta de envio do convite por e-mail (Story 2.4, AC-5).
 *
 * <p>O domínio do caixinha não sabe SMTP/JavaMailSender/log — apenas
 * que existe um {@link ConviteEmail} a entregar. Adapters concretos
 * vivem em {@code com.caxinhabet.caixinha.adapter.notification}.
 *
 * <p>Story 2.4: dois adapters: {@code LogConviteEmailSender} (default,
 * loga no console — dev) e {@code SmtpConviteEmailSender} (usa
 * {@code JavaMailSender} — produção). A escolha é via
 * {@code caixinha.convite.sender=log|smtp}; {@code CaixinhaBootGuard}
 * valida no boot.
 *
 * <p><b>Não reusa {@code MagicLinkSender}</b> (Story 2.1) de propósito:
 * conteúdo diferente, módulo diferente. Manter portas simples (uma porta,
 * um propósito) é mais barato que generalizar prematuramente.
 *
 * <p>Implementações DEVEM falhar de forma "soft" — uma exceção lançada
 * NÃO deve reverter a transação de criação ou de convite (Story 2.4 AC-1:
 * envio é best-effort). O use case dispara em {@code afterCommit} para
 * isolar a falha.
 */
public interface ConviteEmailSender {

	void enviarConvite(ConviteEmail conviteEmail);
}
