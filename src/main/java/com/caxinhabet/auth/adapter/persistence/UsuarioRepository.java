package com.caxinhabet.auth.adapter.persistence;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repositório Spring Data para {@link UsuarioEntity} (Story 2.1).
 *
 * <p>{@code findByEmail} é case-insensitive automaticamente porque a coluna
 * {@code email} é {@code CITEXT} no Postgres. Defesa em profundidade: o
 * use case ainda normaliza o input para lowercase antes de chamar este
 * método.
 */
public interface UsuarioRepository extends JpaRepository<UsuarioEntity, Long> {

	Optional<UsuarioEntity> findByEmail(String email);
}
