package com.caxinhabet.auth.app;

import com.caxinhabet.auth.adapter.persistence.SolicitacaoAcessoEntity;
import com.caxinhabet.auth.adapter.persistence.SolicitacaoAcessoRepository;
import com.caxinhabet.auth.adapter.persistence.UsuarioEntity;
import com.caxinhabet.auth.adapter.persistence.UsuarioRepository;
import com.caxinhabet.auth.adapter.session.SessaoStore;
import com.caxinhabet.auth.domain.AcessoExpiradoException;
import com.caxinhabet.auth.domain.AcessoJaConsumidoException;
import com.caxinhabet.auth.domain.Senha;
import com.caxinhabet.auth.domain.SessaoUsuario;
import com.caxinhabet.auth.domain.TokenAcesso;
import com.caxinhabet.auth.domain.TokenInvalidoException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Caso de uso: redefinir senha a partir de um token de reset (auth por
 * senha, 2026-05). Substitui o {@code ConsumirAcessoUseCase} do magic link.
 *
 * <p>Fluxo (tudo em uma transação):
 * <ol>
 *   <li>Valida a senha nova ({@link Senha}) ANTES de mexer no token — se
 *       a senha é fraca, a transação desfaz e o token continua usável.
 *   <li>Hasheia o token; busca a solicitação ({@code 404} se não existe).
 *   <li>{@code consumido_em != null} → {@link AcessoJaConsumidoException}
 *       ({@code 410}); {@code agora >= expira_em} → {@link
 *       AcessoExpiradoException} ({@code 410}).
 *   <li>Marca {@code consumido_em}; grava o novo {@code senhaHash} no
 *       {@code Usuario}; abre sessão (o usuário sai logado).
 * </ol>
 *
 * <p>Também serve para usuários legados (sem senha) definirem a primeira
 * senha — {@code definirSenhaHash} grava igual, exista hash anterior ou não.
 */
@Service
public class RedefinirSenhaUseCase {

	private final SolicitacaoAcessoRepository solicitacoes;
	private final UsuarioRepository usuarios;
	private final SessaoStore sessaoStore;
	private final PasswordEncoder encoder;
	private final AuthProperties authProps;

	public RedefinirSenhaUseCase(
			SolicitacaoAcessoRepository solicitacoes,
			UsuarioRepository usuarios,
			SessaoStore sessaoStore,
			PasswordEncoder encoder,
			AuthProperties authProps) {
		this.solicitacoes = solicitacoes;
		this.usuarios = usuarios;
		this.sessaoStore = sessaoStore;
		this.encoder = encoder;
		this.authProps = authProps;
	}

	@Transactional
	public SessaoUsuario executar(String tokenCru, String senhaNovaBruta) {
		Senha senhaNova = Senha.crua(senhaNovaBruta); // valida antes de consumir o token
		TokenAcesso token = TokenAcesso.de(tokenCru);

		SolicitacaoAcessoEntity solicitacao =
				solicitacoes
						.findByTokenHash(token.hash())
						.orElseThrow(TokenInvalidoException::new);

		if (solicitacao.getConsumidoEm() != null) {
			throw new AcessoJaConsumidoException();
		}
		Instant agora = Instant.now();
		if (!agora.isBefore(solicitacao.getExpiraEm())) {
			throw new AcessoExpiradoException();
		}

		solicitacao.marcarConsumida(agora);
		solicitacoes.save(solicitacao);

		UsuarioEntity usuario =
				usuarios
						.findById(solicitacao.getUsuarioId())
						.orElseThrow(
								() ->
										new IllegalStateException(
												"Solicitação aponta para usuário inexistente: id="
														+ solicitacao.getUsuarioId()));
		usuario.definirSenhaHash(encoder.encode(senhaNova.valor()));
		usuarios.save(usuario);

		String idSessao = TokenAcesso.gerar().valor();
		Instant expira = agora.plus(authProps.getSessao().getTtlDias(), ChronoUnit.DAYS);
		SessaoUsuario sessao =
				new SessaoUsuario(idSessao, usuario.getId(), usuario.getEmail(), agora, expira);
		sessaoStore.criar(sessao);
		return sessao;
	}
}
