# API de Gestión de Pólizas

**Módulo 2 — Prueba técnica práctica (Hands-on Code Challenge)**
API REST de gestión de pólizas de arrendamiento de inmuebles (individuales y
colectivas), con reglas de negocio, integración con un CORE transaccional legado
(simulado) y seguridad mínima por encabezado.

- **Paquete base:** `com.jmendez.polizas`
- **GroupId / ArtifactId:** `com.jmendez` / `api-polizas`
- **Especificación funcional:** [`docs/specs/SPEC.md`](docs/specs/SPEC.md)

---

## 1. Descripción del proyecto

La plataforma administra dos tipos de póliza:

| Tipo | Riesgos | Caso de uso |
|---|---|---|
| `INDIVIDUAL` | exactamente uno | el arrendatario es tomador y asegurado; el arrendador es beneficiario |
| `COLECTIVA` | cero o muchos al crear (se pueden agregar después) | inmobiliarias y administradores de copropiedad |

Todas las pólizas tienen periodo de vigencia, canon mensual y prima, con el
invariante de dominio **`prima = canon mensual × meses de vigencia`**. La renovación
extiende la vigencia por el mismo número de meses y ajusta canon y prima según el
**IPC** configurado.

## 2. Stack

| Elemento | Versión / detalle |
|---|---|
| Java | 17 (Temurin) |
| Spring Boot | 3.3.5 |
| Maven | 3.9+ |
| Persistencia | Spring Data JPA + Hibernate 6 |
| Base de datos | H2 en memoria (runtime) |
| Validación | `jakarta.validation` (Bean Validation) |
| Cliente HTTP | `RestClient` (Spring 6.1) hacia el CORE |
| Pruebas | JUnit 5, Mockito, MockMvc, AssertJ, `@DataJpaTest` |
| Cobertura | JaCoCo 0.8.12 (informe en `target/site/jacoco/index.html`) |
| Utilidades | Lombok (métodos de acceso de entidades y propiedades) |

## 3. Arquitectura

Capas exigidas por el enunciado (**controller / service / repository**) con
pragmatismo hexagonal: las reglas de negocio viven en un núcleo puro y la salida
hacia el CORE se define como un puerto.

```
com.jmendez.polizas
├── controller/          Adaptadores de entrada HTTP (REST)
│   ├── PolizaController          /polizas...
│   ├── RiesgoController          /polizas/{id}/riesgos, /riesgos/{id}/cancelar
│   ├── CoreMockController        /core-mock/evento  (mock obligatorio del CORE)
│   └── ManejadorErroresGlobales  @RestControllerAdvice (traduce excepciones a HTTP)
├── service/             Servicios de aplicación (orquestan y transaccionan)
│   ├── PolizaService
│   └── RiesgoService
├── domain/              Núcleo de negocio PURO (sin Spring, sin JPA, sin HTTP)
│   └── ReglasPoliza    validaciones, fórmula del IPC e invariante de la prima
├── model/               Entidades y enumerados de dominio
│   ├── Poliza, Riesgo, TipoPoliza, EstadoPoliza, EstadoRiesgo
├── repository/          Puertos de persistencia (Spring Data JPA) + especificaciones
│   ├── PolizaRepository, RiesgoRepository, PolizaSpecifications
├── port/                Puertos de salida
│   ├── CoreGateway      (interfaz) — transporte HTTP hacia el CORE
│   ├── NotificacionCore (interfaz) — cuándo se notifica al CORE (post-commit)
│   └── EventoCore       (payload del evento)
├── infrastructure/core/ Adaptadores de salida
│   ├── CoreHttpAdapter           (RestClient → POST al CORE)
│   └── NotificacionCorePostCommit (difiere el envío hasta el afterCommit)
├── dto/                 Contratos de entrada/salida (records) + estructura de error
├── exception/           Excepciones de dominio → código HTTP
└── config/              Propiedades, filtro de api-key y beans de infraestructura
```

**Dependencias y flujo de una escritura**

```
Petición HTTP
   │  ApiKeyFilter            → 401 si falta o no coincide x-api-key
   ▼
Controller (valida el formato con jakarta.validation)
   ▼
Service  ──► ReglasPoliza (reglas puras: tipos, estados, IPC, prima)
   │  ──► Repository (JPA/H2, transacción @Transactional, control de versión)
   │  ──► NotificacionCore ──► (afterCommit) ──► CoreHttpAdapter ──► POST /core-mock/evento
   ▼
ManejadorErroresGlobales → 400 / 404 / 405 / 409 / 415 / 500 con estructura de error uniforme
```

**Decisiones de diseño**

