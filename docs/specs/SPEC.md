# Especificación de la API de Gestión de Pólizas (SDD)

> **Cómo leer este documento:** es la fuente de verdad funcional del Módulo 2 de la
> prueba técnica. Cada endpoint se especifica con el formato **Given / When / Then**
> y cada regla de negocio con el mismo formato, para que el desarrollo siga la
> secuencia *spec → tests → implementación* (SDD + TDD).
>
> **Contexto de negocio.** La plataforma gestiona pólizas de arrendamiento de
> inmuebles de dos tipos: **INDIVIDUAL** (un solo riesgo: el arrendatario) y
> **COLECTIVA** (uno o muchos riesgos: mudanzas e inconformidades de copropiedad).
> Todas las pólizas tienen periodo de vigencia, canon mensual, prima
> (`prima = canon mensual × meses de vigencia`) y se renuevan por el mismo
> periodo de vigencia inicial ajustando el canon según el **IPC**.

---

## 0. Convenciones comunes

| Aspecto | Definición |
|---|---|
| Base de la API | `http://localhost:8080` |
| Autenticación | Encabezado obligatorio `x-api-key: 123456` en **todas** las peticiones, incluido el mock del CORE |
| Formato | JSON (`Content-Type: application/json`) |
| Fecha | ISO-8601 (`2026-01-01`) |
| Dinero | `BigDecimal` con 2 decimales |
| Identificadores | `Long` autogenerados |

### 0.1 Códigos de respuesta

| Código | Significado |
|---|---|
| `200 OK` | Operación ejecutada correctamente |
| `201 Created` | Recurso creado (póliza o riesgo) |
| `400 Bad Request` | Petición malformada o inconsistente (campos inválidos, JSON inválido, enum inválido) |
| `401 Unauthorized` | Falta el encabezado `x-api-key` o su valor no es el esperado |
| `404 Not Found` | La póliza o el riesgo solicitado no existe |
| `409 Conflict` | Regla de negocio violada (estado incompatible, tipo de póliza no permitido, etc.) |
| `500 Internal Server Error` | Error inesperado (queda registrado en el log) |

### 0.2 Estructura estándar de error

```json
{
  "timestamp": "2026-01-15T10:20:30Z",
  "status": 409,
  "error": "Conflicto",
  "mensaje": "No se puede renovar la póliza 7 porque está CANCELADA.",
  "ruta": "/polizas/7/renovar",
  "erroresCampos": {}
}
```

`erroresCampos` relaciona el nombre del campo con el mensaje de validación
cuando el error proviene de `jakarta.validation` (código `400`).

---

## 1. `POST /polizas` — Crear póliza

Crea una póliza con sus riesgos iniciales. Es la operación que habilita el resto
del flujo (y la que "soporta los consumos necesarios del front").

**Cuerpo de la petición**

```json
{
  "tipo": "COLECTIVA",
  "fechaInicio": "2026-01-01",
  "fechaFin": "2026-12-31",
  "mesesVigencia": 12,
  "canonMensual": 2500000.00,
  "prima": 30000000.00,
  "nombreTomador": "Inmobiliaria Andes S.A.S.",
  "nombreAsegurado": "Arrendatarios Torre Norte",
  "nombreBeneficiario": "Señor Juan Pérez",
  "riesgos": [
    { "descripcion": "Incendio - Immueble torre norte", "valorAsegurado": 80000000.00 }
  ]
}
```

**Given** una póliza válida con su lista de riesgos iniciales,
**When** se invoca `POST /polizas`,
**Then** la API responde `201 Created` con la póliza persistida (incluido su
`id` autogenerado), en estado `VIGENTE`, y notifica el evento `CREACION` al CORE.

### Casos de error de este endpoint

- **Given** una póliza `INDIVIDUAL` con **más de un** riesgo,
  **When** se invoca `POST /polizas`,
  **Then** la API responde `409 Conflict`
  ("Una póliza individual solo puede tener un riesgo...") y **no** persiste nada.
- **Given** una `prima` enviada que no corresponde a `canonMensual × mesesVigencia`,
  **When** se invoca `POST /polizas`,
  **Then** la API responde `400 Bad Request`; la prima se **deriva** siempre en el
  servidor (`prima = canonMensual × mesesVigencia`) y es la única fuente de verdad.
- **Given** `fechaFin` anterior a `fechaInicio`,
  **When** se invoca `POST /polizas`,
  **Then** la API responde `400 Bad Request`.
- **Given** campos obligatorios ausentes o inválidos (`tipo`, fechas, `mesesVigencia`,
  `canonMensual`),
  **When** se invoca `POST /polizas`,
  **Then** la API responde `400 Bad Request` con el detalle por campo.

---

