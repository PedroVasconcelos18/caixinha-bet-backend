package com.caxinhabet.caixinha.app;

import com.caxinhabet.auth.app.AppProperties;
import com.caxinhabet.caixinha.adapter.persistence.CaixinhaEntity;
import com.caxinhabet.caixinha.adapter.persistence.CaixinhaRepository;
import com.caxinhabet.caixinha.domain.ConviteEmail;
import com.caxinhabet.caixinha.domain.ConviteEmailSender;
import com.caxinhabet.caixinha.domain.EstadoCaixinha;
import com.caxinhabet.caixinha.domain.OperacaoNaoAutorizadaException;
import com.caxinhabet.caixinha.domain.PrazoEncerradoException;
import com.caxinhabet.participante.adapter.persistence.ParticipanteEntity;
import com.caxinhabet.participante.adapter.persistence.ParticipanteRepository;
import com.caxinhabet.participante.domain.StatusParticipante;
import com.caxinhabet.shared.money.Money;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Convidar Participantes por e-mail (Story 2.4, FR-4).
 *
 * <p>Fluxo (em uma única transação):
 * <ol>
 *   <li>Carrega a {@code CaixinhaEntity}.
 *   <li>Verifica que o autor é o dono ({@code organizadorUsuarioId ==
 *       caixinha.organizadorUsuarioId}). Senão → 403.
 *   <li>Verifica que {@code prazoEntrada > now()} E
 *       {@code estado in (coletando_convites, coletando_pagamentos)}.
 *       Senão → 422.
 *   <li>Normaliza e-mails (trim + lowercase), deduplica entre si e
 *       contra participantes já cadastrados.
 *   <li>Persiste novos {@code ParticipanteEntity(status=convidado,
 *       dono=false, usuarioId=null)}.
 *   <li>Registra hook {@code afterCommit} para disparar o
 *       {@link ConviteEmailSender} — envio é best-effort fora da
 *       transação (falha SMTP NÃO reverte a criação).
 * </ol>
 *
 * <p>Decisões registradas na Story 2.4:
 * <ul>
 *   <li>Envio pós-commit, não-bloqueante — falha SMTP não rolla back.
 *   <li>Limite {@code @Size(max=50)} no DTO da web (defesa contra abuso).
 *   <li>Resposta sempre 200 com listas {@code convidados}/{@code jaPresentes}
 *       (duplicata não é erro, é informação).
 * </ul>
 */
@Service
public class EnviarConvitesUseCase {

	private static final Logger log = LoggerFactory.getLogger(EnviarConvitesUseCase.class);

	private final CaixinhaRepository caixinhas;
	private final ParticipanteRepository participantes;
	private final ConviteEmailSender sender;
	private final AppProperties appProps;

	public EnviarConvitesUseCase(
			CaixinhaRepository caixinhas,
			ParticipanteRepository participantes,
			ConviteEmailSender sender,
			AppProperties appProps) {
		this.caixinhas = caixinhas;
		this.participantes = participantes;
		this.sender = sender;
		this.appProps = appProps;
	}

