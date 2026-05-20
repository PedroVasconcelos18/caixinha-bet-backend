/**
 * Adapter HTTP do módulo caixinha (Story 2.2).
 *
 * <p>Endpoints:
 * <ul>
 *   <li>{@code POST /caixinhas} — cria a Caixinha (autenticado)
 *   <li>{@code GET  /caixinhas/{id}} — devolve detalhe (só Participantes; outros → 404)
 * </ul>
 *
 * <p><b>NÃO existem</b> {@code PATCH/PUT /caixinhas/{id}} — imutabilidade
 * por construção (FR-3, AC-5). Ausência do endpoint = HTTP 405 default.
 */
package com.caxinhabet.caixinha.adapter.web;
