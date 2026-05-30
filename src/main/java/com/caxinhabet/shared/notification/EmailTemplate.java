package com.caxinhabet.shared.notification;

/**
 * Template HTML compartilhado dos e-mails transacionais (2026-05-30).
 *
 * <p>Recria a identidade visual do app (tema escuro, verde {@code #1fe074},
 * dourado {@code #f5c518}, logo "CAIXINHA BET") em HTML de e-mail —
 * inline CSS + tabelas, compatível com Gmail/Outlook/Apple Mail. Nenhuma
 * linha do protótipo {@code remixed-7b210df2.tsx} nem do front é portada
 * (AGENTS.md): os tokens são re-derivados do design system já implementado
 * em {@code globals.css}/{@code ui.tsx}.
 *
 * <p>Classe utilitária sem estado — só monta {@code String} de HTML. Vive em
 * {@code shared.notification} (cross-cutting, sem regra de negócio) e é
 * consumida pelos adapters {@code Resend*}/{@code Smtp*} dos 3 módulos.
 *
 * <p>Todo dado vindo de fora (título da caixinha, nome do organizador) DEVE
 * passar por {@link #escape(String)} antes de ir ao HTML.
 */
public final class EmailTemplate {

	private EmailTemplate() {}

	/** Tom do {@link #callout(Tom, String)} — espelha o Callout do ui.tsx. */
	public enum Tom {
		INFO("#1fe074", "rgba(31,224,116,0.07)", "rgba(31,224,116,0.20)"),
		WARN("#f5a623", "rgba(245,166,35,0.08)", "rgba(245,166,35,0.30)"),
		NEUTRAL("#7cc4ff", "rgba(124,196,255,0.05)", "rgba(124,196,255,0.20)");

		final String cor;
		final String fundo;
		final String borda;

		Tom(String cor, String fundo, String borda) {
			this.cor = cor;
			this.fundo = fundo;
			this.borda = borda;
		}
	}

	// Famílias de fonte com fallback robusto (D7). Anton ~ display condensado;
	// Sora ~ sans humanista. Onde a web-font é ignorada, o fallback aproxima.
	private static final String FONTE_DISPLAY =
			"'Anton','Arial Black',Impact,'Helvetica Neue',Arial,sans-serif";
	private static final String FONTE_CORPO =
			"'Sora',-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Arial,sans-serif";

	/**
	 * Envolve {@code conteudo} (HTML dos blocos) no documento completo da
	 * marca. {@code preview} vira o {@code <title>} e o preheader (texto
	 * escondido que a inbox mostra ao lado do assunto).
	 */
	public static String pagina(String preview, String conteudo) {
		return """
				<!DOCTYPE html>
				<html lang="pt-BR">
				<head>
				<meta charset="utf-8">
				<meta name="viewport" content="width=device-width,initial-scale=1">
				<meta name="color-scheme" content="dark">
				<meta name="supported-color-schemes" content="dark">
				<title>%1$s</title>
				<style>
				@import url('https://fonts.googleapis.com/css2?family=Anton&family=Sora:wght@400;600;700;800&display=swap');
				body{margin:0;padding:0;background:#070b14;}
				a{text-decoration:none;}
				@media (max-width:480px){.cx-card{padding:24px 18px !important;}}
				</style>
				</head>
				<body style="margin:0;padding:0;background:#070b14;">
				<div style="display:none;max-height:0;overflow:hidden;opacity:0;color:#070b14;font-size:1px;line-height:1px;">%1$s</div>
				<table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="background:#070b14;">
				<tr><td align="center" style="padding:28px 12px;">
				<table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="max-width:560px;width:100%%;">
				%2$s
				%3$s
				%4$s
				</table>
				</td></tr>
				</table>
				</body>
				</html>
				"""
				.formatted(escape(preview), header(), cartao(conteudo), rodape());
	}

	// ---- chrome ----------------------------------------------------------

	/** Header com o logo "CAIXINHA BET" (símbolo em caixa verde + wordmark). */
	private static String header() {
		return """
				<tr><td style="padding:4px 6px 18px;">
				<table role="presentation" cellpadding="0" cellspacing="0">
				<tr>
				<td style="width:42px;height:42px;background:#1fe074;border-radius:11px;text-align:center;vertical-align:middle;font-size:22px;line-height:42px;">🏆</td>
				<td style="padding-left:12px;vertical-align:middle;">
				<div style="font-family:%s;font-size:22px;letter-spacing:0.5px;color:#eaf0fa;line-height:1;">CAIXINHA<span style="color:#1fe074;">BET</span></div>
				<div style="font-family:%s;font-size:11px;color:#7e8ca6;margin-top:3px;">Bolões da Copa entre amigos</div>
				</td>
				</tr>
				</table>
				</td></tr>
				""".formatted(FONTE_DISPLAY, FONTE_CORPO);
	}

	/** Card central escuro que segura o conteúdo do e-mail. */
	private static String cartao(String conteudo) {
		return """
				<tr><td class="cx-card" style="background:#101829;border:1px solid #22304a;border-radius:18px;padding:34px 30px;">
				%s
				</td></tr>
				""".formatted(conteudo);
	}

