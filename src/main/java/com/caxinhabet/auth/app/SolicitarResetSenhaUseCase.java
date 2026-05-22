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
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Caso de uso: solicitar reset de senha por e-mail (auth por senha, 2026-05).
 *
 * <p>Reaproveita a infra de token do antigo magic link: a tabela
 * {@code solicitacao_acesso} agora representa um token de reset de senha,
 * e o {@link MagicLinkSender} entrega o link. O {@code token_hash},
 * {@code expira_em} e {@code consumido_em} servem 1:1; {@code redirect_to}
 * não é mais usado (reset sempre volta para {@code /redefinir-senha}).
 *
 * <p><b>Sempre silencioso quanto à existência do e-mail</b> — se o e-mail
 * não tem conta, NÃO cria usuário e NÃO envia link, mas a API responde
 * 204 igual (anti-enumeração). Diferente do antigo magic link, que criava
 * usuário implícito.
 */
@Service
public class SolicitarResetSenhaUseCase {

	private final UsuarioRepository usuarios;
	private final SolicitacaoAcessoRepository solicitacoes;
	private final MagicLinkSender sender;
	private final AppProperties appProps;
	private final AuthProperties authProps;

	public SolicitarResetSenhaUseCase(
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
	public void executar(String emailBruto) {
		String email = normalizar(emailBruto);
		Optional<UsuarioEntity> usuario = usuarios.findByEmail(email);
		if (usuario.isEmpty()) {
			// E-mail sem conta: não cria nada, não envia. API responde 204
			// igual (anti-enumeração) — a decisão é do controller.
			return;
		}

		TokenAcesso token = TokenAcesso.gerar();
		Instant agora = Instant.now();
		Instant expira =
				agora.plus(authProps.getMagicLink().getTtlMinutos(), ChronoUnit.MINUTES);

		solicitacoes.save(
				new SolicitacaoAcessoEntity(
						usuario.get().getId(), token.hash(), null, agora, expira));

		String linkAbsoluto = montarLink(token.valor());
		sender.enviar(usuario.get().getEmail(), linkAbsoluto, expira);
	}

	private static String normalizar(String email) {
		if (email == null) {
			throw new IllegalArgumentException("email é obrigatório");
		}
		return email.trim().toLowerCase();
	}

	private String montarLink(String tokenCru) {
		try {
			String url =
					appProps.getPublicBaseUrl()
							+ "/redefinir-senha?token="
							+ URLEncoder.encode(tokenCru, StandardCharsets.UTF_8);
			new URI(url); // valida que é parseável
			return url;
		} catch (URISyntaxException e) {
			throw new IllegalStateException(
					"app.public-base-url inválido: " + appProps.getPublicBaseUrl(), e);
		}
	}
}