## 2. `GET /polizas` — Listar pólizas

Lista las pólizas con filtros **opcionales** por tipo y/o estado.

**Parámetros de consulta:** `tipo` (`INDIVIDUAL` | `COLECTIVA`), `estado`
(`VIGENTE` | `RENOVADA` | `CANCELADA`).

**Given** un conjunto de pólizas persistidas,
**When** se invoca `GET /polizas` sin filtros,
**Then** la API responde `200 OK` con la lista completa.

**Given** un conjunto de pólizas de distintos tipos y estados,
**When** se invoca `GET /polizas?tipo=COLECTIVA&estado=VIGENTE`,
**Then** la API responde `200 OK` **solo** con las pólizas que cumplen ambos filtros.

**Given** un valor de `tipo` que no corresponde al enumerado,
**When** se invoca `GET /polizas?tipo=INVALIDO`,
**Then** la API responde `400 Bad Request` indicando el parámetro inválido.

---

## 3. `GET /polizas/{id}/riesgos` — Riesgos de una póliza

**Given** una póliza existente con riesgos,
**When** se invoca `GET /polizas/{id}/riesgos`,
**Then** la API responde `200 OK` con la lista de riesgos de la póliza
(ordenada por identificador).

**Given** un identificador de póliza inexistente,
**When** se invoca `GET /polizas/{id}/riesgos`,
**Then** la API responde `404 Not Found`.

---

## 4. `POST /polizas/{id}/renovar` — Renovar póliza

Renueva la póliza aplicando el **IPC** configurado en `app.ipc-porcentaje`
(por defecto `3.5`).

**Fórmulas de la renovación**

```
canon' = canon × (1 + IPC / 100)              →  redondeo HALF_UP a 2 decimales
prima' = canon' × mesesVigencia                →  redondeo HALF_UP a 2 decimales
```

El periodo de vigencia se extiende en `mesesVigencia` meses a partir del día
siguiente a la fecha de fin vigente, conservando la duración original.

**Given** una póliza existente en estado `VIGENTE` o `RENOVADA`,
**When** se invoca `POST /polizas/{id}/renovar`,
**Then** la API responde `200 OK` con la póliza actualizada: canon y prima
incrementados según el IPC, periodo de vigencia extendido y estado `RENOVADA`;
además notifica el evento `RENOVACION` al CORE.

**Given** una póliza en estado `CANCELADA`,
**When** se invoca `POST /polizas/{id}/renovar`,
**Then** la API responde `409 Conflict`
("No se puede renovar la póliza 7 porque está CANCELADA.") y **no** notifica al CORE.

**Given** un identificador de póliza inexistente,
**When** se invoca `POST /polizas/{id}/renovar`,
**Then** la API responde `404 Not Found`.

---

## 5. `POST /polizas/{id}/cancelar` — Cancelar póliza

**Given** una póliza existente (vigilante o renovada) con sus riesgos,
**When** se invoca `POST /polizas/{id}/cancelar`,
**Then** la API responde `200 OK` con la póliza en estado `CANCELADA` y
**todos** sus riesgos en estado `CANCELADO` (cambio transaccional: o se aplica
completo o no se aplica); además notifica el evento `CANCELACION` al CORE.

**Given** una póliza que ya está `CANCELADA`,
**When** se invoca `POST /polizas/{id}/cancelar`,
**Then** la API responde `409 Conflict` (la operación no es idempotente de forma
silenciosa: el conflicto es explícito para el consumidor).

**Given** un identificador de póliza inexistente,
**When** se invoca `POST /polizas/{id}/cancelar`,
**Then** la API responde `404 Not Found`.

---

## 6. `POST /polizas/{id}/riesgos` — Agregar riesgo

**Given** una póliza existente de tipo `COLECTIVA`,
**When** se invoca `POST /polizas/{id}/riesgos` con un cuerpo válido,
**Then** la API responde `201 Created` con el riesgo creado en estado `ACTIVO`;
además notifica el evento `ALTA_RIESGO` al CORE.

**Given** una póliza existente de tipo `INDIVIDUAL`,
**When** se invoca `POST /polizas/{id}/riesgos`,
**Then** la API responde `409 Conflict`
("Solo las pólizas colectivas admiten riesgos adicionales...") y **no** persiste el riesgo.

**Given** un identificador de póliza inexistente,
**When** se invoca `POST /polizas/{id}/riesgos`,
**Then** la API responde `404 Not Found`.

---

## 7. `POST /riesgos/{id}/cancelar` — Cancelar riesgo

**Given** un riesgo existente en estado `ACTIVO` de una póliza vigente,
**When** se invoca `POST /riesgos/{id}/cancelar`,
**Then** la API responde `200 OK` con el riesgo en estado `CANCELADO`;
además notifica el evento `CANCELACION_RIESGO` al CORE.

