package com.jmendez.polizas.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.slf4j.LoggerFactory;

/**
 * Pruebas del mock del CORE.
 *
 * <p>Se verifica el comportamiento observable del endpoint (respuesta HTTP) y el
 * registro en logs que es el único propósito del mock.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CoreMockControllerTest {

	private static final String API_KEY = "123456";

	@Autowired
	private MockMvc mockMvc;

	private Logger logger;
	private ListAppender<ILoggingEvent> registros;

	@BeforeEach
	void setUp() {
		logger = (Logger) LoggerFactory.getLogger(CoreMockController.class);
		registros = new ListAppender<>();
		registros.start();
		logger.addAppender(registros);
	}

	@AfterEach
	void tearDown() {
		logger.detachAppender(registros);
		registros.stop();
	}

	@DisplayName("POST /core-mock/evento responde 200 con el acuse de recibo")
	@Test
	void registrarEventoResponde200() throws Exception {
		mockMvc.perform(post("/core-mock/evento")
				.header("x-api-key", API_KEY)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"evento": "ACTUALIZACION", "polizaId": 555}
						"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.estado").value("REGISTRADO"))
				.andExpect(jsonPath("$.evento").value("ACTUALIZACION"))
				.andExpect(jsonPath("$.polizaId").value(555));
	}

	@DisplayName("POST /core-mock/evento registra en el log el intento de envío al CORE")
	@Test
	void registrarEventoRegistraElIntentoEnElLog() throws Exception {
		mockMvc.perform(post("/core-mock/evento")
				.header("x-api-key", API_KEY)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"evento": "ACTUALIZACION", "polizaId": 555}
						"""))
				.andExpect(status().isOk());

		List<String> mensajes = registros.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
		assertThat(mensajes).anySatisfy(mensaje -> assertThat(mensaje)
				.isEqualTo("Intento de envío al CORE: evento=ACTUALIZACION polizaId=555"));
	}

	@DisplayName("POST /core-mock/evento sin api-key devuelve 401")
	@Test
	void registrarEventoSinApiKeyDevuelve401() throws Exception {
		mockMvc.perform(post("/core-mock/evento")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"evento": "ACTUALIZACION", "polizaId": 555}
						"""))
				.andExpect(status().isUnauthorized());
	}

	@DisplayName("POST /core-mock/evento sin campos obligatorios devuelve 400")
	@Test
	void registrarEventoSinCamposDevuelve400() throws Exception {
		mockMvc.perform(post("/core-mock/evento")
				.header("x-api-key", API_KEY)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"evento": "", "polizaId": null}
						"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.erroresCampos.evento").exists())
				.andExpect(jsonPath("$.erroresCampos.polizaId").exists());
	}

	@DisplayName("POST /core-mock/evento con saltos de línea en el evento devuelve 400")
	@Test
	void registrarEventoConSaltosDeLineaDevuelve400() throws Exception {
		mockMvc.perform(post("/core-mock/evento")
				.header("x-api-key", API_KEY)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"evento": "ACTUALIZACION\\nINFO  usuario comprometido", "polizaId": 555}
						"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.erroresCampos.evento").exists());
	}

	@DisplayName("POST /core-mock/evento con un evento demasiado largo devuelve 400")
	@Test
	void registrarEventoDemasiadoLargoDevuelve400() throws Exception {
		mockMvc.perform(post("/core-mock/evento")
				.header("x-api-key", API_KEY)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"evento": "%s", "polizaId": 555}
						""".formatted("E".repeat(51))))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.erroresCampos.evento").exists());
	}
}
