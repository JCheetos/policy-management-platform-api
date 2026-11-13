package com.jmendez.polizas.service;

import java.math.BigDecimal;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jmendez.polizas.config.PropiedadesApp;
import com.jmendez.polizas.domain.ReglasPoliza;
import com.jmendez.polizas.dto.CrearPolizaRequest;
import com.jmendez.polizas.dto.RiesgoRequest;
import com.jmendez.polizas.exception.PolizaNoEncontradaException;
import com.jmendez.polizas.exception.SolicitudInvalidaException;
import com.jmendez.polizas.model.EstadoPoliza;
import com.jmendez.polizas.model.EstadoRiesgo;
import com.jmendez.polizas.model.Poliza;
import com.jmendez.polizas.model.Riesgo;
import com.jmendez.polizas.model.TipoPoliza;
import com.jmendez.polizas.port.CoreGateway;
import com.jmendez.polizas.repository.PolizaRepository;
import com.jmendez.polizas.repository.PolizaSpecifications;
import com.jmendez.polizas.repository.RiesgoRepository;

/**
 * Servicio de aplicación de la póliza.
 *
 * <p>Orquesta las operaciones del agregado de póliza (crear, listar, renovar,
 * cancelar, agregar riesgo) aplicando las reglas de negocio de
 * {@link ReglasPoliza}. No conoce HTTP ni SQL: depende de puertos de
 * persistencia ({@link PolizaRepository}, {@link RiesgoRepository}) y del puerto
 * de salida hacia el CORE ({@link CoreGateway}).
 */
@Service
public class PolizaService {

	private static final Logger LOG = LoggerFactory.getLogger(PolizaService.class);

	/** Evento enviado al CORE al crear una póliza. */
	public static final String EVENTO_CREACION = "CREACION";

	/** Evento enviado al CORE al renovar una póliza. */
	public static final String EVENTO_RENOVACION = "RENOVACION";

	/** Evento enviado al CORE al cancelar una póliza. */
	public static final String EVENTO_CANCELACION = "CANCELACION";

	/** Evento enviado al CORE al agregar un riesgo. */
	public static final String EVENTO_ALTA_RIESGO = "ALTA_RIESGO";

	private final PolizaRepository polizaRepository;
	private final RiesgoRepository riesgoRepository;
	private final CoreGateway coreGateway;
	private final ReglasPoliza reglas;
	private final BigDecimal ipcPorcentaje;

	/**
	 * @param polizaRepository puerto de persistencia de pólizas
	 * @param riesgoRepository puerto de persistencia de riesgos
	 * @param coreGateway      puerto de salida hacia el CORE
	 * @param reglas           reglas de negocio puras
	 * @param propiedades      propiedades de la aplicación (IPC de la renovación)
	 */
	public PolizaService(PolizaRepository polizaRepository, RiesgoRepository riesgoRepository,
			CoreGateway coreGateway, ReglasPoliza reglas, PropiedadesApp propiedades) {
		this.polizaRepository = polizaRepository;
		this.riesgoRepository = riesgoRepository;
		this.coreGateway = coreGateway;
		this.reglas = reglas;
		this.ipcPorcentaje = propiedades.getIpcPorcentaje();
	}

	/**
	 * Lista las pólizas aplicando filtros opcionales por tipo y estado.
	 *
	 * @param tipo   tipo de póliza o {@code null} para no filtrar
	 * @param estado estado de la póliza o {@code null} para no filtrar
	 * @return pólizas que cumplen los filtros indicados
	 */
	@Transactional(readOnly = true)
	public List<Poliza> listar(TipoPoliza tipo, EstadoPoliza estado) {
		return polizaRepository.findAll(PolizaSpecifications.porTipoYEstado(tipo, estado));
	}

	/**
	 * Crea una póliza con sus riesgos iniciales.
	 *
	 * @param solicitud datos de alta de la póliza
	 * @return póliza persistida
	 */
	@Transactional
	public Poliza crear(CrearPolizaRequest solicitud) {
		List<RiesgoRequest> riesgosSolicitados = solicitud.riesgos();
		reglas.validarCantidadDeRiesgos(solicitud.tipo(), riesgosSolicitados.size());
		validarPeriodo(solicitud);

		BigDecimal prima = reglas.calcularPrima(solicitud.canonMensual(), solicitud.mesesVigencia());
		if (solicitud.prima() != null && solicitud.prima().compareTo(prima) != 0) {
			throw new SolicitudInvalidaException("La prima enviada (" + solicitud.prima()
					+ ") no corresponde al canon mensual multiplicado por los meses de vigencia (" + prima + ").");
		}

		Poliza poliza = new Poliza();
		poliza.setTipo(solicitud.tipo());
		poliza.setEstado(EstadoPoliza.VIGENTE);
		poliza.setFechaInicio(solicitud.fechaInicio());
		poliza.setFechaFin(solicitud.fechaFin());
		poliza.setMesesVigencia(solicitud.mesesVigencia());
		poliza.setCanonMensual(solicitud.canonMensual());
		poliza.setPrima(prima);
		poliza.setNombreTomador(solicitud.nombreTomador());
		poliza.setNombreAsegurado(solicitud.nombreAsegurado());
		poliza.setNombreBeneficiario(solicitud.nombreBeneficiario());

		for (RiesgoRequest riesgoSolicitado : riesgosSolicitados) {
			poliza.agregarRiesgo(construirRiesgo(riesgoSolicitado));
		}

		Poliza polizaGuardada = polizaRepository.save(poliza);
		LOG.info("Póliza creada: id={} tipo={} canon={} prima={}", polizaGuardada.getId(), polizaGuardada.getTipo(),
				polizaGuardada.getCanonMensual(), polizaGuardada.getPrima());
		notificarCore(EVENTO_CREACION, polizaGuardada.getId());
		return polizaGuardada;
	}

