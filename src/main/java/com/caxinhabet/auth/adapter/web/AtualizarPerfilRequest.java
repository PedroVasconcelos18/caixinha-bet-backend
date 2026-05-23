package com.caxinhabet.auth.adapter.web;

import java.time.LocalDate;

/**
 * Payload de {@code PUT /auth/me/perfil} (Minha Conta, 2026-05).
 *
 * <p>Body parcial: campos {@code null} significam "não mudar". Para
 * limpar um campo opcional ({@code telefone}, {@code cidade}, {@code bio}),
 * envie string vazia — o use case normaliza para {@code null} via
 * {@code UsuarioEntity.definirPerfilExtra}. {@code nomeCompleto} blank é
 * ignorado (preserva valor anterior).
 */
public record AtualizarPerfilRequest(
		String nomeCompleto,
		LocalDate dataNascimento,
		String telefone,
		String cidade,
		String bio) {}
