package com.jmendez.polizas.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import com.jmendez.polizas.model.Poliza;

/**
 * Repositorio (puerto de persistencia) de la póliza.
 *
 * <p>Expone operaciones de lectura y escritura sobre la tabla {@code polizas}.
 * El filtrado por tipo y estado se resuelve con especificaciones, de modo que
 * los filtros opcionales se combinen sin consultas duplicadas.
 */
@Repository
public interface PolizaRepository extends JpaRepository<Poliza, Long>, JpaSpecificationExecutor<Poliza> {
}
