package com.jmendez.polizas.model;

/**
 * Tipo de póliza gestionada por la plataforma.
 *
 * <p>Una póliza {@link #INDIVIDUAL} asegura a un único arrendatario y admite
 * como máximo un riesgo. Una póliza {@link #COLECTIVA} admite uno o muchos
 * riesgos y es la utilizada por inmobiliarias y administradores de copropiedad.
 */
public enum TipoPoliza {

	/** Póliza de un solo riesgo (arrendatario). */
	INDIVIDUAL,

	/** Póliza de uno o muchos riesgos (inmobiliarias y copropiedades). */
	COLECTIVA
}
