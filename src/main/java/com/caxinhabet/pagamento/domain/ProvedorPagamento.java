package com.caxinhabet.pagamento.domain;

import com.caxinhabet.shared.money.Money;

/**
 * Porta hexagonal do módulo {@code pagamento} (AR-3, Story 1.4 / refatorada v5 — 2026-05-21).
 *
 * <p>Resposta arquitetural a <b>R-7/C-3</b> (PRD §11): o Asaas é ponto único
 * de falha sem plano B contratual; arquitetura mitiga tecnicamente fazendo
 * o PSP <b>trocável</b>. Troca de PSP = novo adapter, núcleo intocado.
 *
 * <p>Toda a API desta porta usa <b>tipos do domínio</b> ({@link Money},
 * {@link SolicitacaoCobranca}, {@link CobrancaCriada}, {@link StatusCobranca},
 * {@link Ganhador}, {@link ResultadoTransferencia}) — NUNCA tipos do PSP
 * (Asaas, etc.). O adapter traduz na borda.
 * O {@code com.caxinhabet.arquitetura.ArquiteturaTest} faz cumprir por bytecode.
 *
 * <p><b>Mudança v5 (2026-05-21):</b> Pedro decidiu trocar o modelo de Repasse
 * de <i>split-na-liquidação</i> (v4 — Asaas debitava direto na liquidação,
 * pote nunca consolidava em conta da plataforma) por <i>custódia + payout
 * programático após aceite explícito do Ganhador</i>. Em consequência, esta
 * porta perdeu a operação {@code split(...)} e ganhou {@code transferir(...)}
 * + {@code consultarTransferencia(...)}. Trade-off aceito conscientemente:
 * simplicidade de homologação prevalece sobre defesa arquitetural estrutural
 * (PRD §11 v5 — C-1 agravado, C-4 novo).
 *
 * <p>Story 1.4 (v4 e v5) instala a interface; implementação completa de cada
 * método é dos Épicos 3/4 — o adapter Asaas desta story implementa
 * {@link #criarCobranca} funcional (para o gate da Story 1.5); as demais
 * lançam {@code UnsupportedOperationException} até serem exercitadas.
 */
public interface ProvedorPagamento {

	/**
	 * Registra um "cliente" (quem paga) no PSP e devolve o id opaco do
	 * customer (Story 3.2 v5, FR-7).
	 *
	 * <p>Necessário porque o PSP exige o pagador cadastrado antes de
	 * cobrar (Asaas: {@code POST /v3/customers} com {@code name}+{@code cpfCnpj}).
	 * O caller deve chamar uma única vez por Usuário (lazy, no 1º pagamento)
	 * e persistir o id retornado — o PSP permite duplicatas, o controle de
	 * "criar só uma vez" é do domínio.
	 *
	 * @param dados nome + CPF do pagador.
	 * @return id opaco do customer no PSP (no Asaas: {@code cus_<hash>}).
	 */
	String criarCliente(DadosCliente dados);

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
	 * Dispara uma transferência PIX programática da conta da plataforma no
	 * PSP para a chave PIX do Ganhador (FR-13 v5). Chamada por
	 * {@code DispararPayoutService} após o aceite explícito do Ganhador no
	 * app (Story 4.6).
	 *
	 * <p><b>Idempotência por {@code payoutId}:</b> o domínio gera um
	 * {@code payoutId} único por Ganhador (1 payout = 1 Ganhador de 1
	 * Caixinha apurada). Múltiplas chamadas com o mesmo {@code payoutId}
	 * NÃO duplicam o PIX (FR-13 v5 / NFR-1 corretude monetária).
	 *
	 * <p>Falha do PIX (chave inválida, recusa do PSP) é sinalizada via
	 * {@link ResultadoTransferencia} com status {@code FALHA}; o domínio
	 * reabre o Ganhador para correção da chave PIX. <b>Nenhum prazo
	 * automático de destinação</b> — C-4 §11 PRD: valor retido
	 * indefinidamente até o Ganhador aceitar e cadastrar/corrigir.
	 *
	 * @param ganhador domínio com {@code payoutId} (idempotência) + chave
	 *     PIX cadastrada pelo Participante (FR-5 v5) + valor calculado.
	 * @return estado da transferência aceita pelo PSP; pode ser
	 *     {@code PENDENTE} (aguardar webhook ou polling) ou {@code FALHA}
	 *     (chave inválida etc.).
	 */
	ResultadoTransferencia transferir(Ganhador ganhador);

	/**
	 * Consulta o status atual de uma transferência no PSP — usado pelo job
	 * de reconciliação (NFR-2) e pelo polling de confirmação (FR-13 v5).
	 *
	 * @param transferenciaId id devolvido em {@link #transferir}.
	 * @return estado projetado do PSP no momento da consulta. Não muta
	 *     estado de domínio — quem reage à projeção é o caller.
	 */
	ResultadoTransferencia consultarTransferencia(String transferenciaId);
}
