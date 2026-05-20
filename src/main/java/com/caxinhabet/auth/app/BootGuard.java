package com.caxinhabet.auth.app;

import org.springframework.context.annotation.Configuration;

/**
 * Guarda de boot do módulo auth (Story 2.1).
 *
 * <p>Falha o boot se a config estiver fora do que esta versão suporta —
 * principalmente {@code auth.magic-link.sender}. Hoje só aceitamos
 * {@code log}; quando a Story 2.4 trouxer SMTP, ampliar a lista.
 *
 * <p>Sem isso, um deploy em produção com a config padrão {@code log}
 * faria o token cru ir para o log de aplicação (Datadog/CloudWatch/...).
 * Falhar o boot é a defesa final.
 */
@Configuration
public class BootGuard {

	public BootGuard(AuthProperties props) {
		String sender = props.getMagicLink().getSender();
		if (!"log".equals(sender) && !"smtp".equals(sender)) {
			throw new IllegalStateException(
					"auth.magic-link.sender='"
							+ sender
							+ "' não suportado (aceitos: 'log', 'smtp' — Story 2.4)");
		}
		if (props.getMagicLink().getTtlMinutos() <= 0) {
			throw new IllegalStateException(
					"auth.magic-link.ttl-minutos deve ser > 0 (recebido "
							+ props.getMagicLink().getTtlMinutos()
							+ ")");
		}
		if (props.getSessao().getTtlDias() <= 0) {
			throw new IllegalStateException(
					"auth.sessao.ttl-dias deve ser > 0 (recebido "
							+ props.getSessao().getTtlDias()
							+ ")");
		}
	}
}
