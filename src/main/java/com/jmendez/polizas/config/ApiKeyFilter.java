package com.jmendez.polizas.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jmendez.polizas.dto.ApiErrorResponse;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Filtro de seguridad mínima: exige el encabezado {@code x-api-key} con el valor
 * configurado en {@code app.api-key} en todas las peticiones, incluido el mock
 * del CORE.
 *
 * <p>La comparación se realiza en tiempo constante para no filtrar información
 * por análisis temporal de la respuesta.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ApiKeyFilter extends OncePerRequestFilter {

	private static final Logger LOG = LoggerFactory.getLogger(ApiKeyFilter.class);

	/** Nombre del encabezado de credencial. */
	public static final String NOMBRE_ENCABEZADO = "x-api-key";

	private final String apiKeyEsperada;
	private final ObjectMapper objectMapper;

	/**
	 * @param propiedades  propiedades de la aplicación
	 * @param objectMapper serializador de la respuesta de error
	 */
	public ApiKeyFilter(PropiedadesApp propiedades, ObjectMapper objectMapper) {
		this.apiKeyEsperada = propiedades.getApiKey();
		this.objectMapper = objectMapper;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		String apiKeyRecibida = request.getHeader(NOMBRE_ENCABEZADO);

		if (!esApiKeyValida(apiKeyRecibida)) {
			LOG.warn("Acceso denegado: encabezado {} ausente o inválido en {} {}", NOMBRE_ENCABEZADO, request.getMethod(),
					request.getRequestURI());
			escribirError(response, request);
			return;
		}

		chain.doFilter(request, response);
	}

	private boolean esApiKeyValida(String apiKeyRecibida) {
		if (apiKeyRecibida == null || apiKeyEsperada == null) {
			return false;
		}
		return MessageDigest.isEqual(apiKeyRecibida.getBytes(StandardCharsets.UTF_8),
				apiKeyEsperada.getBytes(StandardCharsets.UTF_8));
	}

	private void escribirError(HttpServletResponse response, HttpServletRequest request) throws IOException {
		ApiErrorResponse error = ApiErrorResponse.de(HttpStatus.UNAUTHORIZED.value(), HttpStatus.UNAUTHORIZED.getReasonPhrase(),
				"Falta el encabezado '" + NOMBRE_ENCABEZADO + "' o su valor no es válido.", request.getRequestURI());

		response.setStatus(HttpStatus.UNAUTHORIZED.value());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.setCharacterEncoding(StandardCharsets.UTF_8.name());
		objectMapper.writeValue(response.getWriter(), error);
	}
}
