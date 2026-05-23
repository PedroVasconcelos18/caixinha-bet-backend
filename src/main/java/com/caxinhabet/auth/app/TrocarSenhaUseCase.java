package com.caxinhabet.auth.app;

import com.caxinhabet.auth.adapter.persistence.UsuarioEntity;
import com.caxinhabet.auth.adapter.persistence.UsuarioRepository;
import com.caxinhabet.auth.domain.Senha;
import com.caxinhabet.auth.domain.SenhaAtualIncorretaException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Caso de uso: trocar senha pelo próprio usuário logado (Minha Conta, 2026-05).
 *
 * <p>Diferente de {@link RedefinirSenhaUseCase} (com token de reset): aqui o
 * usuário já tem sessão e precisa provar a senha atual. Falha de
 * {@link PasswordEncoder#matches} → {@link SenhaAtualIncorretaException}
 * (401). Força da senha nova validada pelo VO {@link Senha} (422 via
 * {@code IllegalArgumentException}).
 *
 * <p>Não invalida outras sessões — o {@code SessaoStore} é in-memory e não
 * indexa por usuário (limitação aceita no spec). Endpoint apenas atualiza o
 * hash.
 */
@Service
public class TrocarSenhaUseCase {

    private final UsuarioRepository usuarios;
    private final PasswordEncoder encoder;

    public TrocarSenhaUseCase(UsuarioRepository usuarios, PasswordEncoder encoder) {
        this.usuarios = usuarios;
        this.encoder = encoder;
    }

    @Transactional
    public void executar(Long usuarioId, String senhaAtualBruta, String senhaNovaBruta) {
        UsuarioEntity u = usuarios.findById(usuarioId).orElseThrow();
        if (u.getSenhaHash() == null
                || senhaAtualBruta == null
                || !encoder.matches(senhaAtualBruta, u.getSenhaHash())) {
            throw new SenhaAtualIncorretaException();
        }
        Senha nova = Senha.crua(senhaNovaBruta);
        u.definirSenhaHash(encoder.encode(nova.valor()));
        usuarios.save(u);
    }
}
