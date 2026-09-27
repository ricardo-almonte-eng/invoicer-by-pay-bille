# 11 — Plan de implementación

Orden pensado para que **haya algo usable en la fase 2** y todo lo demás se apoye encima.

---

## Fase 0 — Andamiaje ✅ (2026-09-16)

- [x] Proyecto KMP: `composeApp` (Android + iOS) + `androidApp` + `iosApp` (Xcode).
- [x] Dependencias: Compose MP, Koin, Voyager, Ktor, kotlinx.serialization, Room KMP, BuildKonfig
      → [01](01-arquitectura.md).
- [x] `local.properties` con `paybille.apiKey` (no se versiona).
- [x] Tema: tokens de [05](05-diseno-y-tema.md), `PbTheme`, tipografía, respuesta al toque.
- [x] Fuentes: 4 TTF estáticos de Google Sans Flex → [13](13-recursos-de-marca.md).
- [x] Iconos Material Symbols Rounded (vectores; desde el 2026-09-18, la fuente).
- [x] `core/network` con el cliente, errores tipados y serializadores tolerantes.
- [x] Pruebas de contrato con la API en `commonTest`.
- [ ] Icono de la app y pantalla de arranque (se quitó el icono prestado de la plantilla).
- [ ] `core/billing/Tax.kt` y `Money.kt` (las dos conversiones de moneda, con prueba por
      dirección → [08](08-reglas-de-negocio.md) §11). Pasan a la fase 2, donde se usan.

---

## Fase 1 — Sesión ✅ (2026-09-16, pendiente de verificar en dispositivo)

- [x] Pantalla de login (usuario/contraseña, `key` de la API en el body).
- [x] **Login multitienda en dos fases** (`requiresMarket`), que la documentación anterior no
      contemplaba.
- [x] Sesión en Room + cascada `persons · roles · markets · Settings` en paralelo.
- [x] `Session.defaultTaxRate` normalizado.
- [x] Arranque: con sesión → Inicio; sin sesión → login. Cerrar sesión con confirmación.
- [x] **La sesión no vence** (decisión del 2026-09-16): un 401/403 **no** cierra sesión; sustituye
      al "401 → logout limpio" que había aquí.
- [x] Refresco del perfil en segundo plano, con aviso de "sin conexión".
- [ ] Verificación en Android y en iOS (Xcode) por el usuario.

**Se termina cuando:** el usuario entra con su cuenta de PayBille y ve el nombre de su tienda.

---

## Fase 2 — Facturar (**el MVP de verdad**)

Adelantado el 2026-09-16, a petición del usuario:

- [x] **Inicio con pestañas Todas / Ventas / Cotizaciones**, leyendo de Room (tabla `sales`,
      migración 1→2) y paginando desde la API con `Gasto IS NULL`.
- [x] `formatMoney` (`$ 1,250.00`) y fecha corta en la zona del negocio.
- [x] Perfil aparte (tienda, sincronización, cerrar sesión).
- [x] Barra inferior, botón "+" y vencimientos en las filas (ver [07](07-navegacion-y-pantallas.md)).

- [x] Borrador de factura en Room, y **cola de envíos** para lo que se haga sin red.
- [x] Buscador de productos (`productinventory/sales` + paginación) y de clientes.
- [x] Editor de factura y cotización → [07](07-navegacion-y-pantallas.md).
- [x] Selector de **tasa de impuesto** y de **moneda + tasa de cambio** en los Totales.
      Al guardar: importes en **moneda base**; moneda y tasa se archivan en Room contra el `id`
      de la venta, que es lo único que permite reimprimir el PDF.
- [x] `core/billing/Tax.kt` y `Money.kt`, con pruebas, conectados a los Totales.
- [x] Guardado con la secuencia de pasos de [02](02-api-y-fetch.md), con reintento sin duplicados
      (`InvoiceSender`, progreso guardado paso a paso).
