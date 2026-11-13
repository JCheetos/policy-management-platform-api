package com.jmendez.polizas.port;

/**
 * Puerto de salida para notificar al CORE los eventos de dominio.
 *
 * <p>Distingue dos responsabilidades que antes estaban mezcladas:
 * <ul>
 * <li>{@link CoreGateway} define <em>cómo</em> se habla con el CORE
 * (transporte HTTP).</li>
 * <li>Este puerto define <em>cuándo</em> se habla con el CORE: la
 * implementación por defecto
 * ({@code infrastructure.core.NotificacionCorePostCommit}) difiere el envío
 * hasta después del commit de la transacción, de modo que el CORE nunca
 * reciba un evento de una operación que finalmente se revierte.</li>
 * </ul>
 *
 * <p>La política es {@code best-effort}: un fallo de comunicación queda
 * registrado en el log y no propaga el error, porque la operación de negocio ya
 * está confirmada en ese momento.
 */
public interface NotificacionCore {

	/**
	 * Notifica al CORE que una operación de negocio se ejecutó.
	 *
	 * <p>La implementación por defecto no propaga fallos de comunicación.
	 *
	 * @param evento   nombre del evento (por ejemplo {@code CREACION}, {@code RENOVACION})
	 * @param polizaId identificador de la póliza afectada
	 */
	void notificar(String evento, Long polizaId);
}
