package com.caxinhabet.auth.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CpfTest {

    @Test
    @DisplayName("CPF válido com máscara → normaliza para 11 dígitos")
    void cpfValidoComMascara() {
        // 529.982.247-25 é um CPF válido conhecido (dígitos verificadores corretos).
        assertThat(Cpf.de("529.982.247-25").digitos()).isEqualTo("52998224725");
    }

    @Test
    @DisplayName("CPF válido sem máscara → aceito")
    void cpfValidoSemMascara() {
        assertThat(Cpf.de("52998224725").digitos()).isEqualTo("52998224725");
    }

    @Test
    @DisplayName("CPF com dígito verificador errado → IllegalArgumentException")
    void cpfDigitoErrado() {
        assertThatThrownBy(() -> Cpf.de("529.982.247-24"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("CPF com todos dígitos iguais → inválido")
    void cpfDigitosRepetidos() {
        assertThatThrownBy(() -> Cpf.de("111.111.111-11"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("CPF com menos de 11 dígitos → inválido")
    void cpfCurto() {
        assertThatThrownBy(() -> Cpf.de("123")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("CPF null ou vazio → inválido")
    void cpfVazio() {
        assertThatThrownBy(() -> Cpf.de(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Cpf.de("  ")).isInstanceOf(IllegalArgumentException.class);
    }
}
