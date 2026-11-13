package com.jmendez.polizas.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;

import com.jmendez.polizas.repository.PolizaRepository;
import com.sun.net.httpserver.HttpServer;

/**
 * Prueba de extremo a extremo de la notificación al CORE.
 *
 * <p>Ejercita el adaptador HTTP <em>real</em> ({@code CoreHttpAdapter}) contra
 * un servidor HTTP que simula el CORE y comprueba dos cosas:
 *
 * <ol>
 * <li>el evento llega al CORE con la credencial compartida, y</li>
 * <li>la notificación se emite <em>después</em> del commit: cuando el CORE
 * recibe el evento, la póliza ya está confirmada en la base de datos (se
 * consulta desde otra conexión y se encuentra).</li>
 * </ol>
 *
 * <p>Si la notificación se emitiera dentro de la transacción, la póliza aún no
 * estaría confirmada y la comprobación fallaría.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Sql(statements = "DELETE FROM riesgos; DELETE FROM polizas;")
class NotificacionCorePostCommitIntegrationTest {

	private static final String API_KEY = "123456";

	private static final Pattern PATRON_POLIZA_ID = Pattern.compile("\"polizaId\"\\s*:\\s*(\\d+)");

	private static HttpServer servidorCore;
	private static final List<String> cuerposRecibidos = new CopyOnWriteArrayList<>();
	private static final List<String> credencialesRecibidas = new CopyOnWriteArrayList<>();
	private static final AtomicBoolean polizaConfirmadaAlRecibir = new AtomicBoolean();
	private static final AtomicReference<PolizaRepository> repositorioDePolizas = new AtomicReference<>();

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private PolizaRepository polizaRepository;

	/**
	 * Levanta el CORE simulado y apunta la aplicación a él.
	 *
	 * @param registro registro de propiedades dinámicas de la prueba
	 * @throws IOException si el servidor simulado no puede levantarse
	 */
	@DynamicPropertySource
	static void configurarUrlDelCore(DynamicPropertyRegistry registro) throws IOException {
		servidorCore = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		servidorCore.createContext("/core-mock/evento", intercambio -> {
			String cuerpo = new String(intercambio.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
			cuerposRecibidos.add(cuerpo);
			credencialesRecibidas.add(String.valueOf(intercambio.getRequestHeaders().getFirst("x-api-key")));
			polizaConfirmadaAlRecibir.set(laPolizaYaEstabaConfirmada(cuerpo));

			byte[] respuesta = "{\"estado\":\"REGISTRADO\"}".getBytes(StandardCharsets.UTF_8);
			intercambio.getResponseHeaders().add("Content-Type", MediaType.APPLICATION_JSON_VALUE);
			intercambio.sendResponseHeaders(200, respuesta.length);
			try (OutputStream salida = intercambio.getResponseBody()) {
				salida.write(respuesta);
			}
		});
		servidorCore.start();

		registro.add("app.core.url",
				() -> "http://127.0.0.1:" + servidorCore.getAddress().getPort() + "/core-mock/evento");
	}

	@AfterAll
	static void detenerElCoreSimulado() {
		if (servidorCore != null) {
			servidorCore.stop(0);
		}
	}

	@BeforeEach
	void setUp() {
		cuerposRecibidos.clear();
		credencialesRecibidas.clear();
		polizaConfirmadaAlRecibir.set(false);
		repositorioDePolizas.set(polizaRepository);
	}

	/**
	 * Comprueba, desde otra conexión, si la póliza del evento ya estaba
	 * confirmada en la base de datos cuando el CORE recibió la notificación.
	 *
	 * @param cuerpo cuerpo de la petición recibida por el CORE
	 * @return {@code true} si la póliza ya era visible para otras conexiones
	 */
	private static boolean laPolizaYaEstabaConfirmada(String cuerpo) {
		Matcher matcher = PATRON_POLIZA_ID.matcher(cuerpo);
		if (!matcher.find()) {
			return false;
		}
		PolizaRepository repositorio = repositorioDePolizas.get();
		return repositorio != null && repositorio.findById(Long.valueOf(matcher.group(1))).isPresent();
	}

	@DisplayName("La notificación real al CORE se emite después del commit de la póliza")
	@Test
	void laNotificacionAlCoreSeEmiteDespuesDelCommit() throws Exception {
		String respuesta = mockMvc.perform(post("/polizas")
				.header("x-api-key", API_KEY)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "tipo": "COLECTIVA",
						  "fechaInicio": "2026-01-01",
						  "fechaFin": "2026-12-31",
						  "mesesVigencia": 12,
						  "canonMensual": 2500000.00,
						  "riesgos": [{"descripcion": "Incendio", "valorAsegurado": 80000000.00}]
						}
						"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.estado").value("VIGENTE"))
				.andReturn().getResponse().getContentAsString();

		String id = respuesta.replaceAll(".*\"id\":(\\d+).*", "$1");

		assertThat(cuerposRecibidos).hasSize(1);
		assertThat(cuerposRecibidos.get(0)).contains("\"evento\":\"CREACION\"");
		assertThat(cuerposRecibidos.get(0)).contains("\"polizaId\":" + id);
		assertThat(credencialesRecibidas).containsExactly(API_KEY);
		assertThat(polizaConfirmadaAlRecibir)
				.as("la póliza debe estar confirmada en la base de datos cuando el CORE recibe el evento")
				.isTrue();
	}
}
