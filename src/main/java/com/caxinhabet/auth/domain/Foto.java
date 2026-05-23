package com.caxinhabet.auth.domain;

import java.util.Set;

/**
 * Foto de avatar validada — VO do perfil (Minha Conta, 2026-05).
 *
 * <p>Persistida como BYTEA + mime na tabela {@code usuario}. Limite de 2 MB
 * é defesa em profundidade (Spring multipart também tem limite no YAML).
 */
public final class Foto {

    public static final long TAMANHO_MAX_BYTES = 2L * 1024L * 1024L;

    private static final Set<String> MIMES_ACEITOS =
            Set.of("image/jpeg", "image/png", "image/webp");

    private final byte[] bytes;
    private final String mime;

    private Foto(byte[] bytes, String mime) {
        this.bytes = bytes;
        this.mime = mime;
    }

    public static Foto de(byte[] bytes, String mime) {
        if (bytes == null || bytes.length == 0) {
            throw new ArquivoInvalidoException("Arquivo vazio");
        }
        if (bytes.length > TAMANHO_MAX_BYTES) {
            throw new ArquivoMuitoGrandeException(
                    "Arquivo excede o tamanho máximo de 2 MB");
        }
        if (mime == null || !MIMES_ACEITOS.contains(mime.toLowerCase())) {
            throw new ArquivoInvalidoException(
                    "Tipo de arquivo inválido — aceitos: jpeg, png, webp");
        }
        return new Foto(bytes, mime.toLowerCase());
    }

    public byte[] bytes() {
        return bytes;
    }

    public String mime() {
        return mime;
    }
}
