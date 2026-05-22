package com.caxinhabet.pagamento.adapter.web;

import com.caxinhabet.pagamento.adapter.persistence.CobrancaEntity;
import com.caxinhabet.pagamento.adapter.persistence.CobrancaRepository;
import com.caxinhabet.pagamento.app.ExpirarCobrancaService;
import com.caxinhabet.pagamento.app.GerarCobrancaUseCase;
import com.caxinhabet.pagamento.domain.EstadoCobranca;
import com.caxinhabet.participante.adapter.persistence.ParticipanteEntity;
import com.caxinhabet.participante.adapter.persistence.ParticipanteRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Endpoints de pagamento PIX (Story 3.2 v5, FR-7).
 *
 * <p>Vive em {@code pagamento.adapter.web} — mantém o módulo coeso. NÃO
 * importa adapters de {@code auth}/{@code caixinha} (ArquiteturaTest):
 * o {@code GerarCobrancaUseCase} resolve o Usuário internamente pela
 * porta {@code PerfilPagamentoGateway}; o controller passa só o e-mail
 * do {@code Authentication}.
 *
 * <ul>
 *   <li>{@code POST /caixinhas/{id}/cobranca} — gera nova cobrança PIX.
 *   <li>{@code GET /caixinhas/{id}/cobranca} — cobrança ativa do
 *       Participante autenticado (para a tela recarregar).
 * </ul>
 */
@RestController
@RequestMapping("/caixinhas/{caixinhaId}/cobranca")
class PagamentoController {

	private final GerarCobrancaUseCase gerarCobranca;
	private final ExpirarCobrancaService expirarCobranca;
	private final CobrancaRepository cobrancas;
	private final ParticipanteRepository participantes;

	PagamentoController(
			GerarCobrancaUseCase gerarCobranca,
			ExpirarCobrancaService expirarCobranca,
			CobrancaRepository cobrancas,
			ParticipanteRepository participantes) {
		this.gerarCobranca = gerarCobranca;
		this.expirarCobranca = expirarCobranca;
		this.cobrancas = cobrancas;
		this.participantes = participantes;
	}

	@PostMapping
	ResponseEntity<CobrancaResponse> gerar(
			@PathVariable long caixinhaId, Authentication auth) {
		String email = emailAutenticado(auth);
		GerarCobrancaUseCase.Resultado r = gerarCobranca.executar(caixinhaId, email);
		return ResponseEntity.status(HttpStatus.CREATED).body(CobrancaResponse.de(r));
	}

	@GetMapping
	ResponseEntity<CobrancaResponse> consultar(
			@PathVariable long caixinhaId, Authentication auth) {
		String email = emailAutenticado(auth);
		// Anti-enumeração: precisa ser Participante da Caixinha.
		ParticipanteEntity participante =
				participantes
						.findByCaixinhaIdAndEmail(caixinhaId, email)
						.orElseThrow(
								() ->
										new ResponseStatusException(
												HttpStatus.NOT_FOUND, "Caixinha não encontrada."));

		// Expiração lazy (Story 3.2): se a cobrança ativa venceu, expira
		// agora antes de responder — o cliente vê o estado correto.
		expirarCobranca.expirarSeVencida(participante.getId());

		CobrancaEntity cobranca =
				cobrancas
						.findByParticipanteIdAndEstado(
								participante.getId(), EstadoCobranca.ativa)
						.orElseThrow(
								() ->
										new ResponseStatusException(
												HttpStatus.NOT_FOUND,
												"Nenhuma cobrança ativa para este Participante."));
		return ResponseEntity.ok(CobrancaResponse.de(cobranca));
	}

	private static String emailAutenticado(Authentication auth) {
		if (auth == null || !auth.isAuthenticated() || auth.getName() == null) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Não autenticado.");
		}
		return auth.getName();
	}
}
