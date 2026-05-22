package com.caxinhabet.caixinha.app;

import com.caxinhabet.auth.adapter.persistence.UsuarioEntity;
import com.caxinhabet.auth.adapter.persistence.UsuarioRepository;
import com.caxinhabet.caixinha.adapter.persistence.CaixinhaEntity;
import com.caxinhabet.caixinha.adapter.persistence.CaixinhaRepository;
import com.caxinhabet.caixinha.adapter.persistence.ResultadoPossivelEntity;
import com.caxinhabet.caixinha.adapter.persistence.ResultadoPossivelRepository;
import com.caxinhabet.caixinha.domain.Caixinha;
import com.caxinhabet.caixinha.domain.CriacaoCaixinhaInvalidaException;
import com.caxinhabet.caixinha.domain.EstadoCaixinha;
import com.caxinhabet.caixinha.domain.NovaCaixinhaSpec;
import com.caxinhabet.caixinha.domain.ResultadoPossivel;
import com.caxinhabet.participante.adapter.persistence.ParticipanteEntity;
import com.caxinhabet.participante.adapter.persistence.ParticipanteRepository;
import com.caxinhabet.participante.domain.StatusParticipante;
import com.caxinhabet.shared.money.Money;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cria uma Caixinha + Resultados Possíveis + Participante-dono numa única
 * transação (Story 2.2, AC-4).
 *
 * <p>Fluxo:
 * <ol>
 *   <li>Valida a {@link NovaCaixinhaSpec} agregando TODOS os motivos.
 *       Inválida → {@link CriacaoCaixinhaInvalidaException}.
 *   <li>Resolve o {@code UsuarioEntity} do organizador (do
 *       {@code SecurityContext}, passado pelo controller).
 *   <li>Persiste {@code CaixinhaEntity} (estado = {@code coletando_convites},
 *       valor em centavos).
 *   <li>Persiste cada {@code ResultadoPossivelEntity} com ordem 0-indexed.
 *   <li>Persiste {@code ParticipanteEntity} com {@code dono=true},
 *       {@code status=convidado}, {@code usuario_id} do organizador,
 *       {@code email} do organizador (que se cadastrou via magic link).
 *   <li>Devolve {@link Caixinha} (record do domínio com tudo populado).
 * </ol>
 *
 * <p>{@code @Transactional}: se qualquer passo falhar, tudo é desfeito —
 * não há "metade Caixinha persistida".
 */
@Service
public class CriarCaixinhaUseCase {

	private final CaixinhaRepository caixinhas;
	private final ResultadoPossivelRepository resultados;
	private final ParticipanteRepository participantes;
	private final UsuarioRepository usuarios;
	private final EnviarConvitesUseCase enviarConvites;

	public CriarCaixinhaUseCase(
			CaixinhaRepository caixinhas,
			ResultadoPossivelRepository resultados,
			ParticipanteRepository participantes,
			UsuarioRepository usuarios,
			EnviarConvitesUseCase enviarConvites) {
		this.caixinhas = caixinhas;
		this.resultados = resultados;
		this.participantes = participantes;
		this.usuarios = usuarios;
		this.enviarConvites = enviarConvites;
	}

	@Transactional
	public Caixinha executar(NovaCaixinhaSpec spec, Long organizadorUsuarioId) {
		List<String> motivos = spec.validar();
		if (!motivos.isEmpty()) {
			throw new CriacaoCaixinhaInvalidaException(motivos);
		}

		UsuarioEntity organizador =
				usuarios.findById(organizadorUsuarioId)
						.orElseThrow(
								() ->
										new IllegalStateException(
												"Organizador autenticado não encontrado: id="
														+ organizadorUsuarioId));

		CaixinhaEntity caixinha =
				new CaixinhaEntity(
						spec.titulo(),
						spec.ladoA(),
						spec.ladoB(),
						spec.valorIngresso().centavos(),
						spec.minimoParticipantes(),
						spec.numeroGanhadores(),
						spec.prazoEntrada(),
						spec.dataApuracao(),
						EstadoCaixinha.coletando_convites,
						organizadorUsuarioId);
		caixinha = caixinhas.save(caixinha);

		List<String> rotulos = spec.rotulosNormalizados();
		List<ResultadoPossivelEntity> resultadosEntidades = new ArrayList<>(rotulos.size());
		for (int ordem = 0; ordem < rotulos.size(); ordem++) {
			resultadosEntidades.add(
					resultados.save(
							new ResultadoPossivelEntity(caixinha.getId(), ordem, rotulos.get(ordem))));
		}

		participantes.save(
				new ParticipanteEntity(
						caixinha.getId(),
						organizadorUsuarioId,
						organizador.getEmail(),
						true,
						StatusParticipante.convidado));

		// Story 2.4: se o wizard mandou emailsConvidados, despacha convites.
		// Composição limpa: o EnviarConvitesUseCase participa da MESMA tx
		// (propagation REQUIRED — default); afterCommit do envio respeita
		// o commit desta tx pai.
		if (!spec.emailsConvidados().isEmpty()) {
			enviarConvites.executar(caixinha.getId(), organizadorUsuarioId, spec.emailsConvidados());
		}

		return toDomain(caixinha, resultadosEntidades);
	}

	private static Caixinha toDomain(
			CaixinhaEntity entity, List<ResultadoPossivelEntity> resultadosEntidades) {
		List<ResultadoPossivel> resultadosDomain = new ArrayList<>(resultadosEntidades.size());
		for (ResultadoPossivelEntity r : resultadosEntidades) {
			resultadosDomain.add(
					new ResultadoPossivel(
							r.getId(), entity.getId(), r.getOrdem(), r.getRotulo()));
		}
		return new Caixinha(
				entity.getId(),
				entity.getTitulo(),
				entity.getLadoA(),
				entity.getLadoB(),
				Money.ofCentavos(entity.getValorIngressoCentavos()),
				entity.getMinimoParticipantes(),
				entity.getNumeroGanhadores(),
				entity.getPrazoEntrada(),
				entity.getDataApuracao(),
				entity.getEstado(),
				entity.getOrganizadorUsuarioId(),
				entity.getCriadoEm(),
				resultadosDomain);
	}
}
