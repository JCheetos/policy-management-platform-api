package com.jmendez.polizas.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Petición de alta de un riesgo asegurado.
 *
 * @param descripcion     descripción del riesgo cubierto
 * @param valorAsegurado valor asegurado del riesgo
 */
public record RiesgoRequest(

		@NotBlank(message = "la descripción del riesgo es obligatoria")
		@Size(max = 500, message = "la descripción del riesgo admite como máximo 500 caracteres")
		String descripcion,

		@NotNull(message = "el valor asegurado es obligatorio")
		@DecimalMin(value = "0.01", message = "el valor asegurado debe ser mayor que cero")
		@Digits(integer = 17, fraction = 2,
				message = "el valor asegurado admite como máximo 17 dígitos enteros y 2 decimales")
		BigDecimal valorAsegurado) {
}
