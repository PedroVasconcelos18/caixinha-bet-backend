package com.caxinhabet.caixinha.adapter.web;

import com.caxinhabet.shared.money.Money;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

/**
 * Payload de {@code POST /caixinhas} (Story 2.2, AC-1/AC-2).
 *
 * <p>Bean Validation cuida das validações de campo individuais (400 se
 * falhar — Spring MVC default ProblemDetail). Validações cross-field
 * ({@code dataApuracao > prazoEntrada}, {@code valorIngresso >= R$ 5})
 * vão para {@code NovaCaixinhaSpec.validar()} no use case (422 +
 * {@code violations}).
 *
 * <p>camelCase 1:1 com a API (regra dura do AGENTS.md). {@code Money}
 * é desserializado como string decimal (NFR-1).
 */
public record CriarCaixinhaRequest(
		@NotBlank @Size(max = 120) String titulo,
		@NotBlank @Size(max = 80) String ladoA,
		@NotBlank @Size(max = 80) String ladoB,
		@NotNull Money valorIngresso,
		@Min(2) int minimoParticipantes,
		@NotNull Instant prazoEntrada,
		@NotNull Instant dataApuracao,
		@NotNull @Size(min = 2) List<@NotBlank @Size(max = 120) String> rotulosResultados,
		@Valid List<@Email String> emailsConvidados) {}
