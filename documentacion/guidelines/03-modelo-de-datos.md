# 03 — Modelo de datos

Formas **reales** de los payloads, copiadas del código del POS que ya escribe contra esta API. No
son un diseño: es lo que el backend acepta hoy. Cada bloque cita el archivo del que salió.

## Reglas transversales (léelas antes que nada)

1. **Los nombres de campo van en PascalCase**, salvo excepciones históricas que están en
   camelCase: `idWarehouse`, `idProduct`, `idMarket` (en `warehouse`), `taxType`, `trade`,
   `financing`, `sold`, `unique`, `retail`, `infinityAmount`, `isTrade`, `isRetail`, `isExtra`,
   `isPaid`. **No las "arregles"**: son las columnas.

2. **Los números llegan como string.** `taxValue`, `Amount`, `Price`, `Total`… La API los devuelve
   a veces como `"0.18"`, `"25.00"` (los `DECIMAL` de MySQL), y algunos booleanos como `0/1`.
   Normaliza **en el DTO** con los serializadores `Lenient*` de `core/network`, nunca en la
   pantalla.

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

### `users` (respuesta del login = contenido del JWT)

```ts
{ id, Username, Password, Email, IdPerson, IdRol, Active, Torning, IdMarket, Image, Master,
  IndRapidLogin, createdAt, updatedAt }
```

- ⚠️ **`Password` viaja en claro** en la respuesta del login y dentro del JWT (el backend firma
  `user.dataValues` entero). `UserDto` no lo declara, así que nunca llega a la base local.
- `Torning` es `STRING` en la tabla. `IdMarket` es la **tienda activa** y es la que manda: la
  tienda se lee de aquí, no de `persons.IdMarket`.
- Respuesta del login con varias tiendas: `{ requiresMarket: true, markets: [{ id, Name,
  Address, Image, TimeZone }], user: { id, Username } }` → [01](01-arquitectura.md).

### `persons`

```ts
{ id, FirstName, LastName, IdMarket, /* … */ }
```

### `marketbyuser/mine` y `marketbyuser/switch` — multitienda

```ts
// mine (controllers/marketByUser.js)
{ markets: [{ id, Name, Address, Image, TimeZone }], IdMarket, IndRapidLogin }
// switch
{ user /* como el del login, con Password: se descarta */, token }
```

- `markets` usa `MARKET_ATTRS` (`id, Name, Address, Image, TimeZone`), ordenadas por nombre: el
  mismo `MarketOptionDto` de la primera fase del login.
- `IdMarket` se relee de la base, no del token. Puede llegar como string (`LenientIntSerializer`).
- Las dos rutas responden **sin** el sobre `{ data }` (son POST sin `isGet`) → [02](02-api-y-fetch.md).

### `markets` — la tienda

```ts
{ id, Name, Address, Phone, RNC, Image, Bank, taxValue, taxLabel?, taxType?, TimeZone? }
```

- `taxValue` es **decimal** (`0.18` = 18 %) y **llega como string**.
- `taxLabel` por defecto `'ITBIS'` (`stores/data/accountDocs.js:22`).
- `TimeZone` por defecto `'America/Santo_Domingo'`.
- `Address` es **NOT NULL**; `Mail`, `Phone`, `RNC`, `Image` (URL, `STRING`) admiten nulo.
  `taxLabel` es ENUM `ITBIS | IVA`; `taxType`, ENUM `included | with_tax | no_tax`.
- **La factura** (`services/ventas.js → facturaData` y antes `shareFactura`) pinta de la tienda `Image`, `Name`,
  `Address`, `RNC`, `Phone` y `Mail`. Es exactamente lo que se edita desde el teléfono (más el
  impuesto) → `feature/store`. `Settings.LogoInBill` **no** afecta a ese PDF: es de la térmica.

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

#### Cómo se listan (verificado en `repositories/generic.js`)

`get/sales` pasa `params` **tal cual** al `where` de Sequelize y ordena **siempre** por
`id DESC`:

```json
{ "params": { "IdMarket": 12, "Gasto": null, "Status": ["Complete", "Pagos Pendientes"] },
  "isGet": true }
```

- `"Gasto": null` → `Gasto IS NULL`. La clave **tiene que ir** con `null` explícito.
- Un arreglo → `IN (…)`.
- ⚠️ **La API genérica no filtra por la tienda del token.** Si falta `IdMarket` en `params`,
  devuelve ventas de todas las tiendas.
- En la lista basta con `id, Secuency, Client, Status, Total, Date, NCF, createdAt`
  (`SaleDto`). `Total` llega como string. Sin cliente → "Consumidor final".

#### Dónde pagar — `PaymentAccounts` y `PaymentNote` (API `F4`, 2026-09-27)

Columnas nuevas de `Sales` (`sql/F4_instrucciones_pago.sql`, **manual**, antes de desplegar la API):

