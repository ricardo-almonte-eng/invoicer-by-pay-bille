# 03 — Modelo de datos

Formas **reales** de los payloads, copiadas del código del POS que ya escribe contra esta API. No
son un diseño: es lo que el backend acepta hoy. Cada bloque cita el archivo del que salió.

## Reglas transversales (léelas antes que nada)

1. **Los nombres de campo van en PascalCase**, salvo excepciones históricas que están en
   camelCase: `idWarehouse`, `idProduct`, `idMarket` (en `warehouse`), `taxType`, `trade`,
   `financing`, `sold`, `unique`, `retail`, `infinityAmount`, `isTrade`, `isRetail`, `isExtra`,
   `isPaid`. **No las "arregles"**: son las columnas.

2. **Los números llegan como string.** `taxValue`, `Amount`, `Price`, `Total`… La API los devuelve
   a veces como `"0.18"`, `"25.00"`. Envuelve **siempre** en `Number()` antes de operar. En
   TypeScript, tipa el borde como `string | number` y normaliza en `lib/api`, no en la pantalla.

3. **`Sales.Date` NO es una fecha ISO: es un string de presentación** con formato
   `DD/MM/YYYY hh:mm am|pm`, construido a mano en el cliente (`completeOrder.vue`, función
   `date()`). Para ordenar, filtrar o calcular usa **`createdAt`**, que sí es un timestamp real.
   Este es el error más caro que puedes cometer con esta API.

4. **Todo va sujeto a `IdMarket`.** Es el discriminador multi-tienda. Si a una consulta se le
   olvida, ve datos de otro negocio.

5. **`Torning`** (el turno de caja) se sigue enviando aunque esta app no tenga turnos: sale de
   `user.Torning` y lo usan los reportes del POS. Mándalo tal cual → [08](08-reglas-de-negocio.md).

---

## Sesión

### `users` (JWT decodificado)

```ts
{ id, IdPerson, IdRol, Torning, /* + lo que el backend meta en el token */ }
```

### `persons`

```ts
{ id, FirstName, LastName, IdMarket, /* … */ }
```

### `markets` — la tienda

```ts
{ id, Name, Address, Phone, RNC, Image, Bank, taxValue, taxLabel?, taxType?, TimeZone? }
```

- `taxValue` es **decimal** (`0.18` = 18 %) y **llega como string**.
- `taxLabel` por defecto `'ITBIS'` (`stores/data/accountDocs.js:22`).
- `TimeZone` por defecto `'America/Santo_Domingo'`.

### `roles`

> Dato del POS. **Invoicer no se ramifica por rol** → [08](08-reglas-de-negocio.md) §8.

```ts
{ id, Name }   // 'PreFacturador' | 'Facturador' | 'Tecnico' | 'Administrator' | 'Master'
```

### `Settings` — ajustes por tienda

```ts
{ id, IdMarket, AutoVisor, VirtualKeyboard, Tax, QR, Guarantee,
  LogoInBill, BillSize, LinkTreeBackground }
```

De todos ellos, aquí solo importan **`Tax`** (si se muestra impuesto) y **`LogoInBill`**.

---

## Facturación

### `sales` — cabecera de factura

Alta (`pages/ventas.vue:1040`, `CreateCotizacion.vue:558`):

```ts
{
  IdClient: number | null,
  Client: string,                 // nombre desnormalizado. 'Venta Base' = borrador del POS
  ClientDiscount?: number,
  IdMarket: number,
  Date: string,                   // 'DD/MM/YYYY hh:mm am' — presentación, NO ISO
  SubTotal: number,
  Tax: number,
  Total: number,                  // ⚠️ SIN moneda. Ver nota bajo el bloque
  Money: number,                  // efectivo recibido
  MoneyDeposit?: number,          // transferencia / depósito
  MoneyCredit?: number,           // tarjeta
  Change: number,                 // devuelta
  trade?: number,                 // intercambio  (fuera de alcance en v1)
  financing?: number,             // financiamiento formal (fuera de alcance en v1)
  IdFinancing?: number,
  IdCuenta: number | null,        // cuenta de dinero donde entró el cobro
  Status: string,                 // ver tabla abajo
  taxType: 'with_tax' | 'included' | 'no_tax',
  NCF: string | null,
  RNC: string | null,
  Secuency: string,               // de post('invoiceSecuency', {}, `next/${IdMarket}`)
  Gasto?: true,                   // SOLO para gastos. Ver nota
  IdPerson: number,
  Username: string,               // `${FirstName} ${LastName}`
  Torning: number,
}
```

