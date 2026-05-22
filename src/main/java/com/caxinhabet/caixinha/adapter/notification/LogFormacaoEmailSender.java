package com.caxinhabet.caixinha.adapter.notification;

import com.caxinhabet.caixinha.domain.FormacaoEmail;
import com.caxinhabet.caixinha.domain.FormacaoEmailSender;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Adapter "log-only" do {@link FormacaoEmailSender} (Story 3.4).
 *
 * <p>Bean ativo quando {@code caixinha.formacao.sender=log} (default).
 * Espelha {@code LogMinimoAtingidoEmailSender}: lista capturada para
 * testes via {@link #avisosEnviados()}; {@link #limpar()} entre cenários.
 */
@Component
@ConditionalOnProperty(
		prefix = "caixinha.formacao",
		name = "sender",
		havingValue = "log",
		matchIfMissing = true)
public class LogFormacaoEmailSender implements FormacaoEmailSender {

	private static final Logger log =
			LoggerFactory.getLogger(LogFormacaoEmailSender.class);

	private final List<FormacaoEmail> capturados =
			Collections.synchronizedList(new ArrayList<>());

	@Override
	public void enviarAvisoFormacao(FormacaoEmail e) {
		capturados.add(e);
		log.info(
				"[FORMACAO {}] Caixinha '{}' — destinatário={}, link={}",
				e.tipo(),
				e.tituloCaixinha(),
				e.destinatario(),
				e.linkCaixinha());
	}

	/** Cópia defensiva — utilizada pelos testes. NÃO usar em produção. */
	public List<FormacaoEmail> avisosEnviados() {
		synchronized (capturados) {
			return new ArrayList<>(capturados);
		}
	}

	public void limpar() {
		capturados.clear();
	}
}
