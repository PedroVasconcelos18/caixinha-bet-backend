package com.caxinhabet.participante.domain;

/**
 * Sub-estado de um Participante que foi selecionado como Ganhador
 * (Épico 4 v5, FR-12/FR-13 — Glossário §3 v5).
 *
 * <p><b>Ortogonal ao {@link StatusParticipante}:</b> {@code StatusParticipante}
 * é o estado de pagamento do ingresso ({@code pago} etc.). Este enum é o
 * estado do <i>Repasse do prêmio</i> — só Participantes marcados como
 * Ganhadores na apuração (Story 4.2) o têm; quem não é Ganhador tem
 * {@code status_vencedor = NULL}.
 *
 * <p>Transições (Stories diferentes):
 * <ul>
 *   <li>{@link #vencedor_aguardando_aceite} — Story 4.2: o Organizador
 *       marcou este Participante como Ganhador na apuração.
 *   <li>{@link #vencedor_aceitou} — Story 4.6: o Ganhador aceitou o prêmio
 *       no app e o PIX foi disparado.
 *   <li>{@link #vencedor_pago} — Story 4.6: o Asaas confirmou a
 *       transferência PIX; comprovante anexado.
 * </ul>
 *
 * <p>Em falha de PIX (Story 4.6) o Ganhador volta de {@code vencedor_aceitou}
 * para {@code vencedor_aguardando_aceite} para corrigir a chave e reaceitar.
 *
 * <p>Nome em {@code snake_case} bate 1:1 com o valor persistido
 * ({@code @Enumerated(EnumType.STRING)}).
 */
public enum StatusVencedor {
	vencedor_aguardando_aceite,
	vencedor_aceitou,
	vencedor_pago
}
