package com.caxinhabet.auth.app;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Propriedades do módulo auth (prefix {@code auth} no application.yml,
 * Story 2.1).
 *
 * <p>Mantidas no pacote {@code app} porque os use cases as consomem
 * direto. Bind via {@link ConfigurationProperties} (sem precisar
 * habilitar globalmente — {@link Component} permite componente
 * gerenciado pelo Spring).
 */
@Component
@ConfigurationProperties(prefix = "auth")
public class AuthProperties {

	private MagicLink magicLink = new MagicLink();
	private Sessao sessao = new Sessao();
	private Verificacao verificacao = new Verificacao();

	public MagicLink getMagicLink() {
		return magicLink;
	}

	public void setMagicLink(MagicLink magicLink) {
		this.magicLink = magicLink;
	}

	public Sessao getSessao() {
		return sessao;
	}

	public void setSessao(Sessao sessao) {
		this.sessao = sessao;
	}

	public Verificacao getVerificacao() {
		return verificacao;
	}

	public void setVerificacao(Verificacao verificacao) {
		this.verificacao = verificacao;
	}

	public static class MagicLink {
		private int ttlMinutos = 15;
		private String sender = "log";

		public int getTtlMinutos() {
			return ttlMinutos;
		}

		public void setTtlMinutos(int ttlMinutos) {
			this.ttlMinutos = ttlMinutos;
		}

		public String getSender() {
			return sender;
		}

		public void setSender(String sender) {
			this.sender = sender;
		}
	}

	public static class Sessao {
		private int ttlDias = 7;

		/**
		 * SameSite do cookie de sessão. {@code Lax} (default) serve dev/mesma
		 * origem; {@code None} é obrigatório para front e API em domínios
		 * diferentes (cross-site) — e exige {@link #cookieSecure} true.
		 */
		private String cookieSameSite = "Lax";

		/**
		 * Atributo {@code Secure} do cookie. Explícito (não derivado de
		 * {@code request.isSecure()}) porque atrás do proxy TLS do Railway a
		 * request chega como HTTP. Deve ser true em prod (HTTPS) e é
		 * obrigatório quando {@link #cookieSameSite} é {@code None}.
		 */
		private boolean cookieSecure = false;

		public int getTtlDias() {
			return ttlDias;
		}

		public void setTtlDias(int ttlDias) {
			this.ttlDias = ttlDias;
		}

		public String getCookieSameSite() {
			return cookieSameSite;
		}

		public void setCookieSameSite(String cookieSameSite) {
			this.cookieSameSite = cookieSameSite;
		}

		public boolean isCookieSecure() {
			return cookieSecure;
		}

		public void setCookieSecure(boolean cookieSecure) {
			this.cookieSecure = cookieSecure;
		}
	}

	public static class Verificacao {
		private int ttlHoras = 24;

		public int getTtlHoras() {
			return ttlHoras;
		}

		public void setTtlHoras(int ttlHoras) {
			this.ttlHoras = ttlHoras;
		}
	}
}
