package com.caxinhabet.auth.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Token opaco do magic link (Story 2.1).
 *
 * <p>Geração: 32 bytes de {@link SecureRandom} codificados em Base64URL sem
 * padding (43 chars). Entropia &gt; 256 bits — colisão por busca exaustiva
 * é computacionalmente inviável.
 *
 * <p>Persistência: o {@code valor} cru NÃO é persistido. O que vai para o
 * Postgres é {@link #hash()}, SHA-256 hex (64 chars). Roubo do banco não
 * dá ao atacante o token usável; o token cru existe apenas na URL do
 * link enviado por e-mail.
 *
 * <p><b>Anti-padrão proibido:</b> persistir o token cru "para conveniência".
 * Quebra a propriedade de defesa em profundidade contra leak do banco.
 */
public final class TokenAcesso {

	private static final SecureRandom RNG = new SecureRandom();
	private static final int TAMANHO_BYTES = 32;

	private final String valor;

	private TokenAcesso(String valor) {
		this.valor = valor;
	}

	/** Cria um token novo aleatório (43 chars Base64URL sem padding). */
	public static TokenAcesso gerar() {
		byte[] bytes = new byte[TAMANHO_BYTES];
		RNG.nextBytes(bytes);
		String b64 = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
		return new TokenAcesso(b64);
	}

	/** Reconstrói um token a partir de uma string recebida (do query param). */
	public static TokenAcesso de(String valor) {
		if (valor == null || valor.isBlank()) {
			throw new IllegalArgumentException("token vazio");
		}
		return new TokenAcesso(valor);
	}

	/** Valor cru do token (sai do servidor apenas no link do e-mail). */
	public String valor() {
		return valor;
	}

	/**
	 * Hash SHA-256 hex (64 chars) — é o que vai para o banco. Determinístico:
	 * mesmo token → mesmo hash sempre.
	 */
	public String hash() {
		try {
			MessageDigest md = MessageDigest.getInstance("SHA-256");
			byte[] digest = md.digest(valor.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 indisponível na JVM", e);
		}
	}
}