	@Transactional
	public Resultado executar(
			long caixinhaId, long organizadorUsuarioId, List<String> emails) {
		CaixinhaEntity caixinha =
				caixinhas
						.findById(caixinhaId)
						.orElseThrow(
								() -> new OperacaoNaoAutorizadaException(
										"Caixinha não encontrada."));

		if (!caixinha.getOrganizadorUsuarioId().equals(organizadorUsuarioId)) {
			throw new OperacaoNaoAutorizadaException(
					"Apenas o Organizador da Caixinha pode convidar Participantes.");
		}

		validarAceitaNovosConvites(caixinha);

		Set<String> emailsNormalizados = new LinkedHashSet<>();
		for (String e : emails) {
			if (e != null) {
				String norm = e.trim().toLowerCase();
				if (!norm.isEmpty()) {
					emailsNormalizados.add(norm);
				}
			}
		}

		Set<String> jaCadastrados = new HashSet<>();
		for (ParticipanteEntity p :
				participantes.findByCaixinhaIdOrderByCriadoEmAsc(caixinha.getId())) {
			jaCadastrados.add(p.getEmail().toLowerCase());
		}

		List<String> convidados = new ArrayList<>();
		List<String> jaPresentes = new ArrayList<>();
		List<ConviteEmail> conviteEmails = new ArrayList<>();

		String organizadorEmail = obterEmailOrganizador(caixinha);
		String confronto = caixinha.getLadoA() + " x " + caixinha.getLadoB();
		String valorFormatado = "R$ "
				+ Money.ofCentavos(caixinha.getValorIngressoCentavos()).toString();

		for (String email : emailsNormalizados) {
			if (jaCadastrados.contains(email)) {
				jaPresentes.add(email);
				continue;
			}
			participantes.save(
					new ParticipanteEntity(
							caixinha.getId(),
							null,
							email,
							false,
							StatusParticipante.convidado));
			convidados.add(email);
			conviteEmails.add(
					new ConviteEmail(
							email,
							organizadorEmail,
							caixinha.getTitulo(),
							confronto,
							valorFormatado,
							montarLinkConvite(caixinha.getId())));
		}

		agendarEnvioPosCommit(conviteEmails);

		return new Resultado(convidados, jaPresentes);
	}

	private void validarAceitaNovosConvites(CaixinhaEntity caixinha) {
		if (!Instant.now().isBefore(caixinha.getPrazoEntrada())) {
			throw new PrazoEncerradoException(
					"O prazo de entrada terminou em "
							+ caixinha.getPrazoEntrada()
							+ "; novos convites não são mais permitidos.");
		}
		EstadoCaixinha estado = caixinha.getEstado();
		if (estado != EstadoCaixinha.coletando_convites
				&& estado != EstadoCaixinha.coletando_pagamentos) {
			throw new PrazoEncerradoException(
					"A Caixinha está em estado '" + estado + "', que não aceita novos convites.");
		}
	}

	private String obterEmailOrganizador(CaixinhaEntity caixinha) {
		// O Organizador é Participante (Story 2.2) com dono=true.
		return participantes
				.findByCaixinhaIdOrderByCriadoEmAsc(caixinha.getId())
				.stream()
				.filter(ParticipanteEntity::isDono)
				.findFirst()
				.map(ParticipanteEntity::getEmail)
				.orElseThrow(
						() -> new IllegalStateException(
								"Caixinha " + caixinha.getId() + " sem dono — estado inconsistente."));
	}

	private String montarLinkConvite(long caixinhaId) {
		return appProps.getPublicBaseUrl() + "/convites/" + caixinhaId;
	}

	private void agendarEnvioPosCommit(List<ConviteEmail> emails) {
		if (emails.isEmpty()) {
			return;
		}
		if (!TransactionSynchronizationManager.isSynchronizationActive()) {
			// Sem transação ativa (uso fora de @Transactional, ex.: testes).
			// Envia direto — o caller assumiu o risco.
			emails.forEach(this::tentarEnviar);
			return;
		}
		TransactionSynchronizationManager.registerSynchronization(
				new TransactionSynchronization() {
					@Override
					public void afterCommit() {
						emails.forEach(EnviarConvitesUseCase.this::tentarEnviar);
					}
				});
	}

	private void tentarEnviar(ConviteEmail c) {
		try {
			sender.enviarConvite(c);
		} catch (Exception e) {
			// Falha soft — não propaga (já estamos pós-commit).
			log.error(
					"Falha no envio do convite para {} (caixinha={}): {}",
					c.destinatario(),
					c.tituloCaixinha(),
					e.getMessage(),
					e);
		}
	}

	public record Resultado(List<String> convidados, List<String> jaPresentes) {}
}
