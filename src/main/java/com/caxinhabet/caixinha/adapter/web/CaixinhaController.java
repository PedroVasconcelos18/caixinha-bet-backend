package com.caxinhabet.caixinha.adapter.web;

import com.caxinhabet.auth.adapter.persistence.UsuarioEntity;
import com.caxinhabet.auth.adapter.persistence.UsuarioRepository;
import com.caxinhabet.caixinha.adapter.persistence.CaixinhaEntity;
import com.caxinhabet.caixinha.adapter.persistence.CaixinhaRepository;
import com.caxinhabet.caixinha.adapter.persistence.ResultadoPossivelEntity;
import com.caxinhabet.caixinha.adapter.persistence.ResultadoPossivelRepository;
import com.caxinhabet.caixinha.app.AceitarConviteUseCase;
import com.caxinhabet.caixinha.app.BuscarConviteUseCase;
import com.caxinhabet.caixinha.app.CriarCaixinhaUseCase;
import com.caxinhabet.caixinha.app.DefinirPalpiteUseCase;
import com.caxinhabet.caixinha.app.EnviarConvitesUseCase;
import com.caxinhabet.caixinha.domain.Caixinha;
import com.caxinhabet.caixinha.domain.NovaCaixinhaSpec;
import com.caxinhabet.caixinha.domain.ResultadoPossivel;
import com.caxinhabet.participante.adapter.persistence.ParticipanteEntity;
import com.caxinhabet.participante.adapter.persistence.ParticipanteRepository;
import com.caxinhabet.shared.money.Money;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Endpoints HTTP de Caixinha (Story 2.2).
 *
 * <p>Autenticação cobre todas as rotas via {@code SecurityConfig.anyRequest().authenticated()}
 * (Story 2.1). O e-mail do usuário vem como {@code Authentication.getName()}.
 *
 * <p><b>Imutabilidade por construção</b>: este controller NÃO expõe PATCH/PUT/DELETE.
 * Ausência = HTTP 405 default do Spring (testado explicitamente em
 * {@code CaixinhaControllerIT}).
 *
 * <p>{@code GET /caixinhas/{id}}: anti-enumeração — non-participantes
 * recebem 404 (não 403). FR-16 diz "só vê Caixinhas das quais participa".
 */
@RestController
@RequestMapping("/caixinhas")
class CaixinhaController {

	private final CriarCaixinhaUseCase criar;
	private final EnviarConvitesUseCase enviarConvites;
	private final BuscarConviteUseCase buscarConvite;
	private final AceitarConviteUseCase aceitarConvite;
	private final DefinirPalpiteUseCase definirPalpite;
	private final CaixinhaRepository caixinhas;
	private final ResultadoPossivelRepository resultados;
	private final ParticipanteRepository participantes;
	private final UsuarioRepository usuarios;
	private final com.caxinhabet.pagamento.app.ExpirarCobrancaService expirarCobranca;

	CaixinhaController(
			CriarCaixinhaUseCase criar,
			EnviarConvitesUseCase enviarConvites,
			BuscarConviteUseCase buscarConvite,
			AceitarConviteUseCase aceitarConvite,
			DefinirPalpiteUseCase definirPalpite,
			CaixinhaRepository caixinhas,
			ResultadoPossivelRepository resultados,
			ParticipanteRepository participantes,
			UsuarioRepository usuarios,
			com.caxinhabet.pagamento.app.ExpirarCobrancaService expirarCobranca) {
		this.criar = criar;
		this.enviarConvites = enviarConvites;
		this.buscarConvite = buscarConvite;
		this.expirarCobranca = expirarCobranca;
		this.aceitarConvite = aceitarConvite;
		this.definirPalpite = definirPalpite;
		this.caixinhas = caixinhas;
		this.resultados = resultados;
		this.participantes = participantes;
		this.usuarios = usuarios;
	}

	@PostMapping
	ResponseEntity<CaixinhaResponse> criarCaixinha(
			@Valid @RequestBody CriarCaixinhaRequest req, Authentication auth) {
		Long organizadorUsuarioId = resolverUsuarioId(auth);

		NovaCaixinhaSpec spec =
				new NovaCaixinhaSpec(
						req.titulo(),
						req.ladoA(),
						req.ladoB(),
						req.valorIngresso(),
						req.minimoParticipantes(),
						req.numeroGanhadores(),
						req.prazoEntrada(),
						req.dataApuracao(),
						req.rotulosResultados(),
						req.emailsConvidados());

		Caixinha caixinha = criar.executar(spec, organizadorUsuarioId);

		// Após criar, busca a lista atual de participantes (só o dono nesta story).
		List<ParticipanteEntity> participantesEntities =
				participantes.findByCaixinhaIdOrderByCriadoEmAsc(caixinha.id());

		CaixinhaResponse body = CaixinhaResponse.de(caixinha, participantesEntities);
		return ResponseEntity.created(URI.create("/caixinhas/" + caixinha.id())).body(body);
	}

