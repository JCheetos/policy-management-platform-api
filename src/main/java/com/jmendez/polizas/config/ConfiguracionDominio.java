package com.jmendez.polizas.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.jmendez.polizas.domain.ReglasPoliza;

/**
 * Configuración del núcleo de negocio.
 *
 * <p>La clase {@link ReglasPoliza} no depende de Spring: se expone como bean
 * para inyectarla en los servicios y mantener el dominio desacoplado del
 * framework.
 */
@Configuration
public class ConfiguracionDominio {

	/**
	 * @return instancia de las reglas de negocio de la póliza
	 */
	@Bean
	public ReglasPoliza reglasPoliza() {
		return new ReglasPoliza();
	}
}
