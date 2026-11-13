package com.jmendez.polizas.model;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Riesgo asegurado dentro de una póliza.
 *
 * <p>Una póliza colectiva admite uno o muchos riesgos; una individual, solo uno.
 * El riesgo se cancela individualmente o en cascada al cancelar su póliza.
 */
@Entity
@Table(name = "riesgos")
@Getter
@Setter
public class Riesgo {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id")
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "poliza_id", nullable = false)
	private Poliza poliza;

	@Enumerated(EnumType.STRING)
	@Column(name = "estado", nullable = false, length = 20)
	private EstadoRiesgo estado;

	@Column(name = "descripcion", length = 500)
	private String descripcion;

	@Column(name = "valor_asegurado", precision = 19, scale = 2)
	private BigDecimal valorAsegurado;
}
