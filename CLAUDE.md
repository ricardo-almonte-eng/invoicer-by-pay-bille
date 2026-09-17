# CLAUDE.md

Guía para Claude Code (claude.ai/code) en este repositorio.

Este archivo es un **índice**, no un manual: el detalle vive en `documentacion/guidelines/`.
Antes de explorar el repo con `grep`/`find`, busca el dato en la guía que corresponda.

## Proyecto

**Invoicer By PayBille**: app móvil de facturación para negocios pequeños y vendedores
independientes de República Dominicana. **El hermano pequeño de PayBille POS.**

**React Native + Expo + TypeScript**, UI en español, `es-DO`, `America/Santo_Domingo` (UTC-4).
Toda la persistencia vive en la **misma API REST de PayBille** (otro repositorio) — misma base de
datos, mismo `IdMarket`, misma identidad visual.

Alcance: facturas · pagos parciales y completos · estatus · inventario · cotizaciones · notas de
crédito y débito · órdenes de compra. **Nada de taller.** Detalle → [00](documentacion/guidelines/00-vision-y-alcance.md).

Comandos — **referencia para el usuario; Claude no los ejecuta**:

```bash
npx expo start        # desarrollo
npx expo start --tunnel
eas build --profile preview --platform android
```

## Documentación — empieza aquí

| Guía | Cuándo leerla |
|---|---|
| **[00-vision-y-alcance.md](documentacion/guidelines/00-vision-y-alcance.md)** | **Siempre, al iniciar.** Qué entra y qué no |
| [01-arquitectura.md](documentacion/guidelines/01-arquitectura.md) | Stack, carpetas, `.env`, arranque de sesión |
| [02-api-y-fetch.md](documentacion/guidelines/02-api-y-fetch.md) | Cliente HTTP, autenticación, endpoints, paginación |
| [03-modelo-de-datos.md](documentacion/guidelines/03-modelo-de-datos.md) | Forma **real** de cada payload de la API |
| [04-estado-y-stores.md](documentacion/guidelines/04-estado-y-stores.md) | Zustand + React Query: qué guarda cada uno |
| [05-diseno-y-tema.md](documentacion/guidelines/05-diseno-y-tema.md) | Tokens portados, tipografía, iconos |
| [06-componentes-ui.md](documentacion/guidelines/06-componentes-ui.md) | Inventario de componentes |
| [07-navegacion-y-pantallas.md](documentacion/guidelines/07-navegacion-y-pantallas.md) | Mapa de rutas |
| [08-reglas-de-negocio.md](documentacion/guidelines/08-reglas-de-negocio.md) | Impuestos, estatus, pagos, inventario, NCF |
| [09-documentos.md](documentacion/guidelines/09-documentos.md) | Cotización, NC/ND, orden de compra |
| [10-convenciones.md](documentacion/guidelines/10-convenciones.md) | Reglas de código obligatorias |
| [11-plan-de-implementacion.md](documentacion/guidelines/11-plan-de-implementacion.md) | Fases y orden de construcción |
| [12-flujo-de-trabajo.md](documentacion/guidelines/12-flujo-de-trabajo.md) | Cómo trabajar y mantener la documentación |
| [13-recursos-de-marca.md](documentacion/guidelines/13-recursos-de-marca.md) | Fuentes, logo, iconos: qué copiar |

Referencia viva del backend y del diseño: **`C:\repos\PayBille_POS`** y sus
`documentacion/guidelines/`.

## Reglas críticas (no negociables)

1. **Impuestos.** Los precios **ya incluyen** impuesto; el cálculo es informativo. **La tasa se
   elige por factura**, prellenada con `market.taxValue`. Nunca hardcodees `0.18` y **siempre**
   envuelve la tasa en `Number()` — la API la devuelve como string, el input del editor también,
   y `1 + "0.18"` concatena, rompiendo el cálculo en silencio.
   ```ts
   taxRate: () => Number(session.market?.taxValue ?? 0.18)   // solo el valor POR DEFECTO
   ```
   Las tres fórmulas por `taxType` viven **solo** en `lib/tax.ts` →
   [08](documentacion/guidelines/08-reglas-de-negocio.md).
