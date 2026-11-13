package com.jmendez.polizas.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Evento dirigido al CORE transaccional legado.
 *
 * @param evento   nombre del evento (por ejemplo {@code ACTUALIZACION})
 * @param polizaId identificador de la póliza afectada
 */
public record EventoCoreRequest(

		@NotBlank(message = "el evento es obligatorio")
		@Size(max = 50, message = "el evento admite como máximo 50 caracteres")
		@Pattern(regexp = "^[\\p{Print}]*$",
				message = "el evento no puede contener saltos de línea ni caracteres de control")
		String evento,

		@NotNull(message = "el identificador de la póliza es obligatorio")
		Long polizaId) {
}
