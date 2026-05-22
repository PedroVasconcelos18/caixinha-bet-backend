package com.caxinhabet.auth.domain;

import java.util.Optional;

/**
 * Porta exposta pelo módulo {@code auth} para outros módulos acessarem
 * o perfil de pagamento de um Usuário (Story 3.2 v5).
 *
 * <p>O módulo {@code pagamento} precisa: (a) ler nome/CPF/chave PIX para
 * gerar a cobrança e (b) ler/gravar o {@code asaasCustomerId} (criado
 * lazy no 1º pagamento). Mas {@code pagamento} NÃO pode importar
 * {@code auth.adapter.persistence} (regra
 * {@code ArquiteturaTest.adapterDeAuthSoConsumidoPorAuth}). Esta
 * interface é o contrato: vive no {@code domain} do auth, o adapter de
 * {@code auth} implementa.
 *
 * <p>Diferente de {@code ConsultaCaixinha} (leitura pura), esta porta
 * tem uma operação de escrita ({@link #registrarAsaasCustomerId}) —
 * justificada: o id do customer é dado de pagamento, faz sentido o
 * módulo {@code pagamento} comandar a gravação, e o módulo {@code auth}
 * apenas persiste.
 */
public interface PerfilPagamentoGateway {

	/**
	 * Busca o perfil de pagamento de um Usuário.
	 *
	 * @param usuarioId id do Usuário.
	 * @return o perfil, ou {@link Optional#empty()} se o Usuário não existe.
	 */
	Optional<PerfilPagamento> buscar(long usuarioId);

	/**
	 * Resolve o id do Usuário a partir do e-mail (identidade do principal
	 * autenticado). Outros módulos recebem o e-mail do {@code Authentication}
	 * e precisam do id sem importar {@code auth.adapter}.
	 *
	 * @param email e-mail do Usuário autenticado.
	 * @return id do Usuário, ou {@link Optional#empty()} se não existe.
	 */
	Optional<Long> idPorEmail(String email);

	/**
	 * Registra o id do customer criado no PSP (Asaas) para o Usuário.
	 * Chamado uma única vez, no 1º pagamento — depois é reusado.
	 *
	 * @param usuarioId id do Usuário.
	 * @param asaasCustomerId id do customer no PSP.
	 */
	void registrarAsaasCustomerId(long usuarioId, String asaasCustomerId);

	/**
	 * Perfil de pagamento — subconjunto do Usuário exposto a outros
	 * módulos. Sem JPA, sem {@code UsuarioEntity}.
	 *
	 * @param nomeCompleto nome (vira {@code name} no PSP); pode ser {@code null}.
	 * @param cpf CPF só dígitos; pode ser {@code null}.
	 * @param chavePix chave PIX de recebimento; pode ser {@code null}.
	 * @param asaasCustomerId id do customer no PSP, ou {@code null} se ainda
	 *     não foi criado.
	 * @param completo {@code true} se nome+CPF+chave PIX estão todos presentes.
	 */
	record PerfilPagamento(
			String nomeCompleto,
			String cpf,
			String chavePix,
			String asaasCustomerId,
			boolean completo) {}
}
