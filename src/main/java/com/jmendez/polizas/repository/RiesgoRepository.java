package com.jmendez.polizas.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.jmendez.polizas.model.Riesgo;

/**
 * Repositorio (puerto de persistencia) del riesgo asegurado.
 */
@Repository
public interface RiesgoRepository extends JpaRepository<Riesgo, Long> {

	/**
	 * Lista los riesgos de una póliza ordenados por identificador.
	 *
	 * @param polizaId identificador de la póliza
	 * @return riesgos que pertenecen a la póliza
	 */
	List<Riesgo> findByPolizaIdOrderByIdAsc(Long polizaId);
}
