package com.caxinhabet.auth.app;

import com.caxinhabet.auth.adapter.web.HistoricoResponse;
import com.caxinhabet.caixinha.adapter.persistence.CaixinhaEntity;
import com.caxinhabet.caixinha.adapter.persistence.CaixinhaRepository;
import com.caxinhabet.caixinha.domain.EstadoCaixinha;
import com.caxinhabet.pagamento.adapter.persistence.CobrancaRepository;
import com.caxinhabet.pagamento.adapter.persistence.PayoutEntity;
import com.caxinhabet.pagamento.adapter.persistence.PayoutRepository;
import com.caxinhabet.participante.adapter.persistence.ParticipanteEntity;
import com.caxinhabet.participante.adapter.persistence.ParticipanteRepository;
import com.caxinhabet.participante.domain.StatusParticipante;
import com.caxinhabet.shared.money.Money;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Caso de uso: histórico agregado do usuário (Minha Conta, 2026-05).
 *
 * <p>Não existe tabela {@code historico} — esta view é montada em Java a
 * partir de {@link ParticipanteRepository} + {@link PayoutRepository} +
 * {@link CaixinhaRepository} + {@link CobrancaRepository}. Trade-off:
 * implementação simples (poucas Caixinhas por usuário no MVP); se virar
 * gargalo, migra para uma view SQL ou consulta JPQL agregada.
 *
 * <p>Tipos de evento:
 * <ul>
 *   <li><b>vitoria</b> — Participante com {@code statusVencedor != null}; valor
 *       = {@link PayoutEntity#getValorCentavos()}; timestamp = {@code Payout.criadoEm}
 *       (instante da apuração).</li>
 *   <li><b>criacao</b> — Caixinha cujo {@code organizadorUsuarioId} == usuário;
 *       sem valor; timestamp = {@code Caixinha.criadoEm}.</li>
 *   <li><b>pagamento</b> — Cobrança {@code confirmadaEm != null} do Participante;
 *       valor = {@code Cobranca.valorCentavos}; timestamp = {@code confirmadaEm}.</li>
 *   <li><b>cancelamento</b> — Caixinha com estado {@code cancelada} em que o
 *       usuário participou; sem valor (não há campo {@code cancelada_em} —
 *       usa {@code Caixinha.criadoEm} como aproximação, suficiente para
 *       ordenação relativa).</li>
 * </ul>
 */
@Service
public class ConsultarHistoricoUseCase {

    private static final int LIMITE_ATIVIDADE = 10;

    private final ParticipanteRepository participantes;
    private final PayoutRepository payouts;
    private final CaixinhaRepository caixinhas;
    private final CobrancaRepository cobrancas;

    public ConsultarHistoricoUseCase(
            ParticipanteRepository participantes,
            PayoutRepository payouts,
            CaixinhaRepository caixinhas,
            CobrancaRepository cobrancas) {
        this.participantes = participantes;
        this.payouts = payouts;
        this.caixinhas = caixinhas;
        this.cobrancas = cobrancas;
    }

    @Transactional(readOnly = true)
    public HistoricoResponse executar(Long usuarioId) {
        List<ParticipanteEntity> ps = participantes.findByUsuarioId(usuarioId);

        Map<Long, CaixinhaEntity> caixinhaPorId = new HashMap<>();
        for (ParticipanteEntity p : ps) {
            caixinhaPorId.computeIfAbsent(
                    p.getCaixinhaId(), id -> caixinhas.findById(id).orElse(null));
        }

        // Inclui Caixinhas onde o usuário só é organizador (mesmo sem
        // Participante registrado para si — não acontece no fluxo padrão,
        // mas é defensivo).
        for (CaixinhaEntity c : caixinhas.findByOrganizadorUsuarioId(usuarioId)) {
            caixinhaPorId.putIfAbsent(c.getId(), c);
        }

        int totalCaixinhas = 0;
        int apuradas = 0;
        int vitorias = 0;
        long totalGanhoCentavos = 0;
        List<HistoricoResponse.EventoAtividade> eventos = new ArrayList<>();

        for (ParticipanteEntity p : ps) {
            if (p.getStatus() == StatusParticipante.convidado) continue;
            totalCaixinhas++;
            CaixinhaEntity c = caixinhaPorId.get(p.getCaixinhaId());
            if (c == null) continue;
            if (c.getEstado() == EstadoCaixinha.apurada
                    || c.getEstado() == EstadoCaixinha.repasse_parcial
                    || c.getEstado() == EstadoCaixinha.repassada) {
                apuradas++;
            }

            if (p.getStatusVencedor() != null) {
                vitorias++;
                payouts.findByCaixinhaIdAndParticipanteId(c.getId(), p.getId())
                        .ifPresent(
                                payout -> {
                                    eventos.add(
                                            new HistoricoResponse.EventoAtividade(
                                                    "vitoria",
                                                    c.getTitulo(),
                                                    payout.getCriadoEm(),
                                                    Money.ofCentavos(payout.getValorCentavos())));
                                });
                totalGanhoCentavos +=
                        payouts.findByCaixinhaIdAndParticipanteId(c.getId(), p.getId())
                                .map(PayoutEntity::getValorCentavos)
                                .orElse(0L);
            }

            cobrancas.findByParticipanteIdAndEstado(
                            p.getId(), com.caxinhabet.pagamento.domain.EstadoCobranca.confirmada)
                    .ifPresent(
                            cob ->
                                    eventos.add(
                                            new HistoricoResponse.EventoAtividade(
                                                    "pagamento",
                                                    c.getTitulo(),
                                                    cob.getConfirmadaEm() != null
                                                            ? cob.getConfirmadaEm()
                                                            : cob.getCriadoEm(),
                                                    Money.ofCentavos(cob.getValorCentavos()))));

            if (c.getEstado() == EstadoCaixinha.cancelada) {
                eventos.add(
                        new HistoricoResponse.EventoAtividade(
                                "cancelamento", c.getTitulo(), c.getCriadoEm(), null));
            }
        }

        for (CaixinhaEntity c : caixinhas.findByOrganizadorUsuarioId(usuarioId)) {
            eventos.add(
                    new HistoricoResponse.EventoAtividade(
                            "criacao", c.getTitulo(), c.getCriadoEm(), null));
        }

        eventos.sort(
                Comparator.comparing(HistoricoResponse.EventoAtividade::ocorridoEm).reversed());
        List<HistoricoResponse.EventoAtividade> recentes =
                eventos.size() > LIMITE_ATIVIDADE
                        ? eventos.subList(0, LIMITE_ATIVIDADE)
                        : eventos;

        double taxaAcerto = apuradas == 0 ? 0.0 : (double) vitorias / apuradas;

        HistoricoResponse.Totais totais =
                new HistoricoResponse.Totais(
                        totalCaixinhas,
                        vitorias,
                        Money.ofCentavos(totalGanhoCentavos),
                        taxaAcerto);
        return new HistoricoResponse(totais, recentes);
    }
}
