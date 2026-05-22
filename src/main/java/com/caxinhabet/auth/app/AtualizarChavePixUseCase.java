package com.caxinhabet.auth.app;

import com.caxinhabet.auth.adapter.persistence.UsuarioEntity;
import com.caxinhabet.auth.adapter.persistence.UsuarioRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Atualiza a chave PIX de recebimento no perfil do Usuário (Story 2.5 v5).
 *
 * <p>Idempotente: chamar 2x com o mesmo valor é OK. A chave é editável a
 * qualquer momento (não congela com o prazo de entrada — diferente do
 * Palpite). Sem validação de formato aqui: o PSP rejeita no payout
 * (FR-13 v5) e o domínio reage abrindo o ganhador para correção. Quanto
 * mais tarde validamos formato, menos risco de divergir do que o Asaas
 * realmente aceita (regras de chave PIX são do BCB e mudam).
 */
@Service
public class AtualizarChavePixUseCase {

	private final UsuarioRepository usuarios;

	public AtualizarChavePixUseCase(UsuarioRepository usuarios) {
		this.usuarios = usuarios;
	}

	@Transactional
	public UsuarioEntity executar(long usuarioId, String chavePix) {
		UsuarioEntity usuario =
				usuarios
						.findById(usuarioId)
						.orElseThrow(
								() ->
										new ResponseStatusException(
												HttpStatus.NOT_FOUND, "Usuário não encontrado."));
		// Normalização (trim + null-se-blank) vive em UsuarioEntity.definirChavePix
		// — invariante do agregado, não do use case (review v5, 2026-05-21).
		usuario.definirChavePix(chavePix);
		return usuario;
	}
}