- [x] Descuento de inventario + `reportInventory` + garantía, con **aviso** si no alcanza la
      existencia (puede quedar negativa) → [08](08-reglas-de-negocio.md) §4.
- [ ] Moneda y tasa archivadas contra el `id` de la venta (el PDF del servidor sale en pesos).
- [x] Alta rápida de cliente y de producto desde los buscadores (2026-09-18).
- [ ] Buscar productos y clientes sin conexión **en los buscadores del editor** (los destinos
      Productos y Clientes ya buscan en Room desde el 2026-09-18; los buscadores aún van a la red).
- [x] Lista de facturas con filtros por estatus y **`Gasto IS NULL`** (en el Inicio).
- [x] Detalle de factura con la "factura grande" (PDF del servidor) como protagonista.
- [x] Compartir y descargar el PDF (hoja del sistema; Descargas / Archivos).

**Se termina cuando:** el usuario factura desde el teléfono y la factura aparece igual en el POS.
A partir de aquí la app ya sirve para algo, y todo lo demás se puede probar contra uso real.

---

## Fase 3 — Cobrar

- [x] `from-sale` al guardar una factura con saldo (y al abrir el detalle de una vieja).
- [x] Pantalla de detalle con abonos, saldo y vencimiento.
- [x] Registrar abono (`{id}/payments`) con método, cuenta y referencia (necesita red).
- [x] Avisos locales de vencimiento y cobro → [07](07-navegacion-y-pantallas.md).
- [ ] Cola de abonos sin red.
- [x] Pantalla **Saldos pendientes**: `accountdocs/byparty` (Reportes, 2026-09-18).
- [ ] Planes de cuotas: crear y ver el calendario.
- [ ] Anular abono. **No es acción de administrador** (la app no tiene roles), pero sí
      destructiva: menú `⋯` y confirmación.

**Cuidado aquí:** no crear movimiento de cuenta al abonar; lo hace el servidor
([08](08-reglas-de-negocio.md), regla 3).

---

## Fase 4 — Inventario y clientes

- [x] Lista de existencias con los tres estados (hay · agotado · negativa) — 2026-09-18.
- [ ] Aviso de stock bajo (`Amount <= MinAmountQty`): `allgrouped` no trae `MinAmountQty` (petición 11).
- [x] Alta rápida: producto + warehouse en un solo formulario (como `ProductQuickCreate`).
- [x] Editar precio, costo y existencia, con su `reportInventory` (solo productos generales de un lote).
- [ ] Ajuste manual de inventario (entrada/salida con motivo).
- [x] Clientes: lista, alta rápida, ficha con su saldo y sus facturas.

---

## Fase 5 — Los otros documentos

- [ ] Cotizaciones: lista, alta y conversión a factura → [09](09-documentos.md).
- [ ] Órdenes de compra: alta, "En camino", confirmar recepción, cuenta por pagar.
- [ ] Notas de crédito y débito por la vía C (abono / documento en `accountdocs`), **con el aviso
      en pantalla de lo que esa vía no cubre**.
- [x] Cuentas de dinero: lista, balance, movimientos y movimiento manual (2026-09-18).

---

## Seis destinos (2026-09-18, a petición del usuario)

- [x] Barra: Facturas (principal) · Resumen · Productos · Clientes · Reportes · Bancos; el "+" en
      todos → [07](07-navegacion-y-pantallas.md).
- [x] Resumen = dashboard del POS (`dashboard/summary`).
- [x] Reportes: ventas por fecha, productos vendidos, saldos pendientes, histórico del inventario.
- [x] Iconos con la fuente Material Symbols Rounded (eje FILL) → [13](13-recursos-de-marca.md).
- [ ] Rango de fechas personalizado en Reportes (hace falta un selector de fechas `Pb*`).
- [ ] Exportar reportes (PDF o Excel).

---

## Cobro y catálogo (2026-09-27, a petición del usuario)

