package com.jmendez.polizas.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;

import com.jmendez.polizas.port.CoreGateway;
import com.jmendez.polizas.service.PolizaService;
import com.jmendez.polizas.service.RiesgoService;

/**
 * Pruebas de extremo a extremo sobre la aplicación completa.
 *
 * <p>Ejercitan el flujo real: controlador → servicio → reglas → repositorio
 * (H2) → notificación al CORE (puerto sustituido por un doble de prueba).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Sql(statements = "DELETE FROM riesgos; DELETE FROM polizas;")
class ApiPolizasIntegrationTest {

	private static final String API_KEY = "123456";

	@Autowired
	private MockMvc mockMvc;

	@MockBean
	private CoreGateway coreGateway;

	@Autowired
	private PolizaService polizaService;

	@Autowired
	private RiesgoService riesgoService;

	private String cuerpoPolizaColectiva() {
		return """
				{
				  "tipo": "COLECTIVA",
				  "fechaInicio": "2026-01-01",
				  "fechaFin": "2026-12-31",
				  "mesesVigencia": 12,
				  "canonMensual": 2500000.00,
				  "nombreTomador": "Inmobiliaria Andes",
				  "nombreAsegurado": "Arrendatarios Torre Norte",
				  "nombreBeneficiario": "Juan Pérez",
				  "riesgos": [
				    {"descripcion": "Incendio torre norte", "valorAsegurado": 80000000.00}
				  ]
				}
				""";
	}


	/**
	 * Lee un identificador numérico del cuerpo de una respuesta JSON.
	 *
	 * @param json  cuerpo de la respuesta
	 * @param ruta  ruta JSON del identificador
	 * @return identificador leído
	 */
	private Long leerId(String json, String ruta) {
		return ((Number) com.jayway.jsonpath.JsonPath.read(json, ruta)).longValue();
	}

	@DisplayName("Flujo completo: crear, listar, agregar riesgo, renovar y cancelar una póliza colectiva")
	@Test
	void flujoCompletoDePolizaColectiva() throws Exception {
		String respuestaCrear = mockMvc.perform(post("/polizas")
				.header("x-api-key", API_KEY)
				.contentType(MediaType.APPLICATION_JSON)
				.content(cuerpoPolizaColectiva()))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.estado").value("VIGENTE"))
				.andExpect(jsonPath("$.prima").value(30000000.00))
				.andReturn().getResponse().getContentAsString();

		Long polizaId = leerId(respuestaCrear, "$.id");

		mockMvc.perform(get("/polizas").param("tipo", "COLECTIVA").header("x-api-key", API_KEY))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1));

		mockMvc.perform(get("/polizas/" + polizaId + "/riesgos").header("x-api-key", API_KEY))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].estado").value("ACTIVO"));

		mockMvc.perform(post("/polizas/" + polizaId + "/riesgos")
				.header("x-api-key", API_KEY)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"descripcion": "Robo torre norte", "valorAsegurado": 20000000.00}
						"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.estado").value("ACTIVO"));

		mockMvc.perform(post("/polizas/" + polizaId + "/renovar").header("x-api-key", API_KEY))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.estado").value("RENOVADA"))
				.andExpect(jsonPath("$.canonMensual").value(2587500.00))
				.andExpect(jsonPath("$.prima").value(31050000.00));

		mockMvc.perform(post("/polizas/" + polizaId + "/cancelar").header("x-api-key", API_KEY))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.estado").value("CANCELADA"));

		mockMvc.perform(get("/polizas/" + polizaId + "/riesgos").header("x-api-key", API_KEY))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].estado").value("CANCELADO"))
				.andExpect(jsonPath("$[1].estado").value("CANCELADO"));

		verify(coreGateway).notificarEvento(eq(PolizaService.EVENTO_CREACION), eq(polizaId));
		verify(coreGateway).notificarEvento(eq(PolizaService.EVENTO_ALTA_RIESGO), eq(polizaId));
		verify(coreGateway).notificarEvento(eq(PolizaService.EVENTO_RENOVACION), eq(polizaId));
		verify(coreGateway).notificarEvento(eq(PolizaService.EVENTO_CANCELACION), eq(polizaId));
	}

	@DisplayName("Regla 1: una póliza individual no admite riesgos adicionales")
	@Test
	void unaPolizaIndividualNoAdmiteRiesgosAdicionales() throws Exception {
		String respuesta = mockMvc.perform(post("/polizas")
				.header("x-api-key", API_KEY)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "tipo": "INDIVIDUAL",
						  "fechaInicio": "2026-01-01",
						  "fechaFin": "2026-12-31",
						  "mesesVigencia": 12,
						  "canonMensual": 800000.00,
						  "riesgos": [{"descripcion": "Incendio", "valorAsegurado": 1000000.00}]
						}
						"""))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();

		Long polizaId = leerId(respuesta, "$.id");

		mockMvc.perform(post("/polizas/" + polizaId + "/riesgos")
				.header("x-api-key", API_KEY)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"descripcion": "Robo", "valorAsegurado": 500000.00}
						"""))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.mensaje").value(org.hamcrest.Matchers.containsString("colectivas")));
	}

	@DisplayName("Regla 1: crear una póliza individual con dos riesgos devuelve 409")
	@Test
	void crearPolizaIndividualConDosRiesgosDevuelve409() throws Exception {
		mockMvc.perform(post("/polizas")
				.header("x-api-key", API_KEY)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "tipo": "INDIVIDUAL",
						  "fechaInicio": "2026-01-01",
						  "fechaFin": "2026-12-31",
						  "mesesVigencia": 12,
						  "canonMensual": 800000.00,
						  "riesgos": [
						    {"descripcion": "Incendio", "valorAsegurado": 1000000.00},
						    {"descripcion": "Robo", "valorAsegurado": 500000.00}
						  ]
						}
						"""))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.mensaje").value(org.hamcrest.Matchers.containsString("un riesgo")));
	}

	@DisplayName("Regla 2: renovar una póliza cancelada devuelve 409")
	@Test
	void renovarPolizaCanceladaDevuelve409() throws Exception {
		String respuesta = mockMvc.perform(post("/polizas")
				.header("x-api-key", API_KEY)
				.contentType(MediaType.APPLICATION_JSON)
				.content(cuerpoPolizaColectiva()))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		Long polizaId = leerId(respuesta, "$.id");

		mockMvc.perform(post("/polizas/" + polizaId + "/cancelar").header("x-api-key", API_KEY))
				.andExpect(status().isOk());

		mockMvc.perform(post("/polizas/" + polizaId + "/renovar").header("x-api-key", API_KEY))
				.andExpect(status().isConflict());
	}

	@DisplayName("Cancelar un riesgo individual lo deja en estado CANCELADO")
	@Test
	void cancelarRiesgoIndividualmente() throws Exception {
		String respuesta = mockMvc.perform(post("/polizas")
				.header("x-api-key", API_KEY)
				.contentType(MediaType.APPLICATION_JSON)
				.content(cuerpoPolizaColectiva()))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		Long polizaId = leerId(respuesta, "$.id");

		String respuestaRiesgos = mockMvc.perform(get("/polizas/" + polizaId + "/riesgos").header("x-api-key", API_KEY))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		Long riesgoId = leerId(respuestaRiesgos, "$[0].id");

		mockMvc.perform(post("/riesgos/" + riesgoId + "/cancelar").header("x-api-key", API_KEY))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.estado").value("CANCELADO"));

		verify(coreGateway).notificarEvento(eq(RiesgoService.EVENTO_CANCELACION_RIESGO), eq(polizaId));
	}

	@DisplayName("El mock del CORE registra el intento de envío")
	@Test
	void elMockDelCoreRegistraElIntentoDeEnvio() throws Exception {
		mockMvc.perform(post("/core-mock/evento")
				.header("x-api-key", API_KEY)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"evento": "ACTUALIZACION", "polizaId": 555}
						"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.polizaId").value(555));
	}

	@DisplayName("Un fallo del CORE no revierte la operación de negocio")
	@Test
	void unFalloDelCoreNoRevierteLaOperacion() throws Exception {
		doThrow(new IllegalStateException("CORE no disponible")).when(coreGateway).notificarEvento(any(), any());

		mockMvc.perform(post("/polizas")
				.header("x-api-key", API_KEY)
				.contentType(MediaType.APPLICATION_JSON)
				.content(cuerpoPolizaColectiva()))
				.andExpect(status().isCreated());

		assertThat(polizaService.listar(null, null)).hasSize(1);
	}

	@DisplayName("Sin la api-key todas las operaciones responden 401")
	@Test
	void sinApiKeyTodasLasOperacionesResponden401() throws Exception {
		mockMvc.perform(get("/polizas")).andExpect(status().isUnauthorized());
		mockMvc.perform(get("/polizas/1/riesgos")).andExpect(status().isUnauthorized());
		mockMvc.perform(post("/polizas/1/renovar")).andExpect(status().isUnauthorized());
		mockMvc.perform(post("/polizas/1/cancelar")).andExpect(status().isUnauthorized());
		mockMvc.perform(post("/polizas/1/riesgos")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"descripcion": "Robo", "valorAsegurado": 500000.00}
						"""))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(post("/riesgos/1/cancelar")).andExpect(status().isUnauthorized());
		mockMvc.perform(post("/core-mock/evento")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"evento": "ACTUALIZACION", "polizaId": 555}
						"""))
				.andExpect(status().isUnauthorized());
	}

	@DisplayName("La prima de la póliza es siempre el canon mensual por los meses de vigencia")
	@Test
	void laPrimaEsElCanonPorLosMesesDeVigencia() throws Exception {
		mockMvc.perform(post("/polizas")
				.header("x-api-key", API_KEY)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "tipo": "COLECTIVA",
						  "fechaInicio": "2026-01-01",
						  "fechaFin": "2026-06-30",
						  "mesesVigencia": 6,
						  "canonMensual": 1234567.89
						}
						"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.prima").value(new BigDecimal("7407407.34")));
	}
}
