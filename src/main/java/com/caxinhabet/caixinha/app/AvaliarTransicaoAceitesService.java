package com.caxinhabet.caixinha.app;

import com.caxinhabet.auth.app.AppProperties;
import com.caxinhabet.caixinha.adapter.persistence.CaixinhaEntity;
import com.caxinhabet.caixinha.adapter.persistence.CaixinhaRepository;
import com.caxinhabet.caixinha.domain.EstadoCaixinha;
import com.caxinhabet.caixinha.domain.MinimoAtingidoEmail;
import com.caxinhabet.caixinha.domain.MinimoAtingidoEmailSender;
import com.caxinhabet.participante.adapter.persistence.ParticipanteEntity;
import com.caxinhabet.participante.adapter.persistence.ParticipanteRepository;
import com.caxinhabet.participante.domain.StatusParticipante;
import com.caxinhabet.shared.money.Money;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Avalia a transição {@code coletando_convites → coletando_pagamentos}
 * de uma Caixinha (Story 3.1, FR-6).
 *
 * <p><b>Modelo de execução (decisão Pedro 2026-05-21):</b> síncrono no
 * aceite. Não há job periódico nem evento async; quem chama é o
 * {@code AceitarConviteUseCase} ou {@code DefinirPalpiteUseCase} dentro
 * da MESMA {@code @Transactional} do aceite, depois de gravar o novo
 * status do Participante.
 *
 * <p><b>Idempotente:</b> o método {@link #avaliar(CaixinhaEntity)} é
 * seguro chamar múltiplas vezes — só transiciona se o estado atual é
 * {@code coletando_convites} E o conjunto de aceites atingiu o mínimo.
 * Estados terminais ({@code formada}, {@code apurada}, ...) NÃO
 * regridem (defesa AC-2).
 *
 * <p><b>Por que "aceito ou superior" e não só "aceito"?</b> PRD §FR-6
 * v5: "Participantes com Status aceito (ou superior)".
 * {@code pagamento_iniciado} e {@code pago} só aparecem nas Stories
 * 3.2/3.3, mas a query já cobre — sem retroajuste.
 *
 * <p><b>Notificação (afterCommit):</b> quando a transição acontece, este
 * serviço registra um {@link TransactionSynchronization} que dispara o
 * aviso "mínimo atingido" para todos os Participantes da Caixinha
 * (com e-mail conhecido). Padrão idêntico ao {@code EnviarConvitesUseCase}
 * da Story 2.4: envio é best-effort; falha SMTP NÃO reverte a transição
 * (Caixinha já está em {@code coletando_pagamentos} no DB).
 */
@Service
public class AvaliarTransicaoAceitesService {

	private static final Logger log =
			LoggerFactory.getLogger(AvaliarTransicaoAceitesService.class);

	/** Status que contam como "aceito ou superior" (FR-6 v5). */
	private static final Set<StatusParticipante> ACEITO_OU_SUPERIOR =
			Set.of(
					StatusParticipante.aceito,
					StatusParticipante.pagamento_iniciado,
					StatusParticipante.pago);

	private final CaixinhaRepository caixinhas;
	private final ParticipanteRepository participantes;
	private final MinimoAtingidoEmailSender sender;
	private final AppProperties appProps;

	public AvaliarTransicaoAceitesService(
			CaixinhaRepository caixinhas,
			ParticipanteRepository participantes,
			MinimoAtingidoEmailSender sender,
			AppProperties appProps) {
		this.caixinhas = caixinhas;
		this.participantes = participantes;
		this.sender = sender;
		this.appProps = appProps;
	}

	/**
	 * Avalia e (se aplicável) transiciona a Caixinha de
	 * {@code coletando_convites} para {@code coletando_pagamentos}, e
	 * agenda o envio dos avisos por e-mail para depois do commit.
	 *
	 * <p>Pré-condição: estar dentro de uma {@code @Transactional} do
	 * caller (o {@code save} aqui herda essa transação; o
	 * {@code afterCommit} só funciona se houver synchronization ativa).
	 *
	 * @param caixinha entidade carregada pelo caller.
	 * @return {@code true} se a transição aconteceu (notificação já
	 *     agendada); {@code false} se já estava em estado posterior ou
	 *     mínimo ainda não atingido.
	 */
	public boolean avaliar(CaixinhaEntity caixinha) {
		if (caixinha.getEstado() != EstadoCaixinha.coletando_convites) {
			return false;
		}
		long aceitos =
				participantes.countByCaixinhaIdAndStatusIn(
						caixinha.getId(), ACEITO_OU_SUPERIOR);
		if (aceitos < caixinha.getMinimoParticipantes()) {
			return false;
		}
		caixinha.transicionarPara(EstadoCaixinha.coletando_pagamentos);
		caixinhas.save(caixinha);

		// Notificação afterCommit. Snapshot dos Participantes feito AGORA
		// (dentro da tx) — depois do commit não há garantia que o repo veria
		// o estado coerente, e a lista é estável: novos convites pós-transição
		// recebem outro fluxo (Story 2.4) e não cabem neste aviso.
		List<MinimoAtingidoEmail> avisos = montarAvisos(caixinha);
		agendarEnvioPosCommit(avisos);
		return true;
	}

	private List<MinimoAtingidoEmail> montarAvisos(CaixinhaEntity caixinha) {
		String confronto = caixinha.getLadoA() + " × " + caixinha.getLadoB();
		String valorFormatado = formatarBrl(caixinha.getValorIngressoCentavos());
		String link =
				appProps.getPublicBaseUrl() + "/caixinhas/" + caixinha.getId();
		return participantes
				.findByCaixinhaIdOrderByCriadoEmAsc(caixinha.getId())
				.stream()
				.map(ParticipanteEntity::getEmail)
				.filter(email -> email != null && !email.isBlank())
				.distinct()
				.map(
						email ->
								new MinimoAtingidoEmail(
										email,
										caixinha.getTitulo(),
										confronto,
										valorFormatado,
										link))
				.toList();
	}

	private static String formatarBrl(long centavos) {
		Money m = Money.ofCentavos(centavos);
		// Formato BR: "R$ 40,00" (vírgula decimal). Money.toString devolve
		// "40.00" — substitui o ponto pela vírgula para o template do e-mail.
		return "R$ " + m.toString().replace('.', ',');
	}

	private void agendarEnvioPosCommit(List<MinimoAtingidoEmail> avisos) {
		if (avisos.isEmpty()) {
			return;
		}
		if (!TransactionSynchronizationManager.isSynchronizationActive()) {
			// Sem tx ativa (caller fora de @Transactional, ex.: testes que
			// chamam o service direto). Envia inline — o caller assumiu o risco.
			avisos.forEach(this::tentarEnviar);
			return;
		}
		TransactionSynchronizationManager.registerSynchronization(
				new TransactionSynchronization() {
					@Override
					public void afterCommit() {
						avisos.forEach(AvaliarTransicaoAceitesService.this::tentarEnviar);
					}
				});
	}

	private void tentarEnviar(MinimoAtingidoEmail e) {
		try {
			sender.enviarAvisoMinimoAtingido(e);
		} catch (Exception ex) {
			// Falha soft — Caixinha já transicionou; aviso é best-effort.
			log.error(
					"Falha no envio do aviso 'mínimo atingido' para {} (caixinha={}): {}",
					e.destinatario(),
					e.tituloCaixinha(),
					ex.getMessage(),
					ex);
		}
	}
}
