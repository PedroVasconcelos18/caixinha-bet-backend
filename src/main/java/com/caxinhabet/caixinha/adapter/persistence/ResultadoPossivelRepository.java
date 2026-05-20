package com.caxinhabet.caixinha.adapter.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ResultadoPossivelRepository
		extends JpaRepository<ResultadoPossivelEntity, Long> {

	List<ResultadoPossivelEntity> findByCaixinhaIdOrderByOrdemAsc(long caixinhaId);
}
