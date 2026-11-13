package com.jmendez.polizas.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.jmendez.polizas.model.TipoPoliza;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Petición de creación de una póliza.
 *
 * @param tipo             tipo de póliza (individual o colectiva)
 * @param fechaInicio      inicio del periodo de vigencia
 * @param fechaFin         fin del periodo de vigencia
 * @param mesesVigencia    número de meses de vigencia
 * @param canonMensual     canon mensual del arrendamiento asegurado
 * @param prima            prima esperada; si se envía debe coincidir con
 *                         {@code canonMensual × mesesVigencia} (el dominio la deriva)
 * @param nombreTomador    nombre de quien contrata la póliza
 * @param nombreAsegurado  nombre de quien está asegurado
 * @param nombreBeneficiario nombre de quien recibe la indemnización
 * @param riesgos          riesgos iniciales de la póliza
 */
public record CrearPolizaRequest(

		@NotNull(message = "el tipo de la póliza es obligatorio")
		TipoPoliza tipo,

		@NotNull(message = "la fecha de inicio de la vigencia es obligatoria")
		LocalDate fechaInicio,

		@NotNull(message = "la fecha de fin de la vigencia es obligatoria")
		LocalDate fechaFin,

		@NotNull(message = "los meses de vigencia son obligatorios")
		@Min(value = 1, message = "los meses de vigencia deben ser mayores o iguales a 1")
		Integer mesesVigencia,

		@NotNull(message = "el canon mensual es obligatorio")
		@DecimalMin(value = "0.01", message = "el canon mensual debe ser mayor que cero")
		BigDecimal canonMensual,

		BigDecimal prima,

		String nombreTomador,

		String nombreAsegurado,

		String nombreBeneficiario,

		@Valid
		List<RiesgoRequest> riesgos) {

	/**
	 * @return la lista de riesgos solicitada, nunca {@code null}
	 */
	public List<RiesgoRequest> riesgos() {
		return riesgos == null ? List.of() : riesgos;
	}
}
