package com.caxinhabet.auth.adapter.notification;

import com.caxinhabet.auth.domain.MagicLinkSender;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Adapter "log-only" do {@link MagicLinkSender} (Story 2.1).
 *
 * <p>Loga o link absoluto no console — destinado a DEV / smoke manual /
 * testes de integração. É o único bean {@link MagicLinkSender} no
 * classpath nesta story; o {@code AuthProperties.MagicLink.sender}
 * deve ser {@code log} no boot (guardado por {@code LogSenderGuard}).
 *
 * <p><b>Por que log e não SMTP nesta story:</b> o ponto da Story 2.1 é o
 * mecanismo do magic link (token, consumo, sessão, cookie). Envio real
 * fica para a Story 2.4 (convite por e-mail) — manter este adapter como
 * porta-trocável evita refatorar o domain depois.
 *
 * <p><b>Risco em PROD:</b> se o boot acontecer com {@code sender=log} em
 * produção, o token cru vai parar no log de aplicação (que pode ir para
 * Datadog/CloudWatch/etc.). O guarda em {@code AuthConfig} falha o boot
 * para qualquer valor diferente de {@code log} hoje; quando a Story 2.4
 * trouxer SMTP, o guarda passa a aceitar {@code smtp} também.
 *
 * <p>Para inspeção em testes: {@link #linksEnviados()} devolve cópia da
 * lista interna de eventos. Não usar em código de produção.
 */
@Component
@ConditionalOnProperty(
		prefix = "auth.magic-link",
		name = "sender",
		havingValue = "log",
		matchIfMissing = true)
public class LogMagicLinkSender implements MagicLinkSender {

	private static final Logger log = LoggerFactory.getLogger(LogMagicLinkSender.class);

	private final List<LinkEnviado> capturados =
			Collections.synchronizedList(new ArrayList<>());

	@Override
	public void enviar(String email, String linkAbsoluto, Instant expiraEm) {
		capturados.add(new LinkEnviado(email, linkAbsoluto, expiraEm));
		log.info(
				"[MAGIC-LINK] Para reproduzir o e-mail, abra: {}  (destinatário={}, expira={})",
				linkAbsoluto,
				email,
				expiraEm);
	}

	@Override
	public void enviarVerificacao(String email, String linkAbsoluto, Instant expiraEm) {
		capturados.add(new LinkEnviado(email, linkAbsoluto, expiraEm));
		log.info(
				"[VERIFICAR-EMAIL] Para reproduzir o e-mail, abra: {}  (destinatário={}, expira={})",
				linkAbsoluto,
				email,
				expiraEm);
	}

	/**
	 * Cópia defensiva dos links enviados — utilizado pelos testes para
	 * recuperar a URL do callback. NÃO usar em código de produção.
	 */
	public List<LinkEnviado> linksEnviados() {
		synchronized (capturados) {
			return new ArrayList<>(capturados);
		}
	}

	public void limpar() {
		capturados.clear();
	}

	public record LinkEnviado(String email, String linkAbsoluto, Instant expiraEm) {}
}
