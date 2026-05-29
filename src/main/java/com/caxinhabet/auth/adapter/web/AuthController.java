package com.caxinhabet.auth.adapter.web;

import com.caxinhabet.auth.adapter.persistence.UsuarioEntity;
import com.caxinhabet.auth.adapter.persistence.UsuarioRepository;
import com.caxinhabet.auth.adapter.session.SessaoStore;
import com.caxinhabet.auth.app.AtualizarChavePixUseCase;
import com.caxinhabet.auth.app.AtualizarPerfilPagamentoUseCase;
import com.caxinhabet.auth.app.AtualizarFotoUseCase;
import com.caxinhabet.auth.app.AtualizarPerfilUseCase;
import com.caxinhabet.auth.app.ConfirmarVerificacaoEmailUseCase;
import com.caxinhabet.auth.app.ConsultarHistoricoUseCase;
import com.caxinhabet.auth.app.ExcluirContaUseCase;
import com.caxinhabet.auth.app.RemoverFotoUseCase;
import com.caxinhabet.auth.app.SolicitarVerificacaoEmailUseCase;
import com.caxinhabet.auth.app.TrocarSenhaUseCase;
import com.caxinhabet.auth.app.AutenticarUseCase;
import com.caxinhabet.auth.app.AuthProperties;
import com.caxinhabet.auth.app.RedefinirSenhaUseCase;
import com.caxinhabet.auth.app.RegistrarUsuarioUseCase;
import com.caxinhabet.auth.app.SolicitarResetSenhaUseCase;
import com.caxinhabet.auth.domain.SessaoUsuario;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/**
 * Endpoints HTTP do módulo auth (auth por senha, 2026-05 — substitui o
 * magic link da Story 2.1).
 *
 * <p>Cadastro, login e redefinição de senha abrem sessão pelo mesmo
 * mecanismo: {@link #montarCookieSessao} monta o cookie {@code HttpOnly}
 * + {@code SameSite=Lax} (+{@code Secure} sob HTTPS).
 */
@RestController
@RequestMapping("/auth")
class AuthController {

	static final String COOKIE_SESSAO = "caixinhabet_sessao";

	private final RegistrarUsuarioUseCase registrar;
	private final AutenticarUseCase autenticar;
	private final SolicitarResetSenhaUseCase solicitarReset;
	private final RedefinirSenhaUseCase redefinirSenha;
	private final SessaoStore sessaoStore;
	private final AuthProperties authProps;
	private final UsuarioRepository usuarios;
	private final AtualizarChavePixUseCase atualizarChavePix;
	private final AtualizarPerfilPagamentoUseCase atualizarPerfilPagamento;
	private final AtualizarPerfilUseCase atualizarPerfil;
	private final TrocarSenhaUseCase trocarSenha;
	private final AtualizarFotoUseCase atualizarFoto;
	private final RemoverFotoUseCase removerFoto;
	private final ConsultarHistoricoUseCase consultarHistorico;
	private final ExcluirContaUseCase excluirConta;
	private final ConfirmarVerificacaoEmailUseCase confirmarVerificacao;
	private final SolicitarVerificacaoEmailUseCase solicitarVerificacao;

	AuthController(
			RegistrarUsuarioUseCase registrar,
			AutenticarUseCase autenticar,
			SolicitarResetSenhaUseCase solicitarReset,
			RedefinirSenhaUseCase redefinirSenha,
			SessaoStore sessaoStore,
			AuthProperties authProps,
			UsuarioRepository usuarios,
			AtualizarChavePixUseCase atualizarChavePix,
			AtualizarPerfilPagamentoUseCase atualizarPerfilPagamento,
			AtualizarPerfilUseCase atualizarPerfil,
			TrocarSenhaUseCase trocarSenha,
			AtualizarFotoUseCase atualizarFoto,
			RemoverFotoUseCase removerFoto,
			ConsultarHistoricoUseCase consultarHistorico,
			ExcluirContaUseCase excluirConta,
			ConfirmarVerificacaoEmailUseCase confirmarVerificacao,
			SolicitarVerificacaoEmailUseCase solicitarVerificacao) {
		this.registrar = registrar;
		this.autenticar = autenticar;
		this.solicitarReset = solicitarReset;
		this.redefinirSenha = redefinirSenha;
		this.sessaoStore = sessaoStore;
		this.authProps = authProps;
		this.usuarios = usuarios;
		this.atualizarChavePix = atualizarChavePix;
		this.atualizarPerfilPagamento = atualizarPerfilPagamento;
		this.atualizarPerfil = atualizarPerfil;
		this.trocarSenha = trocarSenha;
		this.atualizarFoto = atualizarFoto;
		this.removerFoto = removerFoto;
		this.consultarHistorico = consultarHistorico;
		this.excluirConta = excluirConta;
		this.confirmarVerificacao = confirmarVerificacao;
		this.solicitarVerificacao = solicitarVerificacao;
	}

