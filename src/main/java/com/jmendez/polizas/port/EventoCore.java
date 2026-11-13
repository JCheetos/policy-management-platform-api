package com.jmendez.polizas.port;

/**
 * Evento de dominio notificado al CORE transaccional legado.
 *
 * @param evento   nombre del evento
 * @param polizaId identificador de la póliza afectada
 */
public record EventoCore(String evento, Long polizaId) {
}
