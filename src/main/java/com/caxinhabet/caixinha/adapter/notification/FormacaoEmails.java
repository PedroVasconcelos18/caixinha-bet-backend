package com.caxinhabet.caixinha.adapter.notification;

import com.caxinhabet.caixinha.domain.FormacaoEmail;
import com.caxinhabet.shared.notification.EmailTemplate;

/**
 * Conteúdo (texto puro + HTML) do e-mail de Formação — duas variações:
 * {@link FormacaoEmail.Tipo#FORMADA} (celebração) e
 * {@link FormacaoEmail.Tipo#REVERTIDA} (cuidado, não-acusatório). Compartilhado
 * pelos adapters {@code Resend} e {@code Smtp}. Texto = corpo de hoje; HTML =
 * mesmo conteúdo no chrome. Dados de usuário passam por
 * {@link EmailTemplate#escape}.
 */
final class FormacaoEmails {

	private FormacaoEmails() {}

	private static final String CORPO_FORMADA =
			"🏆 É oficial: a caixinha '%s' (%s) está FORMADA!\n\n"
					+ "Pagamentos suficientes confirmados — agora é esperar o jogo.\n"
					+ "Acompanhe tudo aqui:\n%s\n\n"
					+ "Boa sorte a todos!\nCaixinha Bet";

	private static final String CORPO_REVERTIDA =
			"Aviso sobre a caixinha '%s' (%s).\n\n"
					+ "Um pagamento foi estornado e a caixinha voltou a coletar"
					+ " pagamentos — ela ainda NÃO está formada. Aquele aviso de"
					+ " 'Caixinha Formada' que você recebeu antes fica retificado"
					+ " por este.\n\n"
					+ "Nada de errado da sua parte — é só o número de pagamentos"
					+ " confirmados que mudou. Acompanhe aqui:\n%s\n\n"
					+ "Caixinha Bet";

	static boolean formada(FormacaoEmail e) {
		return e.tipo() == FormacaoEmail.Tipo.FORMADA;
	}

	static String assunto(FormacaoEmail e) {
		return (formada(e) ? "🏆 Caixinha formada — " : "Atualização da caixinha — ")
				+ e.tituloCaixinha();
	}

	static String texto(FormacaoEmail e) {
		return String.format(
				formada(e) ? CORPO_FORMADA : CORPO_REVERTIDA,
				e.tituloCaixinha(), e.confronto(), e.linkCaixinha());
	}

	static String html(FormacaoEmail e) {
		String titulo = EmailTemplate.escape(e.tituloCaixinha());
		String confronto = EmailTemplate.escape(e.confronto());
		if (formada(e)) {
			return EmailTemplate.pagina(
					"Caixinha " + titulo + " formada — agora é esperar o jogo",
					EmailTemplate.h1("🏆 Caixinha formada!")
							+ EmailTemplate.paragrafo("É oficial: a caixinha <strong>" + titulo + "</strong> (" + confronto + ") está formada. Pagamentos suficientes confirmados — agora é esperar o jogo.")
							+ EmailTemplate.botao("Acompanhar a caixinha", e.linkCaixinha())
							+ EmailTemplate.linkFallback(e.linkCaixinha())
							+ EmailTemplate.paragrafoMuted("Boa sorte a todos!"));
		}
		return EmailTemplate.pagina(
				"Atualização da caixinha " + titulo,
				EmailTemplate.h1("Atualização da sua caixinha")
						+ EmailTemplate.paragrafo("Sobre a caixinha <strong>" + titulo + "</strong> (" + confronto + ").")
						+ EmailTemplate.callout(EmailTemplate.Tom.WARN,
								"Um pagamento foi estornado e a caixinha voltou a coletar pagamentos — ela ainda <strong>não</strong> está formada. Aquele aviso de 'Caixinha Formada' que você recebeu antes fica retificado por este.")
						+ EmailTemplate.paragrafoMuted("Nada de errado da sua parte — é só o número de pagamentos confirmados que mudou.")
						+ EmailTemplate.botao("Acompanhar a caixinha", e.linkCaixinha())
						+ EmailTemplate.linkFallback(e.linkCaixinha()));
	}
}
