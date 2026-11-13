package com.jmendez.polizas.exception;

/**
 * Fallo de comunicación con el CORE transaccional legado.
 *
 * <p>La notificación al CORE es {@code best-effort}: este error se registra en
 * el log y nunca revierte la operación de negocio que la originó.
 */
public class CoreComunicacionException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	/**
	 * @param mensaje descripción legible del fallo de comunicación
	 * @param causa causa original del fallo
	 */
	public CoreComunicacionException(String mensaje, Throwable causa) {
		super(mensaje, causa);
	}
}
