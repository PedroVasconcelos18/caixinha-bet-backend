package com.caxinhabet.pagamento.adapter.notification;

import com.caxinhabet.pagamento.domain.PremioGanhoEmail;
import com.caxinhabet.shared.notification.EmailTemplate;

/**
 * Conteúdo (texto puro + HTML) do e-mail "você ganhou" — compartilhado pelos
 * adapters {@code Resend} e {@code Smtp}. Texto = corpo de hoje; HTML = mesmo
 * conteúdo no chrome, com o prêmio em destaque dourado. Dados de usuário
 * passam por {@link EmailTemplate#escape}.
 */
final class PremioGanhoEmails {

	private PremioGanhoEmails() {}

	static String assunto(PremioGanhoEmail e) {
		return "🎉 Você ganhou — " + e.tituloCaixinha();
	}

	static String texto(PremioGanhoEmail e) {
		return String.format(
				"🎉 BOA! Você é um dos Ganhadores da caixinha '%s'!\n\nSeu prêmio: R$ %s\n\n"
						+ "Entre no app para confirmar sua chave PIX e receber:\n%s\n\n"
						+ "O dinheiro só sai depois que você aceitar — está tudo no seu controle.\nCaixinha Bet",
				e.tituloCaixinha(), e.valorPremio(), e.linkCaixinha());
	}

	static String html(PremioGanhoEmail e) {
		String titulo = EmailTemplate.escape(e.tituloCaixinha());
		return EmailTemplate.pagina(
				"Você ganhou R$ " + EmailTemplate.escape(e.valorPremio()) + " na caixinha " + titulo,
				EmailTemplate.h1("🎉 BOA! Você ganhou!")
						+ EmailTemplate.paragrafo("Você é um dos Ganhadores da caixinha <strong>" + titulo + "</strong>.")
						+ EmailTemplate.paragrafoMuted("Seu prêmio")
						+ EmailTemplate.destaqueValor("R$ " + EmailTemplate.escape(e.valorPremio()))
						+ EmailTemplate.botao("Aceitar e receber via PIX", e.linkCaixinha())
						+ EmailTemplate.linkFallback(e.linkCaixinha())
						+ EmailTemplate.paragrafoMuted("O dinheiro só sai depois que você aceitar — está tudo no seu controle."));
	}
}
