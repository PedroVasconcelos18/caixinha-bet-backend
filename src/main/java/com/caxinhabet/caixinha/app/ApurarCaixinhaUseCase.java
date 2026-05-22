package com.caxinhabet.caixinha.app;

import com.caxinhabet.caixinha.adapter.persistence.CaixinhaEntity;
import com.caxinhabet.caixinha.adapter.persistence.CaixinhaRepository;
import com.caxinhabet.caixinha.adapter.persistence.ResultadoPossivelEntity;
import com.caxinhabet.caixinha.adapter.persistence.ResultadoPossivelRepository;
import com.caxinhabet.caixinha.domain.ApuracaoInvalidaException;
import com.caxinhabet.caixinha.domain.EstadoCaixinha;
import com.caxinhabet.caixinha.domain.OperacaoNaoAutorizadaException;
import com.caxinhabet.caixinha.domain.PrepararRepasse;
import com.caxinhabet.participante.adapter.persistence.ParticipanteEntity;
import com.caxinhabet.participante.adapter.persistence.ParticipanteRepository;
import com.caxinhabet.participante.domain.StatusParticipante;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Apura uma Caixinha: o Organizador escolhe o Resultado Final, o sistema
 * identifica os palpiteiros corretos e — quando há mais corretos que o Nº
 * de Ganhadores — o Organizador seleciona quais são os Ganhadores
 * (Épico 4 v5, Story 4.2, FR-12).
 *
 * <p>Fluxo (tudo numa {@code @Transactional}):
 * <ol>
 *   <li>Resolve o Participante autor; 404 se não-Participante (anti-
 *       enumeração — padrão das Stories 2.2/3.2).
 *   <li>403 se Participante mas não-Organizador.
 *   <li>422 se a Caixinha não está {@code formada} (estado errado ou já
 *       apurada — o Resultado Final é imutável).
 *   <li>Valida que o Resultado Final pertence a esta Caixinha.
 *   <li>Identifica os palpiteiros corretos (Participantes {@code pago} cujo
 *       Palpite == Resultado Final), em ordem cronológica de entrada.
 *   <li>Aplica a regra de seleção: ≤ Nº → todos viram Ganhadores; > Nº →
 *       exige a seleção exata no request; == 0 → modo reembolso.
 *   <li>Transiciona para {@code apurada}, registra o Resultado Final e
 *       marca os Ganhadores em {@code vencedor_aguardando_aceite}.
 * </ol>
 *
 * <p><b>Lock de apuração:</b> após {@code apurada}, o {@code AvaliarFormacaoService}
 * (Story 3.4) e o {@code ProcessarWebhookService} (fix Épico 3) já são
 * no-op — estornos posteriores não recalculam o rateio nem alteram a
 * seleção. Não há tabela de snapshot: o estado persistido no instante da
 * apuração É o lock (ver Dev Notes da story).
 *
 * <p>Esta story NÃO calcula valores nem dispara PIX — cálculo é a
 * Story 4.3; disparo é a Story 4.6.
 */
@Service
public class ApurarCaixinhaUseCase {

	private static final Logger log = LoggerFactory.getLogger(ApurarCaixinhaUseCase.class);

	private final CaixinhaRepository caixinhas;
	private final ResultadoPossivelRepository resultados;
	private final ParticipanteRepository participantes;
	private final PrepararRepasse prepararRepasse;

	public ApurarCaixinhaUseCase(
			CaixinhaRepository caixinhas,
			ResultadoPossivelRepository resultados,
			ParticipanteRepository participantes,
			PrepararRepasse prepararRepasse) {
		this.caixinhas = caixinhas;
		this.resultados = resultados;
		this.participantes = participantes;
		this.prepararRepasse = prepararRepasse;
	}

	/**
	 * Resultado da apuração.
	 *
	 * @param resultadoFinalId Resultado Final escolhido.
	 * @param ganhadoresIds ids dos Participantes marcados como Ganhadores
	 *     (vazio em modo reembolso).
	 * @param modoReembolso {@code true} se 0 palpiteiros corretos.
	 */
	public record Resultado(
			long resultadoFinalId, List<Long> ganhadoresIds, boolean modoReembolso) {}

