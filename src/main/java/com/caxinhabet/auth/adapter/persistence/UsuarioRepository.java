package com.caxinhabet.auth.adapter.persistence;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repositório Spring Data para {@link UsuarioEntity} (Story 2.1; auth por
 * senha acrescentou {@code findByCpf}).
 *
 * <p>{@code findByEmail} é case-insensitive automaticamente porque a coluna
 * {@code email} é {@code CITEXT} no Postgres. Defesa em profundidade: o
 * use case ainda normaliza o input para lowercase antes de chamar este
 * método.
 *
 * <p>{@code findByCpf} é usado pelo cadastro para barrar CPF duplicado
 * antes do insert (defesa em profundidade sobre o índice único parcial
 * {@code uq_usuario_cpf}). O CPF chega já normalizado (11 dígitos).
 */
public interface UsuarioRepository extends JpaRepository<UsuarioEntity, Long> {

	Optional<UsuarioEntity> findByEmail(String email);

	Optional<UsuarioEntity> findByCpf(String cpf);
}
