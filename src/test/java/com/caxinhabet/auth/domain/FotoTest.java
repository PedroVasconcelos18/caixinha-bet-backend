package com.caxinhabet.auth.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class FotoTest {

    @Test
    void aceitaJpegPequeno() {
        Foto f = Foto.de(new byte[] {1, 2, 3}, "image/jpeg");
        assertThat(f.mime()).isEqualTo("image/jpeg");
        assertThat(f.bytes()).hasSize(3);
    }

    @Test
    void aceitaPngEWebp() {
        assertThat(Foto.de(new byte[]{1}, "image/png").mime()).isEqualTo("image/png");
        assertThat(Foto.de(new byte[]{1}, "image/webp").mime()).isEqualTo("image/webp");
    }

    @Test
    void rejeitaMimeInvalido() {
        assertThatThrownBy(() -> Foto.de(new byte[]{1}, "image/gif"))
                .isInstanceOf(ArquivoInvalidoException.class)
                .hasMessageContaining("Tipo");
    }

    @Test
    void rejeitaMaiorQue2MB() {
        byte[] big = new byte[2 * 1024 * 1024 + 1];
        assertThatThrownBy(() -> Foto.de(big, "image/jpeg"))
                .isInstanceOf(ArquivoMuitoGrandeException.class)
                .hasMessageContaining("tamanho");
    }

    @Test
    void rejeitaBytesVazios() {
        assertThatThrownBy(() -> Foto.de(new byte[0], "image/jpeg"))
                .isInstanceOf(ArquivoInvalidoException.class);
    }
}
