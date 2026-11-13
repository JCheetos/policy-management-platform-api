package com.jmendez.polizas.config;

import java.time.Duration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import com.jmendez.polizas.infrastructure.core.CoreHttpAdapter;
import com.jmendez.polizas.port.CoreGateway;

/**
 * Configuración del adaptador de salida hacia el CORE transaccional legado.
 */
@Configuration
public class ConfiguracionCore {

	/**
	 * Construye el adaptador HTTP del CORE con tiempos de espera acotados para
	 * que una caída del CORE no agote los hilos de la aplicación.
	 *
	 * @param propiedades propiedades de la aplicación
	 * @return implementación del puerto {@link CoreGateway}
	 */
	@Bean
	public CoreGateway coreGateway(PropiedadesApp propiedades) {
		SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
		fabrica.setConnectTimeout(Duration.ofSeconds(propiedades.getCore().getConnectTimeoutSegundos()));
		fabrica.setReadTimeout(Duration.ofSeconds(propiedades.getCore().getReadTimeoutSegundos()));

		RestClient cliente = RestClient.builder()
				.requestFactory(fabrica)
				.build();

		return new CoreHttpAdapter(cliente, propiedades.getCore().getUrl(), propiedades.getApiKey());
	}
}
