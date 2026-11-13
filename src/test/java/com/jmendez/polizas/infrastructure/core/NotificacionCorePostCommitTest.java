package com.jmendez.polizas.infrastructure.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.jmendez.polizas.port.CoreGateway;
import com.jmendez.polizas.port.NotificacionCore;

/**
 * Pruebas del adaptador que difiere la notificación al CORE hasta después del
 * commit de la transacción.
 *
 * <p>Se verifica el comportamiento observable: dentro de una transacción el
 * CORE todavía no ha recibido nada, y solo se habla con él cuando la
 * transacción se confirma.
 */
@ExtendWith(MockitoExtension.class)
class NotificacionCorePostCommitTest {

	@Mock
	private CoreGateway coreGateway;

	private NotificacionCore notificacionCore;

	@BeforeEach
	void setUp() {
		notificacionCore = new NotificacionCorePostCommit(coreGateway);
	}

	@AfterEach
	void tearDown() {
		if (TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.clearSynchronization();
		}
	}

	/**
	 * Confirma las sincronizaciones registradas, simulando el commit de la
	 * transacción.
	 */
	private void confirmarCommit() {
		for (TransactionSynchronization sincronizacion : TransactionSynchronizationManager.getSynchronizations()) {
			sincronizacion.afterCommit();
		}
	}

	@DisplayName("Dentro de la transacción el CORE todavía no recibe el evento")
	@Test
	void dentroDeLaTransaccionElCoreNoRecibeElEvento() {
		TransactionSynchronizationManager.initSynchronization();

		notificacionCore.notificar("CREACION", 1L);

		verify(coreGateway, never()).notificarEvento(any(), any());
	}

	@DisplayName("Tras el commit el CORE recibe el evento")
	@Test
	void trasElCommitElCoreRecibeElEvento() {
		TransactionSynchronizationManager.initSynchronization();
		notificacionCore.notificar("CREACION", 1L);

		confirmarCommit();

		verify(coreGateway).notificarEvento("CREACION", 1L);
	}

	@DisplayName("Una reversión (rollback) no notifica nada al CORE")
	@Test
	void unRollbackNoNotificaAlCore() {
		TransactionSynchronizationManager.initSynchronization();
		notificacionCore.notificar("RENOVACION", 7L);

		// En un rollback no se invocan las sincronizaciones afterCommit.
		assertThat(TransactionSynchronizationManager.getSynchronizations()).hasSize(1);
		verify(coreGateway, never()).notificarEvento(any(), any());
	}

	@DisplayName("Sin transacción activa la notificación se envía de inmediato")
	@Test
	void sinTransaccionActivaLaNotificacionEsInmediata() {
		notificacionCore.notificar("CANCELACION", 3L);

		verify(coreGateway).notificarEvento("CANCELACION", 3L);
	}

	@DisplayName("Un fallo del CORE tras el commit no revierte la operación de negocio")
	@Test
	void unFalloDelCoreNoSePropaga() {
		TransactionSynchronizationManager.initSynchronization();
		doThrow(new IllegalStateException("CORE no disponible")).when(coreGateway).notificarEvento(any(), any());
		notificacionCore.notificar("ALTA_RIESGO", 5L);

		assertThatCode(this::confirmarCommit).doesNotThrowAnyException();
	}
}
