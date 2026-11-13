package com.jmendez.polizas.controller;

import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import com.jmendez.polizas.dto.ApiErrorResponse;
import com.jmendez.polizas.exception.RecursoNoEncontradoException;
import com.jmendez.polizas.exception.ReglaNegocioException;
import com.jmendez.polizas.exception.SolicitudInvalidaException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;

/**
 * Manejador global de errores: traduce las excepciones de la aplicación a
 * respuestas HTTP con una estructura de error uniforme.
 *
 * <ul>
 * <li>{@code 404}: el recurso solicitado no existe.</li>
 * <li>{@code 409}: se violó una regla de negocio.</li>
 * <li>{@code 400}: la petición es inválida o inconsistente.</li>
 * <li>{@code 500}: error inesperado (queda registrado en el log).</li>
 * </ul>
 */
@RestControllerAdvice
public class ManejadorErroresGlobales {

	private static final Logger LOG = LoggerFactory.getLogger(ManejadorErroresGlobales.class);

	/**
	 * Recurso no encontrado.
	 *
	 * @param excepcion excepción con el detalle del recurso ausente
	 * @param request   petición en curso
	 * @return respuesta {@code 404}
	 */
	@ExceptionHandler(RecursoNoEncontradoException.class)
	public ResponseEntity<ApiErrorResponse> manejarRecursoNoEncontrado(RecursoNoEncontradoException excepcion,
			HttpServletRequest request) {
		return construir(HttpStatus.NOT_FOUND, excepcion.getMessage(), request);
	}

	/**
	 * Regla de negocio violada.
	 *
	 * @param excepcion excepción con el detalle de la regla violada
	 * @param request   petición en curso
	 * @return respuesta {@code 409}
	 */
	@ExceptionHandler(ReglaNegocioException.class)
	public ResponseEntity<ApiErrorResponse> manejarReglaNegocio(ReglaNegocioException excepcion,
			HttpServletRequest request) {
		return construir(HttpStatus.CONFLICT, excepcion.getMessage(), request);
	}

	/**
	 * Petición inconsistente.
	 *
	 * @param excepcion excepción con el detalle de la inconsistencia
	 * @param request   petición en curso
	 * @return respuesta {@code 400}
	 */
	@ExceptionHandler(SolicitudInvalidaException.class)
	public ResponseEntity<ApiErrorResponse> manejarSolicitudInvalida(SolicitudInvalidaException excepcion,
			HttpServletRequest request) {
		return construir(HttpStatus.BAD_REQUEST, excepcion.getMessage(), request);
	}

	/**
	 * Errores de validación de los campos del cuerpo de la petición.
	 *
	 * @param excepcion excepción de validación
	 * @param request   petición en curso
	 * @return respuesta {@code 400} con el detalle por campo
	 */
	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ApiErrorResponse> manejarValidacionDeCampos(MethodArgumentNotValidException excepcion,
			HttpServletRequest request) {
		Map<String, String> errores = new LinkedHashMap<>();
		for (FieldError error : excepcion.getBindingResult().getFieldErrors()) {
			errores.putIfAbsent(error.getField(), error.getDefaultMessage());
		}
		return construir(HttpStatus.BAD_REQUEST, "La petición contiene datos inválidos.", request, errores);
	}

	/**
	 * Errores de validación de parámetros de la petición.
	 *
	 * @param excepcion excepción de validación
	 * @param request   petición en curso
	 * @return respuesta {@code 400}
	 */
	@ExceptionHandler(ConstraintViolationException.class)
	public ResponseEntity<ApiErrorResponse> manejarValidacionDeParametros(ConstraintViolationException excepcion,
			HttpServletRequest request) {
		return construir(HttpStatus.BAD_REQUEST, excepcion.getMessage(), request);
	}

	/**
	 * Valor inválido en un parámetro de la petición (por ejemplo, un tipo de
	 * póliza que no existe en el enumerado).
	 *
	 * @param excepcion excepción de conversión
	 * @param request   petición en curso
	 * @return respuesta {@code 400}
	 */
	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	public ResponseEntity<ApiErrorResponse> manejarTipoInvalido(MethodArgumentTypeMismatchException excepcion,
			HttpServletRequest request) {
		String mensaje = "El valor '" + excepcion.getValue() + "' no es válido para el parámetro '"
				+ excepcion.getName() + "'.";
		return construir(HttpStatus.BAD_REQUEST, mensaje, request);
	}

	/**
	 * Cuerpo de la petición ausente o mal formado.
	 *
	 * @param excepcion excepción de lectura del cuerpo
	 * @param request   petición en curso
	 * @return respuesta {@code 400}
	 */
	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<ApiErrorResponse> manejarCuerpoInvalido(HttpMessageNotReadableException excepcion,
			HttpServletRequest request) {
		return construir(HttpStatus.BAD_REQUEST, "El cuerpo de la petición no es válido o está incompleto.", request);
	}

	/**
	 * Error inesperado.
	 *
	 * @param excepcion excepción inesperada
	 * @param request   petición en curso
	 * @return respuesta {@code 500}
	 */
	@ExceptionHandler(Exception.class)
	public ResponseEntity<ApiErrorResponse> manejarErrorInesperado(Exception excepcion, HttpServletRequest request) {
		LOG.error("Error inesperado procesando {} {}", request.getMethod(), request.getRequestURI(), excepcion);
		return construir(HttpStatus.INTERNAL_SERVER_ERROR, "Ocurrió un error inesperado.", request);
	}

	private ResponseEntity<ApiErrorResponse> construir(HttpStatus estado, String mensaje, HttpServletRequest request) {
		return construir(estado, mensaje, request, Map.of());
	}

	private ResponseEntity<ApiErrorResponse> construir(HttpStatus estado, String mensaje, HttpServletRequest request,
			Map<String, String> errores) {
		ApiErrorResponse cuerpo = ApiErrorResponse.de(estado.value(), estado.getReasonPhrase(), mensaje,
				request.getRequestURI(), errores);
		return ResponseEntity.status(estado).body(cuerpo);
	}
}
