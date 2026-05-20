/**
 * Adapters de envio de e-mail do módulo caixinha (Story 2.4).
 *
 * <p>Implementam a porta {@code com.caxinhabet.caixinha.domain.ConviteEmailSender}.
 * Dois adapters disponíveis:
 * <ul>
 *   <li>{@code LogConviteEmailSender} — default, loga URL no console (dev/test).
 *   <li>{@code SmtpConviteEmailSender} — envia via {@code JavaMailSender}
 *       (produção). Configuração via {@code spring.mail.*}.
 * </ul>
 *
 * <p>Bean ativo escolhido por {@code caixinha.convite.sender=log|smtp} +
 * {@code @ConditionalOnProperty} em cada adapter. {@code CaixinhaBootGuard}
 * (em {@code caixinha.app}) valida o valor antes do boot.
 */
package com.caxinhabet.caixinha.adapter.notification;
