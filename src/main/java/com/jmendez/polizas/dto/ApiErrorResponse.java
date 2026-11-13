package com.jmendez.polizas.dto;

import java.time.Instant;
import java.util.Map;

/**
 * Estructura estándar de error de la API.
 *
 * @param timestamp    momento en que se generó el error
 * @param status       código de estado HTTP
 * @param error        razón textual del estado HTTP
 * @param mensaje      descripción legible del problema
 * @param ruta         ruta de la petición que originó el error
 * @param erroresCampos errores de validación por campo (vacío si no aplica)
 */
public record ApiErrorResponse(
		Instant timestamp,
		int status,
		String error,
		String mensaje,
		String ruta,
		Map<String, String> erroresCampos) {

	/**
	 * Construye un error sin detalle de campos.
	 *
	 * @param status  código de estado HTTP
	 * @param error   razón textual del estado HTTP
	 * @param mensaje descripción legible del problema
	 * @param ruta    ruta de la petición
	 * @return estructura de error
	 */
	public static ApiErrorResponse de(int status, String error, String mensaje, String ruta) {
		return new ApiErrorResponse(Instant.now(), status, error, mensaje, ruta, Map.of());
	}

	/**
	 * Construye un error con detalle de validación por campo.
	 *
	 * @param status        código de estado HTTP
	 * @param error         razón textual del estado HTTP
	 * @param mensaje       descripción legible del problema
	 * @param ruta          ruta de la petición
	 * @param erroresCampos errores de validación por campo
	 * @return estructura de error
	 */
	public static ApiErrorResponse de(int status, String error, String mensaje, String ruta,
			Map<String, String> erroresCampos) {
		return new ApiErrorResponse(Instant.now(), status, error, mensaje, ruta, erroresCampos);
	}
}
