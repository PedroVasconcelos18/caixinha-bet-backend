package com.caxinhabet.auth.adapter.web;

import com.caxinhabet.auth.adapter.session.SessaoStore;
import com.caxinhabet.auth.domain.SessaoUsuario;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Lê o cookie {@code caixinhabet_sessao} e popula o
 * {@code SecurityContext} com o {@code email} como principal (Story 2.1).
 *
 * <p>Posicionado antes do {@code UsernamePasswordAuthenticationFilter}
 * na cadeia (ver {@code SecurityConfig}). Quando o cookie está ausente
 * ou a sessão é desconhecida/expirada, deixa o contexto vazio — o Spring
 * Security responde 401 nas rotas autenticadas.
 */
public class SessaoCookieAuthenticationFilter extends OncePerRequestFilter {

	private final SessaoStore sessaoStore;

	public SessaoCookieAuthenticationFilter(SessaoStore sessaoStore) {
		this.sessaoStore = sessaoStore;
	}

	@Override
	protected void doFilterInternal(
			HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		String idSessao = AuthController.lerCookieSessao(request);
		if (idSessao != null) {
			sessaoStore
					.buscar(idSessao)
					.ifPresent(
							sessao -> {
								SessaoAuthentication auth = new SessaoAuthentication(sessao);
								SecurityContextHolder.getContext().setAuthentication(auth);
							});
		}
		chain.doFilter(request, response);
	}

	/**
	 * {@link AbstractAuthenticationToken} mínimo: principal = email, sem
	 * autoridades (autorização granular entra com os papéis de Caixinha
	 * em Stories futuras).
	 */
	private static final class SessaoAuthentication extends AbstractAuthenticationToken {

		private final SessaoUsuario sessao;

		SessaoAuthentication(SessaoUsuario sessao) {
			super(Collections.emptyList());
			this.sessao = sessao;
			setAuthenticated(true);
		}

		@Override
		public Object getCredentials() {
			return null;
		}

		@Override
		public Object getPrincipal() {
			return sessao.email();
		}

		@Override
		public String getName() {
			return sessao.email();
		}
	}
}
