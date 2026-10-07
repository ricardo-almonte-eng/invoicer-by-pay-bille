# 02 — API y capa de datos

Todo lo de aquí está verificado contra `PayBille_POS/stores/data/fetchData.js` y sus llamadas
reales. Es **la misma API**: si algo se comporta distinto, el que está mal es este documento.

## Las dos URLs

| Base | Qué sirve |
|---|---|
| `BASE_URL` (`/ventex/api`) | Endpoints de negocio: `users/login`, `sales`, `accountdocs/*`, `nfc/*`, `cuentas/*`, `invoiceSecuency/*`, `report/*` |
| `BASE_URL_GENERIC` (`/ventex/api/generic`) | CRUD por modelo: `get/{modelo}`, `like/{modelo}`, `count/{modelo}`, `POST /{modelo}`, `PUT /{modelo}/{id}`, `DELETE /{modelo}/{id}` |

## Autenticación

El interceptor del POS hace exactamente esto:

```js
config.headers.Authorization = token   // ← el token CRUDO, sin "Bearer "
```

**No lleva prefijo `Bearer`.** (La ficha `PayBille_POS/.github/copilot-instructions.md` dice
"Bearer token"; el código dice lo contrario y el código es el que corre.) Si un día la API deja de
aceptarlo, es un cambio del backend, no de este documento.

## El cliente

`core/network/PayBilleApi.kt` es el único sitio que habla con el servidor. Réplica de
`useFetchStore` con los mismos nombres de método, para que cualquier código del POS se traduzca
leyéndolo. El cliente Ktor (`HttpClientFactory.kt`) añade el token en cada petición:

```kotlin
createClientPlugin("PayBilleAuth") {
    onRequest { request, _ ->
        val token = tokenProvider.currentToken()          // SessionTokenStore, cacheado
        if (!token.isNullOrBlank() && !request.headers.contains(HttpHeaders.Authorization)) {
            request.headers.append(HttpHeaders.Authorization, token)   // sin "Bearer"
        }
    }
}
```

Tiempos: **2 min por petición** (igual que el POS) pero **15 s para conectar**: sin red el usuario
debe seguir con lo local, no mirar un indicador dos minutos.

### Diferencia deliberada con el POS

`useFetchStore` **muestra el error él mismo** (abre un `MessageBox` global desde el interceptor) y
además `post()` / `postGeneric()` **capturan la excepción y devuelven un string**, así que allí hay
que comprobar `req?.status` antes de tocar `req.data`.

Aquí **no**. Todo método devuelve el `data` del sobre o lanza **`ApiException`**, con el mensaje
ya en español y un `kind`:

| `kind` | Cuándo | Qué hace la pantalla |
|---|---|---|
| `Network` | Sin red, DNS, servidor caído | Aviso "sin conexión" y sigue con lo local |
| `Timeout` | Se agotó el tiempo | Igual que `Network` (`isConnectivity`) |
| `Server` | 4xx/5xx; mensaje sacado de `data.error` / `data.message` | Aviso de error |
| `Unexpected` | La respuesta no tiene la forma esperada | Aviso de error |

> Si copias código del POS, este es el punto donde más te vas a equivocar: allí
> `const req = await post(...)` puede ser un string. Aquí siempre es el `data`, o una excepción.

### El sobre `{ data, meta }`

⚠️ **Corrección (2026-09-27): no toda.** `server.js` solo instala `formatResponseMiddleware`
(`PayBille_API/src/infrastructure/middlewares/formatters`) en los **GET** y en los **POST con
`isGet: true`**. Ahí `res.json(x)` sale como `{ data: x, meta: null }` y `{ rows, count }` como
`{ data: rows, meta: {…} }`. El resto —`PUT` y `POST` genéricos, `marketbyuser/mine` y `switch`,
`image/upload`— responde **el objeto tal cual**, sin `data`.

`PayBilleApi` devuelve `root["data"] ?: root`, así que cada `RemoteDataSource` recibe lo mismo en
los dos casos y lo decodifica a su DTO. Las pruebas usan la forma REAL de cada ruta (con o sin
sobre); no copies un fixture de una ruta a otra sin mirarlo.

### Números como string

Los `DECIMAL` de MySQL llegan como string (`"0.18"`, `"25.00"`) y algunos booleanos como `0/1`.
Se normalizan **en el DTO** con `LenientDoubleSerializer`, `LenientIntSerializer`,
`LenientStringSerializer` y `LenientBooleanSerializer` (`core/network/LenientSerializers.kt`).
Un valor ilegible se lee como `null`, nunca como excepción.