- **Dominio sin framework:** `ReglasPoliza` no importa nada de Spring ni de JPA;
  se instancia en `ConfiguracionDominio` y se inyecta en los servicios. Es la
  pieza que más valor aporta a la mantenibilidad y la que más se prueba.
- **Puertos y adaptadores:** `CoreGateway` (interfaz) + `CoreHttpAdapter`
  (implementación HTTP) y `NotificacionCore` (interfaz) +
  `NotificacionCorePostCommit` (implementación). Sustituir el CORE real por otro
  transporte no toca el dominio ni los servicios.
- **Notificación *post-commit* y *best-effort*:** el evento se envía al CORE
  **después** del commit de la transacción, de modo que el CORE nunca recibe un
  evento de una operación que finalmente se revierte. Si el CORE no responde, el
  error queda en el log y **la operación de negocio no se revierte** (el CORE es
  un sistema externo que no puede decidir sobre el estado de la póliza).
- **Concurrencia optimista:** `Poliza` y `Riesgo` llevan una columna `version`
  (`@Version`): dos renovaciones simultáneas no pueden aplicarle el IPC dos
  veces; la segunda recibe `409 Conflict` y debe reintentar.
- **Transaccionalidad:** la cancelación de una póliza y la de sus riesgos se
  aplican en una única transacción: o se aplican todas o ninguna.

## 4. Modelo de datos

**Poliza** (`polizas`)

| Campo | Tipo | Notas |
|---|---|---|
| `id` | `Long` | autogenerado |
| `version` | `int` | control de concurrencia optimista (`@Version`) |
| `tipo` | `INDIVIDUAL` \| `COLECTIVA` | obligatorio |
| `estado` | `VIGENTE` \| `RENOVADA` \| `CANCELADA` | `CANCELADA` es terminal |
| `fechaInicio`, `fechaFin` | `LocalDate` | periodo de vigencia (mínimo 1 día) |
| `mesesVigencia` | `int` | ≥ 1 y coherente con el periodo declarado |
| `canonMensual` | `BigDecimal(19,2)` | canon del arrendamiento |
| `prima` | `BigDecimal(19,2)` | **derivada**: `canonMensual × mesesVigencia` |
| `nombreTomador`, `nombreAsegurado`, `nombreBeneficiario` | `String` | opcionales (máx. 200) |

**Riesgo** (`riesgos`)

| Campo | Tipo | Notas |
|---|---|---|
| `id` | `Long` | autogenerado |
| `version` | `int` | control de concurrencia optimista (`@Version`) |
| `poliza` | `ManyToOne` → `polizas.id` | propietario de la relación |
| `estado` | `ACTIVO` \| `CANCELADO` | |
| `descripcion` | `String` | máx. 500 |
| `valorAsegurado` | `BigDecimal(19,2)` | |

## 5. Reglas de negocio

| # | Regla | Comportamiento |
|---|---|---|
| 1 | Una póliza `INDIVIDUAL` se crea con **exactamente 1** riesgo (ni 0 ni 2 o más) y no admite riesgos adicionales | `409 Conflict` |
| 2 | **No** se puede renovar una póliza `CANCELADA` | `409 Conflict` |
| 3 | Cancelar una póliza **cancela todos sus riesgos** (transaccional) | aplicado |
| 4 | Agregar riesgo exige validar el tipo: solo `COLECTIVA` admite riesgos adicionales | `409 Conflict` |
| 5 | Una póliza `CANCELADA` es terminal: **no admite riesgos nuevos** | `409 Conflict` |
| 6 | `prima = canonMensual × mesesVigencia` | la prima la deriva el servidor; si se envía inconsistente → `400 Bad Request` |
| 7 | El periodo de vigencia dura **al menos 1 día** y es **coherente con `mesesVigencia`** | `400 Bad Request` |
| 8 | Los importes monetarios tienen **2 decimales** (`DECIMAL(19,2)`) | más de 2 decimales → `400 Bad Request`; internamente se normalizan con `HALF_UP` |
| 9 | Control de concurrencia optimista (`@Version`): dos operaciones simultáneas sobre la misma póliza no se pisan | `409 Conflict` ("fue modificada por otra operación; reintente") |
| 10 | Toda acción que modifica estados notifica al CORE (`CREACION`, `RENOVACION`, `CANCELACION`, `ALTA_RIESGO`, `CANCELACION_RIESGO`) **después del commit** | *best-effort* |

### Fórmula de la renovación

Configurable en `application.properties` con `app.ipc-porcentaje` (por defecto `3.5`):

