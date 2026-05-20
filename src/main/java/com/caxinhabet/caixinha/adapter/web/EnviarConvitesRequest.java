package com.caxinhabet.caixinha.adapter.web;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Payload de {@code POST /caixinhas/{id}/convites} (Story 2.4).
 *
 * <p>{@code @Size(max=50)}: defesa simples contra abuso. Uso real
 * esperado é bolão de 5-20 amigos; 50 dá folga.
 */
public record EnviarConvitesRequest(
		@NotNull @Size(min = 1, max = 50)
				List<@Email @NotBlank String> emails) {}
