# 08 — Reglas de negocio

Las que rompen cosas en silencio van primero.

## 1. Impuestos (ITBIS) — regla crítica

**Los precios de producto ya incluyen impuesto.** El cálculo de impuesto es informativo.

- `market.taxValue` es el **valor por defecto** de la tasa, en **decimal** (`0.18` = 18 %).
- **La tasa se elige al crear la factura** (decisión de producto, 2026-09-06). El editor la
  muestra prellenada con la del negocio y el usuario la cambia si esa factura lleva otra. Vive en
  el **borrador**, no en `Settings`: cambiarla en una factura **no** cambia la siguiente.
- **Siempre normalizada a `Double`.** La API la devuelve como string (`"0.18"`). En el POS
  (JavaScript) `1 + "0.18"` **concatena** (`"10.18"`); en Kotlin no compila, pero la trampa
  cambia de forma: un `toDouble()` a mano en una pantalla revienta con `"18 %"` o con `""`. Por
  eso se normaliza **en el borde**, una sola vez, con `LenientDoubleSerializer`
  (`core/network/LenientSerializers.kt`), y la sesión ya expone un `Double?`.
- **Nunca hardcodees `0.18`**, tampoco ahora que es editable: el valor inicial sale siempre de
  `market.taxValue`. Y el campo del editor devuelve **string**, así que la conversión ahora
  tiene dos entradas, no una: la del editor se valida con `toDoubleOrNull()` y nunca con
  `toDouble()`.

```kotlin
// feature/auth/domain/Session.kt — de aquí sale el valor POR DEFECTO (ya existe)
val defaultTaxRate: Double get() = store?.taxValue ?: DEFAULT_TAX_RATE   // 0.18

// borrador de factura (fase 2) — la tasa EFECTIVA de la factura que se está creando
val taxRate: Double = session.defaultTaxRate   // editable en el editor
```

**La tasa efectiva se lee del borrador, no de la sesión.** `core/billing/Tax.kt` la recibe por
parámetro (`lineTotals(line, taxType, rate)`), así que las fórmulas no cambian: cambia **quién**
le pasa el número.

⚠️ **La API guarda el importe, no el porcentaje.** `sales.Tax` y `salesProducts.Tax` son importes
y no hay columna para la tasa. No se pierde información —se reconstruye con `Tax / SubTotal`— y
el POS muestra exactamente los mismos números, pero **no esperes leer "18 %" de vuelta de la
API**: si hay que mostrarlo en el detalle, se recalcula.

- El **rango aceptado es 0–100 %**. Un `0` es legítimo (equivale a `no_tax` en la práctica, pero
  sin cambiar `taxType`); rechaza negativos y mayores que 100.

### Las tres fórmulas — `core/billing/Tax.kt`, y solo ahí

`taxType` decide. Son las mismas de `stores/components/cart.js → recalcTotals()` y de
`stores/data/accountDocs.js → lineTotals()`, que ya coinciden con el backend:

| `taxType` | Subtotal | Impuesto | Total |
|---|---|---|---|
| `with_tax` | `precio × cantidad` | `base × tasa` | `base + impuesto − descuento` |
| `included` | `base − impuesto` | `base − base/(1+tasa)` | `base` (**no se incrementa**) |
| `no_tax` | `base` | `0` | `base − descuento` |

```kotlin
fun lineTotals(line: DraftLine, taxType: TaxType, rate: Double): LineTotals {
    val gross = round2(line.amount * line.price - line.discount)
    return when (taxType) {
        TaxType.NoTax -> LineTotals(subtotal = gross, tax = 0.0, total = gross)
        TaxType.Included -> {
            val sub = round2(gross / (1 + rate))
            LineTotals(subtotal = sub, tax = round2(gross - sub), total = gross)
        }
        TaxType.WithTax -> {
            val tax = round2(gross * rate)
            LineTotals(subtotal = gross, tax = tax, total = round2(gross + tax))
        }
    }
}
```

`round2` replica el del backend (`services/accountDocuments.js:49`):
`Math.round((v + Number.EPSILON) * 100) / 100`. `Math.round` de JavaScript lleva el `.5` hacia
arriba; **`kotlin.math.round` lo lleva al par** y descuadra céntimos contra el POS. Usa
`floor(x * 100 + 0.5) / 100` (con el mismo épsilon), no `round()`.