| Campo | Tipo | Qué es |
|---|---|---|
| `PaymentAccounts` | `VARCHAR(255)` | Ids de `Cuentas` separados por coma (`"3,7"`), en el orden elegido |
| `PaymentNote` | `TEXT` | Instrucciones libres ("Envía el comprobante al…") |

- Invoicer los manda **solo** en cotizaciones y facturas que quedan debiendo
  (`InvoiceDraft.asksForPayment`); una pagada no los lleva.
- El PDF (`services/ventas.js → shareFactura`) pinta "Dónde pagar" salvo en `Complete` y
  `Cancelada`, y **solo** las cuentas activas, con número, que no son `Caja` y que son de la misma
  tienda que la venta. Se guardan ids, no una copia: si cambia el número, las facturas nuevas salen
  con el nuevo (en la app, también las ya emitidas: se generan al abrirlas).

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

`IndShowOnCatalog` (`TINYINT`, API `F3`, por defecto `0`): el producto sale en el catálogo público.
Invoicer lo manda en el alta (interruptor "Mostrar en el catálogo") y en la edición **solo si lo
conoce**: una ficha guardada antes de existir el campo lo trae vacío y no se toca. Los marcados se
leen con `generic/get/products` `{ IdMarket, IndShowOnCatalog: true }` y se cambian con
`PUT generic/products/{id}`.

#### Catálogo público — `POST catalog/token` + `GET catalog/data` (sin sesión)

`catalog/token { storeName }` → `{ token, market }`; el slug es `Name` en minúsculas y sin espacios,
y solo admite letras (con tilde, `ü`, `ñ`), dígitos, `-` y `_` (`CatalogLink`). `catalog/data`
(cabecera `Authorization: <token>`) devuelve **un producto por tarjeta** (2026-09-27; antes, una fila
por `warehouse`):

```ts
{ products: [{ id, Name, Description, Image, IdCategory, …,
               Price1,          // el más bajo de sus variantes con precio
               Amount,          // existencia sumada (sin las infinitas)
               infinityAmount,  // alguna variante es infinita
               brand: string | null, colors: string[],   // nombres, ya resueltos
               variants: [{ warehouseId, Price1, Amount, infinityAmount, brand, color }] }],
  categories, families, groups }
```

Marca y color salen de `warehouse` (`IdBrand` → `Brand`, `IdColor` → `Colors`, o el texto `Color`)
y, si no hay, de `products.Marca`/`Color`. Las unidades únicas vendidas no salen. `variants` es la
base del carrito futuro → [14](14-carrito-y-pagos-en-linea.md).

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

### Imagen, categoría, marca y color (verificado 2026-09-27, `ProductQuickCreate.vue`)

| Dato | Tabla y columna | Catálogo |
|---|---|---|
| Imagen | `products.Image` (URL de `image/upload`, o una pegada de Google en el POS) | — |
| Categoría | `products.IdCategory` (**0** = sin categoría, no `null`) | `categories` |
| Marca | `warehouse.IdBrand` | `brands` |
| Color | `warehouse.IdColor` **y** `warehouse.Color` (el nombre, texto heredado que el POS sigue leyendo) | `colors` |

Marca y color van en `warehouse` porque en un producto único cada unidad tiene los suyos. Los tres
catálogos tienen la forma `{ id, Name, Active, IdMarket }`. Batería y Estado (`states`) existen en
el POS pero son de celulares: no se traen.

### Inventario agrupado — `productinventory/allgrouped` (lo que lista Productos)

`repositories/productInventoryView.js → getWarehouseAllGrouped`: las filas de `warehouse` **agrupadas
por nombre del producto**: `nombreProducto`, `cantidadAgrupada` (SUM `Amount`, puede ser negativa),
`minPrice`/`maxPrice` (MIN/MAX `Price1`), `unique` (MAX), `idProduct` (**el menor** de los que
comparten nombre), `Marca`, `estado` (`Agotado` si la suma es 0). **No trae id de `warehouse`**: la
ficha lo busca con `getGeneric('warehouse', { idProduct })`.

- Un producto **general** suele tener un `warehouse`; uno **único** (IMEI/serie), uno por unidad.
  Con más de uno, cada fila tiene su precio y existencia: la app los enseña pero **no los edita**.
- `products.taxType` (`included` · `with_tax` · `no_tax`) es **por producto**: el impuesto
  informativo de la ficha lo usa; el alta rápida usa el de la tienda.
- Alta rápida (`ProductQuickCreate.vue`): `postGeneric('products')` → `postGeneric('warehouse')` (con
  `idMarket` **en camelCase**: es el atributo del modelo) → `postGeneric('reportInventory')`. La
  app guarda qué pasos llegaron (`ProductCreateProgress`) para reintentar sin duplicar.

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

