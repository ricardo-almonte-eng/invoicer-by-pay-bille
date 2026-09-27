# 04 — Estado y datos

> Desde el 2026-09-16 el proyecto es Kotlin Multiplatform y **offline first**. Los antiguos
> "stores" de Zustand son ahora **tablas de Room** (lo que persiste) y **`ScreenModel`** (lo que
> vive mientras la pantalla está abierta).

Dos capas, y la frontera es dura:

| Capa | Herramienta | Qué guarda |
|---|---|---|
| **Datos** | **Room** (vía un *Repository*) | Todo lo que debe sobrevivir a cerrar la app: sesión, tienda, borradores, copias locales de facturas, productos y clientes, cola de envíos |
| **Estado de pantalla** | **`StateScreenModel<UiState>`** (Voyager) | Lo que se está tecleando, el paso de un flujo, si hay una acción en curso, el mensaje de error |

**La pantalla nunca lee de la red.** Observa un `Flow` de Room; el repositorio refresca Room
cuando hay red → [02](02-api-y-fetch.md#patrón-estándar-en-una-pantalla).

El error clásico es copiar una lista de Room al `UiState` "para tenerla a mano" y dejar de
observar: a partir de ahí hay dos verdades y una se queda vieja. El `UiState` guarda **lo que la
pantalla añade**, no copias de la base.

## Convención

```kotlin
data class LoginUiState(val username: String = "", val submitting: Boolean = false, /* … */)

class LoginScreenModel(
    private val sessions: SessionRepository,
) : StateScreenModel<LoginUiState>(LoginUiState()) {
    fun submit() { /* mutableState.update { … }; screenModelScope.launch { … } */ }
}
```

- Un `UiState` inmutable (`data class`) por pantalla, actualizado con `mutableState.update {}`.
- Los `ScreenModel` se registran en Koin con `factoryOf(::…)` y la pantalla los pide con
  `koinScreenModel<…>()`.
- **Los repositorios son `single`** y son los únicos que tocan DAO y `RemoteDataSource`.
- La pantalla recibe el estado y lambdas; el `Content()` del `Screen` solo conecta.

## Catálogo

### `SessionRepository` — equivalente de `useUserStore`

Tablas `session` (una fila), `market` y `market_settings`. Detalle del arranque →
[01](01-arquitectura.md#arranque-y-sesión-offline-first).

| Dato | Notas |
|---|---|
| `state: Flow<SessionState>` | `Loading` · `SignedOut` · `SignedIn(session)`. **`App` decide la pantalla con esto** |
| `session.torning` | Turno del usuario. Se manda en las ventas aunque la app no tenga turnos |
| `session.displayName` | Para `Username: "${FirstName} ${LastName}"` |
| `session.roleName` | Se guarda porque la cascada lo trae, pero **la UI no se ramifica por él** → [08](08-reglas-de-negocio.md) §8 |
| `session.store` | La tienda. `taxValue` ya es `Double?` (normalizado en el DTO) |
| `session.settings` | `Tax` y `LogoInBill` de la fila `Settings` |
| `session.defaultTaxRate` | `store?.taxValue ?: 0.18`. **Valor por defecto**; la tasa efectiva vive en el borrador → [08](08-reglas-de-negocio.md) §1 |
| `login()` / `logout()` / `refreshProfile()` | La sesión **no vence**: solo `logout()` la borra |

`SessionTokenStore` guarda el token en memoria para el cliente HTTP. Va aparte del repositorio
porque el repositorio depende de la red y la red del token: juntos serían un ciclo en Koin.

### Borrador de factura (fase 2)

La tabla más importante de la app. Es la que hace que **no** repliquemos la "venta base" del POS.

```kotlin
data class DraftInvoice(
    val kind: DraftKind,                 // Factura · Cotizacion · NotaCredito · NotaDebito
    val client: DraftClient?,            // IdClient, nombre, teléfono, identify
    val lines: List<DraftLine>,          // idWarehouse, IdProduct, Barcode, Name, Amount, Price, Discount
    val taxType: TaxType,                // with_tax · included · no_tax
    val taxRate: Double,                 // tasa EFECTIVA de esta factura (0.18 = 18 %)
    val currency: Currency,              // DOP · USD · EUR — moneda en la que se EMITE
    val exchangeRate: Double,            // unidades de moneda BASE por 1 de `currency`. DOP → 1
    val withNcf: Boolean, val ncfType: NcfType?,
    val payment: DraftPayment,           // efectivo, transferencia, tarjeta, IdCuenta
    val dueDate: String?, val plan: InstallmentPlan?,
    val refIdSale: Int?,                 // solo NC/ND: la factura que se ajusta
    val sendState: SendState,            // Draft · Sending · Error
)
```

Reglas:

- **Se guarda en Room en cada cambio.** Si la app muere a media factura, al volver está todo. Es
  la diferencia entre una app que se usa y una que no.
- **Los totales no se guardan: se derivan** llamando a `core/billing/Tax.kt`. Un total guardado se
  desincroniza de sus líneas.
- **`taxRate` y `exchangeRate` sí se guardan**, y son la excepción que confirma la regla anterior:
  no son totales, son *entradas* del cálculo. Si no se guardaran, un borrador recuperado
  recalcularía con la tasa del negocio y cambiaría el total a espaldas del usuario.
- **`currency` y `exchangeRate` no se mandan a la API**: los importes viajan en moneda base. Al
  guardar con éxito se archivan en Room contra el `id` de la venta, que es lo único que permite
  reimprimir el PDF en su moneda → [08](08-reglas-de-negocio.md) §11.
- Al confirmar el envío con éxito, **se borra**. Si falla, queda con `sendState = Error` y la
  pantalla ofrece reintentar. **Sin red, se encola** y se envía cuando vuelva.

### Cómo quedó guardado (2026-09-16)

| Tabla Room | Qué guarda |
|---|---|
| `invoice_drafts` | Borrador por tipo (`kind` → `InvoiceDraft` en JSON) |
| `invoice_outbox` | **Cola de envíos**: el borrador congelado + `SendProgress` (qué pasos ya se hicieron) |
| `sales` | Lista del Inicio (cabeceras) |
| `sale_details` | Detalle ya visto (`SaleDetail` en JSON), para abrirlo sin red |
| `receivables` | Cuentas por cobrar con saldo: vencimientos del Inicio y avisos |
| `cached_payloads` (v5) | Respuesta de la API en JSON por clave: `dashboard:{rango}`, `report:{reporte}:{rango}`, `balances:Cobrar`, `client:{id}`, `product:{id}`, `inventory:info`, `catalog:categories` · `catalog:brands` · `catalog:colors`, `catalog:shownIds` (ids con `IndShowOnCatalog`), `invoice:payTo` (lo último elegido en "Dónde pagar", prellena la siguiente factura: `CachedPayToMemory`). Lo que no se consulta por columnas no necesita tabla propia (`PayloadCache`) |
| `products` (v5) | Inventario **agrupado por nombre** (`productinventory/allgrouped`): copia completa, se busca en el teléfono |
| `clients` (v5) | Clientes de la tienda: copia completa, se busca en el teléfono |
| `bank_accounts` (v5; v6 + `holderName`, `holderId`) | Cuentas de dinero con su balance y su titular. `canReceiveTransfers`: activa, no `Caja` y con número (las que ofrece "Dónde pagar") |
| `account_movements` (v5) | Movimientos de cada cuenta; se reemplazan por rango de fechas |

**Catálogos completos en el teléfono (v5).** Productos, Clientes y Bancos no paginan contra la
red al hacer scroll: bajan **todo** (páginas de 100, tope de 50 páginas) al mostrarse si lo último
tiene más de 5 min (1 min en Bancos), y la búsqueda es local, `LIKE '%texto%'`. Así buscan sin
red y encuentran "Coca Cola" escribiendo "cola" (el `like` del servidor solo busca por el
principio). Crear y editar necesitan red, como el abono.

La cola la vacía `InvoiceSender` al guardar, al abrir la app y al refrescar el Inicio. Guarda
el progreso **tras cada paso**, así un reintento no pide otra secuencia, no quema otro NCF y no
duplica líneas ni descuentos de inventario (lo comprueban las pruebas de `InvoiceSenderTest`).

### Tema claro / oscuro

Hoy `PbTheme` sigue al sistema (`isSystemInDarkTheme()`). El selector manual (`system` · `light` ·
`dark`) llegará con Ajustes y se guardará en Room. Por defecto **`system`**, que en el POS no
existe porque en web el usuario elige a mano; en móvil lo espera el sistema operativo.

### Avisos y confirmaciones

No hay "store de UI" global. El POS tiene siete (`modals`, `dialog`, `messageBox`, `toast`,
`loading`, `form`, `dataGridView`) porque su shell monta modales globales; aquí el aviso
(`PbBanner`) y la confirmación viven **dentro de la pantalla** que los provoca.

## Lo que NO se porta del POS

| Store del POS | Por qué no |
|---|---|
| `cart.js` | Su estado vive en el servidor (venta base). Lo sustituye el borrador en Room |
| `modals.js`, `dialog.js`, `messageBox.js` | Sin shell de modales globales |
| `loading.js` | Sin overlay global: el indicador va donde está la acción |
| `form.js` | Cada `UiState` lleva sus propios errores de campo |
| `dataGridView.js`, `table.js` | No hay grid: hay listas |
| `mesa.js`, `invoice.js`, `image.js`, `shoppingsData.js`, `printerConfig.js` | Restaurante, impresión térmica y flujos fuera de alcance |
| `commonData.js` | Sus catálogos no se usan. Lo único que sobrevive es el **formato de moneda y fecha**, que va a `core/format/` |

## `core/format/` — copia el comportamiento del POS, no su código

Hoy existe `formatPercent(0.18) == "18 %"`. El formato de dinero llega con la fase 2 y debe ser
**exactamente** este:

```kotlin
enum class Currency(val symbol: String) { DOP("$"), USD("USD"), EUR("EUR") }

// "$ 1,250.00" · "USD 25.00"
fun formatMoney(value: Double, currency: Currency = Currency.DOP): String
```

- **`$` solo para la moneda base.** Es lo que sale por el papel del POS
  (`utils/receipt.js: formatCash(v, symbol = '$')` con `LOCALE 'es-DO'`), espacio incluido.
- **USD y EUR con su código**, no con su símbolo: dos `$` distintos en la misma app es justo como
  se cuela un cobro en la moneda equivocada.
- **Separadores `es-DO`**: coma para miles, punto para decimales, dos decimales siempre.
- ⚠️ **No uses un formateador de moneda de la plataforma.** Con `es-DO` devuelven `US$1,250.00` o
  `DOP 1,250.00`, nunca el `$ 1,250.00` del comprobante, y además Android e iOS no coinciden. Se
  formatea el número a mano en `commonMain` y se concatena el símbolo.

**Por qué `$` y no `RD$`** (decisión cerrada el 2026-09-06; no se reabre sin tocar también el
POS): el papel que el POS entrega hoy ya dice `$ 600.00`. Si el móvil dijera `RD$`, el mismo
negocio estaría dando dos comprobantes que no coinciden. El día que se cambie, se cambia en
`core/format/` **y** en `utils/receipt.js` del POS, a la vez.

Fechas: zona `store?.timeZone ?: "America/Santo_Domingo"`, formato `DD/MM/YYYY hh:mm a` — el mismo
de `commonData.js:215`. Y recuerda: **para ordenar se usa `createdAt`, no `Date`**
([03](03-modelo-de-datos.md)).
