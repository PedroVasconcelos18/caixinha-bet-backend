package com.caxinhabet.pagamento.adapter.notification;

import com.caxinhabet.pagamento.domain.CancelamentoEmail;
import com.caxinhabet.shared.notification.EmailTemplate;

/**
 * Conteúdo (texto puro + HTML) do e-mail de cancelamento — duas variações
 * conforme {@link CancelamentoEmail#houvePagamento()}: com reembolso
 * automático ou só aviso. Compartilhado pelos adapters {@code Resend} e
 * {@code Smtp}. Tom de cuidado, <b>não-punitivo</b> (NFR-6) — nunca culpa
 * ninguém. Dados de usuário passam por {@link EmailTemplate#escape}.
 */
final class CancelamentoEmails {

	private CancelamentoEmails() {}

	private static final String CORPO_COM_PAGAMENTO =
			"A caixinha '%s' não fechou — não atingiu o número mínimo de"
					+ " participantes no prazo.\n\n"
					+ "Você pagou o ingresso, então já estamos devolvendo seu dinheiro"
					+ " automaticamente, valor cheio, direto na origem do pagamento."
					+ " Você não precisa fazer nada.\n\n"
					+ "Acontece — quem sabe na próxima!\nCaixinha Bet";

	private static final String CORPO_SEM_PAGAMENTO =
			"A caixinha '%s' não fechou — não atingiu o número mínimo de"
					+ " participantes no prazo.\n\n"
					+ "Como você ainda não tinha pago o ingresso, não há nada a"
					+ " devolver. Fica para a próxima!\n\nCaixinha Bet";

	static String assunto(CancelamentoEmail e) {
		return "A caixinha '" + e.tituloCaixinha() + "' não fechou";
	}

	static String texto(CancelamentoEmail e) {
		return String.format(
				e.houvePagamento() ? CORPO_COM_PAGAMENTO : CORPO_SEM_PAGAMENTO,
				e.tituloCaixinha());
	}

	static String html(CancelamentoEmail e) {
		String titulo = EmailTemplate.escape(e.tituloCaixinha());
		String corpo = EmailTemplate.h1("A caixinha não fechou")
				+ EmailTemplate.paragrafo("A caixinha <strong>" + titulo + "</strong> não atingiu o número mínimo de participantes no prazo.");
		if (e.houvePagamento()) {
			corpo += EmailTemplate.callout(EmailTemplate.Tom.INFO,
					"Você pagou o ingresso, então já estamos devolvendo seu dinheiro automaticamente, valor cheio, direto na origem do pagamento. <strong>Você não precisa fazer nada.</strong>")
					+ EmailTemplate.paragrafoMuted("Acontece — quem sabe na próxima!");
		} else {
			corpo += EmailTemplate.paragrafoMuted("Como você ainda não tinha pago o ingresso, não há nada a devolver. Fica para a próxima!");
		}
		return EmailTemplate.pagina("A caixinha " + titulo + " não fechou", corpo);
	}
}
