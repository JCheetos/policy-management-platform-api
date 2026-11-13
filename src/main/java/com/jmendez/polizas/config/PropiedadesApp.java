package com.jmendez.polizas.config;

import java.math.BigDecimal;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

/**
 * Propiedades de la aplicación agrupadas bajo el prefijo {@code app}.
 *
 * <ul>
 * <li>{@code app.api-key}: credencial exigida en el encabezado {@code x-api-key}.</li>
 * <li>{@code app.ipc-porcentaje}: incremento porcentual aplicado al canon en la renovación.</li>
 * <li>{@code app.core.*}: datos de conexión con el CORE transaccional legado.</li>
 * </ul>
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "app")
public class PropiedadesApp {

	/** Credencial obligatoria en el encabezado {@code x-api-key}. */
	private String apiKey = "123456";

	/** Incremento porcentual del IPC aplicado en la renovación de pólizas. */
	private BigDecimal ipcPorcentaje = new BigDecimal("3.5");

	/** Datos de conexión con el CORE transaccional legado. */
	private Core core = new Core();

	/**
	 * Configuración del CORE transaccional legado.
	 */
	@Getter
	@Setter
	public static class Core {

		/** URL del endpoint de eventos del CORE. */
		private String url = "http://localhost:8080/core-mock/evento";

		/** Tiempo máximo de espera para conectar con el CORE (segundos). */
		private Integer connectTimeoutSegundos = 2;

		/** Tiempo máximo de espera de la respuesta del CORE (segundos). */
		private Integer readTimeoutSegundos = 3;
	}
}