Redondea **a dos decimales por línea**, no al final: es lo que hace el backend, y sumar sin
redondear produce diferencias de céntimos que el usuario ve y no perdona.

Por defecto, `taxType` sale de `market?.taxType ?? 'with_tax'`.

## 2. Estatus de factura

| Estatus | Cuándo | Efectos |
|---|---|---|
| `Complete` | El cobro cubre el total | Descuenta inventario · movimiento de cuenta |
| `Pagos Pendientes` | Queda saldo | Descuenta inventario · **crea documento en `accountdocs`** (`from-sale`) |
| `Cotizacion` | El usuario eligió cotizar | **No** toca inventario · **no** mueve dinero · **no** consume NCF |
| `Cancelada` | Anulada | Ver abajo |

El POS decide así (`completeOrder.vue:769`): `'Complete'` por defecto; `'Pagos Pendientes'` si el
modo es *Adelanto* o *Cuotas*; `'Cotizacion'` si el modo es Cotización.

**Al saldarse una factura pendiente, el servidor la pasa solo a `Complete`** al registrar el último
abono. No lo hagas tú desde la app: se duplicaría la lógica.

### Anular

El POS marca `Status: 'Cancelada'` (y en algún sitio `'Cancelado'` — filtra por los dos). Anular
una factura que ya movió inventario **no devuelve la existencia automáticamente**: hay que crear el
movimiento inverso en `warehouse` y su fila en `reportInventory`. Si la factura tiene documento en
`accountdocs`, anúlalo también con `post('accountdocs', {}, '{id}/void')`.

> Si la factura ya tiene abonos, **no se anula: se emite una nota de crédito.** Ver
> [09](09-documentos.md).

## 3. Pagos parciales — cómo funcionan de verdad

Este es el corazón de la app, y no vive en `sales`: vive en el **libro de cuentas**
(`AccountDocuments`), que el POS estrenó justamente porque `Sales.Status = 'Pagos Pendientes'` no
guardaba ni abonos, ni fechas de vencimiento, ni historial.

```
Factura con saldo
   └─ post('accountdocs', { IdSale, DueDate, Plan }, 'from-sale')   ← idempotente
        └─ documento con Kind: 'Cobrar', Balance = lo que falta
             └─ post('accountdocs', { Amount, Method, … }, '{id}/payments')   ← cada abono
```

Reglas que **no se pueden romper**:

1. **El saldo tiene un solo punto de escritura.** `Paid` y `Balance` los escribe únicamente
   `applyPayment()` del backend, en transacción y con `lock`. El CRUD genérico los rechaza.
   **Nunca los mandes.**
2. **Un solo camino de cálculo.** `applyPayment`, `voidPayment` y `reconcile` terminan los tres en
   `rebuildFromPayments`. Por eso no hace falta que la app recalcule nada: pide el documento otra
   vez y muestra lo que diga el servidor.
3. **Anti-doble-conteo.** El movimiento de cuenta de un abono **lo crea el servidor** con
   `ReferenceType: 'CuentaDoc'` y `ReferenceId`. Si la app crea otro, el dinero se cuenta dos veces
   en los reportes del POS.
4. **`IsOpening`.** Lo que la factura ya traía cobrado se registra como *abono de apertura* y no
   genera movimiento propio (ese dinero ya se contó al cerrar la factura). Tampoco se reparte sobre
   las cuotas: el plan financia el saldo que quedó **después** del adelanto.

### El método del abono decide la columna de la venta

Al registrar un abono, el backend actualiza la venta (`syncMirror`) sumándolo a una columna
según `Method`: `Efectivo → Money`, `Deposito → MoneyDeposit`, `Intercambio → trade`, y
**cualquier otro → `MoneyCredit` (tarjeta)**. `Transferencia` es un método válido del ENUM, pero
caería en la columna de tarjeta. **Invoicer manda las transferencias como `Deposito`**
(`PaymentMethod.Transfer`).

El abono no puede pasar del saldo (el servidor lo rechaza) y lo registra el servidor con su
movimiento de cuenta si se da `IdCuenta`. Hoy **necesita red**: no hay cola de abonos.

### Plan de cuotas

Cantidad · frecuencia (`Semanal` / `Quincenal` / `Mensual`) · interés · mora.

- El **interés es flat**, y **la última cuota absorbe el residuo de céntimos** para que la suma
  cuadre exacta.
