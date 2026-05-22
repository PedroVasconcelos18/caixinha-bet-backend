package com.caxinhabet.caixinha.adapter.notification;

import com.caxinhabet.caixinha.domain.MinimoAtingidoEmail;
import com.caxinhabet.caixinha.domain.MinimoAtingidoEmailSender;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Adapter "log-only" do {@link MinimoAtingidoEmailSender} (Story 3.1).
 *
 * <p>Loga o aviso no console — destinado a DEV/smoke. É o bean ativo
 * quando {@code caixinha.minimo-atingido.sender=log} (default).
 *
 * <p>Espelha {@code LogConviteEmailSender}: lista interna capturada
 * expõe-se via {@link #avisosEnviados()} para testes; {@link #limpar()}
 * para isolamento entre cenários.
 */
@Component
@ConditionalOnProperty(
		prefix = "caixinha.minimo-atingido",
		name = "sender",
		havingValue = "log",
		matchIfMissing = true)
public class LogMinimoAtingidoEmailSender implements MinimoAtingidoEmailSender {

	private static final Logger log =
			LoggerFactory.getLogger(LogMinimoAtingidoEmailSender.class);

	private final List<MinimoAtingidoEmail> capturados =
			Collections.synchronizedList(new ArrayList<>());

	@Override
	public void enviarAvisoMinimoAtingido(MinimoAtingidoEmail e) {
		capturados.add(e);
		log.info(
				"[MINIMO-ATINGIDO] Caixinha '{}' liberou pagamento — destinatário={}, link={}",
				e.tituloCaixinha(),
				e.destinatario(),
				e.linkCaixinha());
	}

	/** Cópia defensiva — utilizada pelos testes. NÃO usar em produção. */
	public List<MinimoAtingidoEmail> avisosEnviados() {
		synchronized (capturados) {
			return new ArrayList<>(capturados);
		}
	}

	public void limpar() {
		capturados.clear();
	}
}
