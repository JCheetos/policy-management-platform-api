package com.jmendez.polizas.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Petición de alta de un riesgo asegurado.
 *
 * @param descripcion     descripción del riesgo cubierto
 * @param valorAsegurado valor asegurado del riesgo
 */
public record RiesgoRequest(

		@NotBlank(message = "la descripción del riesgo es obligatoria")
		String descripcion,

		@NotNull(message = "el valor asegurado es obligatorio")
		@DecimalMin(value = "0.01", message = "el valor asegurado debe ser mayor que cero")
		BigDecimal valorAsegurado) {
}