⚠️ **No hay columna de moneda en ninguna parte** — ni en `sales`, ni en `markets`, ni en
`Settings`. Todos los importes son números sin unidad, en la misma base de datos que el POS. Por
eso Invoicer **guarda siempre en la moneda base** aunque emita el PDF en otra
→ [08](08-reglas-de-negocio.md) §11, y por eso existe la petición 6 al backend
→ [11](11-plan-de-implementacion.md).

Tampoco hay columna para la **tasa de impuesto**: `sales.Tax` y `salesProducts.Tax` son importes.
La tasa se reconstruye con `Tax / SubTotal` cuando hace falta mostrarla.

#### Vocabulario de `Status` (verificado, sin inventar)

| Valor | Significado | ¿Lo usa Invoicer? |
|---|---|---|
| `'Complete'` | Factura pagada por completo | **Sí** |
| `'Pagos Pendientes'` | Facturada con saldo pendiente → genera documento en `accountdocs` | **Sí** |
| `'Cotizacion'` | Presupuesto. No mueve inventario ni dinero | **Sí** |
| `'Cancelada'` / `'Cancelado'` | Anulada (el POS usa las dos grafías) | **Sí** — escribe `'Cancelada'` y filtra por ambas |
| `'En Proceso'` | Borrador vivo en el servidor (venta base del POS) | No: el borrador vive en el teléfono |
| `'Suspendida por Usuario'` / `'Suspendida por sistema'` | Venta en espera del POS | No |
| `'Esperando Facturacion'` | Flujo PreFacturador → Facturador | No |

> **Guard obligatorio al listar facturas: `Gasto IS NULL`.** Un gasto es una fila de `sales` con
> `Gasto = true`, y la columna es *nullable*, así que filtrar por `Gasto = false` **no funciona**.
> Si se te olvida, los gastos aparecen mezclados entre las facturas.

### `salesProducts` — línea de factura

`pages/ventas.vue:1420` y `CreateCotizacion.vue:584`:

```ts
{
  IdSale: number,
  IdMarket: number,
  IdProduct: number,
  idWarehouse: number,            // ← camelCase. Es la existencia concreta que se vende
  Barcode: string,
  Name: string,
  Amount: number,                 // cantidad
  Price: number,                  // precio unitario
  Tax: number,                    // impuesto de la línea
  Discount: number,
  Total: number,
  isRetail?: boolean,             // venta al detalle (por peso/medida)
  retailTypeSize?: string,        // 'Unidades' | 'Libras' | …
  retailSize?: number,
  retailPrice?: number,           // precio por unidad de medida
  isTrade?: boolean,              // línea de intercambio: NO suma a los totales
  sold?: boolean,                 // se pone a true al cerrar la factura
  Torning: number,
}
```

---

## Inventario — son **dos** tablas

Esta es la parte que más sorprende: **el producto y su existencia están separados.**

- **`products`** = la ficha: nombre, descripción, categoría, imagen.
- **`warehouse`** = una existencia concreta con **su código de barras, su precio, su costo y su
  cantidad**. Un producto puede tener varias filas de `warehouse` (distinto color, grado, lote…).

**Lo que se vende y lo que se descuenta es la fila de `warehouse`**, no el producto.

### `products` (`ProductQuickCreate.vue:597`)

```ts
{
  Name: string,
  Description: string,
  IdCategory: number,
  IdFamily: number, IdSubFamily: number, IdGroup: number,   // 0 si no aplica
  IdGuarantee: number | null,
  Image: string,                  // base64 o URL
  isProduced: boolean, isSolding: boolean,
  IsPart: boolean, IsWorkshop: boolean, isCollection: boolean,
  IdMarket: number,
}
```

