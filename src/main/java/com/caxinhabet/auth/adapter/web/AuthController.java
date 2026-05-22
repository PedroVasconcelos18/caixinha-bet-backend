package com.caxinhabet.auth.adapter.web;

import com.caxinhabet.auth.adapter.persistence.UsuarioEntity;
import com.caxinhabet.auth.adapter.persistence.UsuarioRepository;
import com.caxinhabet.auth.adapter.session.SessaoStore;
import com.caxinhabet.auth.app.AtualizarChavePixUseCase;
import com.caxinhabet.auth.app.AtualizarPerfilPagamentoUseCase;
import com.caxinhabet.auth.app.AutenticarUseCase;
import com.caxinhabet.auth.app.AuthProperties;
import com.caxinhabet.auth.app.RedefinirSenhaUseCase;
import com.caxinhabet.auth.app.RegistrarUsuarioUseCase;
import com.caxinhabet.auth.app.SolicitarResetSenhaUseCase;
import com.caxinhabet.auth.domain.SessaoUsuario;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.Duration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
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

	AuthController(
			RegistrarUsuarioUseCase registrar,
			AutenticarUseCase autenticar,
			SolicitarResetSenhaUseCase solicitarReset,
			RedefinirSenhaUseCase redefinirSenha,
			SessaoStore sessaoStore,
			AuthProperties authProps,
			UsuarioRepository usuarios,
			AtualizarChavePixUseCase atualizarChavePix,
			AtualizarPerfilPagamentoUseCase atualizarPerfilPagamento) {
		this.registrar = registrar;
		this.autenticar = autenticar;
		this.solicitarReset = solicitarReset;
		this.redefinirSenha = redefinirSenha;
		this.sessaoStore = sessaoStore;
		this.authProps = authProps;
		this.usuarios = usuarios;
		this.atualizarChavePix = atualizarChavePix;
		this.atualizarPerfilPagamento = atualizarPerfilPagamento;
	}

	@PostMapping("/registrar")
	ResponseEntity<MeResponse> registrar(
			@Valid @RequestBody RegistrarRequest req, HttpServletRequest httpReq) {
		SessaoUsuario sessao =
				registrar.executar(req.nomeCompleto(), req.cpf(), req.email(), req.senha());
		return respostaComSessao(sessao, httpReq);
	}

	@PostMapping("/login")
	ResponseEntity<MeResponse> login(
			@Valid @RequestBody LoginRequest req, HttpServletRequest httpReq) {
		SessaoUsuario sessao = autenticar.executar(req.email(), req.senha());
		return respostaComSessao(sessao, httpReq);
	}

	@PostMapping("/recuperar-senha")
	ResponseEntity<Void> recuperarSenha(@Valid @RequestBody RecuperarSenhaRequest req) {
		solicitarReset.executar(req.email());
		// Sempre 204 — não distingue e-mail com/sem conta (anti-enumeração).
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/redefinir-senha")
	ResponseEntity<MeResponse> redefinirSenha(
			@Valid @RequestBody RedefinirSenhaRequest req, HttpServletRequest httpReq) {
		SessaoUsuario sessao = redefinirSenha.executar(req.token(), req.senha());
		return respostaComSessao(sessao, httpReq);
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

	@PostMapping("/sair")
	ResponseEntity<Void> sair(HttpServletRequest httpReq) {
		String idSessao = lerCookieSessao(httpReq);
		if (idSessao != null) {
			sessaoStore.invalidar(idSessao);
		}
		ResponseCookie cookie =
				ResponseCookie.from(COOKIE_SESSAO, "")
						.httpOnly(true)
						.sameSite("Lax")
						.secure(httpReq.isSecure())
						.path("/")
						.maxAge(0)
						.build();
		return ResponseEntity.noContent()
				.header(HttpHeaders.SET_COOKIE, cookie.toString())
				.build();
	}

	/** Monta a resposta 200 com o corpo {@link MeResponse} + cookie de sessão. */
	private ResponseEntity<MeResponse> respostaComSessao(
			SessaoUsuario sessao, HttpServletRequest httpReq) {
		UsuarioEntity u =
				usuarios
						.findByEmail(sessao.email())
						.orElseThrow(
								() ->
										new ResponseStatusException(
												HttpStatus.INTERNAL_SERVER_ERROR,
												"Sessão aberta para usuário inexistente."));
		ResponseCookie cookie = montarCookieSessao(sessao, httpReq.isSecure());
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

	private ResponseCookie montarCookieSessao(SessaoUsuario sessao, boolean isSecure) {
		long maxAgeSegundos = Duration.ofDays(authProps.getSessao().getTtlDias()).toSeconds();
		return ResponseCookie.from(COOKIE_SESSAO, sessao.idSessao())
				.httpOnly(true)
				.sameSite("Lax")
				.secure(isSecure)
				.path("/")
				.maxAge(maxAgeSegundos)
				.build();
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
