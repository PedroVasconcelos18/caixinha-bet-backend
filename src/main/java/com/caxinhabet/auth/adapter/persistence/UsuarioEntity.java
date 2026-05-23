package com.caxinhabet.auth.adapter.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Entidade JPA da tabela {@code usuario} (Story 2.1).
 *
 * <p>O {@code email} é {@code CITEXT} no Postgres — comparação
 * case-insensitive na borda do banco. A coluna precisa de
 * {@code @Column(columnDefinition = "citext")} para o JPA não tentar
 * gerar um {@code VARCHAR} e brigar com o schema do Flyway no
 * {@code validate}.
 *
 * <p>Setters privados / construtor sem-args protegido — só o factory
 * estático {@link #criar(String)} ou JPA podem instanciar.
 */
@Entity
@Table(name = "usuario")
public class UsuarioEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, columnDefinition = "citext")
	private String email;

	@Column(nullable = false)
	private Instant criadoEm;

	// Story 2.5 v5: chave PIX de recebimento, cadastrada uma vez no perfil
	// e usada em todas as Caixinhas. Pré-requisito do Palpite (FR-5 v5).
	// Nullable porque cadastro acontece após o primeiro login (UX).
	@Column(length = 512)
	private String chavePix;

	// Story 3.2 v5: perfil de pagamento (nome + CPF) — exigido pelo Asaas
	// para criar o "customer" (quem paga). Cadastro postergado ao 1º
	// pagamento (FR-16 v5). asaasCustomerId é o id do customer no Asaas,
	// criado lazy no 1º pagamento e reusado nas cobranças seguintes.
	@Column(name = "nome_completo", length = 160)
	private String nomeCompleto;

	@Column(length = 11)
	private String cpf;

	@Column(name = "asaas_customer_id", length = 64)
	private String asaasCustomerId;

	// Auth por senha (2026-05): hash BCrypt da senha (60 chars). Nullable —
	// usuários legados do magic link não têm senha (definem via reset). O
	// valor cru NUNCA é armazenado; só o hash. Anti-padrão proibido: expor
	// este campo em qualquer response (não entra no MeResponse).
	@Column(name = "senha_hash", length = 60)
	private String senhaHash;

	// Minha Conta (2026-05): perfil estendido + verificação + soft delete.
	// data_nascimento é obrigatória no cadastro novo (validação no use case);
	// os demais campos opcionais. foto_blob/foto_mime ambos NULL ou ambos
	// preenchidos (invariante mantida em definirFoto/removerFoto).
	@Column(name = "data_nascimento")
	private java.time.LocalDate dataNascimento;

	@Column(length = 11)
	private String telefone;

	@Column(length = 120)
	private String cidade;

	@Column(length = 140)
	private String bio;

	@Column(name = "foto_blob", columnDefinition = "bytea")
	private byte[] fotoBlob;

	@Column(name = "foto_mime", length = 32)
	private String fotoMime;

	@Column(name = "email_verificado", nullable = false)
	private boolean emailVerificado = true;

	@Column(name = "deletado_em")
	private java.time.Instant deletadoEm;

	protected UsuarioEntity() {
		// JPA exige construtor sem-args.
	}

	private UsuarioEntity(String email, Instant criadoEm) {
		this.email = email;
		this.criadoEm = criadoEm;
	}

	public static UsuarioEntity criar(String email) {
		return new UsuarioEntity(email, Instant.now());
	}

	/**
	 * Cria um Usuário já com perfil e senha — usado pelo cadastro explícito
	 * (auth por senha). Diferente de {@link #criar(String)}, que nasce só
	 * com e-mail (cadastro implícito legado do magic link).
	 *
	 * @param senhaHash hash BCrypt já calculado pelo use case.
	 */
	public static UsuarioEntity criarComSenha(
			String email,
			String nomeCompleto,
			String cpf,
			java.time.LocalDate dataNascimento,
			String senhaHash) {
		UsuarioEntity u = new UsuarioEntity(email, Instant.now());
		u.definirPerfilPagamento(nomeCompleto, cpf);
		u.dataNascimento = dataNascimento;
		u.senhaHash = senhaHash;
		u.emailVerificado = false;
		return u;
	}

	public Long getId() {
		return id;
	}

	public String getEmail() {
		return email;
	}

	public Instant getCriadoEm() {
		return criadoEm;
	}

	public String getChavePix() {
		return chavePix;
	}

	/**
	 * Define (ou limpa) a chave PIX do perfil (Story 2.5 v5).
	 *
	 * <p>Normaliza na borda do agregado: {@code trim} aplicado, blank vira
	 * {@code null}. Isso mantém a disciplina "setters privados" da classe
	 * — quem atualiza chave PIX entra por este método (não por setter cru),
	 * e a invariante "blank é tratado como ausente" fica garantida em um
	 * lugar só, não espalhada nos use cases.
	 *
	 * @param chavePix valor cru (pode ter espaços, pode ser {@code null}/blank).
	 */
	public void definirChavePix(String chavePix) {
		this.chavePix = (chavePix == null || chavePix.isBlank()) ? null : chavePix.trim();
	}

	public String getNomeCompleto() {
		return nomeCompleto;
	}

	public String getCpf() {
		return cpf;
	}

	public String getAsaasCustomerId() {
		return asaasCustomerId;
	}

	public String getSenhaHash() {
		return senhaHash;
	}

	/**
	 * Grava o hash BCrypt da senha (cadastro ou redefinição). Recebe o hash
	 * já calculado — a entidade não conhece o {@code PasswordEncoder}.
	 */
	public void definirSenhaHash(String senhaHash) {
		this.senhaHash = senhaHash;
	}

	/**
	 * Define o perfil de pagamento — nome completo e CPF (Story 3.2 v5).
	 *
	 * <p>Normaliza na borda: nome com {@code trim}; CPF mantém só dígitos
	 * (remove pontos/traço/espaços). Blank vira {@code null}. Validação de
	 * formato (CPF de 11 dígitos válido) é do use case — aqui só armazena.
	 *
	 * @param nomeCompleto nome do Participante (usado como {@code name} no Asaas).
	 * @param cpf CPF cru (com ou sem máscara); persistido só com dígitos.
	 */
	public void definirPerfilPagamento(String nomeCompleto, String cpf) {
		this.nomeCompleto =
				(nomeCompleto == null || nomeCompleto.isBlank()) ? null : nomeCompleto.trim();
		this.cpf =
				(cpf == null || cpf.isBlank()) ? null : cpf.replaceAll("\\D", "");
	}

	/**
	 * Atualiza apenas o nome (Minha Conta, 2026-05). {@code null}/blank é
	 * ignorado — preserva o nome atual (cadastro define no factory).
	 * Validação de comprimento mínimo fica no use case (espelha a regra de
	 * {@code RegistrarUsuarioUseCase}).
	 */
	public void definirNome(String nomeCompleto) {
		if (nomeCompleto == null || nomeCompleto.isBlank()) return;
		this.nomeCompleto = nomeCompleto.trim();
	}

	/**
	 * Registra o id do customer criado no Asaas (Story 3.2 v5). Chamado
	 * uma única vez, no 1º pagamento — depois é reusado.
	 */
	public void registrarAsaasCustomerId(String asaasCustomerId) {
		this.asaasCustomerId = asaasCustomerId;
	}

	/**
	 * {@code true} se o perfil de pagamento está completo o suficiente
	 * para gerar uma cobrança (FR-7 v5): nome, CPF e chave PIX presentes.
	 */
	public boolean perfilPagamentoCompleto() {
		return nomeCompleto != null
				&& !nomeCompleto.isBlank()
				&& cpf != null
				&& cpf.length() == 11
				&& chavePix != null
				&& !chavePix.isBlank();
	}

	// ---- Minha Conta (2026-05) -----------------------------------------

	public java.time.LocalDate getDataNascimento() {
		return dataNascimento;
	}

	public String getTelefone() {
		return telefone;
	}

	public String getCidade() {
		return cidade;
	}

	public String getBio() {
		return bio;
	}

	public byte[] getFotoBlob() {
		return fotoBlob;
	}

	public String getFotoMime() {
		return fotoMime;
	}

	public boolean isEmailVerificado() {
		return emailVerificado;
	}

	public java.time.Instant getDeletadoEm() {
		return deletadoEm;
	}

	/**
	 * Atualiza os campos opcionais do perfil. {@code dataNascimento} só é
	 * sobrescrito quando não-{@code null} (cadastro novo já preenche; edição
	 * preserva). Demais campos: blank → {@code null}; trim aplicado.
	 */
	public void definirPerfilExtra(
			java.time.LocalDate dataNascimento,
			String telefone,
			String cidade,
			String bio) {
		if (dataNascimento != null) this.dataNascimento = dataNascimento;
		this.telefone = blankParaNull(telefone);
		this.cidade = blankParaNull(cidade);
		this.bio = blankParaNull(bio);
	}

	public void definirFoto(byte[] blob, String mime) {
		this.fotoBlob = blob;
		this.fotoMime = mime;
	}

	public void removerFoto() {
		this.fotoBlob = null;
		this.fotoMime = null;
	}

	public void marcarEmailVerificado() {
		this.emailVerificado = true;
	}

	public void marcarComoNaoVerificado() {
		this.emailVerificado = false;
	}

	public void marcarExcluido() {
		this.deletadoEm = java.time.Instant.now();
	}

	private static String blankParaNull(String s) {
		return (s == null || s.isBlank()) ? null : s.trim();
	}
}
