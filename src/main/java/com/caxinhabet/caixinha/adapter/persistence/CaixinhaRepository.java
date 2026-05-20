package com.caxinhabet.caixinha.adapter.persistence;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CaixinhaRepository extends JpaRepository<CaixinhaEntity, Long> {

	Optional<CaixinhaEntity> findById(Long id);

	/** Preparação para FR-17 (dashboard das Caixinhas do usuário). */
	List<CaixinhaEntity> findByOrganizadorUsuarioId(Long organizadorUsuarioId);
}