## Métodos

| Método | Petición real | Devuelve |
|---|---|---|
| `get(model, body?, route?, meta?, page=1, pageSize=10)` | `POST {BASE}/{model}/{route}?page&pageSize` con `{...body, isGet: true}`; sin body, `GET` | `data.data`, o `data` completo si `meta` |
| `getGeneric(model, body, like=false, meta=false, page, pageSize)` | `POST {GENERIC}/{get\|like}/{model}?page&pageSize` con `{...body, isGet: true}` | `data.data`, o `data` si `meta` |
| `getCount(model, body)` | `POST {GENERIC}/count/{model}` | `data` |
| `getById(model, id)` | `GET {GENERIC}/{model}/{id}` | `data.data` |
| `post(model, body, route?)` | `POST {BASE}/{model}/{route}` | `data` |
| `postGeneric(model, body)` | `POST {GENERIC}/{model}` | `data` (el registro creado, con su `id`) |
| `put(model, body, id)` | `PUT {GENERIC}/{model}/{id}` | `data` |
| `remove(model, id)` | `DELETE {GENERIC}/{model}/{id}` | `data` |
| `postImage(formData)` | `POST {BASE}/image/upload` | `data` |

> El borrado se llama **`remove`**, no `delete`. Así se llama en el POS y `delete` es palabra
> reservada.

### Filtros

Los `get` / `getGeneric` filtran por la clave **`params`** del body:

```ts
await getGeneric('products', { params: { IdMarket, isSolding: true } });
await getGeneric('sales',    { params: { Status: 'Pagos Pendientes' } });
```

`get/{model}` pasa `params` **tal cual** al `where` de Sequelize (`repositories/generic.js`):
`null` → `IS NULL`, un arreglo → `IN (…)`. **No filtra por la tienda del token**: `IdMarket` va
siempre en `params`. El orden es siempre `id DESC`.

⚠️ **`like/{model}` NO busca en `params`** (corregido 2026-09-16; esta guía decía lo contrario).
Su controlador lee `likeFields` y usa `params` como filtro exacto. Para buscar texto se usa
`get/{model}` con la búsqueda OR de abajo, que además pagina bien.

#### Operadores de comparación

La API genérica entiende sufijos `__gte` / `__lte` sobre cualquier columna
(`pages/cuentas/cotizacion.vue:392`):

```ts
params: {
  Status: 'Cotizacion',
  createdAt__gte: '2026-01-01',
  createdAt__lte: '2026-01-31',
  Total__gte: 1000,
  Total__lte: 5000,
}
```

Filtra por **`createdAt`**, nunca por `Date` — ese campo es un string de presentación
([03](03-modelo-de-datos.md)).

#### Búsqueda OR sobre varios campos

Fuera de `params`, al mismo nivel del body:

```ts
{
  params: { Status: 'Cotizacion' },
  likeOrParams: ['Client', 'id'],   // busca en cualquiera de estos campos
  likeOrValue: texto,
}
```

Es lo que usa el buscador de cotizaciones del POS y lo que debe usar cualquier `<SearchBar>` de
esta app: un solo campo de texto que busca por nombre **o** por número de documento.

### Paginación

Con `meta = true` la respuesta es el sobre completo:

```ts
{ data: T[], meta: { currentPage, pageSize, totalCount, totalPages,
                     hasNextPage, hasPreviousPage, nextPageUrl } }
```

`hasNextPage` es lo que decide si la lista pide la página siguiente al llegar al final. En offline
first las páginas se guardan en Room y la lista lee de Room: la red solo rellena.

## Endpoints que usa esta app

