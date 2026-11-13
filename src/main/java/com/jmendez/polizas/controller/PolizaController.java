package com.jmendez.polizas.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.jmendez.polizas.dto.CrearPolizaRequest;
import com.jmendez.polizas.dto.PolizaResponse;
import com.jmendez.polizas.dto.RiesgoRequest;
import com.jmendez.polizas.dto.RiesgoResponse;
import com.jmendez.polizas.model.EstadoPoliza;
import com.jmendez.polizas.model.TipoPoliza;
import com.jmendez.polizas.service.PolizaService;

import jakarta.validation.Valid;

/**
 * Controlador REST de la póliza.
 *
 * <p>Adaptador de entrada HTTP: traduce peticiones y respuestas, y delega toda
 * la lógica de negocio en {@link PolizaService}.
 */
@RestController
@RequestMapping("/polizas")
public class PolizaController {

	private final PolizaService polizaService;

	/**
	 * @param polizaService servicio de aplicación de la póliza
	 */
	public PolizaController(PolizaService polizaService) {
		this.polizaService = polizaService;
	}

	/**
	 * Lista las pólizas, opcionalmente filtradas por tipo y/o estado.
	 *
	 * @param tipo   filtro por tipo de póliza
	 * @param estado filtro por estado de la póliza
	 * @return pólizas que cumplen los filtros
	 */
	@GetMapping
	public ResponseEntity<List<PolizaResponse>> listar(
			@RequestParam(name = "tipo", required = false) TipoPoliza tipo,
			@RequestParam(name = "estado", required = false) EstadoPoliza estado) {
		List<PolizaResponse> respuesta = polizaService.listar(tipo, estado).stream()
				.map(PolizaResponse::desde)
				.toList();
		return ResponseEntity.ok(respuesta);
	}

	/**
	 * Crea una póliza con sus riesgos iniciales.
	 *
	 * @param solicitud datos de alta de la póliza
	 * @return póliza creada
	 */
	@PostMapping
	public ResponseEntity<PolizaResponse> crear(@Valid @RequestBody CrearPolizaRequest solicitud) {
		PolizaResponse respuesta = PolizaResponse.desde(polizaService.crear(solicitud));
		return ResponseEntity.status(HttpStatus.CREATED).body(respuesta);
	}

	/**
	 * Renueva la póliza aplicando el IPC configurado.
	 *
	 * @param id identificador de la póliza
	 * @return póliza renovada
	 */
	@PostMapping("/{id}/renovar")
	public ResponseEntity<PolizaResponse> renovar(@PathVariable("id") Long id) {
		return ResponseEntity.ok(PolizaResponse.desde(polizaService.renovar(id)));
	}

	/**
	 * Cancela la póliza y todos sus riesgos.
	 *
	 * @param id identificador de la póliza
	 * @return póliza cancelada
	 */
	@PostMapping("/{id}/cancelar")
	public ResponseEntity<PolizaResponse> cancelar(@PathVariable("id") Long id) {
		return ResponseEntity.ok(PolizaResponse.desde(polizaService.cancelar(id)));
	}

	/**
	 * Agrega un riesgo a una póliza colectiva.
	 *
	 * @param id        identificador de la póliza
	 * @param solicitud datos del nuevo riesgo
	 * @return riesgo creado
	 */
	@PostMapping("/{id}/riesgos")
	public ResponseEntity<RiesgoResponse> agregarRiesgo(@PathVariable("id") Long id,
			@Valid @RequestBody RiesgoRequest solicitud) {
		RiesgoResponse respuesta = RiesgoResponse.desde(polizaService.agregarRiesgo(id, solicitud));
		return ResponseEntity.status(HttpStatus.CREATED).body(respuesta);
	}
}
