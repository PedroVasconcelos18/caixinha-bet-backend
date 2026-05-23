package com.caxinhabet.auth.app;

import com.caxinhabet.auth.adapter.persistence.UsuarioEntity;
import com.caxinhabet.auth.adapter.persistence.VerificacaoEmailEntity;
import com.caxinhabet.auth.adapter.persistence.VerificacaoEmailRepository;
import com.caxinhabet.auth.domain.MagicLinkSender;
import com.caxinhabet.auth.domain.TokenAcesso;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Caso de uso: solicitar verificação de e-mail (Minha Conta, 2026-05).
 *
 * <p>Gera token, grava em {@code verificacao_email} (hash SHA-256) e dispara
 * o link {@code <publicBaseUrl>/verificar-email?token=<token cru>} via
 * {@link MagicLinkSender#enviarVerificacao}. Espelha {@link
 * SolicitarResetSenhaUseCase} — mesmo modelo de token, tabela própria.
 *
 * <p>Chamado em dois momentos:
 * <ul>
 *   <li>Cadastro novo (a partir de {@link RegistrarUsuarioUseCase}).</li>
 *   <li>Reenvio (a partir do endpoint {@code /auth/verificar-email/reenviar}).</li>
 * </ul>
 */
@Service
public class SolicitarVerificacaoEmailUseCase {

    private final VerificacaoEmailRepository repo;
    private final MagicLinkSender sender;
    private final AppProperties appProps;
    private final AuthProperties authProps;

    public SolicitarVerificacaoEmailUseCase(
            VerificacaoEmailRepository repo,
            MagicLinkSender sender,
            AppProperties appProps,
            AuthProperties authProps) {
        this.repo = repo;
        this.sender = sender;
        this.appProps = appProps;
        this.authProps = authProps;
    }

    @Transactional
    public void executar(UsuarioEntity usuario) {
        TokenAcesso token = TokenAcesso.gerar();
        Instant agora = Instant.now();
        Instant expira =
                agora.plus(authProps.getVerificacao().getTtlHoras(), ChronoUnit.HOURS);

        repo.save(
                VerificacaoEmailEntity.criar(usuario.getId(), token.hash(), expira));

        String link =
                appProps.getPublicBaseUrl()
                        + "/verificar-email?token="
                        + URLEncoder.encode(token.valor(), StandardCharsets.UTF_8);
        sender.enviarVerificacao(usuario.getEmail(), link, expira);
    }
}