- [x] "Dónde pagar" en la factura: varias cuentas + instrucciones, en el PDF (API `F4`).
- [x] Titular y cédula/RNC en la cuenta (app, POS y API).
- [x] Notificaciones: la campana de Facturas (vencidas, por vencer, sin enviar).
- [x] Catálogo en línea: compartir el enlace y elegir qué productos salen (`IndShowOnCatalog`).
- [x] Catálogo del POS con filtros de marca y color, disponibilidad y orden, estilo tienda.
- [ ] **Ejecutar `F4_instrucciones_pago.sql`** en la base antes de desplegar la API.
- [ ] Carrito y pagos en línea (Stripe, Azul, Cardnet) → [14](14-carrito-y-pagos-en-linea.md).

---

## Fase 6 — Pulido

- [ ] Estados vacíos con ilustración en todas las listas.
- [ ] Tipografía grande del sistema (accesibilidad).
- [ ] Icono de la app y pantalla de arranque con la marca PayBille.
- [ ] Firma y distribución interna (Android: APK/AAB firmado; iOS: TestFlight).

---

## Peticiones al backend

Ordenadas por cuánto simplifican esta app. **Ninguna bloquea el MVP**, pero las dos primeras se
notan mucho.

| # | Petición | Por qué |
|---|---|---|
| 1 | **`POST /sales/complete`** — cabecera + líneas en una transacción, resolviendo secuencia, NCF, inventario y documento espejo | Elimina la secuencia de 8 llamadas y el riesgo de factura a medias con mala señal. Es *la* mejora |
| 2 | **`POST /sales/{id}/credit-note`** — nota de crédito como entidad, enlazada a la factura, con opción de devolver inventario | Hoy las NC/ND no existen y hay que aproximarlas → [09](09-documentos.md) |
| 3 | **`GET /sales/{id}/full`** — factura con sus líneas, abonos y saldo en una llamada | Hoy son 3 llamadas para pintar un detalle |
| 4 | Confirmar los valores reales de **`cuentas.Type`** | La documentación dice `Efectivo\|Banco\|Tarjeta`, el código filtra por `'Caja'` |
| 5 | Un endpoint de **resumen del día** por `IdMarket` | La pantalla de Inicio hoy tendría que sumar en el cliente |
| 7 | **Regenerar el PDF cuando cambia la factura** (`pdf.js → savePDF` lo guarda por nombre y lo devuelve siempre) | Tras un abono, el PDF compartido sigue diciendo "Pendiente" y el saldo viejo |
| 8 | **`GET ventas/factura/{id}` sin token** | Cualquiera puede descargar cualquier factura probando ids. Debería pedir `auth` o usar el token público del QR |
| 9 | **`from-sale` no debería crear documento para una venta pagada** (o un `accountdocs/by-sale/{id}` de solo lectura) | Hoy la app evita llamarlo en ventas pagadas para no crear un documento de más |
| 10 | **`cuentas/{id}/movimientos` debería leer `page`/`pageSize` y fechas del cuerpo** (o el POS mandarlos como la app) | El POS enseña siempre los 10 movimientos más recientes, elija el rango que elija |
| 11 | **`MinAmountQty` en `productinventory/allgrouped`** | Sin él, la lista de productos no puede avisar de "quedan pocos" |
| 12 | **Registrar `report/fiscal` y `report/actualmonth` antes que `report/:isTorning`** (`routes/reports.js`, verificado) | Hoy las atiende la ruta genérica: "Ventas con NCF" del POS enseña la tabla vacía |
| 13 | **`'Nomina'` sin tilde en `CreateCuenta.vue`** | El ENUM de `cuentas.Type` rechaza `'Nómina'`: crear una cuenta de nómina desde el POS falla |
| 6 | **`Currency` (ISO 4217) + `ExchangeRate` en `sales`** | Hoy la moneda de emisión no se puede guardar: vive en el teléfono y se pierde al reinstalar. Es lo único que impide que el POS enseñe la factura como se emitió → [08](08-reglas-de-negocio.md) §11 |

## Decisiones de producto (cerradas el 2026-09-06)

