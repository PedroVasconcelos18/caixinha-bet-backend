package com.caxinhabet.caixinha.app;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

/**
 * Guarda de boot do módulo caixinha (Story 2.4).
 *
 * <p>Análogo ao {@code auth.app.BootGuard} (Story 2.1). Valida o valor de
 * {@code caixinha.convite.sender} antes do app subir — se a config não é
 * uma das aceitas, lança {@code IllegalStateException} e impede o boot.
 *
 * <p>Cada módulo é dono das suas guardas (alinhado com package-by-feature).
 * Custo: 2 classes de guarda no projeto (auth + caixinha). Ganho:
 * isolamento — mudança em auth não força revisitar caixinha.
 */
@Configuration
public class CaixinhaBootGuard {

	public CaixinhaBootGuard(CaixinhaProperties props) {
		String sender = props.getConvite().getSender();
		if (!"log".equals(sender) && !"smtp".equals(sender)) {
			throw new IllegalStateException(
					"caixinha.convite.sender='"
							+ sender
							+ "' não suportado (aceitos: 'log', 'smtp')");
		}
	}

	/** Propriedades do módulo caixinha (prefix {@code caixinha}). */
	@Component
	@ConfigurationProperties(prefix = "caixinha")
	public static class CaixinhaProperties {
		private Convite convite = new Convite();

		public Convite getConvite() {
			return convite;
		}

		public void setConvite(Convite convite) {
			this.convite = convite;
		}

		public static class Convite {
			private String sender = "log";
			private String emailFrom = "caixinha@caixinha.bet";

			public String getSender() {
				return sender;
			}

			public void setSender(String sender) {
				this.sender = sender;
			}

			public String getEmailFrom() {
				return emailFrom;
			}

			public void setEmailFrom(String emailFrom) {
				this.emailFrom = emailFrom;
			}
		}
	}
}
