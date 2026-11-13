package com.jmendez.polizas.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

import com.jmendez.polizas.exception.ReglaNegocioException;
import com.jmendez.polizas.model.EstadoPoliza;
import com.jmendez.polizas.model.EstadoRiesgo;
import com.jmendez.polizas.model.Poliza;
import com.jmendez.polizas.model.Riesgo;
import com.jmendez.polizas.model.TipoPoliza;

/**
 * Reglas de negocio puras de la póliza.
 *
 * <p>Clase sin dependencias de Spring, JPA ni HTTP: el núcleo de negocio se
 * prueba de forma aislada (rápida y enfocada) y se reutiliza desde cualquier
 * adaptador de entrada. Es el equivalente al "dominio" en la arquitectura
 * hexagonal pragmática que adopta este proyecto.
 */
public class ReglasPoliza {

	/** Base para el cálculo de porcentajes. */
	public static final BigDecimal CIENTO = new BigDecimal("100");

	/** Escala decimal del dinero (2 decimales). */
	public static final int ESCALA_MONEDA = 2;

	/**
	 * Regla 1: una póliza individual solo puede tener un riesgo.
	 *
	 * @param tipo          tipo de la póliza
	 * @param cantidadRiesgos número de riesgos que se pretende asociar
	 * @throws ReglaNegocioException si la póliza es individual y recibe más de un riesgo
	 */
	public void validarCantidadDeRiesgos(TipoPoliza tipo, int cantidadRiesgos) {
		if (tipo == TipoPoliza.INDIVIDUAL && cantidadRiesgos > 1) {
			throw new ReglaNegocioException(
					"Una póliza individual solo puede tener un riesgo. Se recibieron " + cantidadRiesgos + " riesgos.");
		}
	}

	/**
	 * Regla 4: solo las pólizas colectivas admiten riesgos adicionales.
	 *
	 * @param poliza póliza destino del nuevo riesgo
	 * @throws ReglaNegocioException si la póliza no es colectiva
	 */
	public void validarTipoParaAgregarRiesgo(Poliza poliza) {
		if (poliza.getTipo() != TipoPoliza.COLECTIVA) {
			throw new ReglaNegocioException("Solo las pólizas colectivas admiten riesgos adicionales. La póliza "
					+ poliza.getId() + " es de tipo " + poliza.getTipo() + ".");
		}
	}

	/**
	 * Regla 2: una póliza cancelada no se puede renovar.
	 *
	 * @param poliza póliza a renovar
	 * @throws ReglaNegocioException si la póliza está cancelada
	 */
	public void validarRenovacion(Poliza poliza) {
		if (poliza.getEstado() == EstadoPoliza.CANCELADA) {
			throw new ReglaNegocioException(
					"No se puede renovar la póliza " + poliza.getId() + " porque está CANCELADA.");
		}
	}

	/**
	 * Una póliza cancelada no se puede volver a cancelar.
	 *
	 * @param poliza póliza a cancelar
	 * @throws ReglaNegocioException si la póliza ya está cancelada
	 */
	public void validarCancelacion(Poliza poliza) {
		if (poliza.getEstado() == EstadoPoliza.CANCELADA) {
			throw new ReglaNegocioException("La póliza " + poliza.getId() + " ya se encuentra CANCELADA.");
		}
	}

	/**
	 * Un riesgo cancelado no se puede volver a cancelar ni si su póliza lo está.
	 *
	 * @param riesgo riesgo a cancelar
	 * @throws ReglaNegocioException si el riesgo ya está cancelado o su póliza está cancelada
	 */
	public void validarCancelacionRiesgo(Riesgo riesgo) {
		if (riesgo.getEstado() == EstadoRiesgo.CANCELADO) {
			throw new ReglaNegocioException("El riesgo " + riesgo.getId() + " ya se encuentra CANCELADO.");
		}
		if (riesgo.getPoliza() != null && riesgo.getPoliza().getEstado() == EstadoPoliza.CANCELADA) {
			throw new ReglaNegocioException("No se puede cancelar el riesgo " + riesgo.getId()
					+ " porque su póliza se encuentra CANCELADA.");
		}
	}

	/**
	 * Invariante de datos: {@code prima = canonMensual × mesesVigencia}.
	 *
	 * @param canonMensual   canon mensual de la póliza
	 * @param mesesVigencia  número de meses de vigencia
	 * @return prima calculada con dos decimales
	 */
	public BigDecimal calcularPrima(BigDecimal canonMensual, int mesesVigencia) {
		return canonMensual.multiply(BigDecimal.valueOf(mesesVigencia)).setScale(ESCALA_MONEDA, RoundingMode.HALF_UP);
	}

	/**
	 * Fórmula de renovación del canon: {@code canon' = canon × (1 + IPC / 100)}.
	 *
	 * @param canonMensual  canon vigente
	 * @param ipcPorcentaje incremento porcentual del IPC (por ejemplo {@code 3.5})
	 * @return canon renewaldo con dos decimales
	 */
	public BigDecimal calcularCanonRenovado(BigDecimal canonMensual, BigDecimal ipcPorcentaje) {
		return canonMensual.multiply(CIENTO.add(ipcPorcentaje))
				.divide(CIENTO, ESCALA_MONEDA, RoundingMode.HALF_UP);
	}

	/**
	 * Renueva la póliza: canon y prima ajustados por el IPC, periodo de vigencia
	 * extendido por el mismo número de meses y estado {@code RENOVADA}.
	 *
	 * @param poliza        póliza a renovar (se modifica en memoria)
	 * @param ipcPorcentaje incremento porcentual del IPC
	 * @throws ReglaNegocioException si la póliza está cancelada
	 */
	public void renovar(Poliza poliza, BigDecimal ipcPorcentaje) {
		validarRenovacion(poliza);

		BigDecimal canonRenovado = calcularCanonRenovado(poliza.getCanonMensual(), ipcPorcentaje);
		poliza.setCanonMensual(canonRenovado);
		poliza.setPrima(calcularPrima(canonRenovado, poliza.getMesesVigencia()));

		LocalDate inicioRenovado = poliza.getFechaFin().plusDays(1);
		LocalDate finRenovado = inicioRenovado.plusMonths(poliza.getMesesVigencia()).minusDays(1);
		poliza.setFechaInicio(inicioRenovado);
		poliza.setFechaFin(finRenovado);

		poliza.setEstado(EstadoPoliza.RENOVADA);
	}

	/**
	 * Regla 3: cancelar la póliza cancela todos sus riesgos.
	 *
	 * @param poliza póliza a cancelar (se modifica en memoria)
	 * @param riesgos riesgos que pertenecen a la póliza
	 * @throws ReglaNegocioException si la póliza ya está cancelada
	 */
	public void cancelar(Poliza poliza, List<Riesgo> riesgos) {
		validarCancelacion(poliza);
		poliza.setEstado(EstadoPoliza.CANCELADA);
		for (Riesgo riesgo : riesgos) {
			riesgo.setEstado(EstadoRiesgo.CANCELADO);
		}
	}
}
