package com.jmendez.polizas.controller;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import com.jmendez.polizas.dto.ApiErrorResponse;
import com.jmendez.polizas.exception.PolizaNoEncontradaException;
import com.jmendez.polizas.exception.ReglaNegocioException;
import com.jmendez.polizas.exception.SolicitudInvalidaException;

import jakarta.validation.ConstraintViolationException;

/**
 * Pruebas del manejador global de errores.
 *
 * <p>Verifican la traducción de cada excepción a su código de estado HTTP y a la
 * estructura de error estándar de la API.
 */
class ManejadorErroresGlobalesTest {

	private final ManejadorErroresGlobales manejador = new ManejadorErroresGlobales();
	private final MockHttpServletRequest peticion = new MockHttpServletRequest("GET", "/polizas/1");

	private <T> ResponseEntity<ApiErrorResponse> verificar(ResponseEntity<ApiErrorResponse> respuesta,
			HttpStatus estado, String fragmentoMensaje) {
		assertThat(respuesta.getStatusCode()).isEqualTo(estado);
		assertThat(respuesta.getBody()).isNotNull();
		assertThat(respuesta.getBody().mensaje()).contains(fragmentoMensaje);
		assertThat(respuesta.getBody().ruta()).isEqualTo("/polizas/1");
		assertThat(respuesta.getBody().status()).isEqualTo(estado.value());
		return respuesta;
	}

	@DisplayName("Un recurso inexistente se traduce a 404")
	@Test
	void unRecursoInexistenteSeTraduceA404() {
		verificar(manejador.manejarRecursoNoEncontrado(new PolizaNoEncontradaException(1L), peticion),
				HttpStatus.NOT_FOUND, "No existe una póliza con el identificador 1");
	}

	@DisplayName("Una regla de negocio violada se traduce a 409")
	@Test
	void unaReglaDeNegocioVioladaSeTraduceA409() {
		verificar(manejador.manejarReglaNegocio(new ReglaNegocioException("regla violada"), peticion),
				HttpStatus.CONFLICT, "regla violada");
	}

	@DisplayName("Una solicitud inconsistente se traduce a 400")
	@Test
	void unaSolicitudInconsistenteSeTraduceA400() {
		verificar(manejador.manejarSolicitudInvalida(new SolicitudInvalidaException("prima inconsistente"), peticion),
				HttpStatus.BAD_REQUEST, "prima inconsistente");
	}

	@DisplayName("Los errores de validación de campos se traducen a 400 con el detalle por campo")
	@Test
	void losErroresDeValidacionDeCamposSeTraducenA400() {
		BeanPropertyBindingResult resultado = new BeanPropertyBindingResult(new Object(), "poliza");
		resultado.addError(new FieldError("poliza", "tipo", "el tipo de la póliza es obligatorio"));

		ResponseEntity<ApiErrorResponse> respuesta = manejador.manejarValidacionDeCampos(
				new MethodArgumentNotValidException(parametro(), resultado), peticion);

		verificar(respuesta, HttpStatus.BAD_REQUEST, "La petición contiene datos inválidos");
		assertThat(respuesta.getBody().erroresCampos()).containsEntry("tipo", "el tipo de la póliza es obligatorio");
	}

	@DisplayName("Los errores de validación de parámetros se traducen a 400")
	@Test
	void losErroresDeValidacionDeParametrosSeTraducenA400() {
		verificar(manejador.manejarValidacionDeParametros(
				new ConstraintViolationException("parámetro inválido", java.util.Set.of()), peticion),
				HttpStatus.BAD_REQUEST, "parámetro inválido");
	}

	@DisplayName("Un valor de parámetro inválido se traduce a 400 indicando el parámetro")
	@Test
	void unValorDeParametroInvalidoSeTraduceA400() {
		MethodArgumentTypeMismatchException excepcion = new MethodArgumentTypeMismatchException("INVALIDO", String.class,
				"tipo", parametro(), new IllegalStateException("valor no válido"));

		verificar(manejador.manejarTipoInvalido(excepcion, peticion), HttpStatus.BAD_REQUEST,
				"El valor 'INVALIDO' no es válido para el parámetro 'tipo'");
	}

	@DisplayName("Un cuerpo mal formado se traduce a 400")
	@Test
	void unCuerpoMalFormadoSeTraduceA400() {
		verificar(manejador.manejarCuerpoInvalido(
				new HttpMessageNotReadableException("json inválido", new IllegalStateException("cuerpo")), peticion),
				HttpStatus.BAD_REQUEST, "El cuerpo de la petición no es válido");
	}

	@DisplayName("Un error inesperado se traduce a 500")
	@Test
	void unErrorInesperadoSeTraduceA500() {
		verificar(manejador.manejarErrorInesperado(new IllegalStateException("fallo"), peticion),
				HttpStatus.INTERNAL_SERVER_ERROR, "Ocurrió un error inesperado");
	}

	private MethodParameter parametro() {
		try {
			return new MethodParameter(ManejadorErroresGlobalesTest.class.getDeclaredMethod("destino", String.class), 0);
		} catch (NoSuchMethodException excepcion) {
			throw new IllegalStateException(excepcion);
		}
	}

	private void destino(String valor) {
		// Método auxiliar: solo sirve para obtener el MethodParameter del constructor
		// de MethodArgumentNotValidException.
	}
}
