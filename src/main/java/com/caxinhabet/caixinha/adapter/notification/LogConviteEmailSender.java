package com.caxinhabet.caixinha.adapter.notification;

import com.caxinhabet.caixinha.domain.ConviteEmail;
import com.caxinhabet.caixinha.domain.ConviteEmailSender;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Adapter "log-only" do {@link ConviteEmailSender} (Story 2.4).
 *
 * <p>Loga o conteúdo do convite no console — destinado a DEV / smoke /
 * testes de integração. É o bean ativo quando
 * {@code caixinha.convite.sender=log} (default).
 *
 * <p>Espelha o {@code LogMagicLinkSender} da Story 2.1: método
 * {@link #convitesEnviados()} expõe cópia da lista interna para testes.
 *
 * <p><b>Risco em PROD:</b> se o boot acontecer com {@code sender=log} em
 * produção, links de convite vão para o log (Datadog/CloudWatch/etc.) —
 * exposição leve (link não é segredo no mesmo grau que token de auth).
 * Mesmo assim o {@code CaixinhaBootGuard} obriga decisão explícita.
 */
@Component
@ConditionalOnProperty(
		prefix = "caixinha.convite",
		name = "sender",
		havingValue = "log",
		matchIfMissing = true)
public class LogConviteEmailSender implements ConviteEmailSender {

	private static final Logger log = LoggerFactory.getLogger(LogConviteEmailSender.class);

	private final List<ConviteEmail> capturados =
			Collections.synchronizedList(new ArrayList<>());

	@Override
	public void enviarConvite(ConviteEmail c) {
		capturados.add(c);
		log.info(
				"[CONVITE] Para reproduzir o e-mail, abra: {}  (destinatário={}, organizador={}, caixinha={})",
				c.linkConvite(),
				c.destinatario(),
				c.organizadorNome(),
				c.tituloCaixinha());
	}

	/** Cópia defensiva — utilizada pelos testes. NÃO usar em produção. */
	public List<ConviteEmail> convitesEnviados() {
		synchronized (capturados) {
			return new ArrayList<>(capturados);
		}
	}

	public void limpar() {
		capturados.clear();
	}
}
