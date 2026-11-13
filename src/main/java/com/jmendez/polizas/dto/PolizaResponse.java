package com.jmendez.polizas.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.jmendez.polizas.model.EstadoPoliza;
import com.jmendez.polizas.model.Poliza;
import com.jmendez.polizas.model.TipoPoliza;

/**
 * Respuesta con la información de una póliza.
 *
 * @param id                identificador de la póliza
 * @param tipo              tipo de póliza
 * @param estado            estado actual de la póliza
 * @param fechaInicio       inicio de la vigencia
 * @param fechaFin          fin de la vigencia
 * @param mesesVigencia     meses de vigencia
 * @param canonMensual      canon mensual
 * @param prima             prima (canon mensual × meses de vigencia)
 * @param nombreTomador     nombre del tomador
 * @param nombreAsegurado   nombre del asegurado
 * @param nombreBeneficiario nombre del beneficiario
 */
public record PolizaResponse(
		Long id,
		TipoPoliza tipo,
		EstadoPoliza estado,
		LocalDate fechaInicio,
		LocalDate fechaFin,
		Integer mesesVigencia,
		BigDecimal canonMensual,
		BigDecimal prima,
		String nombreTomador,
		String nombreAsegurado,
		String nombreBeneficiario) {

	/**
	 * Proyecta la entidad de dominio al contrato de salida.
	 *
	 * @param poliza póliza de origen
	 * @return respuesta con los datos de la póliza
	 */
	public static PolizaResponse desde(Poliza poliza) {
		return new PolizaResponse(
				poliza.getId(),
				poliza.getTipo(),
				poliza.getEstado(),
				poliza.getFechaInicio(),
				poliza.getFechaFin(),
				poliza.getMesesVigencia(),
				poliza.getCanonMensual(),
				poliza.getPrima(),
				poliza.getNombreTomador(),
				poliza.getNombreAsegurado(),
				poliza.getNombreBeneficiario());
	}
}