Para Invoicer: `IsPart`, `IsWorkshop`, `isCollection` van siempre en `false`; `isSolding: true`.

### `warehouse` (`ProductQuickCreate.vue:509`)

```ts
{
  idProduct: number, idMarket: number,      // ← camelCase los dos
  IdProvider: number | null, shopping: number | null,
  Barcode: string,
  Price1: number, Price2: 0, Price3: 0, Price4: 0,   // 4 niveles de precio
  Tax1: number,  Tax2: 0, Tax3: 0, Tax4: 0,
  utility1: number,                          // Price1 - Tax1 - Cost
  RangeMin: 1, RangeMax: 1, RangeTax: 0,
  MinRetailQty: 1, MaxRetailQty: 1,
  retail: boolean,
  Size: number, TypeSize: string,            // 1 / 'Unidades'
  Color: string, IdColor: number | null, IdBrand: number | null,
  Batery: string, Grade: string, IdState: number | null,
  UseStatus: string, Condition: string, Notes: string,
  unique: boolean,                           // artículo serializado: se vende una sola vez
  Amount: number,                            // ← LA EXISTENCIA
  MinAmountQty: number,                      // umbral de stock bajo
  infinityAmount: boolean,                   // servicios: no descuenta nunca
  Cost: number,
  ignoreInReport: boolean,
  IdGuarantee: number | null, GuaranteeDuration: number | null,
  sold?: boolean, IdSale?: number, NameClient?: string,   // se escriben al vender un `unique`
}
```

**Invoicer usa un subconjunto**: `Barcode`, `Price1`, `Cost`, `Amount`, `MinAmountQty`,
`infinityAmount`, `Size`/`TypeSize`, `unique`. El resto se manda con los valores por defecto de
arriba para no romper la fila.

### `reportInventory` — rastro de cada movimiento

```ts
{
  IdProduct, IdWarehouse?, IdSale?, IdMarket,
  Name, ProductName?, Barcode,
  User: string, Comentary: string,      // texto legible: "3 Vendidos por X a Y"
  Before?: number, After?: number,      // null en `unique` e `infinityAmount`
  Total?: number,
  IdUser, IdPerson,
}
```

No es opcional: es el historial que el usuario ve cuando no le cuadra el inventario.

---

## Clientes

`ClientQuickCreate.vue:103`:

```ts
{
  FirstName: string, LastName: string,
  Phone: string, Phone2: string, Whatsapp: boolean,
  Email: string, Photo: string,
  Address: string, Address2: string,
  Identify: string,                      // cédula/RNC SIN guiones — se limpia con .replace(/-/g, '')
  IdentifyType: 'Cedula' | 'Pasaporte' | 'Licencia de conducir',
  CreditLimit: number, Discount: number, PayItbis: boolean,
  IdVoucher: number, Comentary: string,
  IdMarket: number,
}
```

En Invoicer el alta pide solo **FirstName, Phone, Identify**; el resto va por defecto.

## Suplidores — `providers`

El modelo se llama **`providers`** (`crearCompra.vue:541`), aunque la guía del POS lo cite como
"suppliers". Usa `providers`.

---

## Libro de cuentas (pagos parciales)

### Documento — `accountdocs` (`stores/data/accountDocs.js:131`)

```ts
{
  id: number | null,
  Kind: 'Cobrar' | 'Pagar',
  PartyType: 'Cliente' | 'Proveedor' | 'Tercero',
  IdClient: number | null, IdProvider: number | null,
  PartyName: string, PartyPhone: string, PartyIdentify: string,
  Description: string, Notes: string,
  IdFinanceCategory: number | null,
  IdCuenta: number | null,
  NCF: string, RNC: string,
  IssueDate: string,        // 'YYYY-MM-DD'
  DueDate: string,
  taxType: 'with_tax' | 'included' | 'no_tax',
  Items: Array<{
    LineType: 'Libre' | 'Producto',
    Description: string,
    Quantity: number, UnitPrice: number, Discount: number, TaxRate: number,
    IdProduct?: number, idWarehouse?: number,
  }>,
  Plan: {
    Enabled: boolean,
    Count: number,
    Frequency: 'Semanal' | 'Quincenal' | 'Mensual',
    FirstDueDate: string,
    InterestRate: number,               // TASA (0.05 = 5 %)
    LateFeeMode: 'Ninguna' | string,
    LateFeeRate: number,
    GraceDays: number,
  },
}
```

