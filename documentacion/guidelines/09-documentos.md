# 09 — Cotizaciones, Notas de Crédito/Débito y Órdenes de Compra

Los cuatro documentos que la app maneja además de la factura. **Tres de ellos ya existen en la API;
las notas de crédito y débito, no.** Eso último no es un detalle: condiciona el plan de trabajo.

---

## Cotización — ✅ existe

Una cotización **es una fila de `sales` con `Status: 'Cotizacion'`** y sus `salesProducts`. Nada
más. No consume NCF, no descuenta inventario, no mueve dinero.

Alta (`CreateCotizacion.vue:558`): idéntica a una factura, con `Status: 'Cotizacion'`,
`Money: 0`, `Change: 0`.

### Listado

```ts
getGeneric('sales', {
  params: { Status: 'Cotizacion' },
  likeOrParams: ['Client', 'id'],     // ← búsqueda OR sobre varios campos
  likeOrValue: texto,
})
```

### Convertir en factura (`cotizacion.vue:437`)

El POS hace **una sola cosa**: `put('sales', { Status: 'En Proceso' }, id)` y abre la venta en el
módulo de ventas para cobrarla. La cotización **no se copia: se convierte**, conservando su `id` y
sus líneas.

En Invoicer, como no existe `'En Proceso'` ([02](02-api-y-fetch.md)), la conversión es:

1. Cargar la cotización y sus líneas en `useDraftInvoice` con `id` de origen.
2. El usuario ajusta y cobra.
3. Al guardar: **`put('sales', {...}, idCotizacion)`** con los totales, el cobro, la secuencia, el
   NCF y `Status: 'Complete' | 'Pagos Pendientes'`. Las líneas que no cambiaron **no se vuelven a
   crear**; las nuevas se añaden con `postGeneric('salesProducts', …)` y las eliminadas con
   `remove('salesProducts', id)`.
4. A partir de ahí, inventario y documento espejo como en cualquier factura ([02](02-api-y-fetch.md)).

> Ventaja de conservar el `id`: el usuario ve que "su cotización 104 se convirtió en la factura
> 104". Si se creara una factura nueva, tendría dos documentos y una cotización huérfana.

---

## Nota de Crédito y Nota de Débito — ⚠️ **no existen como entidad**

Esto hay que decirlo claro antes de empezar a programar.

**Lo que hay hoy en el backend:** los tipos de NCF **B04** (nota de crédito) y **B03** (nota de
débito), y una función que deriva el título del documento a partir del NCF
(`utils/receipt.js:98`). Además, `'Nota de Credito'` aparece como **método de pago** de un abono
(`AbonoCuentaDoc.vue:98`) — es decir, hoy una NC se "usa" descontando saldo de una cuenta por
cobrar.

**Lo que NO hay:** tabla de notas, campo que enlace una nota con la factura que corrige, ni lógica
que devuelva inventario o ajuste el saldo por emitir una.

### Las tres salidas, y cuál recomiendo

| Opción | Qué implica | Veredicto |
|---|---|---|
| **A. Backend nuevo** — tabla `CreditNotes` con `IdSale`, líneas, NCF y su efecto en saldo e inventario | Lo correcto. Requiere trabajo en el repo de la API | **Objetivo final** |
| **B. `sales` + convención** — una fila de `sales` con NCF B04, totales en negativo y el `id` de la factura original en `Comentary` | No toca el backend. Contamina los reportes: esa fila entra en las ventas del POS y descuadra el mes | **No** |
| **C. `accountdocs`** — la nota se registra como **abono** con `Method: 'Nota de Credito'` sobre el documento de la factura, más un `Reference` con el NCF B04 | Usa un camino que ya existe, que ya es transaccional y que ya recalcula el saldo. No mueve inventario ni ventas | **Sí, para v1** |

**Recomendación: C para el MVP, A como objetivo.**

La opción C describe con honestidad lo que hace una nota de crédito el 90 % de las veces en un
negocio pequeño: *"a este cliente le rebajo lo que me debe"*. Y encaja con el modelo sin mentirle
a los reportes.

Lo que la opción C **no** cubre, y hay que decirle al usuario en pantalla:

- No devuelve mercancía al inventario → si la NC es por una devolución, hay que ajustar el
  inventario a mano (la app puede ofrecer el ajuste como paso opcional).
- No sirve si la factura ya está pagada por completo (no hay saldo del que descontar). Ahí hace
  falta la opción A.
- La **nota de débito** (B03) es el caso simétrico —cobrarle *más* al cliente— y por la vía C se
  registra como un **documento nuevo** de `accountdocs` (`Kind: 'Cobrar'`) con NCF B03 y una línea
  libre que explica el cargo. Eso sí funciona bien.

**Petición al backend** (anotada en [11](11-plan-de-implementacion.md)):

```
POST /ventex/api/sales/{id}/credit-note
  body: { Items[], NCF, Motivo, DevolverInventario: boolean }
  efecto: crea la nota, ajusta el saldo del documento espejo,
          devuelve inventario si procede, y queda enlazada a la factura
```

---

## Orden de compra — ✅ existe, y su flujo es peculiar

`shoppings` + `shoppingProducts` ([03](03-modelo-de-datos.md)).

**La orden no toca el inventario al crearse.** Nace con `isConfirmed: false`, y cada
`shoppingProducts` guarda en el campo **`Spec`** un `JSON.stringify` con el producto, la existencia,
la garantía y los totales completos:

```js
Spec: JSON.stringify({ product, warehouse, garantia, totals })
```

**Solo al Confirmar** (`pages/produccion/compras.vue:752`) se leen esas especificaciones y se crean
de verdad `products` + `warehouse`. Es un diseño deliberado: se puede registrar lo que viene en
camino sin ensuciar el inventario, y si la compra se cancela no queda nada que limpiar.

### En Invoicer

1. **Nueva orden** — suplidor (`providers`), líneas con nombre/cantidad/costo, total.
2. Se guarda con `isConfirmed: false`. Aparece como **"En camino"**.
3. **Confirmar recepción** → crea/actualiza inventario a partir de `Spec` y marca
   `isConfirmed: true`.
4. Si la compra queda a deber: `post('accountdocs', { IdShopping }, 'from-shopping')` genera la
   **cuenta por pagar**. Mismo motor que los cobros, con `Kind: 'Pagar'`.

**Simplificación deliberada para v1:** cuando la línea corresponde a un producto que **ya existe**,
`Spec` lleva el `idWarehouse` existente y confirmar **suma** a `Amount` en vez de crear una fila
nueva. El POS crea siempre producto nuevo porque su catálogo maneja artículos serializados; aquí el
caso normal es reponer lo mismo de siempre.

---

## Resumen de qué toca qué

| Documento | Inventario | Dinero | NCF | Entidad |
|---|---|---|---|---|
| **Factura** | Descuenta | Sí | B01/B02 | `sales` + `salesProducts` |
| **Cotización** | No | No | No | `sales` con `Status: 'Cotizacion'` |
| **Nota de crédito** | *Manual (v1)* | Baja el saldo | B04 | Abono en `accountdocs` ⚠️ |
| **Nota de débito** | No | Sube la deuda | B03 | Documento de `accountdocs` |
| **Orden de compra** | Al confirmar | Al pagar | No | `shoppings` + `shoppingProducts` |
