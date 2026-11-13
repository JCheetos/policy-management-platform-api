package com.jmendez.polizas.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.web.servlet.MockMvc;

import com.jmendez.polizas.exception.PolizaNoEncontradaException;
import com.jmendez.polizas.exception.ReglaNegocioException;
import com.jmendez.polizas.exception.SolicitudInvalidaException;
import com.jmendez.polizas.model.EstadoPoliza;
import com.jmendez.polizas.model.EstadoRiesgo;
import com.jmendez.polizas.model.Poliza;
import com.jmendez.polizas.model.Riesgo;
import com.jmendez.polizas.model.TipoPoliza;
import com.jmendez.polizas.service.PolizaService;
import com.jmendez.polizas.service.RiesgoService;

/**
 * Pruebas web del controlador de póliza con MockMvc.
 *
 * <p>Verifican el contrato HTTP: ruta, método, código de estado y cuerpo de la
 * respuesta, incluyendo la traducción de las excepciones de negocio.
 */
@SpringBootTest
@AutoConfigureMockMvc
class PolizaControllerTest {

	private static final String API_KEY = "123456";

	@Autowired
	private MockMvc mockMvc;

	@MockBean
	private PolizaService polizaService;

	@MockBean
	private RiesgoService riesgoService;

	private Poliza polizaEjemplo() {
		Poliza poliza = new Poliza();
		poliza.setId(1L);
		poliza.setTipo(TipoPoliza.COLECTIVA);
		poliza.setEstado(EstadoPoliza.VIGENTE);
		poliza.setFechaInicio(LocalDate.of(2026, 1, 1));
		poliza.setFechaFin(LocalDate.of(2026, 12, 31));
		poliza.setMesesVigencia(12);
		poliza.setCanonMensual(new BigDecimal("2500000.00"));
		poliza.setPrima(new BigDecimal("30000000.00"));
		poliza.setNombreTomador("Inmobiliaria Andes");
		return poliza;
	}

