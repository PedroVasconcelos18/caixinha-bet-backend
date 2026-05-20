package com.caxinhabet.auth.adapter.session;

import com.caxinhabet.auth.domain.SessaoUsuario;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Store in-memory de sessões de usuário (Story 2.1).
 *
 * <p>API mínima: {@link #criar(SessaoUsuario)}, {@link #buscar(String)}
 * (descartando expiradas), {@link #invalidar(String)}. Thread-safe via
 * {@link ConcurrentHashMap}.
 *
 * <p>Sessões expiradas são removidas <i>lazily</i> em {@link #buscar} —
 * sem job de limpeza. Para volume MVP é suficiente; em escala maior,
 * substituir por adapter Redis/DB.
 */
@Component
public class SessaoStore {

	private final ConcurrentHashMap<String, SessaoUsuario> sessoes = new ConcurrentHashMap<>();

	public void criar(SessaoUsuario sessao) {
		sessoes.put(sessao.idSessao(), sessao);
	}

	public Optional<SessaoUsuario> buscar(String idSessao) {
		if (idSessao == null) {
			return Optional.empty();
		}
		SessaoUsuario s = sessoes.get(idSessao);
		if (s == null) {
			return Optional.empty();
		}
		if (s.estaExpirada(Instant.now())) {
			sessoes.remove(idSessao, s);
			return Optional.empty();
		}
		return Optional.of(s);
	}

	public void invalidar(String idSessao) {
		if (idSessao != null) {
			sessoes.remove(idSessao);
		}
	}

	/** Tamanho atual — usado por testes. Não usar em código de produção. */
	public int tamanho() {
		return sessoes.size();
	}

	/** Limpa tudo — usado por testes. */
	public void limpar() {
		sessoes.clear();
	}
}
