/**
 * Camada de aplicação do módulo auth (Story 2.1).
 *
 * <p>Dois use cases: {@code SolicitarAcessoUseCase} cria a solicitação
 * de acesso + dispara o magic link; {@code ConsumirAcessoUseCase} valida
 * o token, marca consumido e devolve uma sessão. Padrão da Story 1.4:
 * lógica de aplicação fina, acesso direto ao repositório, sem camada de
 * service genérica.
 */
package com.caxinhabet.auth.app;
