package com.jmendez.polizas.exception;

/**
 * La póliza solicitada no existe.
 */
public class PolizaNoEncontradaException extends RecursoNoEncontradoException {

	private static final long serialVersionUID = 1L;

	/**
	 * @param id identificador de la póliza solicitada
	 */
	public PolizaNoEncontradaException(Long id) {
		super("No existe una póliza con el identificador " + id + ".");
	}
}