```
canon' = canon × (1 + IPC / 100)     → redondeo HALF_UP a 2 decimales
prima' = canon' × mesesVigencia       → redondeo HALF_UP a 2 decimales
```

Ejemplo con `canon = 2.500.000` y `IPC = 3.5 %`:
`canon' = 2.587.500` y `prima' = 2.587.500 × 12 = 31.050.000`.

La renovación **extiende además el periodo de vigencia** los mismos
`mesesVigencia` meses, empezando el día siguiente a la fecha de fin vigente
(una póliza `2026-01-01 → 2026-12-31` queda `2027-01-01 → 2027-12-31`).

## 6. Endpoints

Todos exigen el encabezado `x-api-key`.

| Método | Ruta | Respuesta |
|---|---|---|
| `GET` | `/polizas?tipo=&estado=` | `200` con la lista filtrada (filtros opcionales) |
| `POST` | `/polizas` | `201` con la póliza creada |
| `GET` | `/polizas/{id}/riesgos` | `200` con los riesgos de la póliza |
| `POST` | `/polizas/{id}/renovar` | `200` con la póliza renovada |
| `POST` | `/polizas/{id}/cancelar` | `200` con la póliza cancelada |
| `POST` | `/polizas/{id}/riesgos` | `201` con el riesgo creado (solo colectivas no canceladas) |
| `POST` | `/riesgos/{id}/cancelar` | `200` con el riesgo cancelado |
| `POST` | `/core-mock/evento` | `200` con el acuse de recibo del registro en logs |

**Códigos de error**

| Código | Cuándo |
|---|---|
| `400 Bad Request` | cuerpo inválido o campos faltantes (`erroresCampos` detalla el problema), enum inválido, prima inconsistente, periodo incoherente o de menos de un día, importes con más de 2 decimales, textos que exceden la longitud máxima |
| `401 Unauthorized` | falta el encabezado `x-api-key` o su valor no coincide con el configurado |
| `404 Not Found` | la póliza o el riesgo no existe |
| `405 Method Not Allowed` | el método HTTP no está soportado por el recurso |
| `409 Conflict` | violación de regla de negocio (individual con 0 o 2+ riesgos al crear, renovar/cancelar una póliza cancelada, riesgo en póliza individual, riesgo en póliza cancelada, riesgo ya cancelado) o conflicto de concurrencia optimista (la póliza fue modificada por otra operación) |
| `415 Unsupported Media Type` | el `Content-Type` de la petición no está soportado |
| `500 Internal Server Error` | error inesperado (queda registrado en el log) |

Estructura estándar de error:

```json
{
  "timestamp": "2026-01-15T10:20:30Z",
  "status": 409,
  "error": "Conflict",
  "mensaje": "No se puede renovar la póliza 7 porque está CANCELADA.",
  "ruta": "/polizas/7/renovar",
  "erroresCampos": {}
}
```

El campo `error` contiene la razón textual estándar de HTTP en inglés
(`Conflict`, `Bad Request`, `Not Found`…) y `mensaje` la explicación en español
del problema. Los mensajes de error nunca reflejan valores internos del
framework (clases, rutas) ni caracteres de control: los valores recibidos se
sanean y se acotan antes de incluirlos.

## 7. Cómo ejecutar

### Requisitos

- Java 17
- Maven 3.9+
- (opcional) `curl` para los ejemplos

### Levantar la aplicación

```bash
mvn spring-boot:run
```

La aplicación queda disponible en `http://localhost:8080` con la base de datos H2
en memoria (los datos se pierden al detenerla).

Alternativa con el JAR empaquetado:

```bash
mvn clean package
java -jar target/api-polizas-1.0.0.jar
```

### Pruebas y cobertura

```bash
mvn clean verify
```

- Ejecuta **131 pruebas** (unitarias, web con MockMvc, repositorio contra H2 y de
  integración de extremo a extremo).
- Genera el informe de cobertura en **`target/site/jacoco/index.html`**
  (o `target/site/jacoco/jacoco.csv`).
- La meta `jacoco:check` **falla la compilación** si la cobertura de líneas baja
  del **95 %** (cobertura actual: **99,45 %** de líneas; las dos líneas sin
  cubrir son el método `main` de la clase de arranque, que solo se ejecuta al
  iniciar la aplicación).

Solo las pruebas:

```bash
mvn test
```

### Configuración

Valores por defecto en `src/main/resources/application.properties`:

| Propiedad | Por defecto | Descripción |
|---|---|---|
| `app.api-key` | `123456` | credencial exigida en `x-api-key` (ver §8) |
| `app.ipc-porcentaje` | `3.5` | IPC aplicado en la renovación |
| `app.core.url` | `http://localhost:8080/core-mock/evento` | URL del CORE (mock) |
| `app.core.connect-timeout-segundos` | `2` | timeout de conexión al CORE |
| `app.core.read-timeout-segundos` | `3` | timeout de lectura del CORE |
| `server.port` | `8080` | puerto de la aplicación |

