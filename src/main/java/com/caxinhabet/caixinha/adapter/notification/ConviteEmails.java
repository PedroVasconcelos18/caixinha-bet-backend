package com.caxinhabet.caixinha.adapter.notification;

import com.caxinhabet.caixinha.domain.ConviteEmail;
import com.caxinhabet.shared.notification.EmailTemplate;

/**
 * Conteúdo (texto puro + HTML) do e-mail de convite — compartilhado pelos
 * adapters {@code Resend} e {@code Smtp} para mantê-los idênticos.
 *
 * <p>O texto puro é o corpo de hoje (fallback NFR-6 verificável); o HTML é o
 * mesmo conteúdo no chrome da marca. Dados de usuário ({@code organizadorNome},
 * {@code confronto}, {@code tituloCaixinha}, {@code valorIngressoFormatado})
 * passam por {@link EmailTemplate#escape} antes de ir ao HTML.
 */
final class ConviteEmails {

	private ConviteEmails() {}

	static String assunto(ConviteEmail c) {
		return c.organizadorNome() + " te chamou para uma caixinha!";
	}

	static String texto(ConviteEmail c) {
		return String.format(
				"Oi! O %s montou uma caixinha do jogo %s e te convidou para participar.\n\n"
						+ "- Valor do ingresso: %s\n- Caixinha: %s\n\n"
						+ "Aceita o convite e escolhe seu palpite:\n%s\n\n"
						+ "Ah, importante: este é um bolão entre amigos. O dinheiro fica num"
						+ " provedor de pagamento licenciado (Asaas), nunca com a gente. Se"
						+ " a caixinha não der certo, o estorno é automático.\n\n"
						+ "Até já,\nCaixinha Bet",
				c.organizadorNome(), c.confronto(), c.valorIngressoFormatado(),
				c.tituloCaixinha(), c.linkConvite());
	}

	static String html(ConviteEmail c) {
		String org = EmailTemplate.escape(c.organizadorNome());
		String confronto = EmailTemplate.escape(c.confronto());
		String titulo = EmailTemplate.escape(c.tituloCaixinha());
		return EmailTemplate.pagina(
				org + " te convidou para uma caixinha do " + confronto,
				EmailTemplate.h1(org + " te chamou para uma caixinha!")
						+ EmailTemplate.paragrafo("Montaram um bolão do <strong>" + confronto + "</strong> e te convidaram pra entrar.")
						+ EmailTemplate.infoLinha("Caixinha", titulo)
						+ EmailTemplate.infoLinha("Valor do ingresso", EmailTemplate.escape(c.valorIngressoFormatado()))
						+ EmailTemplate.botao("Aceitar e palpitar", c.linkConvite())
						+ EmailTemplate.linkFallback(c.linkConvite())
						+ EmailTemplate.callout(EmailTemplate.Tom.INFO,
								"É um bolão entre amigos. O dinheiro fica num provedor de pagamento licenciado (Asaas), nunca com a gente. Se a caixinha não der certo, o estorno é automático."));
	}
}
