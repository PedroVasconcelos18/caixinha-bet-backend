package com.caxinhabet.auth.app;

import com.caxinhabet.auth.adapter.persistence.UsuarioEntity;
import com.caxinhabet.auth.adapter.persistence.UsuarioRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Atualiza o perfil de pagamento do Usuário — nome completo + CPF
 * (Story 3.2 v5, FR-16 v5).
 *
 * <p>Exigido pelo Asaas para criar o "customer" (quem paga). Cadastro
 * postergado ao 1º pagamento. A chave PIX é atualizada por outro use
 * case ({@code AtualizarChavePixUseCase}) — campos distintos do mesmo
 * "perfil de pagamento", endpoints separados.
 *
 * <p>Validação de formato do CPF (11 dígitos) acontece aqui, com
 * verificação simples de dígitos verificadores. Normalização (só
 * dígitos) é responsabilidade de {@code UsuarioEntity.definirPerfilPagamento}.
 */
@Service
public class AtualizarPerfilPagamentoUseCase {

	private final UsuarioRepository usuarios;

	public AtualizarPerfilPagamentoUseCase(UsuarioRepository usuarios) {
		this.usuarios = usuarios;
	}

	@Transactional
	public UsuarioEntity executar(long usuarioId, String nomeCompleto, String cpf) {
		UsuarioEntity usuario =
				usuarios
						.findById(usuarioId)
						.orElseThrow(
								() ->
										new ResponseStatusException(
												HttpStatus.NOT_FOUND, "Usuário não encontrado."));

		// Validação de nome no use case (fix code review Épico 3, 2026-05-21):
		// o @NotBlank do AtualizarPerfilPagamentoRequest só protege a borda
		// HTTP. Este use case é chamável por outros caminhos — a invariante
		// "nome não-vazio" (exigida pelo Asaas para criar o customer) vive
		// aqui, não só no controller.
		if (nomeCompleto == null || nomeCompleto.isBlank()) {
			throw new ResponseStatusException(
					HttpStatus.UNPROCESSABLE_ENTITY, "Nome completo é obrigatório.");
		}

		String cpfDigitos = cpf == null ? "" : cpf.replaceAll("\\D", "");
		if (!cpfValido(cpfDigitos)) {
			throw new ResponseStatusException(
					HttpStatus.UNPROCESSABLE_ENTITY, "CPF inválido.");
		}
		usuario.definirPerfilPagamento(nomeCompleto, cpfDigitos);
		return usuario;
	}

	/**
	 * Validação de CPF: 11 dígitos + dígitos verificadores corretos.
	 * Rejeita também os "CPFs de placa" (todos os dígitos iguais), que
	 * passam o algoritmo mas não são válidos.
	 */
	static boolean cpfValido(String cpf) {
		if (cpf == null || cpf.length() != 11 || !cpf.chars().allMatch(Character::isDigit)) {
			return false;
		}
		if (cpf.chars().distinct().count() == 1) {
			return false; // 00000000000, 11111111111, etc.
		}
		int dv1 = calcularDigito(cpf, 9);
		int dv2 = calcularDigito(cpf, 10);
		return dv1 == (cpf.charAt(9) - '0') && dv2 == (cpf.charAt(10) - '0');
	}

	private static int calcularDigito(String cpf, int ate) {
		int soma = 0;
		int peso = ate + 1;
		for (int i = 0; i < ate; i++) {
			soma += (cpf.charAt(i) - '0') * peso--;
		}
		int resto = soma % 11;
		return resto < 2 ? 0 : 11 - resto;
	}
}
