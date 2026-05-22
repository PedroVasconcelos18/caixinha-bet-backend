package com.caxinhabet.pagamento.adapter.notification;

import com.caxinhabet.pagamento.domain.PremioGanhoEmail;
import com.caxinhabet.pagamento.domain.PremioGanhoEmailSender;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Adapter "log-only" do {@link PremioGanhoEmailSender} (Story 4.3).
 *
 * <p>Ativo quando {@code caixinha.premio-ganho.sender=log} (default).
 * Lista capturada para testes via {@link #avisosEnviados()}.
 */
@Component
@ConditionalOnProperty(
		prefix = "caixinha.premio-ganho",
		name = "sender",
		havingValue = "log",
		matchIfMissing = true)
public class LogPremioGanhoEmailSender implements PremioGanhoEmailSender {

	private static final Logger log =
			LoggerFactory.getLogger(LogPremioGanhoEmailSender.class);

	private final List<PremioGanhoEmail> capturados =
			Collections.synchronizedList(new ArrayList<>());

	@Override
	public void enviarAvisoPremio(PremioGanhoEmail e) {
		capturados.add(e);
		log.info(
				"[PREMIO-GANHO] Caixinha '{}' — destinatário={}, valor=R$ {}, link={}",
				e.tituloCaixinha(),
				e.destinatario(),
				e.valorPremio(),
				e.linkCaixinha());
	}

	/** Cópia defensiva — utilizada pelos testes. NÃO usar em produção. */
	public List<PremioGanhoEmail> avisosEnviados() {
		synchronized (capturados) {
			return new ArrayList<>(capturados);
		}
	}

	public void limpar() {
		capturados.clear();
	}
}
