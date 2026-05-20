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

		public int getTtlDias() {
			return ttlDias;
		}

		public void setTtlDias(int ttlDias) {
			this.ttlDias = ttlDias;
		}
	}
}
