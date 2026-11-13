package com.jmendez.polizas.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.jmendez.polizas.dto.EventoCoreRequest;
import com.jmendez.polizas.dto.EventoCoreResponse;

import jakarta.validation.Valid;

/**
 * Mock del CORE transaccional legado.
 *
 * <p>Su único propósito es registrar en logs que la operación se intentó enviar
 * al CORE. Es un endpoint real de la aplicación (protegido por la api-key) para
 * que la integración pueda probarse de extremo a extremo sin el CORE real.
 */
@RestController
@RequestMapping("/core-mock")
public class CoreMockController {

	private static final Logger LOG = LoggerFactory.getLogger(CoreMockController.class);

	/**
	 * Registra el intento de envío de un evento al CORE.
	 *
	 * @param solicitud evento a registrar
	 * @return acuse de recibo del registro
	 */
	@PostMapping("/evento")
	public ResponseEntity<EventoCoreResponse> registrarEvento(@Valid @RequestBody EventoCoreRequest solicitud) {
		LOG.info("Intento de envío al CORE: evento={} polizaId={}", solicitud.evento(), solicitud.polizaId());
		return ResponseEntity.ok(new EventoCoreResponse("REGISTRADO", solicitud.evento(), solicitud.polizaId()));
	}
}
