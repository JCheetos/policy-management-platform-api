package com.jmendez.polizas.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import com.jmendez.polizas.model.EstadoPoliza;
import com.jmendez.polizas.model.EstadoRiesgo;
import com.jmendez.polizas.model.Poliza;
import com.jmendez.polizas.model.Riesgo;
import com.jmendez.polizas.model.TipoPoliza;

/**
 * Pruebas de persistencia de la póliza contra H2.
 */
@DataJpaTest
class PolizaRepositoryTest {

	@Autowired
	private PolizaRepository polizaRepository;

	private Poliza poliza(TipoPoliza tipo, EstadoPoliza estado) {
		Poliza poliza = new Poliza();
		poliza.setTipo(tipo);
		poliza.setEstado(estado);
		poliza.setFechaInicio(LocalDate.of(2026, 1, 1));
		poliza.setFechaFin(LocalDate.of(2026, 12, 31));
		poliza.setMesesVigencia(12);
		poliza.setCanonMensual(new BigDecimal("100000"));
		poliza.setPrima(new BigDecimal("1200000.00"));
		return poliza;
	}

	@DisplayName("Guarda y recupera una póliza con sus riesgos en cascada")
	@Test
	void guardaYRecuperaLaPoliza() {
		Poliza poliza = poliza(TipoPoliza.COLECTIVA, EstadoPoliza.VIGENTE);
		Riesgo riesgo = new Riesgo();
		riesgo.setDescripcion("Incendio");
		riesgo.setValorAsegurado(new BigDecimal("500000"));
		riesgo.setEstado(EstadoRiesgo.ACTIVO);
		poliza.agregarRiesgo(riesgo);

		Poliza guardada = polizaRepository.save(poliza);

		Poliza recuperada = polizaRepository.findById(guardada.getId()).orElseThrow();
		assertThat(recuperada.getId()).isNotNull();
		assertThat(recuperada.getTipo()).isEqualTo(TipoPoliza.COLECTIVA);
		assertThat(recuperada.getEstado()).isEqualTo(EstadoPoliza.VIGENTE);
		assertThat(recuperada.getRiesgos()).hasSize(1);
		assertThat(recuperada.getRiesgos().get(0).getDescripcion()).isEqualTo("Incendio");
	}

	@DisplayName("Filtra las pólizas por tipo")
	@Test
	void filtraPorTipo() {
		polizaRepository.save(poliza(TipoPoliza.COLECTIVA, EstadoPoliza.VIGENTE));
		polizaRepository.save(poliza(TipoPoliza.INDIVIDUAL, EstadoPoliza.VIGENTE));

		List<Poliza> resultado = polizaRepository.findAll(PolizaSpecifications.porTipoYEstado(TipoPoliza.COLECTIVA, null));

		assertThat(resultado).hasSize(1);
		assertThat(resultado.get(0).getTipo()).isEqualTo(TipoPoliza.COLECTIVA);
	}

	@DisplayName("Filtra las pólizas por estado")
	@Test
	void filtraPorEstado() {
		polizaRepository.save(poliza(TipoPoliza.COLECTIVA, EstadoPoliza.VIGENTE));
		polizaRepository.save(poliza(TipoPoliza.COLECTIVA, EstadoPoliza.CANCELADA));

		List<Poliza> resultado = polizaRepository.findAll(PolizaSpecifications.porTipoYEstado(null, EstadoPoliza.CANCELADA));

		assertThat(resultado).hasSize(1);
		assertThat(resultado.get(0).getEstado()).isEqualTo(EstadoPoliza.CANCELADA);
	}

	@DisplayName("Filtra las pólizas por tipo y estado a la vez")
	@Test
	void filtraPorTipoYEstado() {
		polizaRepository.save(poliza(TipoPoliza.COLECTIVA, EstadoPoliza.VIGENTE));
		polizaRepository.save(poliza(TipoPoliza.COLECTIVA, EstadoPoliza.CANCELADA));
		polizaRepository.save(poliza(TipoPoliza.INDIVIDUAL, EstadoPoliza.VIGENTE));

		List<Poliza> resultado = polizaRepository
				.findAll(PolizaSpecifications.porTipoYEstado(TipoPoliza.COLECTIVA, EstadoPoliza.VIGENTE));

		assertThat(resultado).hasSize(1);
		assertThat(resultado.get(0).getTipo()).isEqualTo(TipoPoliza.COLECTIVA);
		assertThat(resultado.get(0).getEstado()).isEqualTo(EstadoPoliza.VIGENTE);
	}

	@DisplayName("Sin filtros devuelve todas las pólizas")
	@Test
	void sinFiltrosDevuelveTodasLasPolizas() {
		polizaRepository.save(poliza(TipoPoliza.COLECTIVA, EstadoPoliza.VIGENTE));
		polizaRepository.save(poliza(TipoPoliza.INDIVIDUAL, EstadoPoliza.VIGENTE));

		List<Poliza> resultado = polizaRepository.findAll(PolizaSpecifications.porTipoYEstado(null, null));

		assertThat(resultado).hasSize(2);
	}

	@DisplayName("Un filtro sin coincidencias devuelve una lista vacía")
	@Test
	void unFiltroSinCoincidenciasDevuelveListaVacia() {
		polizaRepository.save(poliza(TipoPoliza.COLECTIVA, EstadoPoliza.VIGENTE));

		List<Poliza> resultado = polizaRepository.findAll(PolizaSpecifications.porTipoYEstado(TipoPoliza.INDIVIDUAL, null));

		assertThat(resultado).isEmpty();
	}
}
