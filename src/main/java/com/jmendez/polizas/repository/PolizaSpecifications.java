package com.jmendez.polizas.repository;

import java.util.List;

import org.springframework.data.jpa.domain.Specification;

import com.jmendez.polizas.model.EstadoPoliza;
import com.jmendez.polizas.model.Poliza;
import com.jmendez.polizas.model.TipoPoliza;

import jakarta.persistence.criteria.Predicate;

/**
 * Especificaciones de filtrado sobre la póliza.
 *
 * <p>Centraliza la construcción de criterios para que los filtros opcionales
 * (tipo y estado) se combinen de forma consistente y sean verificables contra
 * una base de datos real.
 */
public final class PolizaSpecifications {

	private PolizaSpecifications() {
	}

	/**
	 * Construye el criterio de filtrado por tipo y/o estado.
	 *
	 * @param tipo   tipo de póliza o {@code null} para no filtrar por tipo
	 * @param estado estado de la póliza o {@code null} para no filtrar por estado
	 * @return especificación con los filtros indicados
	 */
	public static Specification<Poliza> porTipoYEstado(TipoPoliza tipo, EstadoPoliza estado) {
		return (raiz, consulta, constructores) -> {
			List<Predicate> condiciones = new java.util.ArrayList<>();
			if (tipo != null) {
				condiciones.add(constructores.equal(raiz.get("tipo"), tipo));
			}
			if (estado != null) {
				condiciones.add(constructores.equal(raiz.get("estado"), estado));
			}
			return constructores.and(condiciones.toArray(new Predicate[0]));
		};
	}
}
