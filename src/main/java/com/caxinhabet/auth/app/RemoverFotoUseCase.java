package com.caxinhabet.auth.app;

import com.caxinhabet.auth.adapter.persistence.UsuarioEntity;
import com.caxinhabet.auth.adapter.persistence.UsuarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Caso de uso: limpar a foto do perfil (Minha Conta, 2026-05).
 *
 * <p>Zera {@code foto_blob} + {@code foto_mime}; o front passa a exibir as
 * iniciais. Idempotente.
 */
@Service
public class RemoverFotoUseCase {

    private final UsuarioRepository usuarios;

    public RemoverFotoUseCase(UsuarioRepository usuarios) {
        this.usuarios = usuarios;
    }

    @Transactional
    public UsuarioEntity executar(Long usuarioId) {
        UsuarioEntity u = usuarios.findById(usuarioId).orElseThrow();
        u.removerFoto();
        return usuarios.save(u);
    }
}
