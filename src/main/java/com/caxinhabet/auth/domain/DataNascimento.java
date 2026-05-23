package com.caxinhabet.auth.domain;

import java.time.Clock;
import java.time.LocalDate;
import java.time.Period;

/**
 * Data de nascimento validada — VO do módulo auth (Minha Conta, 2026-05).
 *
 * <p>Aposta exige +18 anos (PRD). Não-futura por definição. O {@link Clock}
 * é injetado para o teste poder fixar a data de "hoje".
 */
public final class DataNascimento {

    private final LocalDate valor;

    private DataNascimento(LocalDate valor) {
        this.valor = valor;
    }

    public static DataNascimento de(LocalDate bruto) {
        return de(bruto, Clock.systemDefaultZone());
    }

    public static DataNascimento de(LocalDate bruto, Clock clock) {
        if (bruto == null) {
            throw new IllegalArgumentException("Data de nascimento é obrigatória");
        }
        LocalDate hoje = LocalDate.now(clock);
        if (bruto.isAfter(hoje)) {
            throw new IllegalArgumentException("Data de nascimento não pode ser futura");
        }
        int idade = Period.between(bruto, hoje).getYears();
        if (idade < 18) {
            throw new IllegalArgumentException("É necessário ter 18 anos ou mais");
        }
        return new DataNascimento(bruto);
    }

    public LocalDate valor() {
        return valor;
    }
}