	@DisplayName("GET /polizas devuelve 200 con las pólizas filtradas")
	@Test
	void listarPolizasDevuelve200() throws Exception {
		when(polizaService.listar(any(), any())).thenReturn(List.of(polizaEjemplo()));

		mockMvc.perform(get("/polizas").param("tipo", "COLECTIVA").param("estado", "VIGENTE")
				.header("x-api-key", API_KEY))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].id").value(1))
				.andExpect(jsonPath("$[0].tipo").value("COLECTIVA"))
				.andExpect(jsonPath("$[0].estado").value("VIGENTE"))
				.andExpect(jsonPath("$[0].prima").value(30000000.00));
	}

	@DisplayName("GET /polizas sin api-key devuelve 401")
	@Test
	void listarPolizasSinApiKeyDevuelve401() throws Exception {
		mockMvc.perform(get("/polizas")).andExpect(status().isUnauthorized());
	}

	@DisplayName("GET /polizas con un tipo inválido devuelve 400")
	@Test
	void listarPolizasConTipoInvalidoDevuelve400() throws Exception {
		mockMvc.perform(get("/polizas").param("tipo", "INVALIDO").header("x-api-key", API_KEY))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.mensaje").value(org.hamcrest.Matchers.containsString("tipo")));
	}

	@DisplayName("POST /polizas devuelve 201 con la póliza creada")
	@Test
	void crearPolizaDevuelve201() throws Exception {
		when(polizaService.crear(any())).thenReturn(polizaEjemplo());

		mockMvc.perform(post("/polizas")
				.header("x-api-key", API_KEY)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "tipo": "COLECTIVA",
						  "fechaInicio": "2026-01-01",
						  "fechaFin": "2026-12-31",
						  "mesesVigencia": 12,
						  "canonMensual": 2500000.00,
						  "prima": 30000000.00,
						  "nombreTomador": "Inmobiliaria Andes",
						  "riesgos": [{"descripcion": "Incendio", "valorAsegurado": 80000000.00}]
						}
						"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").value(1))
				.andExpect(jsonPath("$.tipo").value("COLECTIVA"));
	}

	@DisplayName("POST /polizas con datos inválidos devuelve 400 con el detalle por campo")
	@Test
	void crearPolizaConDatosInvalidosDevuelve400() throws Exception {
		mockMvc.perform(post("/polizas")
				.header("x-api-key", API_KEY)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"tipo": "COLECTIVA", "mesesVigencia": 0}
						"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.erroresCampos.fechaInicio").exists())
				.andExpect(jsonPath("$.erroresCampos.fechaFin").exists())
				.andExpect(jsonPath("$.erroresCampos.canonMensual").exists());
	}

	@DisplayName("POST /polizas con una prima inconsistente devuelve 400")
	@Test
	void crearPolizaConPrimaInconsistenteDevuelve400() throws Exception {
		when(polizaService.crear(any())).thenThrow(new SolicitudInvalidaException(
				"La prima enviada (999) no corresponde al canon mensual multiplicado por los meses de vigencia."));

		mockMvc.perform(post("/polizas")
				.header("x-api-key", API_KEY)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"tipo": "COLECTIVA", "fechaInicio": "2026-01-01", "fechaFin": "2026-12-31",
						 "mesesVigencia": 12, "canonMensual": 2500000.00, "prima": 999}
						"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.mensaje").value(org.hamcrest.Matchers.containsString("prima")));
	}

	@DisplayName("POST /polizas/{id}/renovar devuelve 200 con la póliza renovada")
	@Test
	void renovarPolizaDevuelve200() throws Exception {
		Poliza renovada = polizaEjemplo();
		renovada.setEstado(EstadoPoliza.RENOVADA);
		renovada.setCanonMensual(new BigDecimal("2587500.00"));
		when(polizaService.renovar(1L)).thenReturn(renovada);

		mockMvc.perform(post("/polizas/1/renovar").header("x-api-key", API_KEY))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.estado").value("RENOVADA"))
				.andExpect(jsonPath("$.canonMensual").value(2587500.00));
	}

	@DisplayName("POST /polizas/{id}/renovar sobre una póliza inexistente devuelve 404")
	@Test
	void renovarPolizaInexistenteDevuelve404() throws Exception {
		when(polizaService.renovar(99L)).thenThrow(new PolizaNoEncontradaException(99L));

		mockMvc.perform(post("/polizas/99/renovar").header("x-api-key", API_KEY))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.mensaje").value(org.hamcrest.Matchers.containsString("99")));
	}

	@DisplayName("POST /polizas/{id}/renovar sobre una póliza cancelada devuelve 409")
	@Test
	void renovarPolizaCanceladaDevuelve409() throws Exception {
		when(polizaService.renovar(1L))
				.thenThrow(new ReglaNegocioException("No se puede renovar la póliza 1 porque está CANCELADA."));

		mockMvc.perform(post("/polizas/1/renovar").header("x-api-key", API_KEY))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.mensaje").value(org.hamcrest.Matchers.containsString("CANCELADA")));
	}

	@DisplayName("POST /polizas/{id}/cancelar devuelve 200 con la póliza cancelada")
	@Test
	void cancelarPolizaDevuelve200() throws Exception {
		Poliza cancelada = polizaEjemplo();
		cancelada.setEstado(EstadoPoliza.CANCELADA);
		when(polizaService.cancelar(1L)).thenReturn(cancelada);

		mockMvc.perform(post("/polizas/1/cancelar").header("x-api-key", API_KEY))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.estado").value("CANCELADA"));
	}

	@DisplayName("POST /polizas/{id}/riesgos devuelve 201 con el riesgo creado")
	@Test
	void agregarRiesgoDevuelve201() throws Exception {
		Riesgo riesgo = new Riesgo();
		riesgo.setId(3L);
		riesgo.setEstado(EstadoRiesgo.ACTIVO);
		riesgo.setDescripcion("Incendio");
		riesgo.setValorAsegurado(new BigDecimal("80000000.00"));
		riesgo.setPoliza(polizaEjemplo());
		when(polizaService.agregarRiesgo(eq(1L), any())).thenReturn(riesgo);

		mockMvc.perform(post("/polizas/1/riesgos")
				.header("x-api-key", API_KEY)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"descripcion": "Incendio", "valorAsegurado": 80000000.00}
						"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").value(3))
				.andExpect(jsonPath("$.estado").value("ACTIVO"))
				.andExpect(jsonPath("$.polizaId").value(1));
	}

	@DisplayName("POST /polizas/{id}/riesgos sobre una póliza individual devuelve 409")
	@Test
	void agregarRiesgoEnPolizaIndividualDevuelve409() throws Exception {
		when(polizaService.agregarRiesgo(eq(1L), any())).thenThrow(new ReglaNegocioException(
				"Solo las pólizas colectivas admiten riesgos adicionales. La póliza 1 es de tipo INDIVIDUAL."));

		mockMvc.perform(post("/polizas/1/riesgos")
				.header("x-api-key", API_KEY)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"descripcion": "Incendio", "valorAsegurado": 80000000.00}
						"""))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.mensaje").value(org.hamcrest.Matchers.containsString("colectivas")));
	}

	@DisplayName("POST /polizas/{id}/riesgos con un cuerpo inválido devuelve 400")
	@Test
	void agregarRiesgoConCuerpoInvalidoDevuelve400() throws Exception {
		mockMvc.perform(post("/polizas/1/riesgos")
				.header("x-api-key", API_KEY)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"descripcion": "", "valorAsegurado": 0}
						"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.erroresCampos.descripcion").exists())
				.andExpect(jsonPath("$.erroresCampos.valorAsegurado").exists());
	}

	@DisplayName("Un cuerpo mal formado devuelve 400")
	@Test
	void unCuerpoMalFormadoDevuelve400() throws Exception {
		mockMvc.perform(post("/polizas")
				.header("x-api-key", API_KEY)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{ esto no es json"))
				.andExpect(status().isBadRequest());
	}

	@DisplayName("Un error inesperado devuelve 500 con la estructura de error estándar")
	@Test
	void unErrorInesperadoDevuelve500() throws Exception {
		when(polizaService.listar(any(), any())).thenThrow(new IllegalStateException("fallo inesperado"));

		mockMvc.perform(get("/polizas").header("x-api-key", API_KEY))
				.andExpect(status().isInternalServerError())
				.andExpect(jsonPath("$.mensaje").value("Ocurrió un error inesperado."))
				.andExpect(jsonPath("$.status").value(500));
	}

	@DisplayName("POST /polizas/{id}/renovar con un método no soportado devuelve 405")
	@Test
	void renovarConUnMetodoNoSoportadoDevuelve405() throws Exception {
		mockMvc.perform(get("/polizas/1/renovar").header("x-api-key", API_KEY))
				.andExpect(status().isMethodNotAllowed())
				.andExpect(jsonPath("$.status").value(405));
	}

	@DisplayName("POST /polizas con un tipo de contenido no soportado devuelve 415")
	@Test
	void crearPolizaConTipoDeContenidoNoSoportadoDevuelve415() throws Exception {
		mockMvc.perform(post("/polizas")
				.header("x-api-key", API_KEY)
				.contentType(MediaType.APPLICATION_XML)
				.content("<poliza/>"))
				.andExpect(status().isUnsupportedMediaType())
				.andExpect(jsonPath("$.status").value(415));
	}

	@DisplayName("Un conflicto de concurrencia optimista devuelve 409")
	@Test
	void unConflictoDeConcurrenciaOptimistaDevuelve409() throws Exception {
		when(polizaService.renovar(1L)).thenThrow(new ObjectOptimisticLockingFailureException(Poliza.class, 1L));

		mockMvc.perform(post("/polizas/1/renovar").header("x-api-key", API_KEY))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.status").value(409))
				.andExpect(jsonPath("$.mensaje")
						.value(org.hamcrest.Matchers.containsString("modificada por otra operación")));
	}

	@DisplayName("POST /polizas con un nombre demasiado largo devuelve 400")
	@Test
	void crearPolizaConNombreDemasiadoLargoDevuelve400() throws Exception {
		mockMvc.perform(post("/polizas")
				.header("x-api-key", API_KEY)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"tipo": "COLECTIVA", "fechaInicio": "2026-01-01", "fechaFin": "2026-12-31",
						 "mesesVigencia": 12, "canonMensual": 2500000.00, "nombreTomador": "%s"}
						""".formatted("a".repeat(201))))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.erroresCampos.nombreTomador").exists());
	}

	@DisplayName("POST /polizas con un canon con más de dos decimales devuelve 400")
	@Test
	void crearPolizaConCanonConMasDeDosDecimalesDevuelve400() throws Exception {
		mockMvc.perform(post("/polizas")
				.header("x-api-key", API_KEY)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"tipo": "COLECTIVA", "fechaInicio": "2026-01-01", "fechaFin": "2026-12-31",
						 "mesesVigencia": 12, "canonMensual": 1000.555}
						"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.erroresCampos.canonMensual").exists());
	}

	@DisplayName("POST /polizas/{id}/riesgos con una descripción demasiado larga devuelve 400")
	@Test
	void agregarRiesgoConDescripcionDemasiadoLargaDevuelve400() throws Exception {
		mockMvc.perform(post("/polizas/1/riesgos")
				.header("x-api-key", API_KEY)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"descripcion": "%s", "valorAsegurado": 80000000.00}
						""".formatted("r".repeat(501))))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.erroresCampos.descripcion").exists());
	}
}
