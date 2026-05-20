/**
 * Persistence adapter do módulo caixinha (Story 2.2).
 *
 * <p>Entidades JPA + repos para {@code caixinha} e {@code resultado_possivel}.
 *
 * <p><b>Dinheiro como BIGINT centavos no DB</b> — decisão arquitetural da
 * Story 2.2: armazenamento como inteiro exato (sem fricção de escala);
 * conversão {@code Money} ↔ {@code long centavos} na borda do use case via
 * {@code Money.centavos()} / {@code Money.ofCentavos(long)}.
 */
package com.caxinhabet.caixinha.adapter.persistence;
