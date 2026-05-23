package com.caxinhabet.auth.app;

import com.caxinhabet.auth.adapter.persistence.UsuarioEntity;
import com.caxinhabet.auth.adapter.persistence.UsuarioRepository;
import com.caxinhabet.auth.domain.DataNascimento;
import com.caxinhabet.auth.domain.Telefone;
import java.time.LocalDate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Caso de uso: atualizar campos do perfil (Minha Conta, 2026-05).
 *
 * <p>Edita nome, data de nascimento, telefone, cidade e bio. CPF e e-mail
 * permanecem imutáveis pela UI (alteração de e-mail exigiria
 * re-verificação; CPF é fixo). Valida via VOs ({@link DataNascimento},
 * {@link Telefone}) e delega à entidade.
 */
@Service
public class AtualizarPerfilUseCase {

    private final UsuarioRepository usuarios;

    public AtualizarPerfilUseCase(UsuarioRepository usuarios) {
        this.usuarios = usuarios;
    }

    @Transactional
    public UsuarioEntity executar(
            Long usuarioId,
            String nomeCompleto,
            LocalDate dataNascimento,
            String telefone,
            String cidade,
            String bio) {
        if (nomeCompleto != null && nomeCompleto.trim().length() < 2) {
            throw new IllegalArgumentException("Nome é obrigatório");
        }
        LocalDate nascimentoValidado =
                dataNascimento == null ? null : DataNascimento.de(dataNascimento).valor();
        Telefone telefoneValidado = Telefone.deOpcional(telefone);
        String telefoneDigitos =
                telefoneValidado == null ? telefone : telefoneValidado.digitos();

        UsuarioEntity u = usuarios.findById(usuarioId).orElseThrow();
        u.definirNome(nomeCompleto);
        u.definirPerfilExtra(nascimentoValidado, telefoneDigitos, cidade, bio);
        return usuarios.save(u);
    }
}