La respuesta de `get('accountdocs/{id}')` es
`{ Document, Items, Installments, Payments }` — cuatro colecciones, no un objeto plano.

⚠️ **Escalas distintas y fáciles de confundir:** `AccountDocuments.InterestRate` es una **tasa**
(`0.05` = 5 %), igual que `markets.taxValue`. `Financing.InterestRate` es un **porcentaje**
(`40` = 40 %). Invoicer solo usa la primera.

### Abono — `POST accountdocs/{id}/payments` (`AbonoCuentaDoc.vue:131`)

```ts
{
  Amount: number,
  LateFeeAmount: number,                   // mora cobrada, 0 si no aplica
  Method: 'Efectivo' | 'Transferencia' | 'Tarjeta' | 'Nota de Credito' | …,
  PaymentDate: string,                     // ISO — aquí SÍ es ISO
  IdCuenta: number | null,
  IdAccountDocumentInstallment: number | null,   // si abona una cuota concreta
  Reference: string | null,
  Notes: string | null,
  // + auditoría: IdUser, Username, Torning
}
```

Con `Method: 'Efectivo'`, el POS solo ofrece cuentas de `Type === 'Caja'`. Replícalo.

---

## Dinero — cuentas y movimientos

### `cuentas`

```ts
{ id, Name, Type: 'Efectivo' | 'Banco' | 'Tarjeta' | 'Caja',
  Description, BankName, AccountNumber, Active, Balance, IdMarket }
```

> El campo `Type` tiene valores documentados como `Efectivo | Banco | Tarjeta`
> (`endpointGuide/CUENTAS_README.md`) pero el filtro de abonos compara contra `'Caja'`
> (`AbonoCuentaDoc.vue:104`). **Confírmalo contra datos reales antes de filtrar.**

### Movimiento — `POST cuentas/movimientos`

```ts
{
  IdCuenta: number,
  Type: 'Ingreso' | 'Egreso',
  Amount: number,
  Description: string,
  Reference: string,                    // la secuencia de la factura
  ReferenceType: 'Venta' | 'Compra' | 'CuentaDoc' | 'Gasto' | 'Ajuste' | 'Transferencia',
  ReferenceId: number,
  IdMarket, IdUser, Username,
}
```

El servidor mantiene `Balance`, `BalanceBefore` y `BalanceAfter`. **No los mandes.**

⚠️ **Anti-doble-conteo:** los movimientos generados por un abono los crea **el servidor** con
`ReferenceType: 'CuentaDoc'` y `ReferenceId` = id del documento. Si además los creas tú desde la
app, el dinero se cuenta dos veces en los reportes del POS. Regla: **al cobrar un abono no crees
movimiento de cuenta.** Solo al cerrar una factura pagada.

---

## Órdenes de compra

### `shoppings`

```ts
{ IdProvider, IdMarket, IdUser, Username, IdCuenta?,
  Date, SubTotal, Tax, Total, Cash?, Deposit?, Credit?, Paid?, isPaid?,
  isConfirmed: false,        // ← nace SIN confirmar
  Comentary? }
```

### `shoppingProducts` (`crearCompra.vue:589`)

```ts
{
  IdShopping, IdMarket,
  Barcode, Name, Amount, Price, Tax, Discount, Total,
  IsPart: boolean, IsUnique: boolean,
  Spec: string,   // JSON.stringify({ product, warehouse, garantia, totals })
}
```

**`Spec` es la clave del flujo de compras**: la orden guarda la especificación completa del
producto, y **solo al Confirmar** se crean de verdad `products` + `warehouse` con esos datos. Por
eso una compra no toca inventario hasta que se confirma. Ver [09](09-documentos.md).
