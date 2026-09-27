# 10 — Convenciones de código

## Obligatorio

1. **Todo en `commonMain`.** A `androidMain`/`iosMain` solo baja lo que exige la plataforma, y
   siempre con `expect`/`actual` → [01](01-arquitectura.md).
2. **HTTP solo por `core/network`.** Ninguna pantalla ni `ScreenModel` importa Ktor.
3. **La pantalla lee de Room**, nunca de la red → [04](04-estado-y-stores.md).
4. **El impuesto se calcula solo en `core/billing/Tax.kt`** y la moneda solo en
   `core/billing/Money.kt`. Aritmética de impuesto en un componente es un bug esperando a
   divergir del backend.
5. **Números de la API, normalizados en el DTO** con los serializadores `Lenient*`. Nunca
   `toDouble()` en una pantalla.
6. **Cero colores literales** fuera de `Colors.kt`. Todo por `PbTheme.colors`.
7. **Cero sombras ni elevación.** Se separa con borde de 1 dp.
8. **Sin Material.** Componentes `Pb*` propios → [06](06-componentes-ui.md).
9. **Texto con `PbTheme.typography`**; todo importe con `amount` (cifras tabulares).
10. **Nada pulsable por debajo de 44 dp.**
11. **Nunca envíes `Paid` ni `Balance`** a `accountdocs`: los rechaza el servidor.
12. **`Gasto IS NULL`** en toda consulta sobre `sales`.
13. **UI en español**, `es-DO`, `America/Santo_Domingo`.
14. **Base local con migraciones**: se sube `version`, nunca se destruye → [01](01-arquitectura.md).

## Nombres

| Cosa | Convención | Ejemplo |
|---|---|---|
| Componente propio | `Pb` + PascalCase, un archivo por componente | `PbButton.kt` |
| Pantalla | `…Screen` (`Screen` de Voyager) | `LoginScreen` |
| Estado de pantalla | `…ScreenModel` + `…UiState` | `LoginScreenModel`, `LoginUiState` |
| Repositorio | `…Repository` (`single` en Koin) | `SessionRepository` |
| Llamadas a la API | `…RemoteDataSource` | `AuthRemoteDataSource` |
| DTO de la API | `…Dto`, campos con `@SerialName` **igual que la tabla** | `MarketDto` |
| Tabla Room | `…Entity` + `…Dao` | `SessionEntity` |
| Paquete | `com.paybille.invoicer.feature.<feature>.{data,domain,presentation}` | |
| Función | camelCase, verbo primero | `refreshProfile()` |

**Los campos de la API se escriben exactamente como los devuelve el backend** dentro de
`@SerialName` (`"IdMarket"`, `"taxValue"`, `"Torning"`). La propiedad Kotlin va en camelCase; el
mapeo vive en el DTO y en ningún otro sitio.

## Idioma del código

- **Código en inglés** (nombres de variables, funciones, tipos).
- **Comentarios y textos de interfaz en español.**
- Los campos de la API son lo que son, y muchos ya vienen en español (`Cotizacion`, `Torning`,
  `Gasto`, `cuentas`, `movimientos`). No los traduzcas.

## Kotlin

- DTO y dominio son `data class` inmutables. El dominio **no** lleva anotaciones de
  serialización ni de Room.
- Donde el backend sea irregular, dilo en el DTO (`@Serializable(LenientDoubleSerializer::class)`)
  y normaliza al entrar. No mientas con un `Double` a secas sin serializador.
- `CancellationException` **siempre se relanza**: capturar `Exception` sin hacerlo rompe la
  cancelación de corrutinas.
- Nada de `!!` en código de producción; en pruebas se permite.
- Las pruebas de `commonTest` usan `MockEngine` con respuestas **copiadas de la API real**, y se
  nombran en español describiendo el caso (`credencialesMalasLleganComoStringConHttp200`).

## Comentarios

Se comenta **por qué**, no **qué**. El estilo de la casa está en el POS y es bueno; imítalo:

```kotlin
// El NCF se pide lo más tarde posible: un número consumido no se devuelve,
// así que si la creación de la cabecera falla, ese comprobante queda quemado.
```

Un comentario que explica una regla del backend vale más que diez que describen el código de al
lado.

## Git

- Rama por tarea, mensajes en español, imperativo: `Añade registro de abonos parciales`.
- **Un commit no deja la documentación desactualizada** → [12](12-flujo-de-trabajo.md).

## Lo que NO se hace

- No se replica el patrón de "venta base" del POS → [02](02-api-y-fetch.md).
- No se copian `css/Receipt.css` ni `css/Factura.css`: son papel térmico calibrado.
- No se usan `permissions` ni `checkPermission()`: son *stubs* vacíos en el backend.
- **No se ramifica la UI por `rol.Name`.** La app es de un solo usuario →
  [08](08-reglas-de-negocio.md) §8.
- No se inventan estatus de factura. El vocabulario está en [08](08-reglas-de-negocio.md) y es
  compartido con el POS.
