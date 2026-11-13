package com.jmendez.polizas.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

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

	@Autowired
	private TestEntityManager em;

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

	@DisplayName("Una póliza sin riesgos devuelve una lista vacía")
	@Test
	void unaPolizaSinRiesgosDevuelveListaVacia() {
		Poliza poliza = poliza();

		List<Riesgo> riesgos = riesgoRepository.findByPolizaIdOrderByIdAsc(poliza.getId());

		assertThat(riesgos).isEmpty();
	}

	@DisplayName("La versión del riesgo se incrementa en cada modificación")
	@Test
	void laVersionDelRiesgoSeIncrementaEnCadaModificacion() {
		Poliza poliza = poliza();
		Riesgo riesgo = riesgoRepository.save(riesgo(poliza, "Incendio"));
		em.flush();
		em.clear();
		Riesgo recargado = riesgoRepository.findById(riesgo.getId()).orElseThrow();
		Integer versionInicial = recargado.getVersion();

		recargado.setEstado(EstadoRiesgo.CANCELADO);
		riesgoRepository.save(recargado);
		em.flush();
		em.clear();

		assertThat(riesgoRepository.findById(riesgo.getId()).orElseThrow().getVersion())
				.isEqualTo(versionInicial + 1);
	}
}
