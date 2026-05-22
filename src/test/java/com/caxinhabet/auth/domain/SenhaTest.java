package com.caxinhabet.auth.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SenhaTest {

    @Test
    @DisplayName("Senha com 8+ chars, letras e números → aceita")
    void senhaForte() {
        assertThat(Senha.crua("senha1234").valor()).isEqualTo("senha1234");
    }

    @Test
    @DisplayName("Senha com menos de 8 chars → rejeitada")
    void senhaCurta() {
        assertThatThrownBy(() -> Senha.crua("ab12"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Senha só com letras (sem número) → rejeitada")
    void senhaSemNumero() {
        assertThatThrownBy(() -> Senha.crua("apenasletras"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Senha só com números (sem letra) → rejeitada")
    void senhaSemLetra() {
        assertThatThrownBy(() -> Senha.crua("12345678"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Senha null ou vazia → rejeitada")
    void senhaVazia() {
        assertThatThrownBy(() -> Senha.crua(null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
