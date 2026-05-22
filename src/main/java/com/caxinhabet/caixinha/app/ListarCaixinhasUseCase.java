package com.caxinhabet.caixinha.app;

import com.caxinhabet.caixinha.adapter.persistence.CaixinhaEntity;
import com.caxinhabet.caixinha.adapter.persistence.CaixinhaRepository;
import com.caxinhabet.caixinha.domain.Caixinha;
import com.caxinhabet.caixinha.domain.EstadoCaixinha;
import com.caxinhabet.participante.adapter.persistence.ParticipanteEntity;
import com.caxinhabet.participante.adapter.persistence.ParticipanteRepository;
import com.caxinhabet.participante.domain.StatusParticipante;
import com.caxinhabet.shared.money.Money;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lista as Caixinhas das quais um Usuário participa, para o dashboard
 * (Épico 6 v5, Story 6.1, FR-17).
 *
 * <p>Read-only. O Usuário só vê as Caixinhas das quais é Participante
 * (FR-16) — a listagem filtra por isso.
 */
@Service
public class ListarCaixinhasUseCase {

	private final CaixinhaRepository caixinhas;
	private final ParticipanteRepository participantes;

	public ListarCaixinhasUseCase(
			CaixinhaRepository caixinhas, ParticipanteRepository participantes) {
		this.caixinhas = caixinhas;
		this.participantes = participantes;
	}

	/**
	 * Resumo de uma Caixinha no dashboard.
	 *
	 * @param id id da Caixinha.
	 * @param titulo título.
	 * @param ladoA / @param ladoB lados do confronto.
	 * @param estado estado atual.
	 * @param pagosConfirmados nº de Participantes em {@code pago}.
	 * @param minimoParticipantes Mínimo de Participantes.
	 * @param premioPotencial Prêmio potencial — {@code max(Σ pago − Taxa, 0)}.
	 * @param ativa {@code false} se a Caixinha está em estado terminal
	 *     ({@code repassada}/{@code cancelada}).
	 */
	public record CaixinhaResumo(
			long id,
			String titulo,
			String ladoA,
			String ladoB,
			EstadoCaixinha estado,
			long pagosConfirmados,
			int minimoParticipantes,
			Money premioPotencial,
			boolean ativa) {}

	/**
	 * @param usuarioId Usuário autenticado.
	 * @return resumos das Caixinhas do Usuário, mais recentes primeiro.
	 */
	@Transactional(readOnly = true)
	public List<CaixinhaResumo> executar(long usuarioId) {
		// Caixinhas onde o Usuário é Participante (FR-16).
		List<Long> caixinhaIds =
				participantes.findByUsuarioId(usuarioId).stream()
						.map(ParticipanteEntity::getCaixinhaId)
						.distinct()
						.toList();

		return caixinhas.findAllById(caixinhaIds).stream()
				.sorted(Comparator.comparing(CaixinhaEntity::getCriadoEm).reversed())
				.map(this::resumir)
				.toList();
	}

	private CaixinhaResumo resumir(CaixinhaEntity c) {
		long pagos =
				participantes.countByCaixinhaIdAndStatusIn(
						c.getId(), List.of(StatusParticipante.pago));
		// Prêmio potencial = max(Σ pago − Taxa, 0) — mesma fórmula da Story 3.5.
		Money soma = Money.ofCentavos(c.getValorIngressoCentavos()).times((int) pagos);
		Money premio = soma.minus(Caixinha.TAXA_SERVICO);
		Money premioPotencial =
				premio.compareTo(Money.ZERO) < 0 ? Money.ZERO : premio;

		boolean ativa =
				c.getEstado() != EstadoCaixinha.repassada
						&& c.getEstado() != EstadoCaixinha.cancelada;

		return new CaixinhaResumo(
				c.getId(),
				c.getTitulo(),
				c.getLadoA(),
				c.getLadoB(),
				c.getEstado(),
				pagos,
				c.getMinimoParticipantes(),
				premioPotencial,
				ativa);
	}
}
