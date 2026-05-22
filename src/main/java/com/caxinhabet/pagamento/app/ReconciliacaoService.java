package com.caxinhabet.pagamento.app;

import com.caxinhabet.pagamento.adapter.persistence.CobrancaEntity;
import com.caxinhabet.pagamento.adapter.persistence.CobrancaRepository;
import com.caxinhabet.pagamento.domain.EstadoCobranca;
import com.caxinhabet.pagamento.domain.ProvedorPagamento;
import com.caxinhabet.pagamento.domain.StatusCobranca;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Reconcilia o estado financeiro do app contra o Provedor (Story 3.6 v5,
 * NFR-2 / AR-6).
 *
 * <p>Itera as cobranças "vivas" ({@code ativa}, {@code confirmada}),
 * consulta o status real de cada uma no Provedor e <b>alerta</b> quando
 * diverge — <b>nunca corrige</b>. A verdade do dinheiro é o Provedor; uma
 * correção automática errada moveria dinheiro indevidamente. A divergência
 * é sinalizada (log estruturado) e contabilizada; a correção é decisão
 * operacional humana (regra dura — PRD §13).
 *
 * <p>Cobre o caso do <b>webhook perdido</b> (AR-6): se o webhook
 * {@code PAYMENT_CONFIRMED} se perdeu, a cobrança fica {@code ativa} no
 * app mas {@code CONFIRMADA} no Provedor — divergência detectada aqui.
 *
 * <p>O resultado da última rodada fica disponível via
 * {@link #ultimoResultado()} — consumido pelo health indicator (AC-5).
 */
@Service
public class ReconciliacaoService {

	private static final Logger log = LoggerFactory.getLogger(ReconciliacaoService.class);

	/** Estados "vivos" — valem reconciliação contínua. */
	private static final List<EstadoCobranca> ESTADOS_VIVOS =
			List.of(
					EstadoCobranca.ativa,
					EstadoCobranca.confirmada,
					// Story 5.1: estorno disparado, aguardando confirmação —
					// vale reconciliar (webhook PAYMENT_REFUNDED pode perder-se).
					EstadoCobranca.estorno_solicitado);

	private final CobrancaRepository cobrancas;
	private final ProvedorPagamento provedor;

	/** Resultado da última rodada — lido pelo health indicator. */
	private final AtomicReference<ResultadoReconciliacao> ultimo =
			new AtomicReference<>(ResultadoReconciliacao.nunca());

	public ReconciliacaoService(
			CobrancaRepository cobrancas, ProvedorPagamento provedor) {
		this.cobrancas = cobrancas;
		this.provedor = provedor;
	}

	/**
	 * Executa uma rodada de reconciliação. Não muta estado de domínio —
	 * só consulta, compara e alerta.
	 *
	 * @return o resultado da rodada (também guardado em {@link #ultimoResultado()}).
	 */
	public ResultadoReconciliacao reconciliar() {
		List<CobrancaEntity> vivas = cobrancas.findByEstadoIn(ESTADOS_VIVOS);
		int verificadas = 0;
		int divergentes = 0;
		int erros = 0;

		for (CobrancaEntity c : vivas) {
			verificadas++;
			try {
				StatusCobranca noProvedor = provedor.consultar(c.getCobrancaId());
				if (divergente(c.getEstado(), noProvedor)) {
					divergentes++;
					// Alerta operacional explícito — NUNCA corrige (NFR-2).
					log.error(
							"[RECONCILIACAO] DIVERGÊNCIA cobranca={} | app={} | provedor={}"
									+ " — verifique manualmente, NÃO corrigido automaticamente.",
							c.getCobrancaId(),
							c.getEstado(),
							noProvedor);
				}
			} catch (Exception e) {
				// Falha ao consultar uma cobrança não derruba a rodada inteira.
				erros++;
				log.warn(
						"[RECONCILIACAO] falha ao consultar cobranca={}: {}",
						c.getCobrancaId(),
						e.getMessage());
			}
		}

		ResultadoReconciliacao r =
				new ResultadoReconciliacao(
						Instant.now(), verificadas, divergentes, erros);
		ultimo.set(r);
		log.info(
				"[RECONCILIACAO] rodada concluída: {} verificadas, {} divergentes, {} erros",
				verificadas,
				divergentes,
				erros);
		return r;
	}

	/** Resultado da última rodada — para o health indicator (AC-5). */
	public ResultadoReconciliacao ultimoResultado() {
		return ultimo.get();
	}

	/**
	 * {@code true} se o estado local da cobrança é incompatível com o
	 * status no Provedor.
	 */
	private static boolean divergente(EstadoCobranca local, StatusCobranca provedor) {
		return switch (local) {
			// app diz "ativa" (aguardando pagamento): só é coerente se o
			// Provedor também diz PENDENTE. CONFIRMADA aqui = webhook perdido.
			case ativa -> provedor != StatusCobranca.PENDENTE;
			// app diz "confirmada": coerente se o Provedor confirma. Se o
			// Provedor diz ESTORNADA, é divergência (webhook de estorno perdido).
			case confirmada -> provedor != StatusCobranca.CONFIRMADA;
			// app diz "estorno_solicitado" (Story 5.1): o estorno foi
			// disparado. Coerente enquanto o Provedor ainda diz CONFIRMADA
			// (estorno em processamento) ou já diz ESTORNADA. Qualquer outro
			// status é divergência.
			case estorno_solicitado ->
					provedor != StatusCobranca.CONFIRMADA
							&& provedor != StatusCobranca.ESTORNADA;
			// estados terminais não são reconciliados aqui (não estão em
			// ESTADOS_VIVOS) — defensivo:
			case invalidada, expirada, estornada -> false;
		};
	}

	/**
	 * Resultado de uma rodada de reconciliação.
	 *
	 * @param executadaEm instante da rodada ({@code null} se nunca rodou).
	 * @param verificadas nº de cobranças verificadas.
	 * @param divergentes nº de divergências encontradas.
	 * @param erros nº de cobranças que falharam ao consultar o Provedor.
	 */
	public record ResultadoReconciliacao(
			Instant executadaEm, int verificadas, int divergentes, int erros) {

		static ResultadoReconciliacao nunca() {
			return new ResultadoReconciliacao(null, 0, 0, 0);
		}

		public boolean jaRodou() {
			return executadaEm != null;
		}
	}
}
