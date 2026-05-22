package com.caxinhabet.pagamento.app;

import com.caxinhabet.auth.domain.ChavePixObrigatoriaException;
import com.caxinhabet.auth.domain.PerfilPagamentoGateway;
import com.caxinhabet.caixinha.domain.ConsultaCaixinha;
import com.caxinhabet.caixinha.domain.EstadoCaixinha;
import com.caxinhabet.caixinha.domain.PagamentoIndisponivelException;
import com.caxinhabet.ledger.app.LedgerService;
import com.caxinhabet.pagamento.adapter.persistence.CobrancaEntity;
import com.caxinhabet.pagamento.adapter.persistence.CobrancaRepository;
import com.caxinhabet.pagamento.domain.CobrancaCriada;
import com.caxinhabet.pagamento.domain.DadosCliente;
import com.caxinhabet.pagamento.domain.EstadoCobranca;
import com.caxinhabet.pagamento.domain.ProvedorPagamento;
import com.caxinhabet.pagamento.domain.SolicitacaoCobranca;
import com.caxinhabet.participante.adapter.persistence.ParticipanteEntity;
import com.caxinhabet.participante.adapter.persistence.ParticipanteRepository;
import com.caxinhabet.participante.domain.StatusParticipante;
import com.caxinhabet.shared.money.Money;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Gera uma cobrança PIX para um Participante pagar o ingresso
 * (Story 3.2 v5, FR-7).
 *
 * <p>Fluxo (tudo numa {@code @Transactional}):
 * <ol>
 *   <li>Valida pré-condições (AC-1): Caixinha em {@code coletando_pagamentos},
 *       Participante em {@code aceito} com Palpite, perfil de pagamento
 *       completo (nome+CPF+chave PIX — FR-16 v5).
 *   <li>Garante o customer Asaas: se ainda não há {@code asaasCustomerId},
 *       cria via porta e registra (lazy, 1x).
 *   <li>Invalida cobrança {@code ativa} anterior, se houver (FR-7: máx 1).
 *   <li>Invoca {@code ProvedorPagamento.criarCobranca} — a porta.
 *   <li>Persiste {@code CobrancaEntity} nova ({@code ativa}).
 *   <li>Transiciona o Participante {@code aceito → pagamento_iniciado}.
 * </ol>
 *
 * <p><b>Isolamento de módulos (ArquiteturaTest):</b> este use case vive
 * em {@code pagamento} e NÃO importa adapters de {@code caixinha}/{@code auth}.
 * Usa as portas {@link ConsultaCaixinha} e {@link PerfilPagamentoGateway}.
 * O {@code participante.adapter} é acessível (não há regra contra) e a
 * transição de status do Participante é mutação legítima aqui.
 *
 * <p><b>Não há "marcar como pago"</b> — Status só vira {@code pago} por
 * webhook do Provedor (FR-8, Story 3.3).
 */
@Service
public class GerarCobrancaUseCase {

	private final ConsultaCaixinha consultaCaixinha;
	private final ParticipanteRepository participantes;
	private final PerfilPagamentoGateway perfis;
	private final CobrancaRepository cobrancas;
	private final ProvedorPagamento provedor;
	private final LedgerService ledger;

	public GerarCobrancaUseCase(
			ConsultaCaixinha consultaCaixinha,
			ParticipanteRepository participantes,
			PerfilPagamentoGateway perfis,
			CobrancaRepository cobrancas,
			ProvedorPagamento provedor,
			LedgerService ledger) {
		this.consultaCaixinha = consultaCaixinha;
		this.participantes = participantes;
		this.perfis = perfis;
		this.cobrancas = cobrancas;
		this.provedor = provedor;
		this.ledger = ledger;
	}

	/** Resultado do use case — dados para o controller montar a resposta. */
	public record Resultado(
			String cobrancaId,
			String copiaECola,
			String qrCodeBase64,
			java.time.Instant expiraEm,
			Money valor) {}