| Recurso | Llamada |
|---|---|
| Login | `get('users', { key, username, password, IdMarket? }, 'login')` → `{ user, token }` · `{ requiresMarket, markets }` · `"Incorrect username or password"` → [01](01-arquitectura.md#arranque-y-sesión-offline-first) |
| Subir una imagen | `POST image/upload`, **multipart** con el campo `image` (multer: 5 MB, jpeg/png/gif/webp/avif, la extensión sale del nombre del archivo) → `{ message, url }` sin sobre. **Se sube antes del registro que la usa** y la URL se guarda en `products.Image` / `markets.Image` (`STRING`). La ruta pone `auth` DESPUÉS de multer (`routes/images.js`) |
| Categorías, marcas, colores | `getGeneric('categories' \| 'brands' \| 'colors', { params: { Active: true } }, …, 1, 500)` (el servidor añade `IdMarket`) · alta: `postGeneric(model, { Name, Active: true, IdMarket })` (`commonData.js`, `CreateCatalogItem.vue`) |
| Configuración de la tienda | `getById('markets', id)` · `put('markets', { Name, Address, Phone, Mail, RNC, Image, taxValue, taxLabel, taxType }, id)`: el genérico hace `instance.update(data)`, así que **lo que no viaja no se toca** |
| Tiendas del usuario | `post('marketbyuser', {}, 'mine')` → `{ markets, IdMarket, IndRapidLogin }` **sin sobre**; solo las que tiene **activas** (Master: todas; usuario sin filas en `MarketByUser`: la suya) |
| Cambiar de tienda | `post('marketbyuser', { IdMarket }, 'switch')` → `{ user, token }` sin sobre, con **token nuevo** (el `IdMarket` va dentro). 403 `"No tienes acceso a esta tienda"`. **Sin `RapidLogin`**; sí escribe `Users.IdMarket` y `Persons.IdMarket` (`repositories/marketByUser.js → setActiveMarket`) |
| Usuario / Persona / Rol / Tienda | `getById('users' \| 'persons' \| 'roles' \| 'markets', id)` |
| Ajustes de tienda | `getGeneric('Settings', { params: { IdMarket } })` |
| Productos para vender | `get('productinventory', { IdMarket, barcode \| name }, 'sales', page, pageSize)` → filas de `warehouse` con `product: { id, name, image }` (nombre en **minúscula**). Primero por código; si no hay nada, por nombre (**prefijo**: `name LIKE 'texto%'`). Excluye vendidos, no vendibles y piezas de taller (`productInventoryView.js → getToSale`) |
| Clientes (buscar) | `getGeneric('clients', { params: { IdMarket }, likeOrParams: ['FirstName','LastName','Phone','Identify'], likeOrValue })` |
| Productos | `getGeneric('products', …)` · `postGeneric('products', …)` |
| Existencias | `getGeneric('warehouse', …)` · `postGeneric('warehouse', …)` · `put('warehouse', …, id)` |
| Movimientos de inventario | `postGeneric('reportInventory', …)` |
| Clientes | `getGeneric('clients', …)` · `postGeneric('clients', …)` |
| Suplidores | `getGeneric('providers', …)` — **es `providers`, no `suppliers`** (`crearCompra.vue:541`) |
| Facturas | `postGeneric('sales', …)` · `put('sales', …, id)` · `getGeneric('sales', …)` |
| Líneas de factura | `postGeneric('salesProducts', …)` · `getGeneric('salesProducts', { params: { IdSale } })` |
| Secuencia de factura | `post('invoiceSecuency', {}, 'next/{IdMarket}')` → `{ Sequence }` |
| NCF · comprobar rango | `post('nfc', { IdMarket, tipoNCF }, 'verify')` → `{ newNFC: true }` o `{ newNFC: "No hay rangos…" }` |
| NCF · consumir | `post('nfc', { IdMarket, tipoNCF }, 'getNextNFC')` → `{ newNFC: "B02…" }`. ⚠️ Los errores llegan **dentro de `newNFC`** o como **texto plano con HTTP 200** (`res.send`, fuera del sobre): valida que sea el tipo seguido de dígitos. Su aviso de "rango agotado" nunca salta (compara con `rango.Final`, que no existe) |
| Cuentas de dinero (para cobrar) | `getGeneric('cuentas', { params: { IdMarket, Active: true } })`. (El POS usa `post('cuentas', …, 'get')` y lee `res.data.rows`, que tras el sobre no existe) |
| **Datos de la factura** (2026-10-06) | `GET ventas/factura/{id}/data` (con token; comprueba que la tienda sea del usuario) → `{ sale, items, client, market, receivable, paymentAccounts, paymentNote, config }`, importes como **número**. La app genera la factura con eso → [06](06-componentes-ui.md). ⚠️ Está declarada **antes** que `ventas/:market/:person/:torning` (el cierre), que también casaría con ella |
| Diseño de factura | `GET invoiceConfig/{IdMarket}` (la crea con los valores por defecto) · `POST invoiceConfig/{IdMarket}` (upsert; responde sin sobre). Al guardar viajan **todos** los campos, también los `true` y los `null` (si no, un interruptor que vuelve a encenderse o una firma borrada no llegarían) |
| PDF del servidor (ya no se usa) | `GET ventas/factura/{id}` → el archivo PDF (Puppeteer). Lo sigue usando el POS. Se guarda la primera vez y no se regenera, y no pide token → petición 8 ([11](11-plan-de-implementacion.md)) |
| Detalle de venta | `getGeneric('sales', { params: { id, IdMarket } })` + `getGeneric('salesProducts', { params: { IdSale } })` |
| Cuentas por cobrar | `post('accountdocs', { Kind: 'Cobrar', OnlyWithBalance: true }, 'get')` paginado (filtra por la tienda del token, ordena por vencimiento) |
| Documento de una venta | `post('accountdocs', { IdSale }, 'from-sale')` → `{ Document, Items, Installments, Payments }`. Idempotente: devuelve el que ya existe. **Solo para ventas con saldo** |
| Abono | `post('accountdocs', { Amount, LateFeeAmount: 0, Method, PaymentDate, IdCuenta, Reference }, '{id}/payments')`. `Method: 'Deposito'` para transferencias → [08](08-reglas-de-negocio.md) §3 |
| Movimiento de cuenta | `post('cuentas', { … }, 'movimientos')` |
| Órdenes de compra | `postGeneric('shoppings', …)` · `postGeneric('shoppingProducts', …)` |
| **Resumen** (dashboard) | `get('dashboard', { startDate, endDate, params: { IdMarket } }, 'summary')` → `{ today, range, series, topProducts, activeTornings }`. El controlador saca la tienda de `params` |
| Inventario agrupado | `getPage('productinventory', { is: true, IdMarket, params: [{ key: 'IdMarket', value }] }, 'allgrouped', page, pageSize)` → `{ idProduct, nombreProducto, cantidadAgrupada, minPrice, maxPrice, unique, Marca }` agrupado **por nombre**. `params` como arreglo: el servidor lo reparte por prefijo `Product.` / `Warehouse.` |
| Resumen del inventario | `get('productinventory', { IdMarket }, 'info')` — lee `IdMarket` del **cuerpo**, no de `params` |
| Ficha de producto | `getById('products', id)` + `getGeneric('warehouse', { idProduct, IdMarket })` + `getGeneric('reportInventory', { IdProduct, IdMarket })` |
| Saldos por cliente | `getPage('accountdocs', { Kind: 'Cobrar', OnlyWithBalance: true }, 'byparty', page, pageSize)` → `{ PartyKey (= IdClient, 0 si nombre libre), PartyName, Docs, Total, Paid, Balance, OldestDueDate, DaysOverdue }` |
| Lo que debe un cliente | `getPage('accountdocs', { Kind: 'Cobrar', IdClient, OnlyWithBalance: true }, 'get', 1, 50)` |
| Facturas de un cliente | `getGeneric('sales', { IdMarket, IdClient, Gasto: null, Status: [...] })` |
| Cuentas (todas) | `getGeneric('cuentas', { IdMarket })` · `postGeneric('cuentas', …)` · `put('cuentas', …, id)` |
| **Movimientos de una cuenta** | `getPage('cuentas', { params: { createdAt__gte: 'YYYY-MM-DD 00:00:00', createdAt__lte: '… 23:59:59' } }, '{id}/movimientos', page, pageSize)`. ⚠️ El controlador solo lee `params` del cuerpo y la página de la **query**: el POS manda `dateFrom`/`page` en el cuerpo, se ignoran y **siempre enseña los 10 más recientes** |
| Ventas por fecha | `get('report', { startDate, endDate, params: [{ key: 'IdMarket', value }] }, 'totals/no')` + `getPage('report', { …, params: [IdMarket, { key: 'Gasto', value: null }] }, 'no', …)`. ⚠️ **`params` tiene que ser arreglo**: `repositories/sales.js` lo recorre con `for…of` y un objeto hace fallar al servidor. Sin datos responden `{ message: "No se encontraron datos" }` |
| Productos vendidos | `get('productinventory', { startDate, endDate, params: [IdMarket] }, 'report/totalProductCost')` + `getPage(…, 'report/salesProductsGrouped', …)` (sin paginar en el servidor; agrupa también por estatus y precio: la app suma por producto) |
| Histórico del inventario | `getPage('productinventory', { startDate, endDate, params: [IdMarket] }, 'report/history', 1, 200)` |

### Libro de cuentas (`accountdocs`) — el corazón de los pagos parciales

**No está en la API genérica.** Todo por `post()` / `get()` sobre `BASE_URL`.

| Llamada | Qué hace |
|---|---|
| `get('accountdocs/get', filtros, '', true, page, pageSize)` | Listado paginado |
| `get('accountdocs/summary/Cobrar' \| 'Pagar', {})` | Saldo al corte + cubetas de antigüedad |
| `get('accountdocs/byparty', { Kind, OnlyWithBalance: true, Search }, '', true, …)` | Saldo agrupado por cliente |
| `get('accountdocs/{id}', null)` | `{ Document, Items, Installments, Payments }` |
| `post('accountdocs', body)` | Crear documento **con sus líneas**, en una sola llamada |
| `post('accountdocs', body, '{id}/update')` | Editar — **solo mientras no tenga abonos** |
| `post('accountdocs', payload, '{id}/payments')` | **Registrar abono** |
| `post('accountdocs', {}, 'payments/{id}/void')` | Anular abono (destructivo: pide confirmación) |
| `post('accountdocs', {}, '{id}/void')` | Anular documento |
| `post('accountdocs', { Plan }, '{id}/installments')` | Generar plan de cuotas |
| `post('accountdocs', { IdSale, DueDate, Plan }, 'from-sale')` | **Documento espejo de una factura. Idempotente.** |
| `post('accountdocs', { IdShopping }, 'from-shopping')` | Documento espejo de una compra |

**El saldo lo calcula SIEMPRE el servidor.** Nunca envíes `Paid` ni `Balance`: el CRUD genérico los
rechaza, y `services/accountDocuments.js → applyPayment()` es el único punto de escritura, dentro
de transacción y con `lock`.

Fíjate en la asimetría, porque explica media arquitectura de esta app: **`accountdocs` sí acepta
cabecera + líneas en una sola llamada; `sales` no.**

## El patrón de "venta base" del POS NO se replica

En el POS (`pages/ventas.vue:1040`) la venta **nace vacía en el servidor** con
`Status: 'En Proceso'`, cada producto escaneado hace un `POST salesProducts`, y al cobrar se hace
`PUT sales/{id}`. Tiene sentido en un mostrador con red por cable.

**En móvil eso es un error.** El borrador vive **en el teléfono** (Room) y solo al pulsar
*Guardar* se ejecuta la secuencia:

```
1. post('invoiceSecuency', {}, `next/${IdMarket}`)             → Sequence
2. (si lleva NCF) post('nfc', …, 'verify') → post('nfc', …, 'getNextNFC')
3. postGeneric('sales', cabecera)                              → IdSale
4. for (línea) postGeneric('salesProducts', { IdSale, … })
5. for (línea) put('warehouse', { Amount: nuevo }, idWarehouse)  ← descuenta existencia
6. for (línea) postGeneric('reportInventory', { … })             ← rastro del movimiento
7. si queda saldo → post('accountdocs', { IdSale, DueDate, Plan }, 'from-sale')
8. si hubo cobro contra una cuenta → post('cuentas', { … }, 'movimientos')
```

### El riesgo, y qué hacemos con él

Esa secuencia **no es atómica**. Si el teléfono pierde señal en el paso 4, queda una factura sin
líneas. Mientras el backend no ofrezca algo mejor:

- Los pasos 1–4 van **en serie y con reintento**. Los pasos 5–8 son *best effort*, igual que en el
  POS, que los encadena con `.catch(() => {})`.
- Si falla el 3 o el 4, la factura **se queda como borrador local** y la pantalla ofrece
  *"Reintentar envío"*. Nunca se pierde lo tecleado.
- `from-sale` **es idempotente** (índice `UNIQUE` sobre `IdSale`): el paso 7 se reintenta sin
  duplicar la deuda.
- **Petición pendiente al backend:** un `POST /sales/complete` que reciba cabecera + líneas y
  resuelva secuencia, NCF, inventario y documento espejo en una transacción. Es la mejora que más
  simplifica esta app. Anotada en [11](11-plan-de-implementacion.md).

## Patrón estándar en una pantalla

```
Screen ──collect── ScreenModel ──── Repository ──┬── Room (Flow)   ← la pantalla SIEMPRE lee de aquí
                                                  └── RemoteDataSource → PayBilleApi   ← solo refresca
```

La pantalla observa un `Flow` de Room y pinta lo que haya. El `ScreenModel` pide un refresco;
si hay red, el repositorio escribe en Room y la pantalla se actualiza sola. Si no hay red, se
enseña el aviso de "sin conexión" y todo lo demás sigue funcionando.

Sin *loading* global tapando la pantalla: en móvil el indicador vive **dentro** de la lista o
**dentro** del botón que disparó la acción.
