package com.caxinhabet.auth.adapter.persistence;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VerificacaoEmailRepository
        extends JpaRepository<VerificacaoEmailEntity, Long> {

    Optional<VerificacaoEmailEntity> findByTokenHash(String tokenHash);
}
