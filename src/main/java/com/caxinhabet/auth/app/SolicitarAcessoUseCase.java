package com.caxinhabet.auth.app;

import com.caxinhabet.auth.adapter.persistence.SolicitacaoAcessoEntity;
import com.caxinhabet.auth.adapter.persistence.SolicitacaoAcessoRepository;
import com.caxinhabet.auth.adapter.persistence.UsuarioEntity;
import com.caxinhabet.auth.adapter.persistence.UsuarioRepository;
import com.caxinhabet.auth.domain.MagicLinkSender;
import com.caxinhabet.auth.domain.TokenAcesso;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Caso de uso: solicitar acesso por e-mail (Story 2.1, AC-1).
 *
 * <p>Fluxo (tudo em uma transação):
 * <ol>
 *   <li>Normaliza o e-mail (lowercase, trim) — defesa em profundidade
 *       sobre o {@code citext} do banco.
 *   <li>Resolve {@code Usuario}: busca por e-mail; se não existe, cria
 *       (cadastro implícito — FR-16 sem cadastro elaborado).
 *   <li>Gera {@link TokenAcesso} novo; persiste a solicitação com o
 *       {@code tokenHash} (NUNCA o token cru).
 *   <li>Valida o {@code redirectTo}: precisa ser caminho relativo
 *       começando com {@code /}, sem {@code //} (URL absoluta protocolo-
 *       relativa), sem {@code \\} ou {@code :} (esquemas). Inválido =
 *       descarta silenciosamente (default {@code /}).
 *   <li>Monta o link absoluto e dispara o sender. Se o sender lança,
 *       a transação desfaz a solicitação — sem fantasma.
 * </ol>
 *
 * <p><b>Resposta da API é sempre 204</b>, não distingue e-mail
 * novo/existente (anti-enumeração — AC-1).
 */
@Service
public class SolicitarAcessoUseCase {

	private static final Pattern REDIRECT_VALIDO =
			Pattern.compile("^/[A-Za-z0-9/_\\-?=&%.]*$");

	private final UsuarioRepository usuarios;
	private final SolicitacaoAcessoRepository solicitacoes;
	private final MagicLinkSender sender;
	private final AppProperties appProps;
	private final AuthProperties authProps;

	public SolicitarAcessoUseCase(
			UsuarioRepository usuarios,
			SolicitacaoAcessoRepository solicitacoes,
			MagicLinkSender sender,
			AppProperties appProps,
			AuthProperties authProps) {
		this.usuarios = usuarios;
		this.solicitacoes = solicitacoes;
		this.sender = sender;
		this.appProps = appProps;
		this.authProps = authProps;
	}

	@Transactional
	public void executar(String emailBruto, String redirectToBruto) {
		String email = normalizar(emailBruto);
		String redirectTo = sanitizarRedirect(redirectToBruto);

		UsuarioEntity usuario =
				usuarios.findByEmail(email)
						.orElseGet(() -> usuarios.save(UsuarioEntity.criar(email)));

		TokenAcesso token = TokenAcesso.gerar();
		Instant agora = Instant.now();
		Instant expira =
				agora.plus(authProps.getMagicLink().getTtlMinutos(), ChronoUnit.MINUTES);

		solicitacoes.save(
				new SolicitacaoAcessoEntity(
						usuario.getId(), token.hash(), redirectTo, agora, expira));

		String linkAbsoluto = montarLink(token.valor(), redirectTo);
		sender.enviar(usuario.getEmail(), linkAbsoluto, expira);
	}

	private static String normalizar(String email) {
		if (email == null) {
			throw new IllegalArgumentException("email é obrigatório");
		}
		return email.trim().toLowerCase();
	}

	/** Devolve o redirect válido OU {@code null} (= default /). */
	private static String sanitizarRedirect(String redirectTo) {
		if (redirectTo == null || redirectTo.isBlank()) {
			return null;
		}
		// Bloqueia "//foo" (protocolo-relativo) e qualquer ":" (esquema).
		if (redirectTo.startsWith("//") || redirectTo.contains(":") || redirectTo.contains("\\")) {
			return null;
		}
		if (!REDIRECT_VALIDO.matcher(redirectTo).matches()) {
			return null;
		}
		return redirectTo;
	}

	private String montarLink(String tokenCru, String redirectTo) {
		try {
			StringBuilder url =
					new StringBuilder(appProps.getPublicBaseUrl())
							.append("/auth/callback?token=")
							.append(URLEncoder.encode(tokenCru, StandardCharsets.UTF_8));
			if (redirectTo != null) {
				url.append("&redirectTo=")
						.append(URLEncoder.encode(redirectTo, StandardCharsets.UTF_8));
			}
			// Valida que a URL final é parseável (defesa contra publicBaseUrl
			// mal configurado).
			new URI(url.toString());
			return url.toString();
		} catch (URISyntaxException e) {
			throw new IllegalStateException(
					"app.public-base-url inválido: " + appProps.getPublicBaseUrl(), e);
		}
	}
}
