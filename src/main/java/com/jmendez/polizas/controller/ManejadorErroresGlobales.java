package com.jmendez.polizas.controller;

import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
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
 * <li>{@code 400}: la petición es inválida o inconsistente.</li>
 * <li>{@code 404}: el recurso solicitado no existe.</li>
 * <li>{@code 405}: el método HTTP no está soportado por el recurso.</li>
 * <li>{@code 409}: se violó una regla de negocio o se detectó un conflicto de
 * concurrencia (control de concurrencia optimista).</li>
 * <li>{@code 415}: el tipo de contenido de la petición no está soportado.</li>
 * <li>{@code 500}: error inesperado (queda registrado en el log).</li>
 * </ul>
 */
@RestControllerAdvice
public class ManejadorErroresGlobales {

	private static final Logger LOG = LoggerFactory.getLogger(ManejadorErroresGlobales.class);

	/** Longitud máxima del valor reflejado en un mensaje de error. */
	private static final int MAXIMO_VALOR_REFLEJADO = 50;

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
	 * Conflicto de concurrencia optimista: otra operación modificó la misma
	 * póliza o riesgo antes de que esta confirmara sus cambios.
	 *
	 * <p>Se traduce a {@code 409} para que el consumidor reintente en lugar de
	 * perder una actualización (por ejemplo, el IPC de una renovación).
	 *
	 * @param excepcion excepción de bloqueo optimista
	 * @param request   petición en curso
	 * @return respuesta {@code 409}
	 */
	@ExceptionHandler(OptimisticLockingFailureException.class)
	public ResponseEntity<ApiErrorResponse> manejarConflictoOptimista(OptimisticLockingFailureException excepcion,
			HttpServletRequest request) {
		LOG.warn("Conflicto de concurrencia optimista en {} {}: {}", request.getMethod(), request.getRequestURI(),
				excepcion.getMessage());
		return construir(HttpStatus.CONFLICT,
				"La póliza fue modificada por otra operación; reintente la operación.", request);
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
	 * <p>El mensaje devuelto es genérico: el detalle original puede incluir
	 * nombres internos de clases o rutas del framework y no debe exponerse al
	 * consumidor (queda solo en el log).
	 *
	 * @param excepcion excepción de validación
	 * @param request   petición en curso
	 * @return respuesta {@code 400}
	 */
	@ExceptionHandler(ConstraintViolationException.class)
	public ResponseEntity<ApiErrorResponse> manejarValidacionDeParametros(ConstraintViolationException excepcion,
			HttpServletRequest request) {
		LOG.warn("Violación de validación de parámetros en {} {}: {}", request.getMethod(), request.getRequestURI(),
				excepcion.getMessage());
		return construir(HttpStatus.BAD_REQUEST,
				"Uno o más parámetros de la petición no cumplen las restricciones de validación.", request);
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
		String mensaje = "El valor '" + sanear(excepcion.getValue()) + "' no es válido para el parámetro '"
				+ excepcion.getName() + "'.";
		return construir(HttpStatus.BAD_REQUEST, mensaje, request);
	}

	/**
	 * Método HTTP no soportado por el recurso solicitado.
	 *
	 * @param excepcion excepción del método no soportado
	 * @param request   petición en curso
	 * @return respuesta {@code 405}
	 */
	@ExceptionHandler(HttpRequestMethodNotSupportedException.class)
	public ResponseEntity<ApiErrorResponse>manejarMetodoNoSoportado(HttpRequestMethodNotSupportedException excepcion,
			HttpServletRequest request) {
		StringBuilder permitido = new StringBuilder();
		for (String metodo : excepcion.getSupportedMethods() == null ? new String[0]
				: excepcion.getSupportedMethods()) {
			if (permitido.length() > 0) {
				permitido.append(", ");
			}
			permitido.append(metodo);
		}
		String mensaje = permitido.length() == 0
				? "El método HTTP de la petición no está soportado por este recurso."
				: "El método HTTP de la petición no está soportado por este recurso. Métodos admitidos: " + permitido;
		return construir(HttpStatus.METHOD_NOT_ALLOWED, mensaje, request);
	}

	/**
	 * Tipo de contenido no soportado por el recurso solicitado.
	 *
	 * @param excepcion excepción del tipo de contenido no soportado
	 * @param request   petición en curso
	 * @return respuesta {@code 415}
	 */
	@ExceptionHandler(HttpMediaTypeNotSupportedException.class)
	public ResponseEntity<ApiErrorResponse> manejarTipoDeContenidoNoSoportado(
			HttpMediaTypeNotSupportedException excepcion, HttpServletRequest request) {
		String mensaje = "El tipo de contenido '" + excepcion.getContentType()
				+ "' no está soportado por este recurso. Use 'application/json'.";
		return construir(HttpStatus.UNSUPPORTED_MEDIA_TYPE, mensaje, request);
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

	/**
	 * Sanea un valor reflejado en un mensaje de error: elimina caracteres de
	 * control (para no permitir inyección en logs) y acota su longitud.
	 *
	 * @param valor valor a sanear
	 * @return valor saneado y acotado
	 */
	private String sanear(Object valor) {
		if (valor == null) {
			return "null";
		}
		String texto = String.valueOf(valor).replaceAll("[\\p{Cntrl}]", " ");
		if (texto.length() > MAXIMO_VALOR_REFLEJADO) {
			texto = texto.substring(0, MAXIMO_VALOR_REFLEJADO) + "…";
		}
		return texto;
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
