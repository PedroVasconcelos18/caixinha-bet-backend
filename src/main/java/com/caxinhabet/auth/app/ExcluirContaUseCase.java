package com.caxinhabet.auth.app;

import com.caxinhabet.auth.adapter.persistence.UsuarioEntity;
import com.caxinhabet.auth.adapter.persistence.UsuarioRepository;
import com.caxinhabet.auth.adapter.session.SessaoStore;
import com.caxinhabet.auth.domain.ConfirmacaoInvalidaException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Caso de uso: soft delete da conta do usuário (Minha Conta, 2026-05).
 *
 * <p>Marca {@code deletado_em = now()} e invalida a sessão atual. Não apaga
 * Caixinhas/Payouts/Participações — o ledger é imutável (AR-8). Reversibilidade
 * fica via SQL administrativo (zerar {@code deletado_em}).
 *
 * <p>Exige confirmação textual {@code "EXCLUIR"} no body (defesa em
 * profundidade contra clique acidental).
 */
@Service
public class ExcluirContaUseCase {

    public static final String CONFIRMACAO_ESPERADA = "EXCLUIR";

    private final UsuarioRepository usuarios;
    private final SessaoStore sessaoStore;

    public ExcluirContaUseCase(UsuarioRepository usuarios, SessaoStore sessaoStore) {
        this.usuarios = usuarios;
        this.sessaoStore = sessaoStore;
    }

    @Transactional
    public void executar(Long usuarioId, String idSessao, String confirmacao) {
        if (!CONFIRMACAO_ESPERADA.equals(confirmacao)) {
            throw new ConfirmacaoInvalidaException();
        }
        UsuarioEntity u = usuarios.findById(usuarioId).orElseThrow();
        u.marcarExcluido();
        usuarios.save(u);
        sessaoStore.invalidar(idSessao);
    }
}