	@Transactional
	public Resultado executar(long caixinhaId, String emailAutenticado) {
		// 0. Resolve o id do Usuário pelo e-mail (via porta do auth).
		long usuarioId =
				perfis.idPorEmail(emailAutenticado)
						.orElseThrow(
								() ->
										new ResponseStatusException(
												HttpStatus.UNAUTHORIZED, "Usuário não encontrado."));

		// 1. Caixinha precisa existir (via porta de leitura)
		ConsultaCaixinha.DadosCaixinha caixinha =
				consultaCaixinha
						.buscar(caixinhaId)
						.orElseThrow(
								() ->
										new ResponseStatusException(
												HttpStatus.NOT_FOUND, "Caixinha não encontrada."));

		// 2. Autor precisa ser Participante (anti-enumeração: 404)
		ParticipanteEntity participante =
				participantes
						.findByCaixinhaIdAndEmail(caixinhaId, emailAutenticado)
						.orElseThrow(
								() ->
										new ResponseStatusException(
												HttpStatus.NOT_FOUND, "Caixinha não encontrada."));

		// 3. Pré-condições de estado (AC-1)
		if (caixinha.estado() != EstadoCaixinha.coletando_pagamentos) {
			throw new PagamentoIndisponivelException(
					"O pagamento ainda não está liberado para esta Caixinha.");
		}
		if (participante.getStatus() != StatusParticipante.aceito) {
			throw new PagamentoIndisponivelException(
					"Você precisa aceitar o convite antes de pagar"
							+ " (ou já tem um pagamento em andamento/concluído).");
		}
		if (participante.getPalpiteResultadoPossivelId() == null) {
			throw new PagamentoIndisponivelException(
					"Escolha seu palpite antes de pagar o ingresso.");
		}

		// 4. Perfil de pagamento completo (FR-16 v5) — via porta do auth
		PerfilPagamentoGateway.PerfilPagamento perfil =
				perfis
						.buscar(usuarioId)
						.orElseThrow(
								() ->
										new ResponseStatusException(
												HttpStatus.NOT_FOUND, "Usuário não encontrado."));
		if (!perfil.completo()) {
			throw new ChavePixObrigatoriaException(
					"Complete seu perfil de pagamento (nome, CPF e chave PIX) antes de pagar.");
		}

		// 5. Garante o customer Asaas (lazy — cria 1x, reusa)
		String customerId = perfil.asaasCustomerId();
		if (customerId == null || customerId.isBlank()) {
			customerId =
					provedor.criarCliente(
							new DadosCliente(perfil.nomeCompleto(), perfil.cpf()));
			perfis.registrarAsaasCustomerId(usuarioId, customerId);
		}

		// 6. Invalida cobrança ativa anterior (FR-7: máximo 1 ativa).
		// O flush explícito é necessário: o índice único parcial
		// `uq_pagamento_cobranca_participante_ativa` rejeitaria o INSERT da
		// cobrança nova (passo 8) se a invalidação da anterior ainda
		// estivesse só no contexto de persistência (dirty, não flushada).
		cobrancas
				.findByParticipanteIdAndEstado(participante.getId(), EstadoCobranca.ativa)
				.ifPresent(
						c -> {
							c.transicionarPara(EstadoCobranca.invalidada);
							cobrancas.saveAndFlush(c);
						});

		// 7. Cria a cobrança no PSP via a porta
		Money valor = Money.ofCentavos(caixinha.valorIngressoCentavos());
		String ref = "caixinha-" + caixinhaId + ":participante-" + participante.getId();
		CobrancaCriada criada =
				provedor.criarCobranca(
						new SolicitacaoCobranca(
								customerId, ref, valor, "Caixinha: " + caixinha.titulo()));

		// 8. Persiste a cobrança nova
		CobrancaEntity cobranca =
				new CobrancaEntity(
						participante.getId(),
						caixinhaId,
						criada.cobrancaId(),
						criada.copiaECola(),
						criada.qrCodeImagemBase64(),
						valor.centavos(),
						criada.expiraEm());
		cobrancas.save(cobranca);

		// 8b. Registra a geração no ledger (fix code review Épico 3):
		// crédito `a_receber` — o Participante passou a "dever" este ingresso.
		// Sem isto, registrarCustodia (confirmação) debitaria `a_receber`
		// sem nunca tê-la creditado. A referência de auditoria é o
		// cobrancaId (a geração não tem event_id de webhook).
		ledger.registrarCobrancaGerada(
				caixinhaId,
				participante.getId(),
				criada.cobrancaId(),
				valor,
				criada.cobrancaId());

		// 9. Transiciona Participante aceito → pagamento_iniciado
		participante.setStatus(StatusParticipante.pagamento_iniciado);

		return new Resultado(
				criada.cobrancaId(),
				criada.copiaECola(),
				criada.qrCodeImagemBase64(),
				criada.expiraEm(),
				valor);
	}
}
