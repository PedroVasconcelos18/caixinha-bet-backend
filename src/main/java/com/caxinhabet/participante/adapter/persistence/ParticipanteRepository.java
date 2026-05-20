package com.caxinhabet.participante.adapter.persistence;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ParticipanteRepository extends JpaRepository<ParticipanteEntity, Long> {

	List<ParticipanteEntity> findByCaixinhaIdOrderByCriadoEmAsc(long caixinhaId);

	Optional<ParticipanteEntity> findByCaixinhaIdAndEmail(long caixinhaId, String email);

	List<ParticipanteEntity> findByUsuarioId(Long usuarioId);
}