	/**
	 * @param caixinhaId Caixinha a apurar.
	 * @param organizadorUsuarioId Usuário autenticado (deve ser o Organizador).
	 * @param emailAutenticado e-mail do autor (anti-enumeração).
	 * @param resultadoFinalId Resultado Possível escolhido como Final.
	 * @param ganhadoresEscolhidos ids de Participante escolhidos pelo
	 *     Organizador — usado SÓ quando há mais corretos que o Nº de
	 *     Ganhadores; ignorado caso contrário. {@code null}/vazio aceitável
	 *     quando a seleção é automática.
	 */
	@Transactional
	public Resultado executar(
			long caixinhaId,
			long organizadorUsuarioId,
			String emailAutenticado,
			long resultadoFinalId,
			List<Long> ganhadoresEscolhidos) {

		CaixinhaEntity caixinha =
				caixinhas
						.findById(caixinhaId)
						.orElseThrow(
								() ->
										new ResponseStatusException(
												HttpStatus.NOT_FOUND, "Caixinha não encontrada."));

		// Anti-enumeração: quem não é Participante não sabe que a Caixinha
		// existe — 404, não 403.
		participantes
				.findByCaixinhaIdAndEmail(caixinhaId, emailAutenticado)
				.orElseThrow(
						() ->
								new ResponseStatusException(
										HttpStatus.NOT_FOUND, "Caixinha não encontrada."));

		// Participante, mas não o Organizador → 403.
		if (!caixinha.getOrganizadorUsuarioId().equals(organizadorUsuarioId)) {
			throw new OperacaoNaoAutorizadaException(
					"Apenas o Organizador pode apurar a Caixinha.");
		}

		// Só `formada` apura. `apurada`+ → imutabilidade do Resultado Final.
		if (caixinha.getEstado() != EstadoCaixinha.formada) {
			throw new ApuracaoInvalidaException(
					caixinha.getEstado() == EstadoCaixinha.apurada
									|| caixinha.getEstado() == EstadoCaixinha.repasse_parcial
									|| caixinha.getEstado() == EstadoCaixinha.repassada
							? "Esta Caixinha já foi apurada — o Resultado Final é imutável."
							: "Só Caixinhas formadas podem ser apuradas.");
		}

		// O Resultado Final precisa ser um Resultado Possível DESTA Caixinha.
		ResultadoPossivelEntity resultadoFinal =
				resultados
						.findById(resultadoFinalId)
						.filter(r -> r.getCaixinhaId() == caixinhaId)
						.orElseThrow(
								() ->
										new ApuracaoInvalidaException(
												"O Resultado Final escolhido não pertence a esta"
														+ " Caixinha."));

		// Palpiteiros corretos: Participantes `pago` cujo Palpite congelado
		// é o Resultado Final, em ordem cronológica de entrada.
		List<ParticipanteEntity> corretos =
				participantes
						.findByCaixinhaIdAndStatusOrderByCriadoEmAsc(
								caixinhaId, StatusParticipante.pago)
						.stream()
						.filter(
								p ->
										resultadoFinal
												.getId()
												.equals(p.getPalpiteResultadoPossivelId()))
						.toList();

		// Modo reembolso: ninguém acertou. Caixinha vai a `apurada` sem
		// Ganhadores; o Reembolso é disparado pela Story 4.4 / 5.x.
		if (corretos.isEmpty()) {
			caixinha.registrarResultadoFinal(resultadoFinal.getId());
			caixinha.transicionarPara(EstadoCaixinha.apurada);
			caixinhas.save(caixinha);
			log.info(
					"Caixinha {} APURADA em modo reembolso (0 palpiteiros corretos)",
					caixinhaId);
			return new Resultado(resultadoFinal.getId(), List.of(), true);
		}

		// Define quem são os Ganhadores.
		List<ParticipanteEntity> ganhadores =
				selecionarGanhadores(corretos, caixinha.getNumeroGanhadores(), ganhadoresEscolhidos);

		caixinha.registrarResultadoFinal(resultadoFinal.getId());
		caixinha.transicionarPara(EstadoCaixinha.apurada);

		ganhadores.forEach(ParticipanteEntity::marcarComoVencedor);
		participantes.saveAll(ganhadores);

		// Story 4.3 (FR-13): prepara o Repasse — calcula valores, cria os
		// Payouts, registra a reserva no ledger e notifica os Ganhadores.
		// Roda na MESMA transação da apuração (a porta `PrepararRepasse` é
		// implementada no módulo `pagamento` — isolamento mantido).
		List<Long> ganhadoresIds =
				ganhadores.stream().map(ParticipanteEntity::getId).toList();
		prepararRepasse.prepararRepasse(caixinhaId, ganhadoresIds);

		// Glossário §3 v5: a Caixinha vai de `apurada` para `repasse_parcial`
		// no instante em que o primeiro Ganhador entra em
		// `vencedor_aguardando_aceite` — ou seja, agora.
		caixinha.transicionarPara(EstadoCaixinha.repasse_parcial);
		caixinhas.save(caixinha);

		log.info(
				"Caixinha {} APURADA → repasse_parcial — Resultado Final {},"
						+ " {} Ganhador(es)",
				caixinhaId,
				resultadoFinal.getId(),
				ganhadores.size());

		return new Resultado(resultadoFinal.getId(), ganhadoresIds, false);
	}