La credencial se puede sobreescribir por variable de entorno (recomendado en
cualquier ambiente que no sea la demo de la prueba):

```bash
export APP_API_KEY="una-credencial-segura"
```

Se pueden sobrescribir por línea de comandos:

```bash
mvn spring-boot:run -Dspring-boot.run.arguments="--server.port=9090 --app.ipc-porcentaje=5.0"
```

## 8. Seguridad mínima

Todas las peticiones deben incluir el encabezado:

```
x-api-key: 123456
```

Si falta o no coincide, el filtro `ApiKeyFilter` responde `401 Unauthorized` con
el mensaje *"Falta el encabezado 'x-api-key' o su valor no es válido."*. La
comparación se realiza en tiempo constante. El mock del CORE también está
protegido.

> **Alcance de esta credencial.** El valor `123456` es el **mínimo exigido por
> el enunciado de la prueba** y sirve únicamente para la demostración: no es una
> credencial de producción. La propiedad `app.api-key` está externalizada como
> `${APP_API_KEY:123456}`, de modo que en cualquier ambiente real la credencial
> se inyecta por variable de entorno o gestor de secretos (Vault, AWS Secrets
> Manager, K8s Secret) y nunca queda versionada en el repositorio.
>
> **Pendiente para producción** (fuera del alcance de esta prueba): rotación
> periódica de la credencial, *rate limiting* por consumidor/IP, cabeceras de
> seguridad (`Strict-Transport-Security`, `Content-Security-Policy`,
> `X-Content-Type-Options`), TLS con mTLS contra el CORE y trazas
> distribuidas (traceId) en las cabeceras de respuesta.

## 9. Ejemplos con curl

```bash
# Crear una póliza colectiva (201)
curl -X POST http://localhost:8080/polizas \
  -H "x-api-key: 123456" -H "Content-Type: application/json" \
  -d '{
        "tipo": "COLECTIVA",
        "fechaInicio": "2026-01-01",
        "fechaFin": "2026-12-31",
        "mesesVigencia": 12,
        "canonMensual": 2500000.00,
        "nombreTomador": "Inmobiliaria Andes",
        "nombreAsegurado": "Arrendatarios Torre Norte",
        "nombreBeneficiario": "Juan Pérez",
        "riesgos": [
          {"descripcion": "Incendio torre norte", "valorAsegurado": 80000000.00}
        ]
      }'

# Listar pólizas con filtros opcionales (200)
curl "http://localhost:8080/polizas?tipo=COLECTIVA&estado=VIGENTE" -H "x-api-key: 123456"
curl "http://localhost:8080/polizas" -H "x-api-key: 123456"

# Ver los riesgos de una póliza (200)
curl http://localhost:8080/polizas/1/riesgos -H "x-api-key: 123456"

# Crear una póliza individual: exactamente un riesgo (201)
curl -X POST http://localhost:8080/polizas \
  -H "x-api-key: 123456" -H "Content-Type: application/json" \
  -d '{
        "tipo": "INDIVIDUAL",
        "fechaInicio": "2026-01-01",
        "fechaFin": "2026-12-31",
        "mesesVigencia": 12,
        "canonMensual": 800000.00,
        "nombreTomador": "Arrendatario",
        "nombreAsegurado": "Arrendatario",
        "nombreBeneficiario": "Arrendador",
        "riesgos": [
          {"descripcion": "Incendio", "valorAsegurado": 1000000.00}
        ]
      }'

# Renovar la póliza: canon y prima +IPC, estado RENOVADA (200)
curl -X POST http://localhost:8080/polizas/1/renovar -H "x-api-key: 123456"

# Agregar un riesgo a una póliza colectiva (201)
curl -X POST http://localhost:8080/polizas/1/riesgos \
  -H "x-api-key: 123456" -H "Content-Type: application/json" \
  -d '{"descripcion": "Robo torre norte", "valorAsegurado": 20000000.00}'

# Cancelar un riesgo (200)
curl -X POST http://localhost:8080/riesgos/1/cancelar -H "x-api-key: 123456"

# Cancelar la póliza: cancela también todos sus riesgos (200)
curl -X POST http://localhost:8080/polizas/1/cancelar -H "x-api-key: 123456"

# Mock del CORE: registra en logs el intento de envío (200)
curl -X POST http://localhost:8080/core-mock/evento \
  -H "x-api-key: 123456" -H "Content-Type: application/json" \
  -d '{"evento": "ACTUALIZACION", "polizaId": 555}'

# Sin api-key → 401
curl http://localhost:8080/polizas
```

