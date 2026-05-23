package com.caxinhabet.auth.adapter.web;

import com.caxinhabet.shared.money.Money;
import java.time.Instant;
import java.util.List;

/**
 * Resposta de {@code GET /auth/me/historico} (Minha Conta, 2026-05).
 *
 * <p>View agregada — não há tabela própria. Conta vitórias, criações,
 * pagamentos e cancelamentos a partir de {@code participante} + {@code payout}
 * + {@code caixinha} + {@code cobranca}.
 *
 * <p>Dinheiro como {@link Money} — Jackson serializa como string decimal via
 * {@code @JsonValue} (regra dura NFR-1: nunca {@code Number}).
 *
 * @param totais agregados (caixinhas, vitórias, total ganho, taxa de acerto).
 * @param atividadeRecente últimos 10 eventos, ordenados por {@code ocorridoEm}
 *     desc.
 */
public record HistoricoResponse(Totais totais, List<EventoAtividade> atividadeRecente) {

    /**
     * @param totalCaixinhas Caixinhas em que o usuário entrou (qualquer status
     *     diferente de {@code convidado}).
     * @param vitorias quantas dessas teve {@code statusVencedor != null}.
     * @param totalGanho soma dos {@code Payout.valorCentavos} do usuário.
     * @param taxaAcerto vitórias / Caixinhas apuradas em que participou (0..1).
     */
    public record Totais(
            int totalCaixinhas, int vitorias, Money totalGanho, double taxaAcerto) {}

    /**
     * @param tipo {@code "vitoria" | "criacao" | "pagamento" | "cancelamento"}.
     * @param titulo título da Caixinha (ex.: "Brasil vs Marrocos").
     * @param ocorridoEm timestamp do evento.
     * @param valor {@link Money} se aplicável, {@code null} senão (ex.: criação
     *     não tem valor).
     */
    public record EventoAtividade(String tipo, String titulo, Instant ocorridoEm, Money valor) {}
}
