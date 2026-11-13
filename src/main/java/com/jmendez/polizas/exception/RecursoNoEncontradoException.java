package com.jmendez.polizas.exception;

/**
 * El recurso solicitado (póliza o riesgo) no existe.
 *
 * <p>Se traduce a la respuesta HTTP {@code 404 Not Found}.
 */
public class RecursoNoEncontradoException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	/**
	 * @param mensaje descripción legible del recurso ausente
	 */
	public RecursoNoEncontradoException(String mensaje) {
		super(mensaje);
	}
}
