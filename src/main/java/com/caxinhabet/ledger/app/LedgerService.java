package com.caxinhabet.ledger.app;

import com.caxinhabet.ledger.adapter.persistence.LedgerLancamentoEntity;
import com.caxinhabet.ledger.adapter.persistence.LedgerLancamentoRepository;
import com.caxinhabet.shared.money.Money;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Registra movimentações no ledger de dupla entrada (Story 3.3 v5,
 * FR-8 / AR-6).
 *
 * <p>Toda movimentação grava um PAR de lançamentos (débito + crédito)
 * com a mesma {@code transacaoId} — a invariante de dupla entrada
 * (Σ débitos == Σ créditos) é garantida por construção: este serviço é
 * o único caminho de escrita no ledger e sempre grava os dois lados.
 *
 * <p>Exposto a outros módulos via {@code ledger.app} — o módulo
 * {@code pagamento} chama este serviço ao processar webhooks. As contas
 * são lógicas (categorias contábeis), não bancárias — o ledger é a
 * trilha interna reconciliável contra o extrato do Asaas (Story 3.6).
 */
@Service
public class LedgerService {

	/** Contas lógicas do ledger (espelham o CHECK da migration V10). */
	private static final String CONTA_EXPECTATIVA = "expectativa";
	private static final String CONTA_A_RECEBER = "a_receber";
	private static final String CONTA_CUSTODIA = "custodia";
	private static final String CONTA_ESTORNO = "estorno";

	private static final String DEBITO = "debito";
	private static final String CREDITO = "credito";

	private final LedgerLancamentoRepository lancamentos;

	public LedgerService(LedgerLancamentoRepository lancamentos) {
		this.lancamentos = lancamentos;
	}

	/**
	 * Registra a GERAÇÃO de uma cobrança PIX (lançamento acrescentado no
	 * fix do code review do Épico 3, 2026-05-21).
	 *
	 * <p>Par de lançamentos: débito em {@code expectativa}, crédito em
	 * {@code a_receber} — cria o saldo "o Participante deve este ingresso".
	 * Sem este lançamento, {@link #registrarCustodia} debitaria
	 * {@code a_receber} sem nunca tê-la creditado (saldo estruturalmente
	 * negativo — AC-7 da Story 3.3 não fecharia).
	 *
	 * @param eventoRef referência de auditoria — aqui o {@code cobranca_id}
	 *     (a geração não vem de webhook, não há event_id).
	 */
	public void registrarCobrancaGerada(
			long caixinhaId,
			long participanteId,
			String cobrancaId,
			Money valor,
			String eventoRef) {
		gravarPar(
				UUID.randomUUID(),
				CONTA_EXPECTATIVA,
				CONTA_A_RECEBER,
				valor,
				caixinhaId,
				participanteId,
				cobrancaId,
				eventoRef);
	}

	/**
	 * Registra a entrada de um ingresso confirmado em custódia
	 * (pagamento confirmado pelo Provedor — {@code PAYMENT_CONFIRMED}).
	 *
	 * <p>Par de lançamentos: débito em {@code a_receber} (o Participante
	 * deixou de "dever" — o crédito de {@code a_receber} veio da geração,
	 * {@link #registrarCobrancaGerada}), crédito em {@code custodia} (o
	 * valor está custodiado no Asaas).
	 *
	 * @param caixinhaId Caixinha do pagamento.
	 * @param participanteId Participante que pagou.
	 * @param cobrancaId id da cobrança no PSP.
	 * @param valor valor do ingresso.
	 * @param eventoRef event_id do webhook que originou (auditoria).
	 */
	public void registrarCustodia(
			long caixinhaId,
			long participanteId,
			String cobrancaId,
			Money valor,
			String eventoRef) {
		UUID transacao = UUID.randomUUID();
		gravarPar(
				transacao,
				CONTA_A_RECEBER,
				CONTA_CUSTODIA,
				valor,
				caixinhaId,
				participanteId,
				cobrancaId,
				eventoRef);
	}

	/**
	 * Registra o estorno de um pagamento já custodiado
	 * (evento {@code PAYMENT_REFUNDED}).
	 *
	 * <p>Par de lançamentos: débito em {@code custodia} (o valor saiu da
	 * custódia), crédito em {@code estorno} (devolvido ao pagador).
	 */
	public void registrarEstorno(
			long caixinhaId,
			long participanteId,
			String cobrancaId,
			Money valor,
			String eventoRef) {
		UUID transacao = UUID.randomUUID();
		gravarPar(
				transacao,
				CONTA_CUSTODIA,
				CONTA_ESTORNO,
				valor,
				caixinhaId,
				participanteId,
				cobrancaId,
				eventoRef);
	}

	/**
	 * Verifica que uma transação está balanceada (Σ débitos == Σ créditos).
	 * Usado por testes e pela reconciliação (Story 3.6).
	 */
	public boolean transacaoBalanceada(UUID transacaoId) {
		List<LedgerLancamentoEntity> ls = lancamentos.findByTransacaoId(transacaoId);
		long debitos =
				ls.stream()
						.filter(l -> DEBITO.equals(l.getTipo()))
						.mapToLong(LedgerLancamentoEntity::getValorCentavos)
						.sum();
		long creditos =
				ls.stream()
						.filter(l -> CREDITO.equals(l.getTipo()))
						.mapToLong(LedgerLancamentoEntity::getValorCentavos)
						.sum();
		return debitos == creditos && debitos > 0;
	}

	private void gravarPar(
			UUID transacao,
			String contaDebito,
			String contaCredito,
			Money valor,
			long caixinhaId,
			long participanteId,
			String cobrancaId,
			String eventoRef) {
		long centavos = valor.centavos();
		lancamentos.save(
				new LedgerLancamentoEntity(
						transacao,
						contaDebito,
						DEBITO,
						centavos,
						caixinhaId,
						participanteId,
						cobrancaId,
						eventoRef));
		lancamentos.save(
				new LedgerLancamentoEntity(
						transacao,
						contaCredito,
						CREDITO,
						centavos,
						caixinhaId,
						participanteId,
						cobrancaId,
						eventoRef));
	}
}
