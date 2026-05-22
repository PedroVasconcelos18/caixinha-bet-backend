package com.caxinhabet.pagamento.app;

import com.caxinhabet.caixinha.domain.ConsultaCaixinha;
import com.caxinhabet.caixinha.domain.EstadoCaixinha;
import com.caxinhabet.pagamento.adapter.persistence.CobrancaEntity;
import com.caxinhabet.pagamento.adapter.persistence.CobrancaRepository;
import com.caxinhabet.pagamento.domain.EstadoCobranca;
import com.caxinhabet.participante.adapter.persistence.ParticipanteEntity;
import com.caxinhabet.participante.adapter.persistence.ParticipanteRepository;
import com.caxinhabet.participante.domain.StatusParticipante;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Expira cobranças PIX vencidas, de forma <b>lazy</b> (Story 3.2 v5, FR-7).
 *
 * <p><b>Modelo (decisão Pedro 2026-05-21):</b> sem scheduler. A expiração
 * é detectada e aplicada na próxima LEITURA do estado da Caixinha
 * (ex.: {@code GET /caixinhas/{id}}). Cobrança "esquecida" (Participante
 * nunca reabre) fica em {@code pagamento_iniciado} até: (a) alguém abrir
 * a Caixinha, ou (b) o webhook {@code PAYMENT_EXPIRED} do Asaas chegar
 * (Story 3.3). Aceitável no MVP.
 *
 * <p>Efeito da expiração: cobrança {@code ativa→expirada} e Participante
 * {@code pagamento_iniciado→aceito} (ele pode gerar nova cobrança).
 *
 * <p>Idempotente: cobrança já não-{@code ativa} → no-op; cobrança
 * {@code ativa} não-vencida → no-op.
 */
@Service
public class ExpirarCobrancaService {

	private static final Logger log =
			LoggerFactory.getLogger(ExpirarCobrancaService.class);

	private final CobrancaRepository cobrancas;
	private final ParticipanteRepository participantes;
	private final ConsultaCaixinha consultaCaixinha;

	public ExpirarCobrancaService(
			CobrancaRepository cobrancas,
			ParticipanteRepository participantes,
			ConsultaCaixinha consultaCaixinha) {
		this.cobrancas = cobrancas;
		this.participantes = participantes;
		this.consultaCaixinha = consultaCaixinha;
	}

	/**
	 * Verifica a cobrança {@code ativa} do Participante e, se vencida,
	 * expira-a + reverte o Participante para {@code aceito}.
	 *
	 * <p>Deve ser chamado dentro de uma {@code @Transactional} de escrita
	 * (não {@code readOnly}) — daí a anotação aqui, que cria/herda uma.
	 *
	 * @param participanteId Participante cujo estado de cobrança avaliar.
	 * @return {@code true} se uma expiração aconteceu nesta chamada.
	 */
	@Transactional
	public boolean expirarSeVencida(long participanteId) {
		CobrancaEntity cobranca =
				cobrancas
						.findByParticipanteIdAndEstado(
								participanteId, EstadoCobranca.ativa)
						.orElse(null);
		if (cobranca == null || !cobranca.venceu(Instant.now())) {
			return false;
		}
		// LOCK DE APURAÇÃO (fix Épico 3 #4): se a Caixinha já foi apurada,
		// não mexe — o conjunto de pagamentos está congelado. Uma cobrança
		// `ativa` vencida pendurada numa Caixinha apurada (caso raro) fica
		// como está; expirá-la mutaria o Participante de uma Caixinha cujo
		// rateio já foi calculado.
		if (caixinhaApuradaOuAlem(cobranca.getCaixinhaId())) {
			log.warn(
					"[LOCK-APURACAO] cobrança {} venceu mas a Caixinha {} já foi"
							+ " apurada — expiração lazy NÃO aplicada.",
					cobranca.getCobrancaId(),
					cobranca.getCaixinhaId());
			return false;
		}
		cobranca.transicionarPara(EstadoCobranca.expirada);

		// Reverte o Participante para aceito — só se ainda está em
		// pagamento_iniciado (defesa: se um webhook já o levou a pago,
		// não regride).
		ParticipanteEntity p =
				participantes.findById(participanteId).orElse(null);
		if (p != null && p.getStatus() == StatusParticipante.pagamento_iniciado) {
			p.setStatus(StatusParticipante.aceito);
		}
		return true;
	}

	/**
	 * {@code true} se a Caixinha já foi apurada (ou além) — lock de
	 * apuração (FR-9): nada de mutar pagamentos de Caixinha cujo rateio
	 * já foi calculado.
	 */
	private boolean caixinhaApuradaOuAlem(long caixinhaId) {
		return consultaCaixinha
				.buscar(caixinhaId)
				.map(
						c ->
								switch (c.estado()) {
									case apurada, repasse_parcial, repassada -> true;
									default -> false;
								})
				.orElse(false);
	}
}