Las cuatro que estaban pendientes ya las decidió el dueño del proyecto. Se recogen aquí con su
porqué; la regla operativa vive en la guía que toca.

| # | Decisión | Dónde se aplica |
|---|---|---|
| 1 | **Moneda base `$`** (DOP), separadores `es-DO` → `$ 1,250.00` | `core/format/` → [04](04-estado-y-stores.md) |
| 2 | **La app sí crea productos**: alta rápida con nombre, precio y cantidad, y código de barras automático | Fase 4 → [03](03-modelo-de-datos.md) |
| 3 | **NCF apagado por defecto**, se activa factura a factura | [08](08-reglas-de-negocio.md) §5 |
| 4 | **Vender sin existencia avisa pero deja pasar**; la existencia puede quedar negativa | [08](08-reglas-de-negocio.md) §4 |
| 5 | **La tasa de impuesto se elige por factura**, prellenada con la del negocio | [08](08-reglas-de-negocio.md) §1 |
| 6 | **La moneda se elige por factura** (`DOP`/`USD`/`EUR`) con **tasa de cambio manual**; se guarda en base y se emite en la elegida | [08](08-reglas-de-negocio.md) §11 |

**Y una decisión más que no estaba en ninguna lista: la app no tiene roles.** Es de **uso personal,
un solo usuario**; no hay perfiles que repartir. `session.rol` se guarda porque la cascada de
login lo trae, pero **ninguna pantalla se ramifica por él** y no existe la pantalla de "sin
permiso" → [08](08-reglas-de-negocio.md) §8. Simplifica la fase 3 (anular abono deja de ser
acción de administrador) y la 5 (Compras y Cuentas se ven siempre).

### Lo que se verificó en el POS antes de decidir

- **Moneda.** El papel del POS ya imprime `$ 600.00`: `utils/receipt.js` usa
  `formatCash(v, symbol = '$')` con `LOCALE = 'es-DO'`. La pantalla formatea con `en-US`/`USD`.
  Es decir, el POS dice `$` en los dos sitios; el móvil diciendo `RD$` habría sido el único
  disidente. **No era una incoherencia del POS, como suponía la versión anterior de esta guía.**
- **NCF.** `completeOrder.vue:529` arranca con `withNCF = ref(false)` y rotula *"Esta venta no
  llevará comprobante fiscal"*. Apagado por defecto es replicar el POS, no apartarse de él.
- **Sin existencia.** `productOptions.vue:286` corta con `return` y el mensaje *"Cantidad
  insuficiente de productos disponibles"*. Aquí se decide **no** replicar ese bloqueo.
- **Alta rápida.** `ProductQuickCreate.vue` son 640 líneas, pero solo tres campos son
  obligatorios (`Name`, `Price1`, `Amount`) y el código de barras se genera con
  `randomBarcode()`. La versión móvil cabe en una pantalla.

### Efectos secundarios pendientes de resolver al programar

1. ~~Venta a crédito + NCF apagado.~~ Resuelto: el POS no fuerza el NCF en las ventas a crédito
   sino al pagar con tarjeta, y Invoicer no replica esa regla → [08](08-reglas-de-negocio.md) §5.
2. **Inventario negativo en la UI.** La lista de existencias necesita un tercer estado además de
   "normal" y "stock bajo": **negativo**, que se resalta como aviso y no como error.
3. **La moneda de emisión no tiene dónde vivir en el servidor** (petición 6). Hasta que exista,
   se archiva en el teléfono contra el `id` de la venta y **el PDF es el único registro**: si se
   reinstala la app, esa factura se reimprime en moneda base. Hay que decirlo en pantalla al
   emitir en moneda extranjera, no descubrirlo tres meses después.
4. **Impuesto y moneda son dos entradas nuevas al mismo cálculo**, y las dos llegan como string
   desde un input. `toDoubleOrNull()` en las dos, y las conversiones **solo** en
   `core/billing/Money.kt`: la dirección invertida no lanza error, solo produce un número creíble y equivocado.
