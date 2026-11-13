package com.jmendez.polizas.model;

/**
 * Estados posibles de una póliza dentro de su ciclo de vida.
 *
 * <p>Transición válida: {@code VIGENTE -> RENOVADA} (renovación),
 * {@code VIGENTE|RENOVADA -> CANCELADA} (cancelación). Una póliza cancelada
 * es terminal: no admite renovación.
 */
public enum EstadoPoliza {

	/** Póliza activa y en curso. */
	VIGENTE,

	/** Póliza renovada (canon y prima ajustados por el IPC). */
	RENOVADA,

	/** Póliza cancelada (estado terminal). */
	CANCELADA
}
