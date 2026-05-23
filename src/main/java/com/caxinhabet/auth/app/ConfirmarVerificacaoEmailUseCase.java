package com.caxinhabet.auth.app;

import com.caxinhabet.auth.adapter.persistence.UsuarioEntity;
import com.caxinhabet.auth.adapter.persistence.UsuarioRepository;
import com.caxinhabet.auth.adapter.persistence.VerificacaoEmailEntity;
import com.caxinhabet.auth.adapter.persistence.VerificacaoEmailRepository;
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
 * Caso de uso: confirmar verificação de e-mail (Minha Conta, 2026-05).
 *
 * <p>Espelha {@link RedefinirSenhaUseCase}: hasheia o token cru, busca em
 * {@code verificacao_email}, valida que não está consumido nem expirado, e
 * então:
 * <ul>
 *   <li>marca o usuário como {@code email_verificado=true};</li>
 *   <li>marca a verificação como consumida;</li>
 *   <li>abre sessão (o usuário sai logado direto do clique no e-mail).</li>
 * </ul>
 */
@Service
public class ConfirmarVerificacaoEmailUseCase {

    private final VerificacaoEmailRepository repo;
    private final UsuarioRepository usuarios;
    private final SessaoStore sessaoStore;
    private final AuthProperties authProps;

    public ConfirmarVerificacaoEmailUseCase(
            VerificacaoEmailRepository repo,
            UsuarioRepository usuarios,
            SessaoStore sessaoStore,
            AuthProperties authProps) {
        this.repo = repo;
        this.usuarios = usuarios;
        this.sessaoStore = sessaoStore;
        this.authProps = authProps;
    }

    @Transactional
    public SessaoUsuario executar(String tokenCru) {
        TokenAcesso token = TokenAcesso.de(tokenCru);
        VerificacaoEmailEntity ve =
                repo.findByTokenHash(token.hash()).orElseThrow(TokenInvalidoException::new);

        if (ve.getConsumidoEm() != null) {
            throw new AcessoJaConsumidoException();
        }
        Instant agora = Instant.now();
        if (!agora.isBefore(ve.getExpiraEm())) {
            throw new AcessoExpiradoException();
        }

        UsuarioEntity usuario =
                usuarios
                        .findById(ve.getUsuarioId())
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "Verificação aponta para usuário inexistente: id="
                                                        + ve.getUsuarioId()));
        usuario.marcarEmailVerificado();
        usuarios.save(usuario);

        ve.marcarConsumido(agora);
        repo.save(ve);

        String idSessao = TokenAcesso.gerar().valor();
        Instant expira = agora.plus(authProps.getSessao().getTtlDias(), ChronoUnit.DAYS);
        SessaoUsuario sessao =
                new SessaoUsuario(idSessao, usuario.getId(), usuario.getEmail(), agora, expira);
        sessaoStore.criar(sessao);
        return sessao;
    }
}
