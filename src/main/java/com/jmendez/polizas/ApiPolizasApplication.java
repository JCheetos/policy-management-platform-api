package com.jmendez.polizas;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Punto de entrada de la API de Gestión de Pólizas.
 *
 * <p>Prueba técnica Davivienda - Módulo 2.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class ApiPolizasApplication {

	/**
	 * @param args argumentos de arranque de la aplicación
	 */
	public static void main(String[] args) {
		SpringApplication.run(ApiPolizasApplication.class, args);
	}
}
