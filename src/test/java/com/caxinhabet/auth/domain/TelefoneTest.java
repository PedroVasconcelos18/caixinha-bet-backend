package com.caxinhabet.auth.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class TelefoneTest {

    @Test
    void mascaradoNormalizaParaDigitos() {
        assertThat(Telefone.de("(11) 98765-4321").digitos()).isEqualTo("11987654321");
    }

    @Test
    void aceitaFixo10Digitos() {
        assertThat(Telefone.de("11 3456-7890").digitos()).isEqualTo("1134567890");
    }

    @Test
    void rejeitaCurto() {
        assertThatThrownBy(() -> Telefone.de("123"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejeitaLongo() {
        assertThatThrownBy(() -> Telefone.de("123456789012"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void blankRetornaNull() {
        assertThat(Telefone.deOpcional("  ")).isNull();
        assertThat(Telefone.deOpcional(null)).isNull();
    }
}