	/**
	 * Aplica a regra FR-12 de seleção de Ganhadores.
	 *
	 * <ul>
	 *   <li>{@code corretos.size() <= numeroGanhadores}: todos os corretos
	 *       são Ganhadores — a seleção do request é ignorada.
	 *   <li>{@code corretos.size() > numeroGanhadores}: o request DEVE
	 *       trazer exatamente {@code numeroGanhadores} ids, todos dentro do
	 *       conjunto de corretos — senão {@link ApuracaoInvalidaException}.
	 * </ul>
	 */
	private List<ParticipanteEntity> selecionarGanhadores(
			List<ParticipanteEntity> corretos,
			int numeroGanhadores,
			List<Long> escolhidos) {

		if (corretos.size() <= numeroGanhadores) {
			return corretos; // todos ganham — seleção automática
		}

		// Mais corretos que vagas — exige seleção explícita do Organizador.
		// A exceção carrega a lista de candidatos para o front montar a
		// tela de seleção sem uma segunda chamada.
		if (escolhidos == null || escolhidos.isEmpty()) {
			List<ApuracaoInvalidaException.Candidato> candidatos =
					corretos.stream()
							.map(
									p ->
											new ApuracaoInvalidaException.Candidato(
													p.getId(), p.getEmail()))
							.toList();
			throw new ApuracaoInvalidaException(
					"Há "
							+ corretos.size()
							+ " palpiteiros corretos para "
							+ numeroGanhadores
							+ " vaga(s) de Ganhador — selecione exatamente "
							+ numeroGanhadores
							+ ".",
					candidatos);
		}

		// Sem duplicatas, preservando a ordem informada.
		Set<Long> escolhidosUnicos = new LinkedHashSet<>(escolhidos);
		if (escolhidosUnicos.size() != numeroGanhadores) {
			throw new ApuracaoInvalidaException(
					"Selecione exatamente "
							+ numeroGanhadores
							+ " Ganhador(es) — recebido(s) "
							+ escolhidosUnicos.size()
							+ ".");
		}

		Set<Long> idsCorretos =
				corretos.stream()
						.map(ParticipanteEntity::getId)
						.collect(java.util.stream.Collectors.toSet());
		if (!idsCorretos.containsAll(escolhidosUnicos)) {
			throw new ApuracaoInvalidaException(
					"A seleção inclui Participante que não é palpiteiro correto.");
		}

		return corretos.stream()
				.filter(p -> escolhidosUnicos.contains(p.getId()))
				.toList();
	}
}
