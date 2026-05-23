package com.caxinhabet.auth.adapter.persistence;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

	/**
	 * Variante de {@link #findByEmail(String)} que ignora usuários
	 * soft-deleted ({@code deletado_em IS NOT NULL}). Usada pelo login para
	 * recusar contas excluídas com o mesmo 401 genérico de credenciais
	 * inválidas (anti-enumeração).
	 */
	@Query("""
			SELECT u FROM UsuarioEntity u
			WHERE u.email = :email AND u.deletadoEm IS NULL
			""")
	Optional<UsuarioEntity> findByEmailEAtivo(@Param("email") String email);
}