**Given** un riesgo que ya está en estado `CANCELADO`,
**When** se invoca `POST /riesgos/{id}/cancelar`,
**Then** la API responde `409 Conflict`.

**Given** un riesgo cuya póliza está `CANCELADA` (sus riesgos ya fueron
cancelados en cascada),
**When** se invoca `POST /riesgos/{id}/cancelar`,
**Then** la API responde `409 Conflict`.

**Given** un identificador de riesgo inexistente,
**When** se invoca `POST /riesgos/{id}/cancelar`,
**Then** la API responde `404 Not Found`.

---

## 8. `POST /core-mock/evento` — Mock del CORE transaccional

Mock **obligatorio** del CORE legado. Su único propósito es **registrar en logs**
que la operación se intentó enviar al CORE.

**Cuerpo de la petición**

```json
{ "evento": "ACTUALIZACION", "polizaId": 555 }
```

**Given** una petición con `evento` y `polizaId`,
**When** se invoca `POST /core-mock/evento`,
**Then** la API responde `200 OK` y queda registrada la línea de log
`Intento de envío al CORE: evento={evento} polizaId={polizaId}`.

**Given** un cuerpo sin `evento` o sin `polizaId`,
**When** se invoca `POST /core-mock/evento`,
**Then** la API responde `400 Bad Request`.

**Given** una petición sin el encabezado `x-api-key`,
**When** se invoca `POST /core-mock/evento`,
**Then** la API responde `401 Unauthorized` (el mock también está protegido).

### 8.1 Notificación al CORE desde las operaciones de negocio

Toda acción que modifique el estado de una póliza o de un riesgo debe
notificar al CORE a través del puerto `CoreGateway` (adaptador HTTP):

| Operación | Evento enviado |
|---|---|
| Crear póliza | `CREACION` |
| Renovar póliza | `RENOVACION` |
| Cancelar póliza | `CANCELACION` |
| Agregar riesgo | `ALTA_RIESGO` |
| Cancelar riesgo | `CANCELACION_RIESGO` |

**Given** que el CORE no responde o devuelve un error,
**When** el servicio de dominio ejecuta la operación,
**Then** el error queda registrado en el log y **la operación de negocio NO se
revierte** (la notificación es *best-effort*).

---

## 9. Reglas de negocio (resumen normativo)

| # | Regla | Endpoint afectado | Respuesta ante violación |
|---|---|---|---|
| 1 | Una póliza `INDIVIDUAL` solo puede tener **1** riesgo (ni al crear ni después) | `POST /polizas`, `POST /polizas/{id}/riesgos` | `409 Conflict` |
| 2 | **No** se puede renovar una póliza `CANCELADA` | `POST /polizas/{id}/renovar` | `409 Conflict` |
| 3 | Cancelar una póliza **cancela todos sus riesgos** | `POST /polizas/{id}/cancelar` | — (regla aplicada) |
| 4 | Agregar riesgo exige validar el tipo: solo `COLECTIVA` admite riesgos adicionales | `POST /polizas/{id}/riesgos` | `409 Conflict` |
| 5 | Invariante de datos: `prima = canonMensual × mesesVigencia` | `POST /polizas`, `POST /polizas/{id}/renovar` | `400 Bad Request` (si se envía inconsistente) |
| 6 | Toda acción que modifique estados notifica al CORE (best-effort) | todos los de escritura | — (regla aplicada) |

---

## 10. Trazabilidad spec → pruebas

| Spec | Prueba automatizada |
|---|---|
| §1 Crear póliza | `PolizaServiceTest.crearPolizaColectiva*`, `ApiPolizasIntegrationTest` |
| §2 Listar pólizas | `PolizaServiceTest.listarPolizas*`, `PolizaRepositoryTest` |
| §3 Riesgos por póliza | `RiesgoServiceTest.listarRiesgosDePoliza*` |
| §4 Renovar | `PolizaServiceTest.renovar*`, `ReglasPolizaTest.calcular*` |
| §5 Cancelar póliza | `PolizaServiceTest.cancelarPoliza*` |
| §6 Agregar riesgo | `PolizaServiceTest.agregarRiesgo*` |
| §7 Cancelar riesgo | `RiesgoServiceTest.cancelarRiesgo*` |
| §8 Mock CORE | `CoreMockControllerTest`, `CoreHttpAdapterTest` |
| §9 Reglas 1-4 | `ReglasPolizaTest`, `PolizaServiceTest`, `RiesgoServiceTest` |
| §0.2 Errores | `PolizaControllerTest` (401/404/409/400) |
| §0 Autenticación | `ApiKeyFilterTest` |
