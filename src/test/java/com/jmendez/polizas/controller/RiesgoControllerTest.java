package com.jmendez.polizas.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import com.jmendez.polizas.exception.PolizaNoEncontradaException;
import com.jmendez.polizas.exception.ReglaNegocioException;
import com.jmendez.polizas.exception.RiesgoNoEncontradoException;
import com.jmendez.polizas.model.EstadoPoliza;
import com.jmendez.polizas.model.EstadoRiesgo;
import com.jmendez.polizas.model.Poliza;
import com.jmendez.polizas.model.Riesgo;
import com.jmendez.polizas.model.TipoPoliza;
import com.jmendez.polizas.service.PolizaService;
import com.jmendez.polizas.service.RiesgoService;

/**
 * Pruebas web del controlador de riesgo con MockMvc.
 */
@SpringBootTest
@AutoConfigureMockMvc
class RiesgoControllerTest {

	private static final String API_KEY = "123456";

	@Autowired
	private MockMvc mockMvc;

	@MockBean
	private PolizaService polizaService;

	@MockBean
	private RiesgoService riesgoService;

	private Riesgo riesgoEjemplo() {
		Poliza poliza = new Poliza();
		poliza.setId(1L);
		poliza.setTipo(TipoPoliza.COLECTIVA);
		poliza.setEstado(EstadoPoliza.VIGENTE);
		Riesgo riesgo = new Riesgo();
		riesgo.setId(7L);
		riesgo.setEstado(EstadoRiesgo.ACTIVO);
		riesgo.setDescripcion("Incendio");
		riesgo.setValorAsegurado(new BigDecimal("80000000.00"));
		riesgo.setPoliza(poliza);
		return riesgo;
	}

	@DisplayName("GET /polizas/{id}/riesgos devuelve 200 con los riesgos de la póliza")
	@Test
	void listarRiesgosDevuelve200() throws Exception {
		when(riesgoService.listarPorPoliza(1L)).thenReturn(List.of(riesgoEjemplo()));

		mockMvc.perform(get("/polizas/1/riesgos").header("x-api-key", API_KEY))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].id").value(7))
				.andExpect(jsonPath("$[0].estado").value("ACTIVO"))
				.andExpect(jsonPath("$[0].polizaId").value(1));
	}

	@DisplayName("GET /polizas/{id}/riesgos de una póliza inexistente devuelve 404")
	@Test
	void listarRiesgosDePolizaInexistenteDevuelve404() throws Exception {
		when(riesgoService.listarPorPoliza(99L)).thenThrow(new PolizaNoEncontradaException(99L));

		mockMvc.perform(get("/polizas/99/riesgos").header("x-api-key", API_KEY))
				.andExpect(status().isNotFound());
	}

	@DisplayName("POST /riesgos/{id}/cancelar devuelve 200 con el riesgo cancelado")
	@Test
	void cancelarRiesgoDevuelve200() throws Exception {
		Riesgo riesgo = riesgoEjemplo();
		riesgo.setEstado(EstadoRiesgo.CANCELADO);
		when(riesgoService.cancelar(7L)).thenReturn(riesgo);

		mockMvc.perform(post("/riesgos/7/cancelar").header("x-api-key", API_KEY))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.estado").value("CANCELADO"));
	}

	@DisplayName("POST /riesgos/{id}/cancelar de un riesgo inexistente devuelve 404")
	@Test
	void cancelarRiesgoInexistenteDevuelve404() throws Exception {
		when(riesgoService.cancelar(99L)).thenThrow(new RiesgoNoEncontradoException(99L));

		mockMvc.perform(post("/riesgos/99/cancelar").header("x-api-key", API_KEY))
				.andExpect(status().isNotFound());
	}

	@DisplayName("POST /riesgos/{id}/cancelar de un riesgo ya cancelado devuelve 409")
	@Test
	void cancelarRiesgoYaCanceladoDevuelve409() throws Exception {
		when(riesgoService.cancelar(eq(7L)))
				.thenThrow(new ReglaNegocioException("El riesgo 7 ya se encuentra CANCELADO."));

		mockMvc.perform(post("/riesgos/7/cancelar").header("x-api-key", API_KEY))
				.andExpect(status().isConflict());
	}

	@DisplayName("POST /riesgos/{id}/cancelar sin api-key devuelve 401")
	@Test
	void cancelarRiesgoSinApiKeyDevuelve401() throws Exception {
		mockMvc.perform(post("/riesgos/7/cancelar")).andExpect(status().isUnauthorized());
	}
}
