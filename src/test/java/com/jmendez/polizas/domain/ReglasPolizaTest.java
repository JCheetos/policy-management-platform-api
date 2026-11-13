package com.jmendez.polizas.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.jmendez.polizas.exception.ReglaNegocioException;
import com.jmendez.polizas.model.EstadoPoliza;
import com.jmendez.polizas.model.EstadoRiesgo;
import com.jmendez.polizas.model.Poliza;
import com.jmendez.polizas.model.Riesgo;
import com.jmendez.polizas.model.TipoPoliza;

/**
 * Pruebas unitarias del núcleo de reglas de negocio.
 *
 * <p>Clase pura: se verifica sin Spring, sin base de datos y sin HTTP.
 */
class ReglasPolizaTest {

	private final ReglasPoliza reglas = new ReglasPoliza();

	private static Poliza poliza(TipoPoliza tipo, EstadoPoliza estado) {
		Poliza poliza = new Poliza();
		poliza.setId(10L);
		poliza.setTipo(tipo);
		poliza.setEstado(estado);
		return poliza;
	}

	private static Riesgo riesgo(EstadoRiesgo estado, EstadoPoliza estadoPoliza) {
		Riesgo riesgo = new Riesgo();
		riesgo.setId(20L);
		riesgo.setEstado(estado);
		riesgo.setPoliza(poliza(TipoPoliza.COLECTIVA, estadoPoliza));
		return riesgo;
	}

	@DisplayName("Regla 1: una póliza individual puede tener un solo riesgo")
	@Test
	void unaPolizaIndividualAdmiteUnUnicoRiesgo() {
		reglas.validarCantidadDeRiesgos(TipoPoliza.INDIVIDUAL, 1);
		reglas.validarCantidadDeRiesgos(TipoPoliza.COLECTIVA, 5);
	}

	@DisplayName("Regla 1: una póliza individual con más de un riesgo viola la regla")
	@Test
	void unaPolizaIndividualNoAdmiteVariosRiesgos() {
		assertThatThrownBy(() -> reglas.validarCantidadDeRiesgos(TipoPoliza.INDIVIDUAL, 2))
				.isInstanceOf(ReglaNegocioException.class)
				.hasMessageContaining("Una póliza individual solo puede tener un riesgo");
	}

	@DisplayName("Regla 4: solo las pólizas colectivas admiten riesgos adicionales")
	@Test
	void soloLasColectivasAdmitenRiesgosAdicionales() {
		reglas.validarTipoParaAgregarRiesgo(poliza(TipoPoliza.COLECTIVA, EstadoPoliza.VIGENTE));

		assertThatThrownBy(() -> reglas.validarTipoParaAgregarRiesgo(poliza(TipoPoliza.INDIVIDUAL, EstadoPoliza.VIGENTE)))
				.isInstanceOf(ReglaNegocioException.class)
				.hasMessageContaining("Solo las pólizas colectivas admiten riesgos adicionales");
	}

	@DisplayName("Regla 2: no se puede renovar una póliza cancelada")
	@Test
	void unaPolizaCanceladaNoSePuedeRenovar() {
		reglas.validarRenovacion(poliza(TipoPoliza.COLECTIVA, EstadoPoliza.VIGENTE));
		reglas.validarRenovacion(poliza(TipoPoliza.COLECTIVA, EstadoPoliza.RENOVADA));

		assertThatThrownBy(() -> reglas.validarRenovacion(poliza(TipoPoliza.COLECTIVA, EstadoPoliza.CANCELADA)))
				.isInstanceOf(ReglaNegocioException.class)
				.hasMessageContaining("No se puede renovar la póliza 10 porque está CANCELADA");
	}

	@DisplayName("Una póliza cancelada no se puede volver a cancelar")
	@Test
	void unaPolizaCanceladaNoSePuedeVolverACancelar() {
		assertThatThrownBy(() -> reglas.validarCancelacion(poliza(TipoPoliza.COLECTIVA, EstadoPoliza.CANCELADA)))
				.isInstanceOf(ReglaNegocioException.class)
				.hasMessageContaining("ya se encuentra CANCELADA");
	}

	@DisplayName("Un riesgo cancelado no se puede volver a cancelar")
	@Test
	void unRiesgoCanceladoNoSePuedeVolverACancelar() {
		assertThatThrownBy(() -> reglas.validarCancelacionRiesgo(riesgo(EstadoRiesgo.CANCELADO, EstadoPoliza.VIGENTE)))
				.isInstanceOf(ReglaNegocioException.class)
				.hasMessageContaining("ya se encuentra CANCELADO");
	}

