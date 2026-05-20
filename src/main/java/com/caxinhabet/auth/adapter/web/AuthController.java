package com.caxinhabet.auth.adapter.web;

import com.caxinhabet.auth.adapter.session.SessaoStore;
import com.caxinhabet.auth.app.AuthProperties;
import com.caxinhabet.auth.app.ConsumirAcessoUseCase;
import com.caxinhabet.auth.app.SolicitarAcessoUseCase;
import com.caxinhabet.auth.domain.SessaoUsuario;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.Duration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Endpoints HTTP do módulo auth (Story 2.1).
 *
 * <p>O cookie de sessão é montado por {@link #montarCookieSessao} usando
 * {@link ResponseCookie} — HttpOnly + SameSite=Lax sempre; Secure quando
 * a request veio via HTTPS (em dev local HTTP, Secure é omitido para o
 * cookie funcionar; em produção HTTPS, sempre Secure).
 */
@RestController
@RequestMapping("/auth")
class AuthController {

	static final String COOKIE_SESSAO = "caixinhabet_sessao";

	private final SolicitarAcessoUseCase solicitar;
	private final ConsumirAcessoUseCase consumir;
	private final SessaoStore sessaoStore;
	private final AuthProperties authProps;

	AuthController(
			SolicitarAcessoUseCase solicitar,
			ConsumirAcessoUseCase consumir,
			SessaoStore sessaoStore,
			AuthProperties authProps) {
		this.solicitar = solicitar;
		this.consumir = consumir;
		this.sessaoStore = sessaoStore;
		this.authProps = authProps;
	}

	@PostMapping("/solicitar-acesso")
	ResponseEntity<Void> solicitarAcesso(@Valid @RequestBody SolicitarAcessoRequest req) {
		solicitar.executar(req.email(), req.redirectTo());
		// Sempre 204 — não distingue e-mail novo/existente (anti-enumeração).
		return ResponseEntity.noContent().build();
	}

	@GetMapping("/callback")
	ResponseEntity<Void> callback(
			@RequestParam("token") String token,
			HttpServletRequest httpReq) {

		ConsumirAcessoUseCase.Resultado resultado = consumir.executar(token);
		ResponseCookie cookie = montarCookieSessao(resultado.sessao(), httpReq.isSecure());

		String destino = resultado.redirectTo() == null ? "/" : resultado.redirectTo();
		return ResponseEntity.status(HttpStatus.FOUND)
				.location(URI.create(destino))
				.header(HttpHeaders.SET_COOKIE, cookie.toString())
				.build();
	}

	@GetMapping("/me")
	ResponseEntity<MeResponse> me(Authentication auth) {
		if (auth == null || !auth.isAuthenticated() || auth.getPrincipal() == null) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Não autenticado.");
		}
		return ResponseEntity.ok(new MeResponse(auth.getName()));
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