2. **HTTP.** Ninguna pantalla importa axios: todo por `lib/api`. El token va en `Authorization`
   **sin prefijo `Bearer`**.
3. **`Sales.Date` NO es una fecha**: es un string `DD/MM/YYYY hh:mm am`. Para ordenar y filtrar,
   **`createdAt`**.
4. **`Gasto IS NULL`** en toda consulta sobre `sales`. La columna es nullable: `Gasto = false` no
   filtra nada.
5. **Nunca envíes `Paid` ni `Balance`** a `accountdocs`: el saldo lo escribe solo el servidor. Y al
   registrar un abono **no crees movimiento de cuenta** — lo crea él, y si no, el dinero se cuenta
   dos veces.
6. **Inventario = `warehouse`, no `products`.** La existencia, el precio y el código de barras
   están en `warehouse`; `products` es solo la ficha.
7. **El borrador de factura vive en el teléfono**, no en el servidor. El patrón de "venta base" del
   POS **no se replica** → [02](documentacion/guidelines/02-api-y-fetch.md).
8. **CSS.** Cero colores literales (todo por `useTheme()`), **cero sombras** (se separa con
   `borderWidth: 1`), fuente por `fontFamily` y no por `fontWeight`.
9. **Sin roles.** Uso personal, **un solo usuario**: no hay puertas por rol ni pantalla de "sin
   permiso". `session.rol` se guarda porque la cascada de login lo trae, pero **ninguna pantalla
   se ramifica por él**. `permissions` / `checkPermission()` tampoco existen (stubs vacíos).
10. **No ejecutes la aplicación.** Claude **nunca** arranca el proyecto: nada de `expo start`,
    builds ni simuladores. **De ejecutar y verificar se encarga el usuario.** Al terminar, entrega
    el resumen y di qué hay que revisar.
11. **Responsive.** Todo cambio visual se revisa en pantalla pequeña (~360 dp), pantalla grande,
    tema oscuro, tipografía grande del sistema y con el teclado abierto. Indícalo al usuario.
12. **Moneda.** Se elige por factura (`DOP`/`USD`/`EUR`) con tasa de cambio manual, pero **a la
    API van SIEMPRE los importes en moneda base**: no existe columna de moneda en ninguna tabla, y
    mandar euros donde el POS espera pesos corrompe saldo, cuenta e inventario a la vez. La moneda
    solo cambia el editor y el PDF. Las dos conversiones viven **solo** en `lib/money.ts` →
    [08](documentacion/guidelines/08-reglas-de-negocio.md) §11.

**Decisiones de producto cerradas** (2026-09-06, no se reabren sin avisar al usuario): moneda
base **`$`** con separadores `es-DO` → `$ 1,250.00` · **alta rápida de productos** desde el móvil ·
**NCF apagado por defecto**, se activa factura a factura · vender sin existencia **avisa pero
deja pasar**, y la existencia puede quedar negativa · **impuesto y moneda se eligen por factura**.
Detalle y verificación contra el POS →
[11](documentacion/guidelines/11-plan-de-implementacion.md).

## Regla de mantenimiento de este archivo

**En toda tarea se actualiza `CLAUDE.md`.** Antes de tocar código, anota en *Contexto activo* qué
se va a hacer; al terminar, cierra la entrada con el resultado. El objetivo es que la siguiente
sesión arranque sin releer el repositorio.

- Anota solo lo que **no** se deduce del código ni del historial de git.
- Máximo 3 entradas; borra las más antiguas.
- El detalle permanente va a `documentacion/guidelines/`, no aquí.
- `CLAUDE.md` debe mantenerse **por debajo de 17 KB**.
- Si descubriste algo leyendo código que no estaba documentado, añádelo a la guía correspondiente
  en la misma tarea.

Detalle del flujo → [12-flujo-de-trabajo.md](documentacion/guidelines/12-flujo-de-trabajo.md).

## Contexto activo

### 2026-09-06 — Cierre de las decisiones de producto
- **Qué se hizo:** se cerraron las cuatro decisiones que bloqueaban el arranque y se propagaron a
  las guías (00, 02, 03, 04, 07, 08, 10, 11). Ya no queda nada pendiente del usuario para empezar
  a programar la fase 0.
