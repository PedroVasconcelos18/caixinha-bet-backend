package com.caxinhabet.auth.app;

import com.caxinhabet.auth.adapter.persistence.UsuarioEntity;
import com.caxinhabet.auth.adapter.persistence.UsuarioRepository;
import com.caxinhabet.auth.domain.Cpf;
import com.caxinhabet.auth.domain.CpfJaCadastradoException;
import com.caxinhabet.auth.domain.DataNascimento;
import com.caxinhabet.auth.domain.EmailJaCadastradoException;
import com.caxinhabet.auth.domain.Senha;
import com.caxinhabet.participante.adapter.persistence.ParticipanteEntity;
import com.caxinhabet.participante.adapter.persistence.ParticipanteRepository;
import java.time.LocalDate;
import java.util.List;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Caso de uso: cadastro explícito de usuário (Minha Conta, 2026-05).
 *
 * <p>Fluxo (tudo em uma transação):
 * <ol>
 *   <li>Valida nome, CPF ({@link Cpf}), senha ({@link Senha}) e data de
 *       nascimento ({@link DataNascimento} — exige idade ≥ 18).</li>
 *   <li>Normaliza o e-mail (lowercase/trim).</li>
 *   <li>Barra e-mail/CPF duplicado.</li>
 *   <li>Cria o {@code Usuario} com hash da senha e {@code email_verificado=false}
 *       (default do factory {@link UsuarioEntity#criarComSenha}).</li>
 *   <li>Dispara verificação por e-mail via {@link
 *       SolicitarVerificacaoEmailUseCase}.</li>
 *   <li><b>NÃO abre sessão</b> — usuário precisa clicar no link de verificação.
 *       Rompimento de comportamento intencional (decisão do spec).</li>
 * </ol>
 */
@Service
public class RegistrarUsuarioUseCase {

	private final UsuarioRepository usuarios;
	private final PasswordEncoder encoder;
	private final SolicitarVerificacaoEmailUseCase solicitarVerificacao;
	private final ParticipanteRepository participantes;

	public RegistrarUsuarioUseCase(
			UsuarioRepository usuarios,
			PasswordEncoder encoder,
			SolicitarVerificacaoEmailUseCase solicitarVerificacao,
			ParticipanteRepository participantes) {
		this.usuarios = usuarios;
		this.encoder = encoder;
		this.solicitarVerificacao = solicitarVerificacao;
		this.participantes = participantes;
	}

	@Transactional
	public void executar(
			String nomeBruto,
			String cpfBruto,
			String emailBruto,
			String senhaBruta,
			LocalDate dataNascimentoBruta) {
		String nome = nomeBruto == null ? "" : nomeBruto.trim();
		if (nome.length() < 2) {
			throw new IllegalArgumentException("Nome é obrigatório");
		}
		Cpf cpf = Cpf.de(cpfBruto);
		Senha senha = Senha.crua(senhaBruta);
		DataNascimento nascimento = DataNascimento.de(dataNascimentoBruta);
		String email = normalizar(emailBruto);

		if (usuarios.findByEmail(email).isPresent()) {
			throw new EmailJaCadastradoException();
		}
		if (usuarios.findByCpf(cpf.digitos()).isPresent()) {
			throw new CpfJaCadastradoException();
		}

		String hash = encoder.encode(senha.valor());
		UsuarioEntity usuario =
				usuarios.save(
						UsuarioEntity.criarComSenha(
								email, nome, cpf.digitos(), nascimento.valor(), hash));

		vincularConvitesPendentes(usuario, email);

		solicitarVerificacao.executar(usuario);
	}

	/**
	 * Vincula ao novo Usuário os convites pendentes do mesmo e-mail (Participante
	 * criado por convite na Story 2.4, com {@code usuario_id} NULL). Sem isso, o
	 * dashboard ({@code ListarCaixinhasUseCase}, que filtra por {@code usuario_id})
	 * não exibe as Caixinhas para as quais o convidado foi chamado até ele abrir o
	 * link do convite. Espelha o vínculo preguiçoso da Story 2.5
	 * ({@code BuscarConviteUseCase}).
	 *
	 * <p>Seguro fazer já no cadastro (antes da verificação): o acesso à sessão
	 * exige {@code email_verificado=true} (login e confirmação), então só quem
	 * controla o e-mail chega a ver os convites — mesma garantia do vínculo por
	 * e-mail já existente.
	 */
	private void vincularConvitesPendentes(UsuarioEntity usuario, String email) {
		List<ParticipanteEntity> pendentes =
				participantes.findByEmailAndUsuarioIdIsNull(email);
		for (ParticipanteEntity p : pendentes) {
			p.setUsuarioId(usuario.getId());
		}
		participantes.saveAll(pendentes);
	}

	private static String normalizar(String email) {
		if (email == null || email.isBlank()) {
			throw new IllegalArgumentException("E-mail é obrigatório");
		}
		return email.trim().toLowerCase();
	}
}
