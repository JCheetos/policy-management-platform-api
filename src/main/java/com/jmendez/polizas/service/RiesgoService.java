package com.jmendez.polizas.service;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jmendez.polizas.domain.ReglasPoliza;
import com.jmendez.polizas.exception.PolizaNoEncontradaException;
import com.jmendez.polizas.exception.RiesgoNoEncontradoException;
import com.jmendez.polizas.model.EstadoRiesgo;
import com.jmendez.polizas.model.Riesgo;
import com.jmendez.polizas.port.NotificacionCore;
import com.jmendez.polizas.repository.PolizaRepository;
import com.jmendez.polizas.repository.RiesgoRepository;

/**
 * Servicio de aplicación del riesgo asegurado.
 *
 * <p>Gestiona la consulta de los riesgos de una póliza y su cancelación
 * individual, aplicando las reglas de negocio de {@link ReglasPoliza}.
 */
@Service
public class RiesgoService {

	private static final Logger LOG = LoggerFactory.getLogger(RiesgoService.class);

	/** Evento enviado al CORE al cancelar un riesgo. */
	public static final String EVENTO_CANCELACION_RIESGO = "CANCELACION_RIESGO";

	private final PolizaRepository polizaRepository;
	private final RiesgoRepository riesgoRepository;
	private final NotificacionCore notificacionCore;
	private final ReglasPoliza reglas;

	/**
	 * @param polizaRepository puerto de persistencia de pólizas
	 * @param riesgoRepository puerto de persistencia de riesgos
	 * @param notificacionCore puerto de salida hacia el CORE (notificación post-commit)
	 * @param reglas           reglas de negocio puras
	 */
	public RiesgoService(PolizaRepository polizaRepository, RiesgoRepository riesgoRepository,
			NotificacionCore notificacionCore, ReglasPoliza reglas) {
		this.polizaRepository = polizaRepository;
		this.riesgoRepository = riesgoRepository;
		this.notificacionCore = notificacionCore;
		this.reglas = reglas;
	}

	/**
	 * Lista los riesgos de una póliza.
	 *
	 * @param polizaId identificador de la póliza
	 * @return riesgos de la póliza
	 * @throws PolizaNoEncontradaException si la póliza no existe
	 */
	@Transactional(readOnly = true)
	public List<Riesgo> listarPorPoliza(Long polizaId) {
		if (!polizaRepository.existsById(polizaId)) {
			throw new PolizaNoEncontradaException(polizaId);
		}
		return riesgoRepository.findByPolizaIdOrderByIdAsc(polizaId);
	}

	/**
	 * Cancela un riesgo activo.
	 *
	 * @param riesgoId identificador del riesgo
	 * @return riesgo cancelado
	 * @throws RiesgoNoEncontradoException si el riesgo no existe
	 */
	@Transactional
	public Riesgo cancelar(Long riesgoId) {
		Riesgo riesgo = riesgoRepository.findById(riesgoId)
				.orElseThrow(() -> new RiesgoNoEncontradoException(riesgoId));
		reglas.validarCancelacionRiesgo(riesgo);

		riesgo.setEstado(EstadoRiesgo.CANCELADO);
		Riesgo riesgoCancelado = riesgoRepository.save(riesgo);
		LOG.info("Riesgo cancelado: id={} polizaId={}", riesgoCancelado.getId(),
				riesgoCancelado.getPoliza() == null ? null : riesgoCancelado.getPoliza().getId());
		notificarCore(EVENTO_CANCELACION_RIESGO,
				riesgoCancelado.getPoliza() == null ? null : riesgoCancelado.getPoliza().getId());
		return riesgoCancelado;
	}

	/**
	 * Notifica al CORE del evento de negocio.
	 *
	 * <p>El envío se difiere hasta después del commit (implementación por
	 * defecto del puerto {@link NotificacionCore}) y es {@code best-effort}: un
	 * fallo de comunicación queda registrado y la cancelación no se revierte.
	 *
	 * @param evento   nombre del evento
	 * @param polizaId identificador de la póliza afectada
	 */
	private void notificarCore(String evento, Long polizaId) {
		notificacionCore.notificar(evento, polizaId);
	}
}
