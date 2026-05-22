package com.caxinhabet.pagamento.adapter.notification;

import com.caxinhabet.pagamento.domain.CancelamentoEmail;
import com.caxinhabet.pagamento.domain.CancelamentoEmailSender;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Adapter "log-only" do {@link CancelamentoEmailSender} (Story 5.1).
 *
 * <p>Ativo quando {@code caixinha.cancelamento.sender=log} (default).
 * Lista capturada para testes via {@link #avisosEnviados()}.
 */
@Component
@ConditionalOnProperty(
		prefix = "caixinha.cancelamento",
		name = "sender",
		havingValue = "log",
		matchIfMissing = true)
public class LogCancelamentoEmailSender implements CancelamentoEmailSender {

	private static final Logger log =
			LoggerFactory.getLogger(LogCancelamentoEmailSender.class);

	private final List<CancelamentoEmail> capturados =
			Collections.synchronizedList(new ArrayList<>());

	@Override
	public void enviarAvisoCancelamento(CancelamentoEmail e) {
		capturados.add(e);
		log.info(
				"[CANCELAMENTO] Caixinha '{}' — destinatário={}, houvePagamento={}",
				e.tituloCaixinha(),
				e.destinatario(),
				e.houvePagamento());
	}

	/** Cópia defensiva — utilizada pelos testes. NÃO usar em produção. */
	public List<CancelamentoEmail> avisosEnviados() {
		synchronized (capturados) {
			return new ArrayList<>(capturados);
		}
	}

	public void limpar() {
		capturados.clear();
	}
}
