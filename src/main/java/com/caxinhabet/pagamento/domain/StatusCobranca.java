package com.caxinhabet.pagamento.domain;

/**
 * Estado consolidado de uma cobrança junto ao PSP, mapeado para vocabulário
 * de domínio (não usa os nomes do Asaas).
 *
 * <p>O mapeamento Asaas → este enum vive em {@code pagamento.adapter.asaas}.
 * Outros PSPs (futuros) farão o próprio mapeamento, sem mexer aqui.
 */
public enum StatusCobranca {
	/** Cobrança criada, pagador ainda não pagou. */
	PENDENTE,
	/** Cobrança confirmada como paga pelo PSP. */
	CONFIRMADA,
	/** Cobrança expirou sem pagamento. */
	EXPIRADA,
	/** Cobrança estornada (depois de paga). */
	ESTORNADA
}
