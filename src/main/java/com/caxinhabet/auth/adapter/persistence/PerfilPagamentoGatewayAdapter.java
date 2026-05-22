package com.caxinhabet.auth.adapter.persistence;

import com.caxinhabet.auth.domain.PerfilPagamentoGateway;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementação da porta {@link PerfilPagamentoGateway} (Story 3.2 v5).
 *
 * <p>Traduz a {@code UsuarioEntity} (JPA, interna ao módulo {@code auth})
 * para o record {@code PerfilPagamento} (público). Outros módulos —
 * em especial {@code pagamento} — consomem a porta, nunca este adapter
 * nem o {@code UsuarioRepository} direto.
 */
@Component
class PerfilPagamentoGatewayAdapter implements PerfilPagamentoGateway {

	private final UsuarioRepository usuarios;

	PerfilPagamentoGatewayAdapter(UsuarioRepository usuarios) {
		this.usuarios = usuarios;
	}

	@Override
	public Optional<PerfilPagamento> buscar(long usuarioId) {
		return usuarios
				.findById(usuarioId)
				.map(
						u ->
								new PerfilPagamento(
										u.getNomeCompleto(),
										u.getCpf(),
										u.getChavePix(),
										u.getAsaasCustomerId(),
										u.perfilPagamentoCompleto()));
	}

	@Override
	public Optional<Long> idPorEmail(String email) {
		return usuarios.findByEmail(email).map(UsuarioEntity::getId);
	}

	@Override
	@Transactional
	public void registrarAsaasCustomerId(long usuarioId, String asaasCustomerId) {
		UsuarioEntity usuario =
				usuarios
						.findById(usuarioId)
						.orElseThrow(
								() ->
										new IllegalStateException(
												"Usuário não encontrado: id=" + usuarioId));
		usuario.registrarAsaasCustomerId(asaasCustomerId);
		// dirty checking dentro da @Transactional persiste.
	}
}