- **La quinta decisión, que no estaba en la lista: la app no tiene roles.** Uso personal, un solo
  usuario. Esto borró la tabla de perfiles de [08](documentacion/guidelines/08-reglas-de-negocio.md)
  §8, la regla de ocultar por rol de [07](documentacion/guidelines/07-navegacion-y-pantallas.md) y
  la puerta de administrador sobre "anular abono".
- **Lo que cambió respecto a lo que decía la documentación:** el POS **no** era incoherente con la
  moneda. Su papel imprime `$ 600.00` (`utils/receipt.js`, `formatCash(v, symbol = '$')`,
  `LOCALE = 'es-DO'`) y su pantalla usa `en-US`/`USD`: dice `$` en los dos sitios. Y la guía 08 se
  contradecía sola en el stock ("nunca dejes existencias negativas" + "aviso que se puede
  saltar"); ahora dice una sola cosa: **negativo se permite y se deja negativo**.
- **Dos trampas encontradas leyendo el POS, ya documentadas:** (1) una venta a crédito fuerza el
  NCF (`completeOrder.vue:748`) pero puede pedirlo con `tipoNCF: ""` — en Invoicer hay que
  preguntar el tipo antes de guardar una factura a crédito; (2) `Intl` con
  `style: 'currency'` y locale `es-DO` devuelve `US$1,250.00`, nunca el `$ 1,250.00` del papel:
  el símbolo se concatena a mano.
- **Ampliación en la misma sesión:** el usuario pidió **elegir moneda (`DOP`/`USD`/`EUR`) y % de
  impuesto al crear la factura**. El impuesto no tiene truco: `sales.Tax` y `salesProducts.Tax`
  son importes, así que cualquier tasa se persiste bien y el POS ve los mismos números. **La
  moneda sí lo tiene: no hay columna de moneda en ninguna tabla.** Por eso la regla es *se guarda
  en base, se emite en la elegida* → [08](documentacion/guidelines/08-reglas-de-negocio.md) §11;
  la moneda vive en el teléfono y se pidió `Currency` + `ExchangeRate` al backend (petición 6).
- **Estado:** terminado. **Sigue sin haber código**: lo siguiente es la fase 0 de
  [11](documentacion/guidelines/11-plan-de-implementacion.md).
- **Qué mirar:** que la moneda `$` (y no `RD$`) sea de verdad lo que el usuario quiere ver en el
  PDF que manda por WhatsApp; es lo único de las cinco decisiones que el cliente final ve.

### 2026-09-06 — Documentación de arranque del proyecto
- **Qué se hizo:** se creó `documentacion/guidelines/` (14 documentos) con todo lo necesario para
  empezar la app: alcance, stack, contrato real con la API de PayBille, formas de payload
  verificadas contra el código del POS, tokens de diseño portados a RN, mapa de pantallas, reglas
  de negocio y plan por fases. **Todavía no hay código.**
- **Por qué así:** el valor no estaba en elegir librerías, sino en dejar por escrito el contrato
  con una API que no está documentada y cuyas trampas (números como string, `Date` que no es
  fecha, `Gasto` nullable, inventario en dos tablas) cuestan horas de depuración cada una.
- **Decisiones tomadas y justificadas en las guías:** Expo + TypeScript + expo-router + Zustand +
  React Query; el borrador de factura vive en el teléfono y no como "venta base" en el servidor;
  los pagos parciales se apoyan en `accountdocs`, que ya resuelve saldo, abonos y cuotas.
- **El hueco real:** **las notas de crédito y débito no existen en el backend.** Solo hay los
  tipos de NCF B03/B04. En [09](documentacion/guidelines/09-documentos.md) se comparan tres
  salidas y se recomienda la vía `accountdocs` para v1, con petición al backend para hacerlo bien.
- **Estado:** terminado. **Pendiente del usuario:** cuatro decisiones de producto listadas al
  final de [11](documentacion/guidelines/11-plan-de-implementacion.md) — moneda (`RD$` vs `$`),
  alta de productos desde el móvil, NCF por defecto, y qué pasa al vender sin existencia.
- **Qué mirar:** que el alcance de [00](documentacion/guidelines/00-vision-y-alcance.md) coincida
  con lo que el usuario tiene en la cabeza, antes de escribir la primera línea de código.
