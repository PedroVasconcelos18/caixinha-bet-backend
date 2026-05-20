/**
 * Adapters de envio do magic link (Story 2.1).
 *
 * <p>Implementa a porta {@code com.caxinhabet.auth.domain.MagicLinkSender}.
 * No MVP existe apenas o {@code LogMagicLinkSender} (loga o link para
 * teste manual local). <b>Envio SMTP real fica para a Story 2.4</b>
 * (convidar Participantes por e-mail) — quando entrar, será outro bean
 * neste pacote ativado por {@code auth.magic-link.sender=smtp}, sem
 * mexer no domain nem nos use cases.
 *
 * <p>TODO Story 2.4: adicionar {@code SmtpMagicLinkSender} usando
 * {@code spring-boot-starter-mail} + JavaMailSender. Configurar via
 * {@code spring.mail.*} (host, porta, credenciais via {@code .env}).
 */
package com.caxinhabet.auth.adapter.notification;
