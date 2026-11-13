package com.jmendez.polizas.config;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.jmendez.polizas.model.EstadoPoliza;
import com.jmendez.polizas.model.Poliza;
import com.jmendez.polizas.model.TipoPoliza;
import com.jmendez.polizas.service.PolizaService;

/**
 * Pruebas del filtro de seguridad mínima (encabezado {@code x-api-key}).
 */
@SpringBootTest
@AutoConfigureMockMvc
class ApiKeyFilterTest {

	private static final String API_KEY = "123456";

	@Autowired
	private MockMvc mockMvc;

	@MockBean
	private PolizaService polizaService;

	@DisplayName("Una petición sin el encabezado x-api-key devuelve 401")
	@Test
	void peticionSinApiKeyDevuelve401() throws Exception {
		mockMvc.perform(get("/polizas"))
				.andExpect(status().isUnauthorized())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
	}

	@DisplayName("Una petición con una api-key incorrecta devuelve 401")
	@Test
	void peticionConApiKeyIncorrectaDevuelve401() throws Exception {
		mockMvc.perform(get("/polizas").header("x-api-key", "000000"))
				.andExpect(status().isUnauthorized());
	}

	@DisplayName("El error 401 indica el encabezado requerido")
	@Test
	void elError401IndicaElEncabezadoRequerido() throws Exception {
		mockMvc.perform(get("/polizas"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.status").value(401))
				.andExpect(jsonPath("$.mensaje").value(org.hamcrest.Matchers.containsString("x-api-key")))
				.andExpect(jsonPath("$.ruta").value("/polizas"));
	}

	@DisplayName("Una petición con la api-key correcta continúa el flujo")
	@Test
	void peticionConApiKeyCorrectaContinua() throws Exception {
		Poliza poliza = new Poliza();
		poliza.setId(1L);
		poliza.setTipo(TipoPoliza.INDIVIDUAL);
		poliza.setEstado(EstadoPoliza.VIGENTE);
		when(polizaService.listar(any(), any())).thenReturn(List.of(poliza));

		mockMvc.perform(get("/polizas").header("x-api-key", API_KEY))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].id").value(1));
	}

	@DisplayName("El mock del CORE también exige la api-key")
	@Test
	void elMockDelCoreTambienExigeApiKey() throws Exception {
		mockMvc.perform(post("/core-mock/evento")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"evento": "ACTUALIZACION", "polizaId": 555}
						"""))
				.andExpect(status().isUnauthorized());
	}
}
