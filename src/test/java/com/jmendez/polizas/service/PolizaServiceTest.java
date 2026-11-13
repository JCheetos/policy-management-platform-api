package com.jmendez.polizas.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.jmendez.polizas.config.PropiedadesApp;
import com.jmendez.polizas.domain.ReglasPoliza;
import com.jmendez.polizas.dto.CrearPolizaRequest;
import com.jmendez.polizas.dto.RiesgoRequest;
import com.jmendez.polizas.exception.PolizaNoEncontradaException;
import com.jmendez.polizas.exception.ReglaNegocioException;
import com.jmendez.polizas.exception.SolicitudInvalidaException;
import com.jmendez.polizas.model.EstadoPoliza;
import com.jmendez.polizas.model.EstadoRiesgo;
import com.jmendez.polizas.model.Poliza;
import com.jmendez.polizas.model.Riesgo;
import com.jmendez.polizas.model.TipoPoliza;
import com.jmendez.polizas.port.NotificacionCore;
import com.jmendez.polizas.repository.PolizaRepository;
import com.jmendez.polizas.repository.RiesgoRepository;

/**
 * Pruebas unitarias del servicio de póliza.
 *
 * <p>Los puertos (repositorios y CORE) se sustituyen por dobles de prueba: se
 * verifica el cumplimiento de las reglas de negocio y la notificación al CORE
 * sin tocar base de datos ni HTTP.
 */
@ExtendWith(MockitoExtension.class)
class PolizaServiceTest {

	@Mock
	private PolizaRepository polizaRepository;

	@Mock
	private RiesgoRepository riesgoRepository;

	@Mock
	private NotificacionCore notificacionCore;

	private PolizaService servicio;

	@BeforeEach
	void setUp() {
		PropiedadesApp propiedades = new PropiedadesApp();
		propiedades.setIpcPorcentaje(new BigDecimal("3.5"));
		servicio = new PolizaService(polizaRepository, riesgoRepository, notificacionCore, new ReglasPoliza(), propiedades);
	}

