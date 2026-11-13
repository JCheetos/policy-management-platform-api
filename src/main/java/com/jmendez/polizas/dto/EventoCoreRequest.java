package com.jmendez.polizas.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Evento dirigido al CORE transaccional legado.
 *
 * @param evento   nombre del evento (por ejemplo {@code ACTUALIZACION})
 * @param polizaId identificador de la póliza afectada
 */
public record EventoCoreRequest(

		@NotBlank(message = "el evento es obligatorio")
		String evento,

		@NotNull(message = "el identificador de la póliza es obligatorio")
		Long polizaId) {
}
