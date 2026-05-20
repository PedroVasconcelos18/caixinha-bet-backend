/**
 * Store de sessões in-memory (Story 2.1).
 *
 * <p><b>Trade-off explícito:</b> sessões vivem em
 * {@code ConcurrentHashMap}; reiniciar o backend invalida todas. No MVP
 * (1 instância, dev local, soft-launch) isso é aceitável. Quando o volume
 * justificar, trocar este adapter por {@code RedisSessaoStore} (sem
 * mexer em domain ou use case — a sessão é criada/recuperada via
 * {@link com.caxinhabet.auth.adapter.session.SessaoStore}). Reavaliar na
 * retrospectiva do Épico 2.
 */
package com.caxinhabet.auth.adapter.session;
