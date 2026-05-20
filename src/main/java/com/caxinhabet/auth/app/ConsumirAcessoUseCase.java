package com.caxinhabet.auth.app;

import com.caxinhabet.auth.adapter.persistence.SolicitacaoAcessoEntity;
import com.caxinhabet.auth.adapter.persistence.SolicitacaoAcessoRepository;
import com.caxinhabet.auth.adapter.persistence.UsuarioEntity;
import com.caxinhabet.auth.adapter.persistence.UsuarioRepository;
import com.caxinhabet.auth.adapter.session.SessaoStore;
import com.caxinhabet.auth.domain.AcessoExpiradoException;
import com.caxinhabet.auth.domain.AcessoJaConsumidoException;
import com.caxinhabet.auth.domain.SessaoUsuario;
import com.caxinhabet.auth.domain.TokenAcesso;
import com.caxinhabet.auth.domain.TokenInvalidoException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Caso de uso: consumir magic link e abrir sessão (Story 2.1, AC-2/AC-3).
 *
 * <p>Fluxo (tudo em uma transação):
 * <ol>
 *   <li>Hasheia o token recebido.
 *   <li>Busca a solicitação. Se não existe → {@link TokenInvalidoException}
 *       (404).
 *   <li>Se {@code consumido_em != null} → {@link AcessoJaConsumidoException}
 *       (410).
 *   <li>Se {@code agora >= expira_em} → {@link AcessoExpiradoException}
 *       (410).
 *   <li>Marca {@code consumido_em = now}; busca o {@code Usuario}; cria
 *       a sessão no {@link SessaoStore}.
 * </ol>
 *
 * <p>A ordem das checagens (consumido antes de expirado) casa com o AC-3:
 * mensagem clara distinta para o front oferecer reenvio.
 *
 * <p>Race condition: dois requests com o mesmo token chegando ao mesmo
 * tempo. Defesa: {@code consumidoEm} é atualizado pelo Hibernate na mesma
 * transação; o segundo request leria {@code consumido_em != null} e
 * estouraria {@link AcessoJaConsumidoException}. Para concorrência
 * dura, um futuro UPDATE explícito com WHERE consumido_em IS NULL
 * elimina qualquer brecha de leitura suja — não implementado nesta story
 * porque o cenário é improvável (duplo-clique no link é raro em ms).
 */
@Service
public class ConsumirAcessoUseCase {

	private final SolicitacaoAcessoRepository solicitacoes;
	private final UsuarioRepository usuarios;
	private final SessaoStore sessaoStore;
	private final AuthProperties authProps;

	public ConsumirAcessoUseCase(
			SolicitacaoAcessoRepository solicitacoes,
			UsuarioRepository usuarios,
			SessaoStore sessaoStore,
			AuthProperties authProps) {
		this.solicitacoes = solicitacoes;
		this.usuarios = usuarios;
		this.sessaoStore = sessaoStore;
		this.authProps = authProps;
	}

	@Transactional
	public Resultado executar(String tokenCru) {
		TokenAcesso token = TokenAcesso.de(tokenCru);
		SolicitacaoAcessoEntity solicitacao =
				solicitacoes.findByTokenHash(token.hash()).orElseThrow(TokenInvalidoException::new);

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
				usuarios.findById(solicitacao.getUsuarioId())
						.orElseThrow(
								() ->
										new IllegalStateException(
												"Solicitação aponta para usuário inexistente: id="
														+ solicitacao.getUsuarioId()));

		String idSessao = TokenAcesso.gerar().valor();
		Instant expiraSessao =
				agora.plus(authProps.getSessao().getTtlDias(), ChronoUnit.DAYS);
		SessaoUsuario sessao =
				new SessaoUsuario(idSessao, usuario.getId(), usuario.getEmail(), agora, expiraSessao);
		sessaoStore.criar(sessao);

		return new Resultado(sessao, solicitacao.getRedirectTo());
	}

	public record Resultado(SessaoUsuario sessao, String redirectTo) {}
}
