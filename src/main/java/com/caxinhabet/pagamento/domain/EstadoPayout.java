package com.caxinhabet.pagamento.domain;

/**
 * Estado de um {@code Payout} — o Repasse do prêmio a um Ganhador
 * (Épico 4 v5, FR-13).
 *
 * <ul>
 *   <li>{@link #pendente_aceite} — Payout criado na apuração (Story 4.3);
 *       o Ganhador ainda não aceitou.
 *   <li>{@link #transferindo} — o Ganhador aceitou e o PIX foi disparado
 *       no Asaas (Story 4.6); aguardando confirmação.
 *   <li>{@link #pago} — o Asaas confirmou a transferência (Story 4.6);
 *       comprovante anexado.
 *   <li>{@link #falha} — o Asaas recusou (chave PIX inválida); o Ganhador
 *       corrige a chave e aceita de novo (Story 4.6).
 * </ul>
 *
 * <p>Nome em {@code snake_case} bate 1:1 com o {@code CHECK} da migration V17.
 */
public enum EstadoPayout {
	pendente_aceite,
	transferindo,
	pago,
	falha
}