	/**
	 * Renueva la póliza incrementando canon y prima según el IPC configurado.
	 *
	 * @param polizaId identificador de la póliza
	 * @return póliza renovada
	 */
	@Transactional
	public Poliza renovar(Long polizaId) {
		Poliza poliza = obtener(polizaId);
		reglas.renovar(poliza, ipcPorcentaje);
		Poliza polizaRenovada = polizaRepository.save(poliza);
		LOG.info("Póliza renovada: id={} canon={} prima={} ipc={}%", polizaRenovada.getId(),
				polizaRenovada.getCanonMensual(), polizaRenovada.getPrima(), ipcPorcentaje);
		notificarCore(EVENTO_RENOVACION, polizaRenovada.getId());
		return polizaRenovada;
	}

	/**
	 * Cancela la póliza y, en cascada, todos sus riesgos.
	 *
	 * @param polizaId identificador de la póliza
	 * @return póliza cancelada
	 */
	@Transactional
	public Poliza cancelar(Long polizaId) {
		Poliza poliza = obtener(polizaId);
		List<Riesgo> riesgos = riesgoRepository.findByPolizaIdOrderByIdAsc(polizaId);
		reglas.cancelar(poliza, riesgos);
		polizaRepository.save(poliza);
		riesgoRepository.saveAll(riesgos);
		LOG.info("Póliza cancelada: id={} riesgosCancelados={}", poliza.getId(), riesgos.size());
		notificarCore(EVENTO_CANCELACION, poliza.getId());
		return poliza;
	}

	/**
	 * Agrega un riesgo a una póliza colectiva.
	 *
	 * @param polizaId   identificador de la póliza
	 * @param solicitud  datos del nuevo riesgo
	 * @return riesgo creado
	 */
	@Transactional
	public Riesgo agregarRiesgo(Long polizaId, RiesgoRequest solicitud) {
		Poliza poliza = obtener(polizaId);
		reglas.validarTipoParaAgregarRiesgo(poliza);

		Riesgo riesgo = construirRiesgo(solicitud);
		riesgo.setPoliza(poliza);
		Riesgo riesgoGuardado = riesgoRepository.save(riesgo);
		LOG.info("Riesgo agregado: id={} polizaId={}", riesgoGuardado.getId(), polizaId);
		notificarCore(EVENTO_ALTA_RIESGO, polizaId);
		return riesgoGuardado;
	}

	/**
	 * Obtiene la póliza indicada o lanza el error de recurso no encontrado.
	 *
	 * @param polizaId identificador de la póliza
	 * @return póliza solicitada
	 */
	@Transactional(readOnly = true)
	public Poliza obtener(Long polizaId) {
		return polizaRepository.findById(polizaId)
				.orElseThrow(() -> new PolizaNoEncontradaException(polizaId));
	}

	private void validarPeriodo(CrearPolizaRequest solicitud) {
		if (solicitud.fechaFin().isBefore(solicitud.fechaInicio())) {
			throw new SolicitudInvalidaException("La fecha de fin de la vigencia (" + solicitud.fechaFin()
					+ ") no puede ser anterior a la fecha de inicio (" + solicitud.fechaInicio() + ").");
		}
	}

	private Riesgo construirRiesgo(RiesgoRequest solicitud) {
		Riesgo riesgo = new Riesgo();
		riesgo.setDescripcion(solicitud.descripcion());
		riesgo.setValorAsegurado(solicitud.valorAsegurado());
		riesgo.setEstado(EstadoRiesgo.ACTIVO);
		return riesgo;
	}

	/**
	 * Notifica al CORE de forma {@code best-effort}: si la comunicación falla,
	 * el error queda registrado y la operación de negocio no se revierte.
	 */
	private void notificarCore(String evento, Long polizaId) {
		try {
			coreGateway.notificarEvento(evento, polizaId);
		} catch (Exception excepcion) {
			LOG.error("No fue posible notificar al CORE el evento {} de la póliza {}. La operación de negocio se mantiene.",
					evento, polizaId, excepcion);
		}
	}
}
