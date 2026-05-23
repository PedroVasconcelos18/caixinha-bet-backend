package com.caxinhabet.auth.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class DataNascimentoTest {

    private final Clock clockFixo = Clock.fixed(
            LocalDate.of(2026, 5, 23).atStartOfDay(ZoneId.of("UTC")).toInstant(),
            ZoneId.of("UTC"));

    @Test
    void aceitaDataComExatamente18Anos() {
        LocalDate d = LocalDate.of(2008, 5, 23);
        assertThat(DataNascimento.de(d, clockFixo).valor()).isEqualTo(d);
    }

    @Test
    void rejeitaMenorDe18() {
        LocalDate d = LocalDate.of(2008, 5, 24);
        assertThatThrownBy(() -> DataNascimento.de(d, clockFixo))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("18");
    }

    @Test
    void rejeitaDataFutura() {
        LocalDate d = LocalDate.of(2030, 1, 1);
        assertThatThrownBy(() -> DataNascimento.de(d, clockFixo))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejeitaNull() {
        assertThatThrownBy(() -> DataNascimento.de(null, clockFixo))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