- `InterestRate` de `accountdocs` es una **tasa** (`0.05` = 5 %). La de `Financing` es un
  **porcentaje** (`40` = 40 %). No las mezcles.
- La mora se **recalcula** (no se acumula) desde el vencimiento: correr el devengo dos veces el
  mismo día da el mismo resultado.

## 4. Inventario

- Lo que se vende y se descuenta es la fila de **`warehouse`**, no el producto → [03](03-modelo-de-datos.md).
- Al cerrar la factura, por cada línea (`completeOrder.vue:812`):
  ```
  Amount_nuevo = infinityAmount ? Amount : Amount - línea.Amount
  sold = (unique || isExtra) ? true : false
  ```
- **`infinityAmount: true`** = servicio o producto sin control de existencia: **nunca** se
  descuenta.
- **`unique: true`** = artículo serializado: se vende una sola vez y queda `sold: true` con su
  `IdSale` y `NameClient`.
- En `reportInventory`, `Before`/`After` van a **`null`** cuando el artículo es `unique` o
  `infinityAmount` (no tiene sentido un "antes y después" de algo sin cantidad).
- **Vender sin existencia avisa, pero NO bloquea** (decisión de producto, 2026-09-06). El POS sí
  corta: `productOptions.vue:286` hace `return` antes de añadir la línea, con *"Cantidad
  insuficiente de productos disponibles"*. Aquí no, porque el vendedor en la calle vende lo que
  trae en la mano antes de darlo de alta.
- **La existencia puede quedar negativa, y se deja negativa.** No la recortes a `0`: un `-3` en
  Inventario es la señal de que faltan tres unidades por registrar, y ponerlo a cero borra la
  señal y hace mentir al inventario. La lista de existencias lo resalta igual que el stock bajo.
- El aviso dice cuánto hay y cuánto se pide, y **se puede continuar**. No lo muestres en líneas
  con `infinityAmount` ni `unique`: ahí no hay cantidad que comparar.

## 5. NCF (comprobantes fiscales dominicanos)

- **Apagado por defecto** (decisión de producto, 2026-09-06): la factura nace sin NCF y el
  usuario lo activa cuando le hace falta. Es lo mismo que hace el POS —`completeOrder.vue:529`,
  `withNCF = ref(false)`, rotulado *"Esta venta no llevará comprobante fiscal"*—, así que el
  vendedor independiente no se topa nunca con el campo.
- Las **secuencias se administran en el POS** (`pages/administracion/NCF.vue`). Esta app **solo
  consume**.
- Antes de emitir: `post('nfc', { IdMarket, tipoNCF }, 'verify')`. Si `newNFC` es exactamente
  `"No hay rangos disponibles para este tipo de NCF."`, **para y avisa** — no sigas.
- Para consumir: `post('nfc', { IdMarket, tipoNCF }, 'getNextNFC')` → el NCF que va en la factura.
- Tipos: **B01** crédito fiscal · **B02** consumidor final · **B03** nota de débito ·
  **B04** nota de crédito.
- **Un NCF consumido no se devuelve.** Si la factura falla después de pedirlo, ese número queda
  quemado: por eso el NCF se pide **lo más tarde posible**, justo antes de crear la cabecera.
- El título del documento se deriva del NCF (`utils/receipt.js:98`).

⚠️ **Trampa del POS (corregida 2026-09-16): lo que fuerza el NCF es pagar con TARJETA, no la
venta a crédito.** En `completeOrder.vue:748` la condición es
`if (withNCF.value || useCartStore().credit)`, y `cartStore.credit` es el importe cobrado con
**tarjeta** (`MoneyCredit`). Con `TypeNCF` vacío acaba llamando a `nfc/verify` con
`tipoNCF: ""`. **Invoicer no replica esa regla**: el NCF solo se pide cuando el usuario lo
activa, siempre con un tipo (B02 por defecto), y B01 exige RNC.

**Secuencia interna** (distinta del NCF, y siempre presente):
`post('invoiceSecuency', {}, 'next/{IdMarket}')` → `{ Sequence }`. Es el número de factura que ve
el usuario.

## 6. Métodos de cobro

Cómo lo guarda Invoicer (`InvoiceDraft`, `InvoiceSender`):

- **Estatus:** con saldo → `Pagos Pendientes`; sin saldo → `Complete`. El POS, en su modo
  normal, **bloquea** guardar con saldo y exige elegir "Adelanto" o "Cuotas"; aquí el saldo es
  la señal (guía 07: "si faltante > 0, nace Pagos Pendientes").
