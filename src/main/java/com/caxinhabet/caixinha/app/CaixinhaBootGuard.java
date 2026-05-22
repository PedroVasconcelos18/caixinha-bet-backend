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
		assertSender("caixinha.convite.sender", props.getConvite().getSender());
		// Story 3.1: nova porta de notificação "mínimo atingido" — mesma
		// disciplina de escolha explícita do convite (log/smtp).
		assertSender(
				"caixinha.minimo-atingido.sender", props.getMinimoAtingido().getSender());
		// Story 3.4: notificação de Formação / Reversão da Caixinha.
		assertSender("caixinha.formacao.sender", props.getFormacao().getSender());
	}

	private static void assertSender(String chave, String sender) {
		if (!"log".equals(sender) && !"smtp".equals(sender)) {
			throw new IllegalStateException(
					chave + "='" + sender + "' não suportado (aceitos: 'log', 'smtp')");
		}
	}

	/** Propriedades do módulo caixinha (prefix {@code caixinha}). */
	@Component
	@ConfigurationProperties(prefix = "caixinha")
	public static class CaixinhaProperties {
		private Convite convite = new Convite();
		private MinimoAtingido minimoAtingido = new MinimoAtingido();
		private Formacao formacao = new Formacao();

		public Convite getConvite() {
			return convite;
		}

		public void setConvite(Convite convite) {
			this.convite = convite;
		}

		public MinimoAtingido getMinimoAtingido() {
			return minimoAtingido;
		}

		public void setMinimoAtingido(MinimoAtingido minimoAtingido) {
			this.minimoAtingido = minimoAtingido;
		}

		public Formacao getFormacao() {
			return formacao;
		}

		public void setFormacao(Formacao formacao) {
			this.formacao = formacao;
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

		/** Story 3.1: aviso "mínimo atingido — hora de pagar" (FR-6). */
		public static class MinimoAtingido {
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

		/** Story 3.4: aviso de Formação / Reversão da Caixinha (FR-9). */
		public static class Formacao {
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
