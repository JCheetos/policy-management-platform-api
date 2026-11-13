package com.jmendez.polizas.dto;

import java.math.BigDecimal;

import com.jmendez.polizas.model.EstadoRiesgo;
import com.jmendez.polizas.model.Riesgo;

/**
 * Respuesta con la información de un riesgo asegurado.
 *
 * @param id            identificador del riesgo
 * @param polizaId      póliza a la que pertenece el riesgo
 * @param estado        estado actual del riesgo
 * @param descripcion   descripción del riesgo
 * @param valorAsegurado valor asegurado
 */
public record RiesgoResponse(
		Long id,
		Long polizaId,
		EstadoRiesgo estado,
		String descripcion,
		BigDecimal valorAsegurado) {

	/**
	 * Proyecta la entidad de dominio al contrato de salida.
	 *
	 * @param riesgo riesgo de origen
	 * @return respuesta con los datos del riesgo
	 */
	public static RiesgoResponse desde(Riesgo riesgo) {
		return new RiesgoResponse(
				riesgo.getId(),
				riesgo.getPoliza() == null ? null : riesgo.getPoliza().getId(),
				riesgo.getEstado(),
				riesgo.getDescripcion(),
				riesgo.getValorAsegurado());
	}
}
