package com.jmendez.polizas.exception;

/**
 * Violación de una regla de negocio.
 *
 * <p>Se traduce a la respuesta HTTP {@code 409 Conflict}: la petición es
 * sintácticamente válida pero entra en conflicto con el estado actual del
 * agregado o con las reglas del negocio.
 */
public class ReglaNegocioException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	/**
	 * @param mensaje descripción legible de la regla violada
	 */
	public ReglaNegocioException(String mensaje) {
		super(mensaje);
	}
}