	@PostMapping("/registrar")
	ResponseEntity<Void> registrar(@Valid @RequestBody RegistrarRequest req) {
		registrar.executar(
				req.nomeCompleto(),
				req.cpf(),
				req.email(),
				req.senha(),
				req.dataNascimento());
		// 202 Accepted: cadastro persistido, verificação enviada por e-mail.
		// Sem cookie de sessão — usuário só entra após confirmar o link.
		return ResponseEntity.accepted().build();
	}

	@PostMapping("/login")
	ResponseEntity<MeResponse> login(@Valid @RequestBody LoginRequest req) {
		SessaoUsuario sessao = autenticar.executar(req.email(), req.senha());
		return respostaComSessao(sessao);
	}

	@PostMapping("/recuperar-senha")
	ResponseEntity<Void> recuperarSenha(@Valid @RequestBody RecuperarSenhaRequest req) {
		solicitarReset.executar(req.email());
		// Sempre 204 — não distingue e-mail com/sem conta (anti-enumeração).
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/redefinir-senha")
	ResponseEntity<MeResponse> redefinirSenha(@Valid @RequestBody RedefinirSenhaRequest req) {
		SessaoUsuario sessao = redefinirSenha.executar(req.token(), req.senha());
		return respostaComSessao(sessao);
	}

	@PostMapping("/verificar-email/confirmar")
	ResponseEntity<MeResponse> confirmarVerificacao(
			@Valid @RequestBody ConfirmarVerificacaoRequest req) {
		SessaoUsuario sessao = confirmarVerificacao.executar(req.token());
		return respostaComSessao(sessao);
	}

	@PostMapping("/verificar-email/reenviar")
	ResponseEntity<Void> reenviarVerificacao(
			@Valid @RequestBody ReenviarVerificacaoRequest req) {
		// 204 sempre — anti-enumeração. Só dispara se o e-mail existe E
		// ainda não foi verificado.
		usuarios.findByEmail(req.email().trim().toLowerCase())
				.filter(u -> !u.isEmailVerificado())
				.ifPresent(solicitarVerificacao::executar);
		return ResponseEntity.noContent().build();
	}

	@GetMapping("/me")
	ResponseEntity<MeResponse> me(Authentication auth) {
		UsuarioEntity u = usuarioAutenticado(auth);
		return ResponseEntity.ok(MeResponse.de(u));
	}

	@PutMapping("/me/chave-pix")
	ResponseEntity<MeResponse> atualizarChavePix(
			@Valid @RequestBody AtualizarChavePixRequest req, Authentication auth) {
		UsuarioEntity u = usuarioAutenticado(auth);
		UsuarioEntity atualizado = atualizarChavePix.executar(u.getId(), req.chavePix());
		return ResponseEntity.ok(MeResponse.de(atualizado));
	}

	@PutMapping("/me/perfil-pagamento")
	ResponseEntity<MeResponse> atualizarPerfilPagamento(
			@Valid @RequestBody AtualizarPerfilPagamentoRequest req, Authentication auth) {
		UsuarioEntity u = usuarioAutenticado(auth);
		UsuarioEntity atualizado =
				atualizarPerfilPagamento.executar(u.getId(), req.nomeCompleto(), req.cpf());
		return ResponseEntity.ok(MeResponse.de(atualizado));
	}

	@PutMapping("/me/perfil")
	ResponseEntity<MeResponse> atualizarPerfil(
			@Valid @RequestBody AtualizarPerfilRequest req, Authentication auth) {
		UsuarioEntity u = usuarioAutenticado(auth);
		UsuarioEntity atualizado =
				atualizarPerfil.executar(
						u.getId(),
						req.nomeCompleto(),
						req.dataNascimento(),
						req.telefone(),
						req.cidade(),
						req.bio());
		return ResponseEntity.ok(MeResponse.de(atualizado));
	}

	@PutMapping("/me/senha")
	ResponseEntity<Void> trocarSenha(
			@Valid @RequestBody TrocarSenhaRequest req, Authentication auth) {
		UsuarioEntity u = usuarioAutenticado(auth);
		trocarSenha.executar(u.getId(), req.senhaAtual(), req.senhaNova());
		return ResponseEntity.noContent().build();
	}

	@PostMapping(value = "/me/foto", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	ResponseEntity<MeResponse> uploadFoto(
			@RequestParam("foto") MultipartFile arquivo, Authentication auth)
			throws IOException {
		UsuarioEntity u = usuarioAutenticado(auth);
		UsuarioEntity atualizado =
				atualizarFoto.executar(
						u.getId(), arquivo.getBytes(), arquivo.getContentType());
		return ResponseEntity.ok(MeResponse.de(atualizado));
	}

	@GetMapping("/me/foto")
	ResponseEntity<byte[]> baixarFoto(Authentication auth) {
		UsuarioEntity u = usuarioAutenticado(auth);
		if (u.getFotoBlob() == null) {
			return ResponseEntity.notFound().build();
		}
		return ResponseEntity.ok()
				.contentType(MediaType.parseMediaType(u.getFotoMime()))
				.cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS).cachePrivate())
				.body(u.getFotoBlob());
	}

