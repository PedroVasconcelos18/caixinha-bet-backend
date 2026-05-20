package com.caxinhabet.pagamento.domain;

import com.caxinhabet.shared.money.Money;
import java.util.List;

/**
 * Porta hexagonal do módulo {@code pagamento} (AR-3, Story 1.4).
 *
 * <p>Resposta arquitetural a <b>R-7/C-3</b> (PRD §11): o Asaas é ponto único
 * de falha sem plano B contratual; arquitetura mitiga tecnicamente fazendo
 * o PSP <b>trocável</b>. Troca de PSP = novo adapter, núcleo intocado.
 *
 * <p>Toda a API desta porta usa <b>tipos do domínio</b> ({@link Money},
 * {@link SolicitacaoCobranca}, {@link CobrancaCriada}, {@link StatusCobranca},
 * {@link Vencedor}) — NUNCA tipos do PSP (Asaas, etc.). O adapter traduz na
 * borda. O {@link com.caxinhabet.arquitetura.ArquiteturaTest} faz cumprir
 * por bytecode.
 *
 * <p>Story 1.4 instala a interface; implementação completa (split real,
 * estorno, polling) é Épico 3/4. O {@code AsaasProvedorPagamentoAdapter}
 * desta story implementa {@link #criarCobranca} funcional (para o gate da
 * Story 1.5); as demais lançam {@code UnsupportedOperationException} até
 * serem exercitadas pelos épicos seguintes.
 */
public interface ProvedorPagamento {

	/**
	 * Cria uma cobrança PIX no PSP e devolve o QR code + copia-e-cola.
	 * Não muta estado de domínio — quem grava o {@code cobrancaId} é o caller.
	 */
	CobrancaCriada criarCobranca(SolicitacaoCobranca solicitacao);

	/** Consulta o status atual de uma cobrança no PSP (verdade do dinheiro). */
	StatusCobranca consultar(String cobrancaId);

	/**
	 * Estorna integralmente uma cobrança paga (FR-11). O PSP devolve o valor
	 * ao pagador original; o domínio reage ao webhook do evento de estorno.
	 */
	void estornar(String cobrancaId);

	/**
	 * Executa o split do prêmio entre vencedores + retenção da taxa
	 * (FR-13). A plataforma instrui o PSP — <b>nunca</b> tem o pote
	 * consolidado em conta própria (Res. BCB 16/2025).
	 *
	 * @param vencedores lista 1..N de vencedores com chave PIX e valor.
	 * @param taxaPlataforma quanto a plataforma retém (R$ 10,00 fixo — AR-7).
	 */
	void split(List<Vencedor> vencedores, Money taxaPlataforma);
}
