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

`lib/api/client.ts` es el único sitio donde se instancia axios. Réplica de `useFetchStore` con los
mismos nombres de método, para que cualquier código del POS se traduzca leyéndolo.

```ts
import axios from 'axios';
import * as SecureStore from 'expo-secure-store';

const api = axios.create({
  baseURL: process.env.EXPO_PUBLIC_BASE_URL,
  timeout: 120_000,            // 2 min, igual que el POS
});

api.interceptors.request.use(async (config) => {
  const token = await SecureStore.getItemAsync('token');
  if (token) config.headers.Authorization = token;   // sin "Bearer"
  return config;
});

api.interceptors.response.use(
  (r) => r,
  (error) => {
    if (error.code === 'ECONNABORTED') {
      return Promise.reject(new ApiError('Tiempo de espera agotado. Revisa tu conexión.'));
    }
    if (!error.response) {
      return Promise.reject(new ApiError('No se pudo conectar al servidor.'));
    }
    return Promise.reject(new ApiError(mensajeDe(error.response.data), error.response.status));
  },
);
```

### Diferencia deliberada con el POS

`useFetchStore` **muestra el error él mismo** (abre un `MessageBox` global desde el interceptor) y
además `post()` / `postGeneric()` **capturan la excepción y devuelven un string**, así que allí hay
que comprobar `req?.status` antes de tocar `req.data`.

Aquí **no**. El interceptor rechaza siempre con un `ApiError` tipado y quien llama decide. React
Query ya distingue `isError` de `data`, y un toast lanzado desde un interceptor no sabe si la
pantalla sigue montada.

> Si copias código del POS, este es el punto donde más te vas a equivocar: allí
> `const req = await post(...)` puede ser un string. Aquí siempre es la respuesta, o un throw.

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
await getGeneric('clients',  { params: { FirstName: texto } }, true);   // like
await getGeneric('sales',    { params: { Status: 'Pagos Pendientes' } });
```

`like = true` cambia la ruta a `/like/{model}` y hace búsqueda parcial: es lo que alimenta el
buscador de clientes y el de productos.

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

`hasNextPage` es lo que alimenta el `onEndReached` de las `FlatList`. Con React Query:
`useInfiniteQuery` + `getNextPageParam: (last) => last.meta.hasNextPage ? last.meta.currentPage + 1 : undefined`.

## Endpoints que usa esta app

| Recurso | Llamada |
|---|---|
| Login | `get('users', { key, username, password }, 'login')` → `{ token }` |
| Usuario / Persona / Rol / Tienda | `getById('users' \| 'persons' \| 'roles' \| 'markets', id)` |
| Ajustes de tienda | `getGeneric('Settings', { params: { IdMarket } })` |
| Productos | `getGeneric('products', …)` · `postGeneric('products', …)` |
| Existencias | `getGeneric('warehouse', …)` · `postGeneric('warehouse', …)` · `put('warehouse', …, id)` |
| Movimientos de inventario | `postGeneric('reportInventory', …)` |
| Clientes | `getGeneric('clients', …)` · `postGeneric('clients', …)` |
| Suplidores | `getGeneric('providers', …)` — **es `providers`, no `suppliers`** (`crearCompra.vue:541`) |
| Facturas | `postGeneric('sales', …)` · `put('sales', …, id)` · `getGeneric('sales', …)` |
| Líneas de factura | `postGeneric('salesProducts', …)` · `getGeneric('salesProducts', { params: { IdSale } })` |
| Secuencia de factura | `post('invoiceSecuency', {}, 'next/{IdMarket}')` → `{ Sequence }` |
| NCF · comprobar rango | `post('nfc', { IdMarket, tipoNCF }, 'verify')` → `{ newNFC }` |
| NCF · consumir | `post('nfc', { IdMarket, tipoNCF }, 'getNextNFC')` → `{ newNFC }` |
| Cuentas de dinero | `getGeneric('cuentas', { params: { IdMarket, Active: true } })` |
| Movimiento de cuenta | `post('cuentas', { … }, 'movimientos')` |
| Órdenes de compra | `postGeneric('shoppings', …)` · `postGeneric('shoppingProducts', …)` |

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

**En móvil eso es un error.** El borrador vive **en el teléfono** (Zustand + AsyncStorage) y solo al
pulsar *Guardar* se ejecuta la secuencia:

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

```ts
const { data, isLoading, refetch } = useQuery({
  queryKey: ['facturas', filtros],
  queryFn: () => salesApi.list(filtros, page),
});
```

Sin *loading* global tapando la pantalla: en móvil el indicador vive **dentro** de la lista (skeleton
o `RefreshControl`) o **dentro** del botón que disparó la acción.
