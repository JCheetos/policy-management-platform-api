package com.jmendez.polizas.exception;

/**
 * El riesgo solicitado no existe.
 */
public class RiesgoNoEncontradoException extends RecursoNoEncontradoException {

	private static final long serialVersionUID = 1L;

	/**
	 * @param id identificador del riesgo solicitado
	 */
	public RiesgoNoEncontradoException(Long id) {
		super("No existe un riesgo con el identificador " + id + ".");
	}
}
