package com.jmendez.polizas.dto;

/**
 * Acuse de recibo del mock del CORE.
 *
 * @param estado   resultado del registro del evento
 * @param evento   nombre del evento registrado
 * @param polizaId identificador de la póliza asociada
 */
public record EventoCoreResponse(String estado, String evento, Long polizaId) {
}
