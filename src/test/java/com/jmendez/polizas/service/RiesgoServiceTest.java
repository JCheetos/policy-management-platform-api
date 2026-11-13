package com.jmendez.polizas.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.jmendez.polizas.domain.ReglasPoliza;
import com.jmendez.polizas.exception.PolizaNoEncontradaException;
import com.jmendez.polizas.exception.ReglaNegocioException;
import com.jmendez.polizas.exception.RiesgoNoEncontradoException;
import com.jmendez.polizas.model.EstadoPoliza;
import com.jmendez.polizas.model.EstadoRiesgo;
import com.jmendez.polizas.model.Poliza;
import com.jmendez.polizas.model.Riesgo;
import com.jmendez.polizas.model.TipoPoliza;
import com.jmendez.polizas.port.NotificacionCore;
import com.jmendez.polizas.repository.PolizaRepository;
import com.jmendez.polizas.repository.RiesgoRepository;

/**
 * Pruebas unitarias del servicio de riesgo.
 */
@ExtendWith(MockitoExtension.class)
class RiesgoServiceTest {

	@Mock
	private PolizaRepository polizaRepository;

	@Mock
	private RiesgoRepository riesgoRepository;

	@Mock
	private NotificacionCore notificacionCore;

	private RiesgoService servicio;

	@BeforeEach
	void setUp() {
		servicio = new RiesgoService(polizaRepository, riesgoRepository, notificacionCore, new ReglasPoliza());
	}

	private Poliza poliza(EstadoPoliza estado) {
		Poliza poliza = new Poliza();
		poliza.setId(1L);
		poliza.setTipo(TipoPoliza.COLECTIVA);
		poliza.setEstado(estado);
		return poliza;
	}

	private Riesgo riesgo(EstadoRiesgo estado, EstadoPoliza estadoPoliza) {
		Riesgo riesgo = new Riesgo();
		riesgo.setId(5L);
		riesgo.setEstado(estado);
		riesgo.setDescripcion("Incendio");
		riesgo.setValorAsegurado(new BigDecimal("1000000"));
		riesgo.setPoliza(poliza(estadoPoliza));
		return riesgo;
	}

	@DisplayName("Listar los riesgos de una póliza existente devuelve sus riesgos")
	@Test
	void listarRiesgosDePolizaExistente() {
		Riesgo riesgo = riesgo(EstadoRiesgo.ACTIVO, EstadoPoliza.VIGENTE);
		when(polizaRepository.existsById(1L)).thenReturn(true);
		when(riesgoRepository.findByPolizaIdOrderByIdAsc(1L)).thenReturn(List.of(riesgo));

		List<Riesgo> resultado = servicio.listarPorPoliza(1L);

		assertThat(resultado).containsExactly(riesgo);
	}

	@DisplayName("Listar los riesgos de una póliza inexistente devuelve el error de recurso no encontrado")
	@Test
	void listarRiesgosDePolizaInexistenteFalla() {
		when(polizaRepository.existsById(99L)).thenReturn(false);

		assertThatThrownBy(() -> servicio.listarPorPoliza(99L)).isInstanceOf(PolizaNoEncontradaException.class);
		verify(riesgoRepository, never()).findByPolizaIdOrderByIdAsc(any());
	}

	@DisplayName("Cancelar un riesgo activo lo deja en estado CANCELADO y notifica al CORE")
	@Test
	void cancelarRiesgoActivo() {
		Riesgo riesgo = riesgo(EstadoRiesgo.ACTIVO, EstadoPoliza.VIGENTE);
		when(riesgoRepository.findById(5L)).thenReturn(Optional.of(riesgo));
		when(riesgoRepository.save(any(Riesgo.class))).thenAnswer(invocacion -> invocacion.getArgument(0));

		Riesgo cancelado = servicio.cancelar(5L);

		assertThat(cancelado.getEstado()).isEqualTo(EstadoRiesgo.CANCELADO);
		verify(notificacionCore).notificar(RiesgoService.EVENTO_CANCELACION_RIESGO, 1L);
	}

	@DisplayName("Cancelar un riesgo inexistente devuelve el error de recurso no encontrado")
	@Test
	void cancelarRiesgoInexistenteFalla() {
		when(riesgoRepository.findById(99L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> servicio.cancelar(99L)).isInstanceOf(RiesgoNoEncontradoException.class);
	}

	@DisplayName("Cancelar un riesgo ya cancelado viola la regla de negocio")
	@Test
	void cancelarRiesgoYaCanceladoFalla() {
		when(riesgoRepository.findById(5L))
				.thenReturn(Optional.of(riesgo(EstadoRiesgo.CANCELADO, EstadoPoliza.VIGENTE)));

		assertThatThrownBy(() -> servicio.cancelar(5L))
				.isInstanceOf(ReglaNegocioException.class)
				.hasMessageContaining("ya se encuentra CANCELADO");

		verify(notificacionCore, never()).notificar(any(), any());
	}

	@DisplayName("Cancelar un riesgo de una póliza cancelada viola la regla de negocio")
	@Test
	void cancelarRiesgoDePolizaCanceladaFalla() {
		when(riesgoRepository.findById(5L))
				.thenReturn(Optional.of(riesgo(EstadoRiesgo.ACTIVO, EstadoPoliza.CANCELADA)));

		assertThatThrownBy(() -> servicio.cancelar(5L))
				.isInstanceOf(ReglaNegocioException.class)
				.hasMessageContaining("su póliza se encuentra CANCELADA");
	}

	@DisplayName("La cancelación delega la notificación al CORE en el puerto de salida")
	@Test
	void laNotificacionAlCoreSeDelegaEnElPuertoDeSalida() {
		Riesgo riesgo = riesgo(EstadoRiesgo.ACTIVO, EstadoPoliza.VIGENTE);
		when(riesgoRepository.findById(5L)).thenReturn(Optional.of(riesgo));
		when(riesgoRepository.save(any(Riesgo.class))).thenAnswer(invocacion -> invocacion.getArgument(0));

		Riesgo cancelado = servicio.cancelar(5L);

		assertThat(cancelado.getEstado()).isEqualTo(EstadoRiesgo.CANCELADO);
		verify(notificacionCore).notificar(RiesgoService.EVENTO_CANCELACION_RIESGO, 1L);
	}
}
