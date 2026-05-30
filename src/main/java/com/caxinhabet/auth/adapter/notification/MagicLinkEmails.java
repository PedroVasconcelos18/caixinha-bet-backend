package com.caxinhabet.auth.adapter.notification;

import com.caxinhabet.shared.notification.EmailTemplate;

/**
 * Conteúdo (texto puro + HTML) dos dois e-mails do magic link — acesso e
 * verificação de e-mail. Compartilhado pelos adapters {@code Resend*} e
 * {@code Smtp*} para que ambos os caminhos de envio fiquem idênticos sem
 * duplicar o corpo.
 *
 * <p>O texto puro é exatamente o corpo de hoje (fallback verificável); o HTML
 * é esse mesmo conteúdo dentro do chrome da marca ({@link EmailTemplate}). O
 * {@code link} é URL nossa (montada de {@code app.public-base-url} + token) —
 * não precisa de {@code escape}.
 */
final class MagicLinkEmails {

	private MagicLinkEmails() {}

	static String textoAcesso(String link) {
		return "Oi!\n\nClica no link abaixo para entrar no Caixinha Bet. Ele vale por"
				+ " 15 minutos e só pode ser usado uma vez:\n\n" + link
				+ "\n\nSe não foi você que pediu, é só ignorar este e-mail — sem"
				+ " ações na sua conta.\n\nAté já,\nCaixinha Bet";
	}

	static String htmlAcesso(String link) {
		return EmailTemplate.pagina(
				"Seu link de acesso — vale por 15 minutos",
				EmailTemplate.h1("Entre no Caixinha Bet")
						+ EmailTemplate.paragrafo("Clica no botão abaixo para entrar. O link vale por <strong>15 minutos</strong> e só pode ser usado uma vez.")
						+ EmailTemplate.botao("Entrar agora", link)
						+ EmailTemplate.linkFallback(link)
						+ EmailTemplate.paragrafoMuted("Se não foi você que pediu, é só ignorar este e-mail — sem ações na sua conta."));
	}

	static String textoVerificacao(String link) {
		return "Oi!\n\nPara concluir seu cadastro no Caixinha Bet, confirme seu"
				+ " e-mail clicando no link abaixo. Ele vale por 24 horas e só"
				+ " pode ser usado uma vez:\n\n" + link
				+ "\n\nSe não foi você que pediu, é só ignorar este e-mail — sem"
				+ " ações na sua conta.\n\nAté já,\nCaixinha Bet";
	}

	static String htmlVerificacao(String link) {
		return EmailTemplate.pagina(
				"Confirme seu e-mail no Caixinha Bet",
				EmailTemplate.h1("Confirme seu e-mail")
						+ EmailTemplate.paragrafo("Para concluir seu cadastro, confirme seu e-mail clicando no botão abaixo. O link vale por <strong>24 horas</strong> e só pode ser usado uma vez.")
						+ EmailTemplate.botao("Confirmar e-mail", link)
						+ EmailTemplate.linkFallback(link)
						+ EmailTemplate.paragrafoMuted("Se não foi você que pediu, é só ignorar este e-mail — sem ações na sua conta."));
	}
}
