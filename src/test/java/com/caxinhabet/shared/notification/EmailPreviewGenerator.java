package com.caxinhabet.shared.notification;

import com.caxinhabet.shared.notification.EmailTemplate.Tom;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Gera os 8 e-mails (HTML montado com o {@link EmailTemplate} real, os mesmos
 * blocos usados em produção) em {@code target/email-preview/} para inspeção
 * visual no navegador. Não é um teste de asserção — é um utilitário de preview.
 *
 * <p>O conteúdo aqui espelha o dos helpers {@code *Emails} (package-private nos
 * adapters); como o preview vive em outro pacote, ele recompõe os mesmos blocos
 * via {@link EmailTemplate}. Rode com:
 *
 * <pre>./mvnw -Dtest=EmailPreviewGenerator test</pre>
 *
 * e abra {@code target/email-preview/index.html}.
 */
class EmailPreviewGenerator {

	private static final String LINK = "https://caixinhabet.com/exemplo?token=abc123";

	@Test
	@DisplayName("gera os 8 HTML de preview em target/email-preview/")
	void gerarPreviews() throws Exception {
		Map<String, String> emails = new LinkedHashMap<>();

		// 1 — acesso (magic link)
		emails.put("1-acesso", EmailTemplate.pagina(
				"Seu link de acesso — vale por 15 minutos",
				EmailTemplate.h1("Entre no Caixinha Bet")
						+ EmailTemplate.paragrafo("Clica no botão abaixo para entrar. O link vale por <strong>15 minutos</strong> e só pode ser usado uma vez.")
						+ EmailTemplate.botao("Entrar agora", LINK)
						+ EmailTemplate.linkFallback(LINK)
						+ EmailTemplate.paragrafoMuted("Se não foi você que pediu, é só ignorar este e-mail — sem ações na sua conta.")));

		// 2 — verificação de e-mail
		emails.put("2-verificacao-email", EmailTemplate.pagina(
				"Confirme seu e-mail no Caixinha Bet",
				EmailTemplate.h1("Confirme seu e-mail")
						+ EmailTemplate.paragrafo("Para concluir seu cadastro, confirme seu e-mail clicando no botão abaixo. O link vale por <strong>24 horas</strong> e só pode ser usado uma vez.")
						+ EmailTemplate.botao("Confirmar e-mail", LINK)
						+ EmailTemplate.linkFallback(LINK)
						+ EmailTemplate.paragrafoMuted("Se não foi você que pediu, é só ignorar este e-mail — sem ações na sua conta.")));

		// 3 — convite
		emails.put("3-convite", EmailTemplate.pagina(
				"Rafael te convidou para uma caixinha do Brasil x Marrocos",
				EmailTemplate.h1("Rafael te chamou para uma caixinha!")
						+ EmailTemplate.paragrafo("Montaram um bolão do <strong>Brasil x Marrocos</strong> e te convidaram pra entrar.")
						+ EmailTemplate.infoLinha("Caixinha", "Brasil x Marrocos")
						+ EmailTemplate.infoLinha("Valor do ingresso", "R$ 40,00")
						+ EmailTemplate.botao("Aceitar e palpitar", LINK)
						+ EmailTemplate.linkFallback(LINK)
						+ EmailTemplate.callout(Tom.INFO,
								"É um bolão entre amigos. O dinheiro fica num provedor de pagamento licenciado (Asaas), nunca com a gente. Se a caixinha não der certo, o estorno é automático.")));

		// 4 — mínimo atingido
		emails.put("4-minimo-atingido", EmailTemplate.pagina(
				"Mínimo atingido na caixinha Brasil x Marrocos — hora de pagar",
				EmailTemplate.h1("✅ Mínimo atingido!")
						+ EmailTemplate.paragrafo("A caixinha <strong>Brasil x Marrocos</strong> (Brasil x Marrocos) liberou o pagamento. Bora garantir seu lugar?")
						+ EmailTemplate.infoLinha("Valor do ingresso", "R$ 40,00")
						+ EmailTemplate.botao("Pagar com PIX", LINK)
						+ EmailTemplate.linkFallback(LINK)
						+ EmailTemplate.callout(Tom.INFO,
								"O dinheiro fica no provedor de pagamento licenciado (Asaas), não com a gente. Se a caixinha não der certo, o estorno é automático.")));

		// 5a — formada
		emails.put("5a-formada", EmailTemplate.pagina(
				"Caixinha Brasil x Marrocos formada — agora é esperar o jogo",
				EmailTemplate.h1("🏆 Caixinha formada!")
						+ EmailTemplate.paragrafo("É oficial: a caixinha <strong>Brasil x Marrocos</strong> (Brasil x Marrocos) está formada. Pagamentos suficientes confirmados — agora é esperar o jogo.")
						+ EmailTemplate.botao("Acompanhar a caixinha", LINK)
						+ EmailTemplate.linkFallback(LINK)
						+ EmailTemplate.paragrafoMuted("Boa sorte a todos!")));

		// 5b — revertida
		emails.put("5b-revertida", EmailTemplate.pagina(
				"Atualização da caixinha Brasil x Marrocos",
				EmailTemplate.h1("Atualização da sua caixinha")
						+ EmailTemplate.paragrafo("Sobre a caixinha <strong>Brasil x Marrocos</strong> (Brasil x Marrocos).")
						+ EmailTemplate.callout(Tom.WARN,
								"Um pagamento foi estornado e a caixinha voltou a coletar pagamentos — ela ainda <strong>não</strong> está formada. Aquele aviso de 'Caixinha Formada' que você recebeu antes fica retificado por este.")
						+ EmailTemplate.paragrafoMuted("Nada de errado da sua parte — é só o número de pagamentos confirmados que mudou.")
						+ EmailTemplate.botao("Acompanhar a caixinha", LINK)
						+ EmailTemplate.linkFallback(LINK)));

		// 6 — prêmio ganho
		emails.put("6-premio-ganho", EmailTemplate.pagina(
				"Você ganhou R$ 150,00 na caixinha Brasil x Marrocos",
				EmailTemplate.h1("🎉 BOA! Você ganhou!")
						+ EmailTemplate.paragrafo("Você é um dos Ganhadores da caixinha <strong>Brasil x Marrocos</strong>.")
						+ EmailTemplate.paragrafoMuted("Seu prêmio")
						+ EmailTemplate.destaqueValor("R$ 150,00")
						+ EmailTemplate.botao("Aceitar e receber via PIX", LINK)
						+ EmailTemplate.linkFallback(LINK)
						+ EmailTemplate.paragrafoMuted("O dinheiro só sai depois que você aceitar — está tudo no seu controle.")));

		// 7a — cancelamento com pagamento
		emails.put("7a-cancelamento-com-pagamento", EmailTemplate.pagina(
				"A caixinha Brasil x Marrocos não fechou",
				EmailTemplate.h1("A caixinha não fechou")
						+ EmailTemplate.paragrafo("A caixinha <strong>Brasil x Marrocos</strong> não atingiu o número mínimo de participantes no prazo.")
						+ EmailTemplate.callout(Tom.INFO,
								"Você pagou o ingresso, então já estamos devolvendo seu dinheiro automaticamente, valor cheio, direto na origem do pagamento. <strong>Você não precisa fazer nada.</strong>")
						+ EmailTemplate.paragrafoMuted("Acontece — quem sabe na próxima!")));

		// 7b — cancelamento sem pagamento
		emails.put("7b-cancelamento-sem-pagamento", EmailTemplate.pagina(
				"A caixinha Brasil x Marrocos não fechou",
				EmailTemplate.h1("A caixinha não fechou")
						+ EmailTemplate.paragrafo("A caixinha <strong>Brasil x Marrocos</strong> não atingiu o número mínimo de participantes no prazo.")
						+ EmailTemplate.paragrafoMuted("Como você ainda não tinha pago o ingresso, não há nada a devolver. Fica para a próxima!")));

		Path dir = Path.of("target", "email-preview");
		Files.createDirectories(dir);

		StringBuilder index = new StringBuilder("""
				<!DOCTYPE html><html lang="pt-BR"><head><meta charset="utf-8">
				<title>Preview dos e-mails — Caixinha Bet</title>
				<style>body{font-family:system-ui,sans-serif;background:#070b14;color:#eaf0fa;padding:24px;}
				h1{color:#1fe074;}a{color:#7cc4ff;display:block;padding:6px 0;font-size:15px;}</style>
				</head><body><h1>Preview dos e-mails (HTML real, via EmailTemplate)</h1>
				<p>Cada link abre o e-mail exatamente como será renderizado:</p>
				""");

		for (Map.Entry<String, String> e : emails.entrySet()) {
			String arquivo = e.getKey() + ".html";
			Files.writeString(dir.resolve(arquivo), e.getValue());
			index.append("<a href=\"").append(arquivo).append("\">").append(e.getKey()).append("</a>\n");
		}
		index.append("</body></html>");
		Files.writeString(dir.resolve("index.html"), index.toString());

		System.out.println("\n==> Preview gerado: " + dir.toAbsolutePath().resolve("index.html") + "\n");
	}
}
