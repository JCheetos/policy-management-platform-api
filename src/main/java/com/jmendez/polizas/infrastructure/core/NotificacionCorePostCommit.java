package com.jmendez.polizas.infrastructure.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.jmendez.polizas.port.CoreGateway;
import com.jmendez.polizas.port.NotificacionCore;

/**
 * Implementación del puerto {@link NotificacionCore} que difiere el envío al
 * CORE hasta después del commit de la transacción.
 *
 * <p>Problema que resuelve: si la notificación se emite dentro de la
 * transacción, el CORE recibe el evento <em>antes</em> de que la base de datos
 * confirme el cambio. Si el commit falla, el CORE ya actuó sobre un estado que
 * nunca existió y además se retiene la conexión y el hilo de la petición
 * durante la llamada HTTP.
 *
 * <p>Con esta implementación el envío se registra como sincronización
 * {@code afterCommit}: solo se habla con el CORE cuando la operación de
 * negocio está confirmada, y una reversión (rollback) no genera ninguna
 * notificación.
 *
 * <p>Mejora pendiente para producción (documentada en el README): marcar el
 * envío como asíncrono ({@code @Async} con cola de mensajes o *outbox*) para
 * no bloquear el hilo de la petición con la latencia del CORE.
 */
public class NotificacionCorePostCommit implements NotificacionCore {

	private static final Logger LOG = LoggerFactory.getLogger(NotificacionCorePostCommit.class);

	private final CoreGateway coreGateway;

	/**
	 * @param coreGateway transporte de salida hacia el CORE
	 */
	public NotificacionCorePostCommit(CoreGateway coreGateway) {
		this.coreGateway = coreGateway;
	}

	@Override
	public void notificar(String evento, Long polizaId) {
		if (TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void afterCommit() {
					enviar(evento, polizaId);
				}
			});
			LOG.debug("Notificación al CORE diferida hasta el commit: evento={} polizaId={}", evento, polizaId);
			return;
		}
		enviar(evento, polizaId);
	}

	/**
	 * Envía el evento al CORE aplicando la política {@code best-effort}: el
	 * fallo se registra pero no se propaga, porque la operación de negocio ya
	 * está confirmada y el CORE es un sistema externo que no puede decidir sobre
	 * el estado de la póliza.
	 *
	 * @param evento   nombre del evento
	 * @param polizaId identificador de la póliza afectada
	 */
	private void enviar(String evento, Long polizaId) {
		try {
			coreGateway.notificarEvento(evento, polizaId);
		} catch (Exception excepcion) {
			LOG.error("No fue posible notificar al CORE el evento {} de la póliza {}. "
					+ "La operación de negocio ya está confirmada y no se revierte.", evento, polizaId, excepcion);
		}
	}
}
