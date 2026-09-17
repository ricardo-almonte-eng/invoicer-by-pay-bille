# 01 — Arquitectura

## Stack

| Pieza | Elección | Por qué |
|---|---|---|
| Runtime | **Expo (managed)**, SDK estable más reciente (≥ 54) | Build en la nube (EAS), OTA updates, `expo-font`/`expo-secure-store`/`expo-print` resueltos. Un desarrollador solo no mantiene proyectos bare. |
| Lenguaje | **TypeScript** | El POS es JS sin tipos y sus payloads son irregulares (`Amount` string, `taxValue` string). Tipar el borde con la API es la mayor ganancia de calidad disponible. |
| Navegación | **expo-router** (file-based) | Mismo modelo mental que `pages/` de Nuxt. |
| Estado | **Zustand** | Equivalente 1:1 de los *setup stores* de Pinia. Ver [04](04-estado-y-stores.md). |
| Datos de servidor | **@tanstack/react-query** | Listas, caché, reintentos y *pull to refresh* gratis. Es la única dependencia no trivial y se paga sola en las 8 pantallas de listado. |
| HTTP | **axios** | Mismos interceptores que `useFetchStore` del POS. |
| Almacenamiento | `expo-secure-store` (token) + `@react-native-async-storage/async-storage` (borradores, tema) | El token es una credencial: no va a AsyncStorage. |
| Formularios | `react-hook-form` + `zod` | |
| Fechas | `dayjs` + `dayjs/plugin/utc` + `timezone` | `moment-timezone` (el del POS) pesa demasiado para móvil. |
| PDF / compartir | `expo-print` + `expo-sharing` | El "imprimir" del móvil es compartir por WhatsApp. |

**Sin NativeWind ni librería de componentes.** El sistema visual de PayBille es propio y
específico (islas, bordes de 1 px, cero sombras); envolverlo en Tailwind o en Paper añade una capa
que hay que pelear. Se usa `StyleSheet` + un `theme` tipado → [05](05-diseno-y-tema.md).

## Mapa de carpetas

```
app/                      # Rutas (expo-router). Ver 07-navegacion-y-pantallas.md
  (auth)/                 #   login
  (tabs)/                 #   inicio · facturas · inventario · más
  factura/[id].tsx        #   detalle
  factura/nueva.tsx       #   editor
components/
  ui/                     # Primitivas: Input, Select, Button, Card, Label, Sheet…
  domain/                 # Piezas de negocio: LineaFactura, SelectorCliente, ResumenTotales
lib/
  api/                    # client.ts (axios) + un archivo por recurso: sales.ts, products.ts…
  format.ts               # moneda, fecha, tabular-nums
  tax.ts                  # las tres fórmulas de impuesto. ÚNICO sitio donde viven
stores/                   # Zustand
types/                    # Tipos de la API (espejo de 03-modelo-de-datos.md)
theme/                    # tokens.ts · ThemeProvider.tsx · useTheme.ts
assets/
  fonts/                  # Google Sans Flex (TTF estáticos) + iconos
  img/                    # Logo PayBille
documentacion/guidelines/ # Esto
```

Regla: **`lib/api` es la única carpeta que sabe que existe un servidor.** Ni una pantalla importa
axios.

## Variables de entorno

Expo expone al cliente solo las variables con prefijo `EXPO_PUBLIC_`.

```bash
# .env
EXPO_PUBLIC_BASE_URL="https://api.paybille.com/ventex/api"
EXPO_PUBLIC_BASE_URL_GENERIC="https://api.paybille.com/ventex/api/generic"
EXPO_PUBLIC_API_KEY="<pídesela al dueño del proyecto>"
```

| Variable | Uso |
|---|---|
| `EXPO_PUBLIC_BASE_URL` | API de negocio (login, ventas, accountdocs, nfc, cuentas, reportes) |
| `EXPO_PUBLIC_BASE_URL_GENERIC` | CRUD genérico por modelo (`get`, `like`, `count`, `PUT`, `DELETE`) |
| `EXPO_PUBLIC_API_KEY` | Va en el **body** del login como `key`, no en una cabecera |

Entornos que usa el POS hoy (`PayBille_POS/.env`):

```
Local           http://localhost:2001/ventex/api
Producción      https://api.paybille.com/ventex/api
```

⚠️ `EXPO_PUBLIC_*` se **incrusta en el bundle**: cualquiera que descargue el APK puede leer la
`API_KEY`. Hoy es igual de público que en el POS (Nuxt la expone en `runtimeConfig.public`), así
que no empeoramos nada — pero **no metas ahí ningún secreto nuevo**. Si algún día hace falta uno,
va detrás de un endpoint del backend.

⚠️ **`MARKET_TYPE` no se replica.** En el POS decide si la interfaz es restaurante o supermercado;
aquí la app es siempre `REGULAR`.

## Arranque de sesión

Mismo encadenado que `PayBille_POS/pages/credentials/login.vue → initUser()`:

1. `POST {BASE_URL}/users/login` con `{ key, username, password, isGet: true }` → `{ token }`.
2. Guardar el token en **SecureStore**. (El POS usa la cookie `token`; en móvil no hay cookies.)
3. `jwtDecode(token)` → `user`.
4. En cascada, y en este orden porque cada uno depende del anterior:
   - `getById('persons', user.IdPerson)` → `person`
   - `getById('roles', user.IdRol)` → `rol`
   - `getById('markets', person.IdMarket)` → `market` — **normaliza `taxValue` a `Number` aquí,
     una sola vez** (ver [08](08-reglas-de-negocio.md))
   - `getGeneric('Settings', { params: { IdMarket } })` → `settings`
5. Cargar catálogos ligeros (categorías) — el POS carga 6 (`familias`, `grupos`, `marcas`,
   `colores`, `estados`); aquí solo hace falta **categorías**.

**No se replica `checkTorning()`**: no hay turno de caja en móvil.

## Decisiones que ya están tomadas (no las vuelvas a abrir)

1. **La API no se toca desde este repo.** Vive en otro proyecto. Si algo falta, se anota como
   *petición al backend* en [09](09-documentos.md) o [11](11-plan-de-implementacion.md).
2. **Nada de escribir `Paid`/`Balance`.** El saldo lo calcula el servidor y `controllers/generic.js`
   rechaza esos campos por el CRUD genérico.
3. **Cero colores literales.** Todo por `theme` → [05](05-diseno-y-tema.md).
4. **Español, `es-DO`, `America/Santo_Domingo` (UTC-4).**
5. **Sin suite de pruebas automatizada** (igual que el POS): la verificación es manual, en
   dispositivo, **y la hace el usuario**.