	@GetMapping("/{id}")
	ResponseEntity<CaixinhaResponse> obter(@PathVariable Long id, Authentication auth) {
		// Resolve o usuário (lança 401 se não autenticado). Ignoramos o id —
		// só precisamos do email do principal.
		resolverUsuarioId(auth);

		CaixinhaEntity entity =
				caixinhas
						.findById(id)
						.orElseThrow(
								() ->
										new ResponseStatusException(
												org.springframework.http.HttpStatus.NOT_FOUND,
												"Caixinha não encontrada."));

		// Anti-enumeração: só participantes podem ver. Não-participante → 404.
		var participanteOpt =
				participantes.findByCaixinhaIdAndEmail(entity.getId(), auth.getName());
		if (participanteOpt.isEmpty()) {
			throw new ResponseStatusException(
					org.springframework.http.HttpStatus.NOT_FOUND, "Caixinha não encontrada.");
		}

		// Story 3.2 (FR-7): expiração lazy — se o Participante que está
		// consultando tem uma cobrança vencida, expira-a agora (volta a
		// `aceito`) antes de montar a resposta. Sem scheduler.
		expirarCobranca.expirarSeVencida(participanteOpt.get().getId());

		List<ResultadoPossivelEntity> resultadosEntidades =
				resultados.findByCaixinhaIdOrderByOrdemAsc(entity.getId());
		List<ParticipanteEntity> participantesEntities =
				participantes.findByCaixinhaIdOrderByCriadoEmAsc(entity.getId());

		List<ResultadoPossivel> resultadosDomain = new ArrayList<>(resultadosEntidades.size());
		for (ResultadoPossivelEntity r : resultadosEntidades) {
			resultadosDomain.add(
					new ResultadoPossivel(
							r.getId(), entity.getId(), r.getOrdem(), r.getRotulo()));
		}

		Caixinha caixinha =
				new Caixinha(
						entity.getId(),
						entity.getTitulo(),
						entity.getLadoA(),
						entity.getLadoB(),
						Money.ofCentavos(entity.getValorIngressoCentavos()),
						entity.getMinimoParticipantes(),
						entity.getNumeroGanhadores(),
						entity.getPrazoEntrada(),
						entity.getDataApuracao(),
						entity.getEstado(),
						entity.getOrganizadorUsuarioId(),
						entity.getCriadoEm(),
						resultadosDomain);

		return ResponseEntity.ok(CaixinhaResponse.de(caixinha, participantesEntities));
	}

	@PostMapping("/{id}/convites")
	ResponseEntity<EnviarConvitesResponse> convidar(
			@PathVariable Long id,
			@Valid @RequestBody EnviarConvitesRequest req,
			Authentication auth) {
		Long organizadorUsuarioId = resolverUsuarioId(auth);
		EnviarConvitesUseCase.Resultado r =
				enviarConvites.executar(id, organizadorUsuarioId, req.emails());
		return ResponseEntity.ok(new EnviarConvitesResponse(r.convidados(), r.jaPresentes()));
	}

	// ---------- Story 2.5: convite (aceitar + palpite) ----------

	@GetMapping("/{id}/convite")
	ResponseEntity<ConviteResponse> verConvite(
			@PathVariable Long id, Authentication auth) {
		Long usuarioId = resolverUsuarioId(auth);
		BuscarConviteUseCase.Resultado r =
				buscarConvite.executar(id, usuarioId, auth.getName());
		return ResponseEntity.ok(
				ConviteResponse.de(r.caixinha(), r.resultadosPossiveis(), r.eu()));
	}

	@PostMapping("/{id}/aceitar")
	ResponseEntity<ParticipanteResponse> aceitar(
			@PathVariable Long id, Authentication auth) {
		Long usuarioId = resolverUsuarioId(auth);
		ParticipanteEntity p = aceitarConvite.executar(id, usuarioId, auth.getName());
		return ResponseEntity.ok(ParticipanteResponse.de(p));
	}

	@PutMapping("/{id}/palpite")
	ResponseEntity<ParticipanteResponse> palpitar(
			@PathVariable Long id,
			@Valid @RequestBody DefinirPalpiteRequest req,
			Authentication auth) {
		Long usuarioId = resolverUsuarioId(auth);
		ParticipanteEntity p =
				definirPalpite.executar(
						id, usuarioId, auth.getName(), req.resultadoPossivelId());
		return ResponseEntity.ok(ParticipanteResponse.de(p));
	}

	private Long resolverUsuarioId(Authentication auth) {
		if (auth == null || !auth.isAuthenticated() || auth.getName() == null) {
			throw new ResponseStatusException(
					org.springframework.http.HttpStatus.UNAUTHORIZED, "Não autenticado.");
		}
		UsuarioEntity u =
				usuarios.findByEmail(auth.getName())
						.orElseThrow(
								() ->
										new ResponseStatusException(
												org.springframework.http.HttpStatus.UNAUTHORIZED,
												"Usuário autenticado não encontrado."));
		return u.getId();
	}
}
