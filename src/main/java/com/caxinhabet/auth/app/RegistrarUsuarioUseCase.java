package com.caxinhabet.auth.app;

import com.caxinhabet.auth.adapter.persistence.UsuarioEntity;
import com.caxinhabet.auth.adapter.persistence.UsuarioRepository;
import com.caxinhabet.auth.adapter.session.SessaoStore;
import com.caxinhabet.auth.domain.Cpf;
import com.caxinhabet.auth.domain.CpfJaCadastradoException;
import com.caxinhabet.auth.domain.EmailJaCadastradoException;
import com.caxinhabet.auth.domain.Senha;
import com.caxinhabet.auth.domain.SessaoUsuario;
import com.caxinhabet.auth.domain.TokenAcesso;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Caso de uso: cadastro explícito de usuário (auth por senha, 2026-05).
 *
 * <p>Fluxo (tudo em uma transação):
 * <ol>
 *   <li>Valida nome (não-branco), CPF ({@link Cpf} — dígitos verificadores)
 *       e senha ({@link Senha} — força mínima). Inválido → IllegalArgument-
 *       Exception (vira 400 no handler de validação do MVC).
 *   <li>Normaliza o e-mail (lowercase/trim).
 *   <li>Barra e-mail duplicado e CPF duplicado — defesa em profundidade
 *       sobre os índices únicos do banco; mensagem de erro distinta por
 *       caso (409 com type próprio).
 *   <li>Calcula o hash BCrypt; persiste o {@code Usuario} com perfil + hash.
 *   <li>Abre sessão imediatamente — o usuário sai logado do cadastro.
 * </ol>
 */
@Service
public class RegistrarUsuarioUseCase {

	private final UsuarioRepository usuarios;
	private final SessaoStore sessaoStore;
	private final PasswordEncoder encoder;
	private final AuthProperties authProps;

	public RegistrarUsuarioUseCase(
			UsuarioRepository usuarios,
			SessaoStore sessaoStore,
			PasswordEncoder encoder,
			AuthProperties authProps) {
		this.usuarios = usuarios;
		this.sessaoStore = sessaoStore;
		this.encoder = encoder;
		this.authProps = authProps;
	}

	@Transactional
	public SessaoUsuario executar(
			String nomeBruto, String cpfBruto, String emailBruto, String senhaBruta) {
		String nome = nomeBruto == null ? "" : nomeBruto.trim();
		if (nome.length() < 2) {
			throw new IllegalArgumentException("Nome é obrigatório");
		}
		Cpf cpf = Cpf.de(cpfBruto);
		Senha senha = Senha.crua(senhaBruta);
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
						UsuarioEntity.criarComSenha(email, nome, cpf.digitos(), hash));

		return abrirSessao(usuario);
	}

	private SessaoUsuario abrirSessao(UsuarioEntity usuario) {
		Instant agora = Instant.now();
		String idSessao = TokenAcesso.gerar().valor();
		Instant expira = agora.plus(authProps.getSessao().getTtlDias(), ChronoUnit.DAYS);
		SessaoUsuario sessao =
				new SessaoUsuario(idSessao, usuario.getId(), usuario.getEmail(), agora, expira);
		sessaoStore.criar(sessao);
		return sessao;
	}

	private static String normalizar(String email) {
		if (email == null || email.isBlank()) {
			throw new IllegalArgumentException("E-mail é obrigatório");
		}
		return email.trim().toLowerCase();
	}
}
