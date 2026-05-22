package com.caxinhabet.auth.domain;

/**
 * Lançada quando uma operação exige chave PIX cadastrada no perfil do
 * Usuário e ela ainda não foi cadastrada (FR-5 v5, Story 2.5 v5).
 *
 * <p>Mapeada para HTTP 422 pelo {@code GlobalExceptionHandler} com type
 * próprio ({@code /problems/chave-pix-obrigatoria}) — assim o front sabe
 * que precisa mostrar o form de cadastro da chave PIX, sem precisar
 * inspecionar texto da mensagem.
 *
 * <p>Cenários de uso: tentar definir Palpite (FR-5 v5) sem chave
 * cadastrada. Aceitar convite NÃO dispara — o aceite é UX pré-requisito
 * (decisão de produto 2026-05-21).
 */
public class ChavePixObrigatoriaException extends RuntimeException {

	public ChavePixObrigatoriaException(String mensagem) {
		super(mensagem);
	}
}
