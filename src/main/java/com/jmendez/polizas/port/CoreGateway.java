package com.jmendez.polizas.port;

/**
 * Puerto de salida hacia el CORE transaccional legado.
 *
 * <p>Es la única abstracción que el dominio necesita para notificar eventos al
 * CORE. El adaptador por defecto es HTTP ({@code infrastructure.core.CoreHttpAdapter}),
 * pero el dominio no depende de él: se puede sustituir por un doble de prueba
 * o por otro transporte sin tocar las reglas de negocio.
 */
public interface CoreGateway {

	/**
	 * Notifica al CORE que una operación de negocio se ejecutó.
	 *
	 * @param evento  nombre del evento (por ejemplo {@code CREACION}, {@code RENOVACION})
	 * @param polizaId identificador de la póliza afectada
	 */
	void notificarEvento(String evento, Long polizaId);
}
