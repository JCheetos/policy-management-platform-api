package com.jmendez.polizas.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

/**
 * Póliza de arrendamiento de inmueble asegurada por la plataforma.
 *
 * <p>Invariante de dominio: {@code prima = canonMensual × mesesVigencia}.
 * La prima nunca se recibe como dato maestro: la deriva el dominio.
 */
@Entity
@Table(name = "polizas")
@Getter
@Setter
public class Poliza {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id")
	private Long id;

	/**
	 * Versión de la fila para el control de concurrencia optimista.
	 *
	 * <p>Dos renovaciones simultáneas de la misma póliza compiten por la misma
	 * versión: solo una gana y la otra recibe un conflicto (HTTP 409) en lugar
	 * de perder silenciosamente el ajuste del IPC.
	 */
	@Version
	@Column(name = "version", nullable = false)
	private Integer version;

	@Enumerated(EnumType.STRING)
	@Column(name = "tipo", nullable = false, length = 20)
	private TipoPoliza tipo;

	@Enumerated(EnumType.STRING)
	@Column(name = "estado", nullable = false, length = 20)
	private EstadoPoliza estado;

	@Column(name = "fecha_inicio", nullable = false)
	private LocalDate fechaInicio;

	@Column(name = "fecha_fin", nullable = false)
	private LocalDate fechaFin;

	@Column(name = "meses_vigencia", nullable = false)
	private Integer mesesVigencia;

	@Column(name = "canon_mensual", nullable = false, precision = 19, scale = 2)
	private BigDecimal canonMensual;

	@Column(name = "prima", nullable = false, precision = 19, scale = 2)
	private BigDecimal prima;

	@Column(name = "nombre_tomador", length = 200)
	private String nombreTomador;

	@Column(name = "nombre_asegurado", length = 200)
	private String nombreAsegurado;

	@Column(name = "nombre_beneficiario", length = 200)
	private String nombreBeneficiario;

	/**
	 * Riesgos que integran la póliza. La relación es bidireccional: la póliza es el
	 * lado propietario y el borrado en cascada permite que la vida de los riesgos
	 * dependa de la de la póliza.
	 */
	@OneToMany(mappedBy = "poliza", cascade = CascadeType.ALL, orphanRemoval = true)
	private List<Riesgo> riesgos = new ArrayList<>();

	/**
	 * Asocia un riesgo a la póliza manteniendo la relación bidireccional coherente.
	 *
	 * @param riesgo riesgo a incorporar a la póliza
	 */
	public void agregarRiesgo(Riesgo riesgo) {
		riesgo.setPoliza(this);
		this.riesgos.add(riesgo);
	}
}