	private CrearPolizaRequest solicitudColectiva() {
		return new CrearPolizaRequest(TipoPoliza.COLECTIVA, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), 12,
				new BigDecimal("2500000"), new BigDecimal("30000000.00"), "Inmobiliaria Andes", "Arrendatarios",
				"Beneficiario", List.of(new RiesgoRequest("Incendio", new BigDecimal("80000000"))));
	}

	private Poliza polizaVigente() {
		Poliza poliza = new Poliza();
		poliza.setId(1L);
		poliza.setTipo(TipoPoliza.COLECTIVA);
		poliza.setEstado(EstadoPoliza.VIGENTE);
		poliza.setFechaInicio(LocalDate.of(2026, 1, 1));
		poliza.setFechaFin(LocalDate.of(2026, 12, 31));
		poliza.setMesesVigencia(12);
		poliza.setCanonMensual(new BigDecimal("100000"));
		poliza.setPrima(new BigDecimal("1200000.00"));
		return poliza;
	}

	@DisplayName("Crear una póliza colectiva la persiste y notifica la creación al CORE")
	@Test
	void crearPolizaColectivaGuardaYNotificaAlCore() {
		when(polizaRepository.save(any(Poliza.class))).thenAnswer(invocacion -> {
			Poliza poliza = invocacion.getArgument(0);
			poliza.setId(1L);
			return poliza;
		});

		Poliza creada = servicio.crear(solicitudColectiva());

		assertThat(creada.getId()).isEqualTo(1L);
		assertThat(creada.getEstado()).isEqualTo(EstadoPoliza.VIGENTE);
		assertThat(creada.getPrima()).isEqualByComparingTo(new BigDecimal("30000000.00"));
		verify(polizaRepository).save(any(Poliza.class));
		verify(notificacionCore).notificar(PolizaService.EVENTO_CREACION, 1L);
	}

	@DisplayName("Crear una póliza conserva sus riesgos iniciales en estado activo")
	@Test
	void crearPolizaConservaLosRiesgosIniciales() {
		when(polizaRepository.save(any(Poliza.class))).thenAnswer(invocacion -> invocacion.getArgument(0));

		Poliza creada = servicio.crear(solicitudColectiva());

		assertThat(creada.getRiesgos()).hasSize(1);
		Riesgo riesgo = creada.getRiesgos().get(0);
		assertThat(riesgo.getEstado()).isEqualTo(EstadoRiesgo.ACTIVO);
		assertThat(riesgo.getDescripcion()).isEqualTo("Incendio");
		assertThat(riesgo.getValorAsegurado()).isEqualByComparingTo(new BigDecimal("80000000"));
		assertThat(riesgo.getPoliza()).isSameAs(creada);
	}

	@DisplayName("Regla 1: crear una póliza individual sin riesgos viola la regla de negocio")
	@Test
	void crearPolizaIndividualSinRiesgosFalla() {
		CrearPolizaRequest solicitud = new CrearPolizaRequest(TipoPoliza.INDIVIDUAL, LocalDate.of(2026, 1, 1),
				LocalDate.of(2026, 12, 31), 12, new BigDecimal("800000"), null, null, null, null, null);

		assertThatThrownBy(() -> servicio.crear(solicitud))
				.isInstanceOf(ReglaNegocioException.class)
				.hasMessageContaining("exactamente un riesgo")
				.hasMessageContaining("Se recibieron 0 riesgos");

		verify(polizaRepository, never()).save(any(Poliza.class));
		verify(notificacionCore, never()).notificar(any(), any());
	}

	@DisplayName("Una póliza colectiva puede crearse sin riesgos y agregarlos después")
	@Test
	void crearPolizaColectivaSinRiesgosEsValido() {
		when(polizaRepository.save(any(Poliza.class))).thenAnswer(invocacion -> invocacion.getArgument(0));
		CrearPolizaRequest solicitud = new CrearPolizaRequest(TipoPoliza.COLECTIVA, LocalDate.of(2026, 1, 1),
				LocalDate.of(2026, 12, 31), 12, new BigDecimal("2500000"), null, null, null, null, null);

		Poliza creada = servicio.crear(solicitud);

		assertThat(creada.getRiesgos()).isEmpty();
		assertThat(creada.getPrima()).isEqualByComparingTo(new BigDecimal("30000000.00"));
	}

	@DisplayName("Regla 1: crear una póliza individual con dos riesgos viola la regla de negocio")
	@Test
	void crearPolizaIndividualConDosRiesgosFalla() {
		CrearPolizaRequest solicitud = new CrearPolizaRequest(TipoPoliza.INDIVIDUAL, LocalDate.of(2026, 1, 1),
				LocalDate.of(2026, 12, 31), 12, new BigDecimal("800000"), null, null, null, null,
				List.of(new RiesgoRequest("Incendio", new BigDecimal("100")), new RiesgoRequest("Robo", new BigDecimal("200"))));

		assertThatThrownBy(() -> servicio.crear(solicitud))
				.isInstanceOf(ReglaNegocioException.class)
				.hasMessageContaining("exactamente un riesgo");

		verify(polizaRepository, never()).save(any(Poliza.class));
		verify(notificacionCore, never()).notificar(any(), any());
	}

	@DisplayName("Los importes monetarios se normalizan a dos decimales al crear la póliza")
	@Test
	void crearPolizaNormalizaLosImportesMonetarios() {
		when(polizaRepository.save(any(Poliza.class))).thenAnswer(invocacion -> invocacion.getArgument(0));
		CrearPolizaRequest solicitud = new CrearPolizaRequest(TipoPoliza.COLECTIVA, LocalDate.of(2026, 1, 1),
				LocalDate.of(2026, 12, 31), 12, new BigDecimal("1000.555"), null, null, null, null,
				List.of(new RiesgoRequest("Incendio", new BigDecimal("5000.444"))));

		Poliza creada = servicio.crear(solicitud);

		assertThat(creada.getCanonMensual()).isEqualByComparingTo(new BigDecimal("1000.56"));
		assertThat(creada.getCanonMensual().scale()).isEqualTo(2);
		assertThat(creada.getPrima()).isEqualByComparingTo(new BigDecimal("12006.72"));
		assertThat(creada.getRiesgos().get(0).getValorAsegurado()).isEqualByComparingTo(new BigDecimal("5000.44"));
	}

	@DisplayName("Invariante: una prima enviada que no corresponde al canon × meses se rechaza")
	@Test
	void crearPolizaConPrimaInconsistenteFalla() {
		CrearPolizaRequest solicitud = new CrearPolizaRequest(TipoPoliza.COLECTIVA, LocalDate.of(2026, 1, 1),
				LocalDate.of(2026, 12, 31), 12, new BigDecimal("2500000"), new BigDecimal("999"), null, null, null, null);

		assertThatThrownBy(() -> servicio.crear(solicitud))
				.isInstanceOf(SolicitudInvalidaException.class)
				.hasMessageContaining("no corresponde al canon mensual multiplicado por los meses de vigencia");

		verify(polizaRepository, never()).save(any(Poliza.class));
	}

	@DisplayName("Una fecha de fin anterior a la fecha de inicio se rechaza")
	@Test
	void crearPolizaConPeriodoInvalidoFalla() {
		CrearPolizaRequest solicitud = new CrearPolizaRequest(TipoPoliza.COLECTIVA, LocalDate.of(2026, 12, 31),
				LocalDate.of(2026, 1, 1), 12, new BigDecimal("2500000"), null, null, null, null, null);

		assertThatThrownBy(() -> servicio.crear(solicitud))
				.isInstanceOf(SolicitudInvalidaException.class)
				.hasMessageContaining("no puede ser anterior a la fecha de inicio");
	}

	@DisplayName("Un periodo de vigencia de cero días se rechaza")
	@Test
	void crearPolizaConPeriodoDeCeroDiasFalla() {
		CrearPolizaRequest solicitud = new CrearPolizaRequest(TipoPoliza.COLECTIVA, LocalDate.of(2026, 1, 1),
				LocalDate.of(2026, 1, 1), 12, new BigDecimal("2500000"), null, null, null, null, null);

		assertThatThrownBy(() -> servicio.crear(solicitud))
				.isInstanceOf(SolicitudInvalidaException.class)
				.hasMessageContaining("al menos un día");

		verify(polizaRepository, never()).save(any(Poliza.class));
	}

	@DisplayName("Un periodo incoherente con los meses de vigencia declarados se rechaza")
	@Test
	void crearPolizaConPeriodoIncoherenteConLosMesesDeVigenciaFalla() {
		CrearPolizaRequest solicitud = new CrearPolizaRequest(TipoPoliza.COLECTIVA, LocalDate.of(2026, 1, 1),
				LocalDate.of(2026, 12, 31), 6, new BigDecimal("2500000"), null, null, null, null, null);

		assertThatThrownBy(() -> servicio.crear(solicitud))
				.isInstanceOf(SolicitudInvalidaException.class)
				.hasMessageContaining("cubre 12 meses, pero se declararon 6 meses de vigencia");

		verify(polizaRepository, never()).save(any(Poliza.class));
	}

	@DisplayName("Listar pólizas delega el filtro en el repositorio")
	@Test
	void listarPolizasDelegaElFiltro() {
		Poliza poliza = polizaVigente();
		when(polizaRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class)))
				.thenReturn(List.of(poliza));

		List<Poliza> resultado = servicio.listar(TipoPoliza.COLECTIVA, EstadoPoliza.VIGENTE);

		assertThat(resultado).containsExactly(poliza);
		verify(polizaRepository).findAll(any(org.springframework.data.jpa.domain.Specification.class));
	}

	@DisplayName("Renovar incrementa canon y prima según el IPC y deja la póliza en estado RENOVADA")
	@Test
	void renovarPolizaActualizaCanonPrimaYEstado() {
		Poliza poliza = polizaVigente();
		when(polizaRepository.findById(1L)).thenReturn(Optional.of(poliza));
		when(polizaRepository.save(any(Poliza.class))).thenAnswer(invocacion -> invocacion.getArgument(0));

		Poliza renovada = servicio.renovar(1L);

		assertThat(renovada.getCanonMensual()).isEqualByComparingTo(new BigDecimal("103500.00"));
		assertThat(renovada.getPrima()).isEqualByComparingTo(new BigDecimal("1242000.00"));
		assertThat(renovada.getEstado()).isEqualTo(EstadoPoliza.RENOVADA);
		assertThat(renovada.getFechaInicio()).isEqualTo(LocalDate.of(2027, 1, 1));
		assertThat(renovada.getFechaFin()).isEqualTo(LocalDate.of(2027, 12, 31));
		verify(notificacionCore).notificar(PolizaService.EVENTO_RENOVACION, 1L);
	}

	@DisplayName("Regla 2: renovar una póliza cancelada viola la regla de negocio")
	@Test
	void renovarPolizaCanceladaFalla() {
		Poliza poliza = polizaVigente();
		poliza.setEstado(EstadoPoliza.CANCELADA);
		when(polizaRepository.findById(1L)).thenReturn(Optional.of(poliza));

		assertThatThrownBy(() -> servicio.renovar(1L))
				.isInstanceOf(ReglaNegocioException.class)
				.hasMessageContaining("No se puede renovar la póliza 1 porque está CANCELADA");

		verify(polizaRepository, never()).save(any(Poliza.class));
		verify(notificacionCore, never()).notificar(any(), any());
	}

	@DisplayName("Renovar una póliza inexistente devuelve el error de recurso no encontrado")
	@Test
	void renovarPolizaInexistenteFalla() {
		when(polizaRepository.findById(99L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> servicio.renovar(99L)).isInstanceOf(PolizaNoEncontradaException.class);
	}

	@DisplayName("Regla 3: cancelar la póliza cancela todos sus riesgos")
	@Test
	void cancelarPolizaCancelaTodosLosRiesgos() {
		Poliza poliza = polizaVigente();
		Riesgo riesgoUno = new Riesgo();
		riesgoUno.setId(1L);
		riesgoUno.setEstado(EstadoRiesgo.ACTIVO);
		Riesgo riesgoDos = new Riesgo();
		riesgoDos.setId(2L);
		riesgoDos.setEstado(EstadoRiesgo.ACTIVO);
		when(polizaRepository.findById(1L)).thenReturn(Optional.of(poliza));
		when(riesgoRepository.findByPolizaIdOrderByIdAsc(1L)).thenReturn(List.of(riesgoUno, riesgoDos));

		Poliza cancelada = servicio.cancelar(1L);

		assertThat(cancelada.getEstado()).isEqualTo(EstadoPoliza.CANCELADA);
		assertThat(riesgoUno.getEstado()).isEqualTo(EstadoRiesgo.CANCELADO);
		assertThat(riesgoDos.getEstado()).isEqualTo(EstadoRiesgo.CANCELADO);
		verify(riesgoRepository).saveAll(List.of(riesgoUno, riesgoDos));
		verify(notificacionCore).notificar(PolizaService.EVENTO_CANCELACION, 1L);
	}

	@DisplayName("Cancelar una póliza ya cancelada viola la regla de negocio")
	@Test
	void cancelarPolizaYaCanceladaFalla() {
		Poliza poliza = polizaVigente();
		poliza.setEstado(EstadoPoliza.CANCELADA);
		when(polizaRepository.findById(1L)).thenReturn(Optional.of(poliza));

		assertThatThrownBy(() -> servicio.cancelar(1L))
				.isInstanceOf(ReglaNegocioException.class)
				.hasMessageContaining("ya se encuentra CANCELADA");

		verify(notificacionCore, never()).notificar(any(), any());
	}

	@DisplayName("Cancelar una póliza inexistente devuelve el error de recurso no encontrado")
	@Test
	void cancelarPolizaInexistenteFalla() {
		when(polizaRepository.findById(99L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> servicio.cancelar(99L)).isInstanceOf(PolizaNoEncontradaException.class);
	}

	@DisplayName("Agregar un riesgo a una póliza colectiva lo persiste y notifica al CORE")
	@Test
	void agregarRiesgoEnPolizaColectivaLoGuarda() {
		Poliza poliza = polizaVigente();
		when(polizaRepository.findById(1L)).thenReturn(Optional.of(poliza));
		when(riesgoRepository.save(any(Riesgo.class))).thenAnswer(invocacion -> {
			Riesgo riesgo = invocacion.getArgument(0);
			riesgo.setId(9L);
			return riesgo;
		});

		Riesgo riesgo = servicio.agregarRiesgo(1L, new RiesgoRequest("Robo", new BigDecimal("50000")));

		assertThat(riesgo.getId()).isEqualTo(9L);
		assertThat(riesgo.getEstado()).isEqualTo(EstadoRiesgo.ACTIVO);
		assertThat(riesgo.getPoliza()).isSameAs(poliza);
		verify(notificacionCore).notificar(PolizaService.EVENTO_ALTA_RIESGO, 1L);
	}

	@DisplayName("Regla 4: agregar un riesgo a una póliza individual viola la regla de negocio")
	@Test
	void agregarRiesgoEnPolizaIndividualFalla() {
		Poliza poliza = polizaVigente();
		poliza.setTipo(TipoPoliza.INDIVIDUAL);
		when(polizaRepository.findById(1L)).thenReturn(Optional.of(poliza));

		assertThatThrownBy(() -> servicio.agregarRiesgo(1L, new RiesgoRequest("Robo", new BigDecimal("50000"))))
				.isInstanceOf(ReglaNegocioException.class)
				.hasMessageContaining("Solo las pólizas colectivas admiten riesgos adicionales");

		verify(riesgoRepository, never()).save(any(Riesgo.class));
		verify(notificacionCore, never()).notificar(any(), any());
	}

	@DisplayName("Regla 5: agregar un riesgo a una póliza cancelada viola la regla de negocio")
	@Test
	void agregarRiesgoEnPolizaCanceladaFalla() {
		Poliza poliza = polizaVigente();
		poliza.setEstado(EstadoPoliza.CANCELADA);
		when(polizaRepository.findById(1L)).thenReturn(Optional.of(poliza));

		assertThatThrownBy(() -> servicio.agregarRiesgo(1L, new RiesgoRequest("Robo", new BigDecimal("50000"))))
				.isInstanceOf(ReglaNegocioException.class)
				.hasMessageContaining("No se puede agregar riesgos a la póliza 1 porque está CANCELADA");

		verify(riesgoRepository, never()).save(any(Riesgo.class));
		verify(notificacionCore, never()).notificar(any(), any());
	}

	@DisplayName("Agregar un riesgo a una póliza inexistente devuelve el error de recurso no encontrado")
	@Test
	void agregarRiesgoEnPolizaInexistenteFalla() {
		when(polizaRepository.findById(99L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> servicio.agregarRiesgo(99L, new RiesgoRequest("Robo", new BigDecimal("50000"))))
				.isInstanceOf(PolizaNoEncontradaException.class);
	}

	@DisplayName("La notificación al CORE se delega en el puerto de salida")
	@Test
	void laNotificacionAlCoreSeDelegaEnElPuertoDeSalida() {
		when(polizaRepository.save(any(Poliza.class))).thenAnswer(invocacion -> invocacion.getArgument(0));

		Poliza creada = servicio.crear(solicitudColectiva());

		assertThat(creada.getTipo()).isEqualTo(TipoPoliza.COLECTIVA);
		verify(notificacionCore).notificar(eq(PolizaService.EVENTO_CREACION), any());
	}

	@DisplayName("El evento enviado al CORE viaja con el identificador de la póliza afectada")
	@Test
	void elEventoAlCoreIncluyeElIdentificadorDeLaPoliza() {
		when(polizaRepository.save(any(Poliza.class))).thenAnswer(invocacion -> {
			Poliza poliza = invocacion.getArgument(0);
			poliza.setId(1L);
			return poliza;
		});
		when(polizaRepository.findById(1L)).thenReturn(Optional.of(polizaVigente()));
		when(riesgoRepository.findByPolizaIdOrderByIdAsc(1L)).thenReturn(List.of());

		servicio.crear(solicitudColectiva());
		servicio.cancelar(1L);

		ArgumentCaptor<String> evento = ArgumentCaptor.forClass(String.class);
		ArgumentCaptor<Long> polizaId = ArgumentCaptor.forClass(Long.class);
		verify(notificacionCore, org.mockito.Mockito.times(2)).notificar(evento.capture(), polizaId.capture());
		assertThat(evento.getAllValues()).containsExactly(PolizaService.EVENTO_CREACION, PolizaService.EVENTO_CANCELACION);
		assertThat(polizaId.getAllValues()).containsExactly(1L, 1L);
	}
}
