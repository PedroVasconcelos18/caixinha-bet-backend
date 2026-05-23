package com.caxinhabet.auth.app;

import com.caxinhabet.auth.adapter.persistence.UsuarioEntity;
import com.caxinhabet.auth.adapter.persistence.UsuarioRepository;
import com.caxinhabet.auth.domain.Foto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Caso de uso: substituir a foto do perfil (Minha Conta, 2026-05).
 *
 * <p>Valida via {@link Foto} (mime + tamanho); persiste bytes e mime na
 * tabela {@code usuario} (colunas {@code foto_blob} / {@code foto_mime}).
 */
@Service
public class AtualizarFotoUseCase {

    private final UsuarioRepository usuarios;

    public AtualizarFotoUseCase(UsuarioRepository usuarios) {
        this.usuarios = usuarios;
    }

    @Transactional
    public UsuarioEntity executar(Long usuarioId, byte[] bytes, String mime) {
        Foto foto = Foto.de(bytes, mime);
        UsuarioEntity u = usuarios.findById(usuarioId).orElseThrow();
        u.definirFoto(foto.bytes(), foto.mime());
        return usuarios.save(u);
    }
}