- **`Change`** = devuelta, **nunca negativa** y **solo sale del efectivo**. El POS manda
  `Money − Total` aunque sea negativo; el cierre de caja suma `Change` en las ventas pagadas
  (`services/ventas.js`). Cobrar de más por transferencia o tarjeta no se deja guardar.
- **Movimiento de cuenta** = lo que de verdad entró: `cobrado − devuelta`. El POS suma el cobro
  bruto (devuelta incluida) y cuenta de más.

`cash` (efectivo) · `deposit` (transferencia/depósito) · `credit` (tarjeta) — y en el POS también
`trade` (intercambio) y `financing`, **que Invoicer no usa**.

- `money = suma de los métodos`; `missing = total − money`.
- Si `missing > 0`, la factura nace `Pagos Pendientes`.
- `IdCuenta` asocia el cobro a una cuenta de dinero. Si se indica, se crea el movimiento de
  `Ingreso` (`ReferenceType: 'Venta'`).

⚠️ En `sales`, el efectivo se guarda en **`Money`**, la transferencia en **`MoneyDeposit`** y la
tarjeta en **`MoneyCredit`**. El nombre `Money` no significa "total cobrado": significa "efectivo".

## 7. Gastos

Un gasto **es una fila de `sales` con `Gasto = true`**. Así lo leen el resumen del turno, el
dashboard y los reportes del POS.

- `Money` lleva **siempre** el total del gasto, sea cual sea la forma de pago. **No** uses
  `MoneyDeposit` ni `MoneyCredit`: el cierre de caja los suma como cobros.
- El movimiento de cuenta lleva `ReferenceType: 'Gasto'`.
- **Guard obligatorio en cualquier consulta sobre `sales`: `Gasto IS NULL`.** La columna es
  nullable, así que `Gasto = false` **no filtra nada**.

Los gastos no están en el alcance v1, pero la regla del guard aplica desde la primera lista de
facturas que escribas.

## 8. Sin roles

**Decisión de producto (2026-09-06): Invoicer es de uso personal, un solo usuario.** No hay
perfiles que repartir, así que **no hay puertas por rol**: ni redirecciones, ni menús que
aparecen y desaparecen, ni pantalla de "sin permiso". Todo está siempre disponible.

- `session.rol` **se guarda** —la cascada de login lo trae— pero **ninguna pantalla se ramifica
  por él**. Si escribes un `if` sobre `rol.Name`, te has salido de esta decisión.
- `permissions` y `checkPermission()` tampoco valen: son *stubs* vacíos en el backend.
- La tabla `roles` sigue existiendo en la API con `'PreFacturador' | 'Facturador' | 'Tecnico' |
  'Administrator' | 'Master'` → [03](03-modelo-de-datos.md). Eso es dato del POS, no
  comportamiento de esta app.

Consecuencias concretas: **anular un abono deja de ser acción de administrador** (sigue siendo
destructiva: menú `⋯` y confirmación → [07](07-navegacion-y-pantallas.md)), y Compras y Cuentas
se ven siempre.

## 9. Turno de caja (`torning`)

**No existe en móvil**, pero el campo sí: `Torning: user.Torning` va en `sales`, `salesProducts` y
en la auditoría de los abonos. **Mándalo siempre tal cual.**

Consecuencia real y ya resuelta en el backend: las facturas con documento espejo **no aportan sus
acumulados al cierre de caja**; el dinero entra por `AccountDocumentPayments.Torning`, que guarda
el turno del abono. Es decir: **un cobro hecho desde el teléfono aparece en el cierre del POS del
día en que se cobró**, no en el de la venta original. Está bien así.

## 10. Multi-tienda

Todo lleva `IdMarket`. Un usuario pertenece a una tienda vía `person.IdMarket`. Esta app **no**
permite cambiar de tienda: eso es Control Maestro y vive en el POS.

## 11. Moneda por factura

**Decisión de producto (2026-09-06): la moneda se elige al crear la factura** —`DOP`, `USD` o
`EUR` por ahora— **con una tasa de cambio manual** que se teclea al lado.

### La regla que lo sostiene todo: se guarda en base, se emite en la elegida

**A la API van SIEMPRE los importes en la moneda base del negocio.** La moneda elegida y la tasa
cambian **solo lo que se ve en el editor y lo que se imprime en el PDF**.

