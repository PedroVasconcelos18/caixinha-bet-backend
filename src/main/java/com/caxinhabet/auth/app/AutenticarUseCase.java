package com.caxinhabet.auth.app;

import com.caxinhabet.auth.adapter.persistence.UsuarioEntity;
import com.caxinhabet.auth.adapter.persistence.UsuarioRepository;
import com.caxinhabet.auth.adapter.session.SessaoStore;
import com.caxinhabet.auth.domain.CredenciaisInvalidasException;
import com.caxinhabet.auth.domain.SessaoUsuario;
import com.caxinhabet.auth.domain.TokenAcesso;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Caso de uso: login por e-mail + senha (auth por senha, 2026-05).
 *
 * <p>Fluxo (somente leitura, exceto a criação da sessão):
 * <ol>
 *   <li>Normaliza o e-mail; busca o {@code Usuario}.
 *   <li>Se não existe, OU não tem {@code senhaHash} (usuário legado do
 *       magic link), OU a senha não confere → {@link
 *       CredenciaisInvalidasException}. Mensagem genérica idêntica nos
 *       três casos — anti-enumeração.
 *   <li>Senha confere → abre sessão.
 * </ol>
 *
 * <p>O encoder do BCrypt tem custo constante mesmo quando o usuário não
 * existe? Não — a busca falha antes. Aceitamos o pequeno sinal de timing:
 * o MVP não tem rate limiting (risco aceito no spec) e a janela é estreita.
 */
@Service
public class AutenticarUseCase {

	private final UsuarioRepository usuarios;
	private final SessaoStore sessaoStore;
	private final PasswordEncoder encoder;
	private final AuthProperties authProps;

	public AutenticarUseCase(
			UsuarioRepository usuarios,
			SessaoStore sessaoStore,
			PasswordEncoder encoder,
			AuthProperties authProps) {
		this.usuarios = usuarios;
		this.sessaoStore = sessaoStore;
		this.encoder = encoder;
		this.authProps = authProps;
	}

	@Transactional
	public SessaoUsuario executar(String emailBruto, String senhaBruta) {
		String email = emailBruto == null ? "" : emailBruto.trim().toLowerCase();
		UsuarioEntity usuario =
				usuarios.findByEmail(email).orElseThrow(CredenciaisInvalidasException::new);

		if (usuario.getSenhaHash() == null
				|| senhaBruta == null
				|| !encoder.matches(senhaBruta, usuario.getSenhaHash())) {
			throw new CredenciaisInvalidasException();
		}

		Instant agora = Instant.now();
		String idSessao = TokenAcesso.gerar().valor();
		Instant expira = agora.plus(authProps.getSessao().getTtlDias(), ChronoUnit.DAYS);
		SessaoUsuario sessao =
				new SessaoUsuario(idSessao, usuario.getId(), usuario.getEmail(), agora, expira);
		sessaoStore.criar(sessao);
		return sessao;
	}
}