	@DisplayName("Un riesgo de una póliza cancelada no se puede cancelar")
	@Test
	void unRiesgoDePolizaCanceladaNoSePuedeCancelar() {
		assertThatThrownBy(() -> reglas.validarCancelacionRiesgo(riesgo(EstadoRiesgo.ACTIVO, EstadoPoliza.CANCELADA)))
				.isInstanceOf(ReglaNegocioException.class)
				.hasMessageContaining("su póliza se encuentra CANCELADA");
	}

	@DisplayName("Invariante: la prima es el canon mensual multiplicado por los meses de vigencia")
	@Test
	void laPrimaEsElCanonPorLosMesesDeVigencia() {
		BigDecimal prima = reglas.calcularPrima(new BigDecimal("2500000"), 12);

		assertThat(prima).isEqualByComparingTo(new BigDecimal("30000000.00"));
		assertThat(prima.scale()).isEqualTo(2);
	}

	@DisplayName("Fórmula de renovación: canon' = canon × (1 + IPC/100)")
	@Test
	void elCanonSeIncrementaSegunElIpc() {
		BigDecimal canonRenovado = reglas.calcularCanonRenovado(new BigDecimal("100000"), new BigDecimal("3.5"));

		assertThat(canonRenovado).isEqualByComparingTo(new BigDecimal("103500.00"));
	}

	@DisplayName("La renovación redondea el canon a dos decimales")
	@Test
	void laRenovacionRedondeaADosDecimales() {
		BigDecimal canonRenovado = reglas.calcularCanonRenovado(new BigDecimal("1000.55"), new BigDecimal("3.5"));

		assertThat(canonRenovado).isEqualByComparingTo(new BigDecimal("1035.57"));
		assertThat(canonRenovado.scale()).isEqualTo(2);
	}

	@DisplayName("La renovación actualiza canon, prima, periodo de vigencia y estado")
	@Test
	void laRenovacionActualizaLaPolizaCompleta() {
		Poliza poliza = poliza(TipoPoliza.COLECTIVA, EstadoPoliza.VIGENTE);
		poliza.setCanonMensual(new BigDecimal("100000"));
		poliza.setPrima(new BigDecimal("1200000.00"));
		poliza.setMesesVigencia(12);
		poliza.setFechaInicio(LocalDate.of(2026, 1, 1));
		poliza.setFechaFin(LocalDate.of(2026, 12, 31));

		reglas.renovar(poliza, new BigDecimal("3.5"));

		assertThat(poliza.getCanonMensual()).isEqualByComparingTo(new BigDecimal("103500.00"));
		assertThat(poliza.getPrima()).isEqualByComparingTo(new BigDecimal("1242000.00"));
		assertThat(poliza.getFechaInicio()).isEqualTo(LocalDate.of(2027, 1, 1));
		assertThat(poliza.getFechaFin()).isEqualTo(LocalDate.of(2027, 12, 31));
		assertThat(poliza.getEstado()).isEqualTo(EstadoPoliza.RENOVADA);
	}

	@DisplayName("Regla 3: cancelar la póliza cancela todos sus riesgos")
	@Test
	void cancelarLaPolizaCancelaSusRiesgos() {
		Poliza poliza = poliza(TipoPoliza.COLECTIVA, EstadoPoliza.VIGENTE);
		List<Riesgo> riesgos = new ArrayList<>();
		riesgos.add(riesgo(EstadoRiesgo.ACTIVO, EstadoPoliza.VIGENTE));
		riesgos.add(riesgo(EstadoRiesgo.ACTIVO, EstadoPoliza.VIGENTE));

		reglas.cancelar(poliza, riesgos);

		assertThat(poliza.getEstado()).isEqualTo(EstadoPoliza.CANCELADA);
		assertThat(riesgos).allMatch(riesgo -> riesgo.getEstado() == EstadoRiesgo.CANCELADO);
	}

	@DisplayName("Cancelar una póliza sin riesgos no falla")
	@Test
	void cancelarUnaPolizaSinRiesgosNoFalla() {
		Poliza poliza = poliza(TipoPoliza.INDIVIDUAL, EstadoPoliza.VIGENTE);

		reglas.cancelar(poliza, List.of());

		assertThat(poliza.getEstado()).isEqualTo(EstadoPoliza.CANCELADA);
	}
}
