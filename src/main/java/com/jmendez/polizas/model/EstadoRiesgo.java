package com.jmendez.polizas.model;

/**
 * Estados posibles de un riesgo asegurado dentro de una póliza.
 *
 * <p>Transición válida: {@code ACTIVO -> CANCELADO}. La cancelación de la póliza
 * arrastra la cancelación de todos sus riesgos.
 */
public enum EstadoRiesgo {

	/** Riesgo vigente. */
	ACTIVO,

	/** Riesgo cancelado. */
	CANCELADO
}