No es una limitación que haya que arreglar: es lo correcto. `sales.Total`, `accountdocs.Balance`,
los movimientos de `cuentas` y el valor del inventario comparten base de datos con el POS y
**ninguno tiene columna de moneda** → [03](03-modelo-de-datos.md). Si mandáramos `25` donde el POS
espera `1500`, se corromperían a la vez el saldo del cliente, el balance de la cuenta y el
inventario. Guardando en base, todo cuadra **y** el cliente recibe igualmente su papel en euros.

### La tasa: dirección y ejemplo

`tasaCambio` = **cuántas unidades de la moneda base vale 1 unidad de la moneda elegida.** Es como
se dice en la calle: *"el dólar está a 60"*.

| | |
|---|---|
| Moneda base (los precios de `warehouse`) | `DOP` |
| Esta factura | `USD`, `tasaCambio: 60` |
| Precio en inventario | `1500` |
| **Se imprime** | `USD 25.00` |
| **Se manda a la API** | `1500` |

```kotlin
// core/billing/Money.kt — las dos únicas conversiones de la app
fun toInvoiceCurrency(base: Double, rate: Double): Double = round2(base / rate)
fun toBaseCurrency(value: Double, rate: Double): Double = round2(value * rate)
```

**Con `DOP` la tasa vale `1` y el campo no se muestra.** Un input que siempre vale uno es ruido.

⚠️ **La dirección es el bug clásico de esto.** Multiplicar donde tocaba dividir convierte 1,500
pesos en 90,000 dólares y **no falla nada**: sale un número, grande y creíble. Por eso las dos
funciones viven **solo** en `core/billing/Money.kt`, con un test por dirección, y ninguna pantalla hace la
cuenta a mano. Es la misma clase de trampa que la tasa de impuesto como string (§1).

### Qué se imprime en el PDF

> **Estado (2026-10-06):** la factura ya la genera el teléfono (`InvoiceHtml`), pero la moneda y la
> tasa todavía **no** se archivan contra el `id` de la venta (casilla abierta en
> [11](11-plan-de-implementacion.md)), así que sale en moneda base, como salía la del servidor.
> Archivarlas y pasarlas a `InvoiceHtml.model` es lo que falta para las tres líneas de abajo.

Un comprobante en moneda extranjera lleva **las tres líneas**, o no hay forma de auditarlo:

```
Total:        USD 25.00
Tasa:         1 USD = 60.00 DOP
Equivalente:  $ 1,500.00
```

Sin el equivalente, ni el cliente ni el contable pueden cuadrar ese papel contra el POS, que
seguirá enseñando `$ 1,500.00`.

### Lo que esta vía NO cubre — y hay que decirlo en pantalla

- **La moneda no viaja al POS.** No hay dónde guardarla: el POS mostrará `$ 1,500.00` para una
  factura emitida en euros. Correcta en importe, engañosa en símbolo.
- **La tasa tampoco se guarda en el servidor.** Vive en el teléfono (Room, asociada al
  `id` de la venta) y **se pierde al reinstalar**. Reimprimir esa factura desde otro teléfono la
  sacará en moneda base.
- Las dos se resuelven con la **petición 6** al backend →
  [11](11-plan-de-implementacion.md). Mientras tanto **el PDF es el único registro de la moneda**:
  mándaselo al cliente en el momento, no cuentes con reconstruirlo después.

### NCF y moneda extranjera

Un documento con NCF es fiscal. **Si hay NCF y la moneda no es `DOP`, avisa antes de guardar.** El
equivalente en pesos va impreso siempre, pero si el contable exige el comprobante en moneda
nacional, esa factura se emite en `DOP`. Eso no lo decide esta app: lo decide la DGII y el
contable del negocio.

## Dónde pagar (2026-09-27)

1. Solo en lo que se paga después: cotización o factura con saldo. Una factura cobrada completa
   no manda cuentas ni nota (y el PDF no las pintaría).
2. Solo cuentas que pueden recibir una transferencia: activas, que no son `Caja` y con número.
3. El titular y su **cédula/RNC** van en la cuenta, no en la factura: se escriben una vez.
4. Se guardan **ids** (`Sales.PaymentAccounts`). El servidor filtra por la tienda de la venta: un id
   ajeno no expone la cuenta de otra tienda.
5. La nota es texto libre, se recorta a 400 caracteres y el PDF la escapa (Handlebars).

