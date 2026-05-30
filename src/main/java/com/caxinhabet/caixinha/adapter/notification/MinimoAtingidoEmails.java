package com.caxinhabet.caixinha.adapter.notification;

import com.caxinhabet.caixinha.domain.MinimoAtingidoEmail;
import com.caxinhabet.shared.notification.EmailTemplate;

/**
 * Conteúdo (texto puro + HTML) do e-mail "mínimo atingido" — compartilhado
 * pelos adapters {@code Resend} e {@code Smtp}. Texto = corpo de hoje; HTML =
 * mesmo conteúdo no chrome da marca. Dados de usuário passam por
 * {@link EmailTemplate#escape}.
 */
final class MinimoAtingidoEmails {

	private MinimoAtingidoEmails() {}

	static String assunto(MinimoAtingidoEmail e) {
		return "Mínimo atingido! Hora de pagar — " + e.tituloCaixinha();
	}

	static String texto(MinimoAtingidoEmail e) {
		return String.format(
				"✅ Mínimo atingido! A caixinha '%s' (%s) liberou o pagamento.\n\n"
						+ "- Valor do ingresso: %s\n\n"
						+ "Bora pagar? É PIX direto no app — em 1 minuto seu lugar está garantido:\n"
						+ "%s\n\n"
						+ "Lembrete: o dinheiro fica no provedor de pagamento (Asaas), não com a"
						+ " gente. Se a caixinha não der certo, o estorno é automático.\n\n"
						+ "Até já,\nCaixinha Bet",
				e.tituloCaixinha(), e.confronto(), e.valorIngressoFormatado(), e.linkCaixinha());
	}

	static String html(MinimoAtingidoEmail e) {
		String titulo = EmailTemplate.escape(e.tituloCaixinha());
		String confronto = EmailTemplate.escape(e.confronto());
		return EmailTemplate.pagina(
				"Mínimo atingido na caixinha " + titulo + " — hora de pagar",
				EmailTemplate.h1("✅ Mínimo atingido!")
						+ EmailTemplate.paragrafo("A caixinha <strong>" + titulo + "</strong> (" + confronto + ") liberou o pagamento. Bora garantir seu lugar?")
						+ EmailTemplate.infoLinha("Valor do ingresso", EmailTemplate.escape(e.valorIngressoFormatado()))
						+ EmailTemplate.botao("Pagar com PIX", e.linkCaixinha())
						+ EmailTemplate.linkFallback(e.linkCaixinha())
						+ EmailTemplate.callout(EmailTemplate.Tom.INFO,
								"O dinheiro fica no provedor de pagamento licenciado (Asaas), não com a gente. Se a caixinha não der certo, o estorno é automático."));
	}
}
