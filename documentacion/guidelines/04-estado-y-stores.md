# 04 — Estado y stores

Dos capas, y la frontera es dura:

| Capa | Herramienta | Qué guarda |
|---|---|---|
| **Estado de servidor** | React Query | Todo lo que vive en la API: listas de facturas, productos, clientes, saldos. **Nunca se copia a Zustand.** |
| **Estado de cliente** | Zustand | Sesión, borrador de factura, tema, preferencias. Lo que no existe en el servidor. |

El error clásico es traerse una lista con React Query y volcarla en un store "para tenerla a
mano": a partir de ahí hay dos verdades y una se queda vieja. Si lo necesitas en otra pantalla,
vuelve a llamar a `useQuery` con la misma `queryKey` — el caché lo resuelve sin red.

## Convención

Un archivo por store en `stores/`, patrón *setup* igual que Pinia:

```ts
// stores/session.ts
export const useSession = create<SessionState>()((set, get) => ({
  user: null, person: null, rol: null, market: null, settings: null,
  taxRate: () => Number(get().market?.taxValue ?? 0.18),
  async login(username, password) { /* … */ },
  async logout() { /* … */ },
}));
```

## Catálogo de stores

### `useSession` — equivalente de `useUserStore`

| Campo | Notas |
|---|---|
| `user` | JWT decodificado. De aquí sale `Torning` |
| `person` | Para `Username: \`${FirstName} ${LastName}\`` |
| `rol` | Se guarda porque la cascada lo trae, pero **la UI no se ramifica por el**: la app no tiene roles -> [08](08-reglas-de-negocio.md) §8 |
| `market` | La tienda. `taxValue` ya normalizado a `Number` **aquí, una sola vez** |
| `settings` | Fila de `Settings` por `IdMarket` |
| `taxRate()` | `Number(market?.taxValue ?? 0.18)`. **Valor por defecto**; la tasa efectiva vive en el borrador y se elige por factura → [08](08-reglas-de-negocio.md) §1 |

