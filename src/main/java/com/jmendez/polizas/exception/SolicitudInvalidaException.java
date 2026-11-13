package com.jmendez.polizas.exception;

/**
 * Petición inconsistente o semánticamente inválida (por ejemplo, un periodo de
 * vigencia con fecha fin anterior a la fecha inicio).
 *
 * <p>Se traduce a la respuesta HTTP {@code 400 Bad Request}.
 */
public class SolicitudInvalidaException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	/**
	 * @param mensaje descripción legible del problema detectado
	 */
	public SolicitudInvalidaException(String mensaje) {
		super(mensaje);
	}
}
