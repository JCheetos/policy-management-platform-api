package com.jmendez.polizas.infrastructure.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.jmendez.polizas.exception.CoreComunicacionException;
import com.jmendez.polizas.port.CoreGateway;
import com.jmendez.polizas.port.EventoCore;

/**
 * Adaptador HTTP del puerto {@link CoreGateway}.
 *
 * <p>Traduce la notificación de dominio a una llamada {@code POST} contra el
 * servicio agnóstico de edición del CORE legado. El fallo de comunicación se
 * expresa con {@link CoreComunicacionException}; la política {@code best-effort}
 * la aplica el servicio de aplicación, que registra el error y no revierte la
 * operación de negocio.
 */
public class CoreHttpAdapter implements CoreGateway {

	private static final Logger LOG = LoggerFactory.getLogger(CoreHttpAdapter.class);

	/** Encabezado de credencial compartida con el CORE. */
	public static final String ENCABEZADO_API_KEY = "x-api-key";

	private final RestClient cliente;
	private final String urlCore;
	private final String apiKey;

	/**
	 * @param cliente  cliente HTTP con el que se habla con el CORE
	 * @param urlCore  URL del endpoint de eventos del CORE
	 * @param apiKey   credencial compartida con el CORE
	 */
	public CoreHttpAdapter(RestClient cliente, String urlCore, String apiKey) {
		this.cliente = cliente;
		this.urlCore = urlCore;
		this.apiKey = apiKey;
	}

	@Override
	public void notificarEvento(String evento, Long polizaId) {
		EventoCore cuerpo = new EventoCore(evento, polizaId);
		try {
			cliente.post()
					.uri(urlCore)
					.header(ENCABEZADO_API_KEY, apiKey)
					.contentType(MediaType.APPLICATION_JSON)
					.body(cuerpo)
					.retrieve()
					.toBodilessEntity();
			LOG.info("Evento notificado al CORE: evento={} polizaId={}", evento, polizaId);
		} catch (RestClientResponseException excepcion) {
			throw new CoreComunicacionException(
					"El CORE respondió con estado " + excepcion.getStatusCode().value() + " al enviar el evento "
							+ evento + " de la póliza " + polizaId,
					excepcion);
		} catch (ResourceAccessException excepcion) {
			throw new CoreComunicacionException(
					"No fue posible conectar con el CORE para enviar el evento " + evento + " de la póliza " + polizaId,
					excepcion);
		}
	}
}