	@DeleteMapping("/me/foto")
	ResponseEntity<MeResponse> removerFoto(Authentication auth) {
		UsuarioEntity u = usuarioAutenticado(auth);
		UsuarioEntity atualizado = removerFoto.executar(u.getId());
		return ResponseEntity.ok(MeResponse.de(atualizado));
	}

	@GetMapping("/me/historico")
	ResponseEntity<HistoricoResponse> historico(Authentication auth) {
		UsuarioEntity u = usuarioAutenticado(auth);
		return ResponseEntity.ok(consultarHistorico.executar(u.getId()));
	}

	@DeleteMapping("/me")
	ResponseEntity<Void> excluirConta(
			@Valid @RequestBody ExcluirContaRequest req,
			Authentication auth,
			HttpServletRequest httpReq) {
		UsuarioEntity u = usuarioAutenticado(auth);
		String idSessao = lerCookieSessao(httpReq);
		excluirConta.executar(u.getId(), idSessao, req.confirmacao());

		ResponseCookie cookie = montarCookieLimpeza();
		return ResponseEntity.noContent()
				.header(HttpHeaders.SET_COOKIE, cookie.toString())
				.build();
	}

	@PostMapping("/sair")
	ResponseEntity<Void> sair(HttpServletRequest httpReq) {
		String idSessao = lerCookieSessao(httpReq);
		if (idSessao != null) {
			sessaoStore.invalidar(idSessao);
		}
		ResponseCookie cookie = montarCookieLimpeza();
		return ResponseEntity.noContent()
				.header(HttpHeaders.SET_COOKIE, cookie.toString())
				.build();
	}

	/** Monta a resposta 200 com o corpo {@link MeResponse} + cookie de sessão. */
	private ResponseEntity<MeResponse> respostaComSessao(SessaoUsuario sessao) {
		UsuarioEntity u =
				usuarios
						.findByEmail(sessao.email())
						.orElseThrow(
								() ->
										new ResponseStatusException(
												HttpStatus.INTERNAL_SERVER_ERROR,
												"Sessão aberta para usuário inexistente."));
		ResponseCookie cookie = montarCookieSessao(sessao);
		return ResponseEntity.ok()
				.header(HttpHeaders.SET_COOKIE, cookie.toString())
				.body(MeResponse.de(u));
	}

	private UsuarioEntity usuarioAutenticado(Authentication auth) {
		if (auth == null || !auth.isAuthenticated() || auth.getPrincipal() == null) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Não autenticado.");
		}
		return usuarios
				.findByEmail(auth.getName())
				.orElseThrow(
						() ->
								new ResponseStatusException(
										HttpStatus.UNAUTHORIZED, "Usuário não encontrado."));
	}

	private ResponseCookie montarCookieSessao(SessaoUsuario sessao) {
		long maxAgeSegundos = Duration.ofDays(authProps.getSessao().getTtlDias()).toSeconds();
		return cookieBuilder(sessao.idSessao(), maxAgeSegundos).build();
	}

	/** Cookie de limpeza (logout / exclusão): mesmos atributos, Max-Age=0. */
	private ResponseCookie montarCookieLimpeza() {
		return cookieBuilder("", 0).build();
	}

	/**
	 * Builder comum dos cookies de sessão. Aplica {@code Domain} só quando
	 * configurado — domain vazio deixa o cookie host-only (dev/mesma origem);
	 * em prod {@code .caixinhabet.com} compartilha entre www e api.
	 */
	private ResponseCookie.ResponseCookieBuilder cookieBuilder(String valor, long maxAge) {
		AuthProperties.Sessao s = authProps.getSessao();
		ResponseCookie.ResponseCookieBuilder b =
				ResponseCookie.from(COOKIE_SESSAO, valor)
						.httpOnly(true)
						.sameSite(s.getCookieSameSite())
						.secure(s.isCookieSecure())
						.path("/")
						.maxAge(maxAge);
		if (s.getCookieDomain() != null && !s.getCookieDomain().isBlank()) {
			b.domain(s.getCookieDomain());
		}
		return b;
	}

	static String lerCookieSessao(HttpServletRequest req) {
		if (req.getCookies() == null) {
			return null;
		}
		for (jakarta.servlet.http.Cookie c : req.getCookies()) {
			if (COOKIE_SESSAO.equals(c.getName())) {
				return c.getValue();
			}
		}
		return null;
	}
}