Persiste solo el token, y en **SecureStore**. Al arrancar la app: si hay token → rehidratar con la
cascada de [01](01-arquitectura.md#arranque-de-sesión); si no → `/login`.

### `useDraftInvoice` — el borrador de factura

El store más importante de la app. Es el que hace que **no** repliquemos la "venta base" del POS.

```ts
{
  tipo: 'Factura' | 'Cotizacion' | 'NotaCredito' | 'NotaDebito',
  cliente: { IdClient, nombre, telefono, identify } | null,
  lineas: LineaBorrador[],       // { idWarehouse, IdProduct, Barcode, Name, Amount, Price, Discount }
  taxType: 'with_tax' | 'included' | 'no_tax',
  taxRate: number,               // tasa EFECTIVA de esta factura, en decimal (0.18 = 18 %).
                                 // Nace de session.taxRate() y es editable en el editor
  moneda: 'DOP' | 'USD' | 'EUR', // moneda en la que se EMITE esta factura
  tasaCambio: number,            // unidades de moneda BASE por 1 de `moneda`. DOP -> 1
  conNCF: boolean, tipoNCF: 'B01' | 'B02' | 'B03' | 'B04',
  cobro: { efectivo, transferencia, tarjeta, IdCuenta },
  vencimiento: string | null,
  plan: PlanCuotas | null,
  refIdSale: number | null,      // solo NC/ND: la factura que se ajusta
  estadoEnvio: 'borrador' | 'enviando' | 'error',
}
```

Reglas:

- **Se persiste en AsyncStorage en cada cambio.** Si la app muere a media factura, al volver está
  todo. Es la diferencia entre una app que se usa y una que no.
- **Los totales no se guardan: se derivan** con un selector que llama a `lib/tax.ts`. Un total
  guardado se desincroniza de sus líneas.
- **`taxRate` y `tasaCambio` sí se guardan**, y son la excepción que confirma la regla anterior:
  no son totales, son *entradas* del cálculo. Si no se guardaran, un borrador recuperado del disco
  recalcularía con la tasa del negocio y cambiaría el total a espaldas del usuario.
- **`moneda` y `tasaCambio` no se mandan a la API**: los importes viajan en moneda base. Al
  guardar con éxito se archivan en AsyncStorage contra el `id` de la venta, que es lo único que
  permite reimprimir el PDF en su moneda → [08](08-reglas-de-negocio.md) §11.
- Al confirmar el envío con éxito, **se limpia**. Si falla, queda con `estadoEnvio: 'error'` y la
  pantalla ofrece reintentar.

### `useTheme` — claro / oscuro

`{ mode: 'system' | 'light' | 'dark', toggle() }`, persistido en AsyncStorage
(el POS usa `localStorage.theme`). Por defecto **`system`**, que en el POS no existe porque en web
el usuario elige a mano; en móvil lo espera el sistema operativo.

### `useUi` — piezas globales de interfaz

Toast y hoja de confirmación. Nada más. El POS tiene siete stores de UI
(`modals`, `dialog`, `messageBox`, `toast`, `loading`, `form`, `dataGridView`) porque su shell
monta modales globales; aquí los `BottomSheet` y `Modal` de React Native se montan donde se usan.

## Lo que NO se porta del POS

| Store del POS | Por qué no |
|---|---|
| `cart.js` | Su estado vive en el servidor (venta base). Lo sustituye `useDraftInvoice` |
| `modals.js`, `dialog.js`, `messageBox.js` | Sin shell de modales globales |
| `loading.js` | Sin overlay global: el indicador va donde está la acción |
| `form.js` | Lo cubre `react-hook-form` |
| `dataGridView.js`, `table.js` | No hay grid: hay `FlatList` |
| `mesa.js`, `invoice.js`, `image.js`, `shoppingsData.js`, `printerConfig.js` | Restaurante, impresión térmica y flujos fuera de alcance |
| `commonData.js` | Sus catálogos (familias, grupos, marcas, colores, estados) no se usan. Lo único que sobrevive es el **formato de moneda y fecha**, que va a `lib/format.ts`, no a un store |

## `lib/format.ts` — copia el comportamiento del POS, no su código

```ts
export type Moneda = 'DOP' | 'USD' | 'EUR';

// `$` SOLO para la moneda base. Es exactamente lo que sale por el papel del POS
// (utils/receipt.js: `formatCash(v, symbol = '$')` con LOCALE 'es-DO'), el espacio incluido.
//
// USD y EUR van con su CÓDIGO, no con su símbolo: dos `$` distintos conviviendo en
// la misma app es justo como se cuela un cobro en la moneda equivocada. `EUR 25.00`
// no se confunde con nada.
const SIMBOLO: Record<Moneda, string> = { DOP: '$', USD: 'USD', EUR: 'EUR' };

export const money = (v: number | string, moneda: Moneda = 'DOP') =>
  `${SIMBOLO[moneda]} ${new Intl.NumberFormat('es-DO', { minimumFractionDigits: 2,
                                                         maximumFractionDigits: 2 })
    .format(Number(v) || 0)}`;
```

⚠️ **No uses `style: 'currency'` aquí.** Con locale `es-DO` devuelve `US$1,250.00` o
`DOP 1,250.00` según el código — nunca el `$ 1,250.00` del comprobante. Por eso el símbolo se
concatena a mano sobre un formato numérico normal.

**Por qué `$` y no `RD$`** (decisión cerrada el 2026-09-06; no se reabre sin tocar también el
POS): el papel que el POS entrega hoy ya dice `$ 600.00`. Si el móvil dijera `RD$`, el mismo
negocio estaría dando dos comprobantes que no coinciden. El día que se cambie, se cambia en
`lib/format.ts` **y** en `utils/receipt.js` del POS, a la vez.

Fechas: `dayjs` con `utc` + `timezone`, zona `market?.TimeZone ?? 'America/Santo_Domingo'`, formato
`DD/MM/YYYY hh:mm a` — el mismo de `commonData.js:215`. Y recuerda: **para ordenar se usa
`createdAt`, no `Date`** ([03](03-modelo-de-datos.md)).