	/** Rodapé institucional (+18 / responsabilidade) — espelha o Footer.tsx. */
	private static String rodape() {
		return """
				<tr><td style="padding:22px 8px 4px;text-align:center;font-family:%s;font-size:11.5px;color:#7e8ca6;line-height:1.6;">
				⚽ Caixinha Bet — Aposte com responsabilidade · +18<br>
				O dinheiro fica num provedor de pagamento licenciado (Asaas), nunca com a gente.
				</td></tr>
				""".formatted(FONTE_CORPO);
	}

	// ---- blocos de conteúdo ---------------------------------------------

	/** Título display (Anton/fallback). */
	public static String h1(String texto) {
		return """
				<h1 style="margin:0 0 14px;font-family:%s;font-weight:400;font-size:26px;line-height:1.15;letter-spacing:0.5px;color:#eaf0fa;">%s</h1>
				""".formatted(FONTE_DISPLAY, texto);
	}

	/** Parágrafo de corpo (Sora/fallback). */
	public static String paragrafo(String texto) {
		return """
				<p style="margin:0 0 14px;font-family:%s;font-size:15px;line-height:1.6;color:#eaf0fa;">%s</p>
				""".formatted(FONTE_CORPO, texto);
	}

	/** Parágrafo secundário, cor muted. */
	public static String paragrafoMuted(String texto) {
		return """
				<p style="margin:0 0 14px;font-family:%s;font-size:13.5px;line-height:1.6;color:#7e8ca6;">%s</p>
				""".formatted(FONTE_CORPO, texto);
	}

	/**
	 * CTA verde (gradiente, alvo ≥44px). Tabela + padding em vez de
	 * height para máxima compatibilidade. {@code href} e {@code label}
	 * devem já estar seguros (href é URL nossa; label é literal).
	 */
	public static String botao(String label, String href) {
		return """
				<table role="presentation" cellpadding="0" cellspacing="0" style="margin:6px 0 18px;">
				<tr><td style="border-radius:12px;background:#1fe074;background-image:linear-gradient(135deg,#1fe074,#0fb85c);">
				<a href="%s" style="display:inline-block;padding:14px 26px;font-family:%s;font-weight:700;font-size:15px;color:#04210f;border-radius:12px;">%s</a>
				</td></tr>
				</table>
				""".formatted(href, FONTE_CORPO, label);
	}

	/** Linha "rótulo: valor" (ex.: ingresso, confronto). */
	public static String infoLinha(String rotulo, String valor) {
		return """
				<table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="margin:0 0 8px;">
				<tr>
				<td style="font-family:%1$s;font-size:13px;color:#7e8ca6;">%2$s</td>
				<td align="right" style="font-family:%1$s;font-size:14px;font-weight:600;color:#eaf0fa;">%3$s</td>
				</tr>
				</table>
				""".formatted(FONTE_CORPO, rotulo, valor);
	}

	/** Número grande dourado (ex.: prêmio). */
	public static String destaqueValor(String valor) {
		return """
				<div style="margin:6px 0 18px;font-family:%s;font-size:38px;line-height:1;letter-spacing:0.5px;color:#f5c518;">%s</div>
				""".formatted(FONTE_DISPLAY, valor);
	}

	/**
	 * Bloco de aviso colorido (espelha o Callout do ui.tsx). Uma fina barra
	 * lateral na cor do tom acentua o aviso (verde/âmbar/azul); o texto fica
	 * legível em {@code #eaf0fa} sobre o fundo tingido.
	 */
	public static String callout(Tom tom, String htmlInterno) {
		return """
				<table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="margin:4px 0 16px;border:1px solid %1$s;border-left:3px solid %2$s;border-radius:12px;background:%3$s;">
				<tr><td style="padding:14px 16px;font-family:%4$s;font-size:13px;line-height:1.55;color:#eaf0fa;">%5$s</td></tr>
				</table>
				""".formatted(tom.borda, tom.cor, tom.fundo, FONTE_CORPO, htmlInterno);
	}

	/** "ou copie este link" + URL (acessibilidade: o botão pode não renderizar). */
	public static String linkFallback(String href) {
		return """
				<p style="margin:0 0 4px;font-family:%1$s;font-size:12px;color:#7e8ca6;">Se o botão não funcionar, copie e cole este link:</p>
				<p style="margin:0 0 8px;font-family:%1$s;font-size:12px;word-break:break-all;"><a href="%2$s" style="color:#7cc4ff;">%2$s</a></p>
				""".formatted(FONTE_CORPO, href);
	}

	/** Escapa caracteres HTML de dados de usuário. Load-bearing. */
	public static String escape(String bruto) {
		if (bruto == null) {
			return "";
		}
		return bruto
				.replace("&", "&amp;")
				.replace("<", "&lt;")
				.replace(">", "&gt;")
				.replace("\"", "&quot;");
	}
}