En Invoicer el alta (`ClientEditorScreen`, 2026-09-18) pide **Nombre** (obligatorio), Apellido,
Teléfono, "Tiene WhatsApp", tipo y número de documento y, plegados, Correo, Dirección, Descuento
y "Paga ITBIS". Los datos financieros del POS (`financialData`, `debt`…) no entran: son de
financiamientos.

- `Whatsapp` es un **booleano** ("el teléfono es de WhatsApp"), no un número.
- `PayItbis` es `NOT NULL`: se manda siempre (por defecto `true`). `Discount` es entero (%).
- El `PUT` genérico no devuelve la fila: la app la arma con lo enviado.

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
{ id, Name, Type: 'Ahorros' | 'Cheque' | 'Corriente' | 'Nomina' | 'Empresarial' | 'Caja',
  Description, BankName, AccountNumber, HolderName, HolderId, Active, Balance, IdMarket }
```

`HolderName` y `HolderId` (API `F4`, nullable): titular y su cédula/RNC, lo que pide una
transferencia desde otro banco. Salen en "Dónde pagar" del PDF. En una `Caja` no se mandan.

> **`Type` confirmado** en el modelo (`domain/models/cuentas.js`) y en `sql/F1_esquema_cuentas.sql`
> (2026-09-18): el ENUM de arriba. `CUENTAS_API.md` (`Efectivo | Banco | Tarjeta`) está desfasado.
> ⚠️ `Nomina` va **sin tilde**: el POS ofrece `'Nómina'` en `CreateCuenta.vue` y el ENUM lo rechaza.
> `Balance` solo se manda al crear (`0`); después lo mueven los movimientos. `BankName` es texto
> libre con los 14 bancos de `Models/Bancos.js`.

Movimientos (`cuentasMovimientos`): `{ id, IdCuenta, Type: 'Ingreso' | 'Egreso', Amount,
BalanceBefore, BalanceAfter, Description, Reference, ReferenceType: 'Venta' | 'Compra' | 'Ajuste' |
'Transferencia' | 'Gasto' | 'CuentaDoc' | 'Cuota' | 'Taller', ReferenceId, IdFinanceCategory,
Username, createdAt }`. No hay transferencia entre cuentas: "Transferencia" es solo una etiqueta.

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

## `invoiceconfig` — diseño de la factura (sql/F5, 2026-10-06)

Un registro por tienda (`IdMarket`, UNIQUE KEY en la base, no en el modelo: con `sync({alter})`
se duplicaría el índice en cada arranque, igual que `printerconfig`). Todo nullable con valor por
defecto; la app trata `null` como el valor por defecto.

| Campo | Tipo | Por defecto | Qué |
|---|---|---|---|
| `Title` | `VARCHAR(40)` | `Factura` | Título grande. La cotización se titula siempre "Cotización" |
| `AccentColor` | `VARCHAR(9)` | `null` (= `#16426F`) | `#RRGGBB`; otra cosa se guarda como `null` |
| `ShowLogo` · `ShowStoreInfo` | `TINYINT(1)` | `1` | Logo · RNC, dirección, teléfono y correo de la tienda |
| `ShowClient` · `ShowClientContact` | `TINYINT(1)` | `1` | Nombre · cédula/RNC, dirección, teléfono, correo (de `Clients`) |
| `ShowDueDate` · `ShowNcf` · `ShowTax` | `TINYINT(1)` | `1` | |
| `ShowBalance` · `ShowPayments` | `TINYINT(1)` | `1` | Pagado y saldo · historial de abonos |
| `ShowPaymentAccounts` · `ShowPaymentNote` | `TINYINT(1)` | `1` | "Dónde pagar" y sus instrucciones (solo si se debe o es cotización) |
| `DefaultPaymentNote` | `TEXT` | `null` | Instrucciones cuando la venta no trae `PaymentNote` |
| `ShowTerms` · `Terms` | `TINYINT(1)` · `TEXT` | `0` · texto | Condiciones antes de la firma |
| `ShowSignature` · `SignatureName` · `Signature` | `TINYINT(1)` · `VARCHAR(120)` · `LONGTEXT` | `0` · `null` · `null` | Firma: SVG dibujado en la app (`viewBox 0 0 600 200`) |
| `FooterMessage` | `TEXT` | `Gracias por su compra.` | |
| `TemplateHtml` | `LONGTEXT` | `null` | Plantilla HTML entera como texto; `null` = la de la app |

`GET ventas/factura/{id}/data` devuelve `client` con `Sales.Client`/`Sales.RNC` (lo que se
facturó) completado con la ficha de `Clients` **solo si es de la misma tienda**, y `receivable`
con el documento de `AccountDocuments` de la venta (sin crearlo: no llama a `from-sale`) y sus
abonos `Aplicado`.
