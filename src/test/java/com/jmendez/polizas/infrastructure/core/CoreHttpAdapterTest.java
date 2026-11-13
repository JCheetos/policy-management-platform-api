package com.jmendez.polizas.infrastructure.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.io.IOException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.jmendez.polizas.exception.CoreComunicacionException;

/**
 * Pruebas del adaptador HTTP hacia el CORE.
 *
 * <p>Se verifica el contrato de la llamada (método, ruta, encabezado de
 * credencial y cuerpo) y el tratamiento de los fallos de comunicación.
 */
class CoreHttpAdapterTest {

	private static final String URL_CORE = "http://localhost:8080/core-mock/evento";
	private static final String API_KEY = "123456";

	private MockRestServiceServer servidor;
	private CoreHttpAdapter adaptador;

	@BeforeEach
	void setUp() {
		RestClient.Builder constructor = RestClient.builder();
		servidor = MockRestServiceServer.bindTo(constructor).build();
		adaptador = new CoreHttpAdapter(constructor.build(), URL_CORE, API_KEY);
	}

	@DisplayName("Envía el evento al CORE con la credencial compartida")
	@Test
	void enviaElEventoAlCore() {
		servidor.expect(requestTo(URL_CORE))
				.andExpect(method(HttpMethod.POST))
				.andExpect(header("x-api-key", API_KEY))
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(content().json("{\"evento\": \"RENOVACION\", \"polizaId\": 555}"))
				.andRespond(withSuccess("{\"estado\":\"REGISTRADO\"}", MediaType.APPLICATION_JSON));

		adaptador.notificarEvento("RENOVACION", 555L);

		servidor.verify();
	}

	@DisplayName("Un error del CORE se traduce a CoreComunicacionException")
	@Test
	void unErrorDelCoreSeTraduceAExcepcion() {
		servidor.expect(requestTo(URL_CORE)).andRespond(withServerError());

		assertThatThrownBy(() -> adaptador.notificarEvento("RENOVACION", 555L))
				.isInstanceOf(CoreComunicacionException.class)
				.hasMessageContaining("500");
	}

	@DisplayName("Un fallo de conexión se traduce a CoreComunicacionException")
	@Test
	void unFalloDeConexionSeTraduceAExcepcion() {
		servidor.expect(requestTo(URL_CORE)).andRespond(invocacion -> {
			throw new IOException("conexión rechazada");
		});

		assertThatThrownBy(() -> adaptador.notificarEvento("CREACION", 1L))
				.isInstanceOf(CoreComunicacionException.class)
				.hasMessageContaining("No fue posible conectar con el CORE");
	}

	@DisplayName("La notificación exitosa no propaga excepciones")
	@Test
	void laNotificacionExitosaNoPropagaExcepciones() {
		servidor.expect(requestTo(URL_CORE)).andRespond(withSuccess());

		adaptador.notificarEvento("CANCELACION", 3L);

		servidor.verify();
	}
}
