package com.caxinhabet.auth.adapter.web;

import com.caxinhabet.auth.adapter.persistence.UsuarioEntity;
import java.time.LocalDate;

/**
 * Resposta de {@code GET /auth/me} (Minha Conta, 2026-05 — perfil estendido,
 * verificação de e-mail, foto).
 *
 * <p>Sucesso = corpo direto (sem envelope) — regra dura AR-8. camelCase 1:1
 * com o front. Dinheiro nunca aparece neste DTO (usuário não é Conta-Ledger);
 * datas em ISO (LocalDate / Instant via Jackson default).
 *
 * @param fotoUrl path relativo ({@code /auth/me/foto?v=…}) ou {@code null}.
 *     O front prepende a base URL da API. O query param {@code v} é
 *     cache-buster derivado do tamanho do blob — invalida cache do browser
 *     quando a foto muda. Não expõe conteúdo.
 */
public record MeResponse(
        String email,
        String chavePix,
        String nomeCompleto,
        String cpf,
        LocalDate dataNascimento,
        String telefone,
        String cidade,
        String bio,
        String fotoUrl,
        boolean emailVerificado,
        boolean perfilPagamentoCompleto) {

    public static MeResponse de(UsuarioEntity u) {
        String fotoUrl =
                u.getFotoBlob() == null
                        ? null
                        : "/auth/me/foto?v=" + u.getFotoBlob().length;
        return new MeResponse(
                u.getEmail(),
                u.getChavePix(),
                u.getNomeCompleto(),
                u.getCpf(),
                u.getDataNascimento(),
                u.getTelefone(),
                u.getCidade(),
                u.getBio(),
                fotoUrl,
                u.isEmailVerificado(),
                u.perfilPagamentoCompleto());
    }
}
