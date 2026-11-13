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
 * Pruebas de persistencia del riesgo asegurado contra H2.
 */
@DataJpaTest
class RiesgoRepositoryTest {

	@Autowired
	private PolizaRepository polizaRepository;

	@Autowired
	private RiesgoRepository riesgoRepository;

	private Poliza poliza() {
		Poliza poliza = new Poliza();
		poliza.setTipo(TipoPoliza.COLECTIVA);
		poliza.setEstado(EstadoPoliza.VIGENTE);
		poliza.setFechaInicio(LocalDate.of(2026, 1, 1));
		poliza.setFechaFin(LocalDate.of(2026, 12, 31));
		poliza.setMesesVigencia(12);
		poliza.setCanonMensual(new BigDecimal("100000"));
		poliza.setPrima(new BigDecimal("1200000.00"));
		return polizaRepository.save(poliza);
	}

	private Riesgo riesgo(Poliza poliza, String descripcion) {
		Riesgo riesgo = new Riesgo();
		riesgo.setPoliza(poliza);
		riesgo.setDescripcion(descripcion);
		riesgo.setValorAsegurado(new BigDecimal("500000"));
		riesgo.setEstado(EstadoRiesgo.ACTIVO);
		return riesgo;
	}

	@DisplayName("Guarda y recupera un riesgo asociado a su póliza")
	@Test
	void guardaYRecuperaElRiesgo() {
		Poliza poliza = poliza();

		Riesgo guardado = riesgoRepository.save(riesgo(poliza, "Incendio"));

		Riesgo recuperado = riesgoRepository.findById(guardado.getId()).orElseThrow();
		assertThat(recuperado.getDescripcion()).isEqualTo("Incendio");
		assertThat(recuperado.getEstado()).isEqualTo(EstadoRiesgo.ACTIVO);
		assertThat(recuperado.getPoliza().getId()).isEqualTo(poliza.getId());
	}

	@DisplayName("Lista los riesgos de una póliza ordenados por identificador")
	@Test
	void listaLosRiesgosDeUnaPolizaOrdenados() {
		Poliza poliza = poliza();
		riesgoRepository.save(riesgo(poliza, "Incendio"));
		riesgoRepository.save(riesgo(poliza, "Robo"));

		List<Riesgo> riesgos = riesgoRepository.findByPolizaIdOrderByIdAsc(poliza.getId());

		assertThat(riesgos).hasSize(2);
		assertThat(riesgos.get(0).getId()).isLessThan(riesgos.get(1).getId());
	}

	@DisplayName("Filtra los riesgos de una póliza por estado")
	@Test
	void filtraLosRiesgosPorEstado() {
		Poliza poliza = poliza();
		Riesgo activo = riesgoRepository.save(riesgo(poliza, "Incendio"));
		Riesgo cancelado = riesgoRepository.save(riesgo(poliza, "Robo"));
		cancelado.setEstado(EstadoRiesgo.CANCELADO);
		riesgoRepository.save(cancelado);

		List<Riesgo> riesgos = riesgoRepository.findByPolizaIdAndEstadoOrderByIdAsc(poliza.getId(), EstadoRiesgo.ACTIVO);

		assertThat(riesgos).hasSize(1);
		assertThat(riesgos.get(0).getId()).isEqualTo(activo.getId());
	}

	@DisplayName("Una póliza sin riesgos devuelve una lista vacía")
	@Test
	void unaPolizaSinRiesgosDevuelveListaVacia() {
		Poliza poliza = poliza();

		List<Riesgo> riesgos = riesgoRepository.findByPolizaIdOrderByIdAsc(poliza.getId());

		assertThat(riesgos).isEmpty();
	}
}
