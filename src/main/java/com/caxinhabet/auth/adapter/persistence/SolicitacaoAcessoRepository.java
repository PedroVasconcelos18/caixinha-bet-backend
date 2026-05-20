package com.caxinhabet.auth.adapter.persistence;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repositório Spring Data para {@link SolicitacaoAcessoEntity} (Story 2.1).
 *
 * <p>A busca é sempre por {@code tokenHash} — o cliente envia o token cru
 * no query param, o use case hasheia, e pergunta aqui. O índice UNIQUE
 * garante O(log n) em qualquer volume.
 */
public interface SolicitacaoAcessoRepository
		extends JpaRepository<SolicitacaoAcessoEntity, Long> {

	Optional<SolicitacaoAcessoEntity> findByTokenHash(String tokenHash);
}