En el log de la aplicación queda registrado el intento de envío al CORE por cada
operación de escritura (siempre **después** del commit de la transacción):

```
INFO  c.j.p.infrastructure.core.CoreHttpAdapter - Evento notificado al CORE: evento=CREACION polizaId=1
INFO  c.j.p.controller.CoreMockController - Intento de envío al CORE: evento=CREACION polizaId=1
INFO  c.j.p.infrastructure.core.CoreHttpAdapter - Evento notificado al CORE: evento=RENOVACION polizaId=1
INFO  c.j.p.controller.CoreMockController - Intento de envío al CORE: evento=RENOVACION polizaId=1
```

## 10. Cobertura de las pruebas

| Suite | Foco | Pruebas |
|---|---|---|
| `ReglasPolizaTest` | reglas puras (tipos, estados, IPC, prima, escala del dinero) | 17 |
| `PolizaServiceTest` | servicio de póliza con puertos simulados | 23 |
| `RiesgoServiceTest` | servicio de riesgo con puertos simulados | 7 |
| `PolizaControllerTest` | contrato HTTP de `/polizas` (200/201/400/401/404/405/409/415/500) | 21 |
| `RiesgoControllerTest` | contrato HTTP de los riesgos | 6 |
| `CoreMockControllerTest` | mock del CORE, validaciones y registro en logs | 6 |
| `ManejadorErroresGlobalesTest` | traducción de excepciones a códigos HTTP | 14 |
| `ApiKeyFilterTest` | seguridad mínima por encabezado | 5 |
| `CoreHttpAdapterTest` | adaptador HTTP del CORE (éxito, error, fallo de red) | 4 |
| `NotificacionCorePostCommitTest` | notificación diferida al commit y política *best-effort* | 5 |
| `PolizaRepositoryTest` / `RiesgoRepositoryTest` | persistencia, versión y filtros contra H2 | 10 |
| `ApiPolizasIntegrationTest` | flujo completo sobre la aplicación real | 12 |
| `NotificacionCorePostCommitIntegrationTest` | adaptador HTTP real contra un CORE simulado (verifica que el evento se envía **después** del commit) | 1 |
| **Total** | | **131** |

Cobertura de líneas: **99,45 %** (363 de 365). Informe:
`target/site/jacoco/index.html`.

## 11. Notas de implementación

- El IPC se aplica con una sola operación de redondeo
  (`canon × (100 + IPC) / 100`) para evitar doble redondeo.
- La prima **nunca** se toma como dato maestro: la deriva el dominio. Si el
  cliente la envía, se valida contra el valor calculado y se rechaza con `400`
  si no coincide.
- Los importes se normalizan a **2 decimales** (`HALF_UP`) al construir las
  entidades, de modo que lo que devuelve la API es exactamente lo que se
  guarda en la base de datos (`DECIMAL(19,2)`); además, los DTOs rechazan con
  `400` cualquier importe con más decimales (`@Digits`).
- La renovación extiende el periodo de vigencia; es la lectura de negocio del
  enunciado ("renovadas por el mismo periodo de vigencia inicial").
- La base de datos es H2 en memoria porque el alcance del módulo es la lógica de
  negocio y la integración; el esquema se genera por JPA (`ddl-auto=create-drop`)
  y la capa de repositorio ya está preparada para MySQL/PostgreSQL.
- **Concurrencia:** `Poliza` y `Riesgo` llevan `@Version`. Dos renovaciones
  simultáneas de la misma póliza no pueden aplicarle el IPC dos veces: la
  segunda recibe `409 Conflict` ("La póliza fue modificada por otra operación;
  reintente la operación.") y el consumidor puede reintentar de forma segura.
- **Notificación al CORE:** el envío se registra como sincronización
  `afterCommit`, de modo que el CORE solo se entera de operaciones confirmadas
  y un *rollback* no genera ninguna notificación.
  *Mejora pendiente para producción:* sacar el envío del hilo de la petición
  (`@Async` con cola de mensajes, o patrón *outbox* + *relay*) para que la
  latencia del CORE no bloquee al consumidor, y añadir reintentos con
  *backoff* exponencial y cola de mensajes pendientes (dead letter queue).
- **Validaciones de entrada:** los textos tienen longitud máxima (`@Size`) y el
  evento dirigido al CORE rechaza saltos de línea y caracteres de control
  (`@Pattern`), para evitar inyección en los logs (log forging, CWE-117).
