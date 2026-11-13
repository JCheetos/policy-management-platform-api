package com.jmendez.polizas.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import com.jmendez.polizas.dto.RiesgoResponse;
import com.jmendez.polizas.service.RiesgoService;

/**
 * Controlador REST del riesgo asegurado.
 */
@RestController
public class RiesgoController {

	private final RiesgoService riesgoService;

	/**
	 * @param riesgoService servicio de aplicación del riesgo
	 */
	public RiesgoController(RiesgoService riesgoService) {
		this.riesgoService = riesgoService;
	}

	/**
	 * Lista los riesgos de una póliza.
	 *
	 * @param polizaId identificador de la póliza
	 * @return riesgos de la póliza
	 */
	@GetMapping("/polizas/{id}/riesgos")
	public ResponseEntity<List<RiesgoResponse>> listarPorPoliza(@PathVariable("id") Long polizaId) {
		List<RiesgoResponse> respuesta = riesgoService.listarPorPoliza(polizaId).stream()
				.map(RiesgoResponse::desde)
				.toList();
		return ResponseEntity.ok(respuesta);
	}

	/**
	 * Cancela un riesgo.
	 *
	 * @param id identificador del riesgo
	 * @return riesgo cancelado
	 */
	@PostMapping("/riesgos/{id}/cancelar")
	public ResponseEntity<RiesgoResponse> cancelar(@PathVariable("id") Long id) {
		return ResponseEntity.ok(RiesgoResponse.desde(riesgoService.cancelar(id)));
	}
}
