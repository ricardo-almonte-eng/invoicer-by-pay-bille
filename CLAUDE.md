# CLAUDE.md

Guía para Claude Code (claude.ai/code) en este repositorio.

Este archivo es un **índice**, no un manual: el detalle vive en `documentacion/guidelines/`.
Antes de explorar el repo con `grep`/`find`, busca el dato en la guía que corresponda.

## Proyecto

**Invoicer By PayBille**: app móvil de facturación para negocios pequeños y vendedores
independientes de República Dominicana. **El hermano pequeño de PayBille POS.**

**Kotlin Multiplatform (Android + iOS) + Compose Multiplatform** — lógica **y** UI en
`composeApp/src/commonMain`; `expect`/`actual` solo para lo que exige la plataforma. **Koin**,
**Voyager**, **Ktor**, **Room**, componentes propios `Pb*` (sin Material). UI en español, `es-DO`,
`America/Santo_Domingo` (UTC-4). **Offline first**: la base local es la fuente de verdad y se
sincroniza con la **misma API REST de PayBille** (otro repositorio) — misma base de datos, mismo
`IdMarket`, misma identidad visual.

Alcance: facturas · pagos parciales y completos · estatus · inventario · cotizaciones · notas de
crédito y débito · órdenes de compra. **Nada de taller.** Detalle → [00](documentacion/guidelines/00-vision-y-alcance.md).

Configuración en `local.properties` (no versionado): `paybille.apiKey` y, opcionales,
`paybille.apiBaseUrl` y `paybille.webBaseUrl` (web del POS, para el enlace del catálogo). Claude **sí** puede compilar y correr pruebas; **no** instala ni arranca:

```bash
./gradlew :composeApp:compileAndroidMain :androidApp:compileDebugKotlin
./gradlew :composeApp:testAndroidHostTest
```

iOS no compila en Windows: se verifica en Xcode (`iosApp/iosApp.xcodeproj`).

## Documentación — empieza aquí

| Guía | Cuándo leerla |
|---|---|
| **[00-vision-y-alcance.md](documentacion/guidelines/00-vision-y-alcance.md)** | **Siempre, al iniciar.** Qué entra y qué no |
| [01-arquitectura.md](documentacion/guidelines/01-arquitectura.md) | Stack KMP, carpetas, `local.properties`, sesión |
| [02-api-y-fetch.md](documentacion/guidelines/02-api-y-fetch.md) | Cliente HTTP, autenticación, endpoints, paginación |
| [03-modelo-de-datos.md](documentacion/guidelines/03-modelo-de-datos.md) | Forma **real** de cada payload de la API |
| [04-estado-y-stores.md](documentacion/guidelines/04-estado-y-stores.md) | Room + ScreenModel: qué dato vive dónde |
| [05-diseno-y-tema.md](documentacion/guidelines/05-diseno-y-tema.md) | `PbTheme`: tokens, tipografía, iconos |
| [06-componentes-ui.md](documentacion/guidelines/06-componentes-ui.md) | Inventario de componentes |
| [07-navegacion-y-pantallas.md](documentacion/guidelines/07-navegacion-y-pantallas.md) | Mapa de pantallas (Voyager) |
| [08-reglas-de-negocio.md](documentacion/guidelines/08-reglas-de-negocio.md) | Impuestos, estatus, pagos, inventario, NCF |
| [09-documentos.md](documentacion/guidelines/09-documentos.md) | Cotización, NC/ND, orden de compra |
| [10-convenciones.md](documentacion/guidelines/10-convenciones.md) | Reglas de código obligatorias |
| [11-plan-de-implementacion.md](documentacion/guidelines/11-plan-de-implementacion.md) | Fases y orden de construcción |
| [12-flujo-de-trabajo.md](documentacion/guidelines/12-flujo-de-trabajo.md) | Cómo trabajar y mantener la documentación |
| [13-recursos-de-marca.md](documentacion/guidelines/13-recursos-de-marca.md) | Fuentes, logo, iconos: qué copiar |
| [14-carrito-y-pagos-en-linea.md](documentacion/guidelines/14-carrito-y-pagos-en-linea.md) | **Plan futuro**: carrito, Stripe/Azul/Cardnet, links de pago |

Referencia viva del backend y del diseño: **`C:\repos\PayBille_POS`** y sus
`documentacion/guidelines/`.

## Reglas críticas (no negociables)

1. **Impuestos.** Los precios **ya incluyen** impuesto; el cálculo es informativo. **La tasa se
   elige por factura**, prellenada con `session.defaultTaxRate` (`market.taxValue`). Nunca
   hardcodees `0.18` fuera de `DEFAULT_TAX_RATE`. La API manda la tasa (y todo `DECIMAL`) como
   **string**: se normaliza en el DTO con los serializadores `Lenient*`, nunca en la pantalla; el
   input del editor, con `toDoubleOrNull()`. Las tres fórmulas por `taxType` viven **solo** en
   `core/billing/Tax.kt`, con el redondeo *half-up* del backend →
   [08](documentacion/guidelines/08-reglas-de-negocio.md).
2. **HTTP.** Ninguna pantalla ni `ScreenModel` importa Ktor: todo por `core/network`
   (`PayBilleApi`, errores como `ApiException`). El token va en `Authorization` **sin prefijo
   `Bearer`**. Los GET y los POST con `isGet` vienen envueltos en `{ data, meta }`; el resto
   (PUT, `postGeneric`, `marketbyuser/*`, `image/upload`) llega **sin sobre** y `PayBilleApi` acepta
   las dos formas.
3. **`Sales.Date` NO es una fecha**: es un string `DD/MM/YYYY hh:mm am`. Para ordenar y filtrar,
   **`createdAt`**.
4. **`Gasto IS NULL`** en toda consulta sobre `sales`. La columna es nullable: `Gasto = false` no
   filtra nada.
5. **Nunca envíes `Paid` ni `Balance`** a `accountdocs`: el saldo lo escribe solo el servidor. Y al
   registrar un abono **no crees movimiento de cuenta** — lo crea él, y si no, el dinero se cuenta
   dos veces.
6. **Inventario = `warehouse`, no `products`.** La existencia, el precio y el código de barras
   están en `warehouse`; `products` es solo la ficha.
7. **Offline first.** La pantalla lee de **Room**, nunca de la red; el repositorio refresca Room.
   El borrador de factura vive en el teléfono; el patrón de "venta base" del POS **no se
   replica** → [02](documentacion/guidelines/02-api-y-fetch.md). Room se migra, **nunca** se
   destruye (la sesión vive ahí).
8. **Diseño.** Sin Material: componentes `Pb*` propios. Cero colores literales fuera de
   `Colors.kt` (todo por `PbTheme.colors`), **cero sombras/elevación** (borde de 1 dp), texto con
   `PbTheme.typography`, importes con `amount` (cifras tabulares). Iconos: la **fuente** Material
   Symbols Rounded (eje FILL) con `PbIcon(PbSymbols.*)`; `PbSymbols` se genera con
   `documentacion/scripts/material_symbols.py` → [13](documentacion/guidelines/13-recursos-de-marca.md).
9. **Sin roles.** Uso personal, **un solo usuario**: no hay puertas por rol ni pantalla de "sin
   permiso". `session.roleName` se guarda porque la cascada de login lo trae, pero **ninguna pantalla
   se ramifica por él**. `permissions` / `checkPermission()` tampoco existen (stubs vacíos).
10. **No ejecutes la aplicación.** Claude compila y corre `commonTest`, pero **nunca** instala ni
    arranca la app, ni abre emuladores o simuladores. **De ejecutar y verificar se encarga el
    usuario.** Al terminar, entrega el resumen y di qué hay que revisar.
11. **Responsive.** Todo cambio visual se revisa en pantalla pequeña (~360 dp), pantalla grande,
    tema oscuro, tipografía grande del sistema y con el teclado abierto. Indícalo al usuario.
12. **Moneda.** Se elige por factura (`DOP`/`USD`/`EUR`) con tasa de cambio manual, pero **a la
    API van SIEMPRE los importes en moneda base**: no existe columna de moneda en ninguna tabla, y
    mandar euros donde el POS espera pesos corrompe saldo, cuenta e inventario a la vez. La moneda
    solo cambia el editor y el PDF. Las dos conversiones viven **solo** en
    `core/billing/Money.kt` → [08](documentacion/guidelines/08-reglas-de-negocio.md) §11.
13. **La sesión no vence.** Solo "Cerrar sesión" la borra; un 401/403 **no** desloguea. El login
    es en **dos fases** si el usuario tiene varias tiendas, y **nunca** se manda `RapidLogin`
    (cambiaría la preferencia del POS) → [01](documentacion/guidelines/01-arquitectura.md).

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

### 2026-09-27 (4) — Dónde pagar, notificaciones y catálogo compartible
- **Problema del usuario:** notificaciones (la campana era un "próximamente"); al crear la factura,
  elegir **dónde pagar** (varias cuentas) e instrucciones, y que salgan en el PDF (tocar el
  endpoint); la cuenta con la **cédula** del titular. Compartir el **catálogo**, elegir qué sale y
  filtros de marca y color "grises rellenos", estilo tienda. Un `.md` para carrito y pasarelas.
- **Qué se hizo:** API: `F4_instrucciones_pago.sql` (`Cuentas.HolderName/HolderId`,
  `Sales.PaymentAccounts/PaymentNote`), `shareFactura` + `factura.html` con "Dónde pagar";
  `catalog/data` agrupa por producto con `brand`, `colors` y `variants`. POS: `/catalogo`
  rehecho (píldoras grises, panel de marca/color/orden, "Agotado", compartir) y titular en
  `CreateCuenta.vue`. App: `PayTo` en el borrador (memoria `invoice:payTo`), sección del editor,
  titular en cuentas (Room v6), `NotificationsScreen` + campana, `CatalogScreen` + interruptor en la
  ficha, `DocumentPlatform.shareText`, `PbCheckRow`. Plan futuro → guía 14.
- **Estado:** compila en Android sin avisos; **91 pruebas** en verde. iOS (`shareText`) pendiente
  de Xcode. **`F4` hay que ejecutarlo a mano antes de desplegar la API** (si no, todo `GET` de
  ventas y cuentas falla con `Unknown column`).
- **Qué mirar:** factura a crédito con 2 cuentas + nota → PDF; una pagada sin bloque; cuenta con
  titular/cédula; campana con una vencida, una de hoy y un envío rechazado; catálogo: compartir,
  ver, marcar sin red; `/catalogo/<tienda>` en móvil y escritorio, claro y oscuro; 360 dp y letra
  grande en el editor y el catálogo.

### 2026-09-27 (3) — Listas separadas y facturas con saldo y vencimiento
- **Problema del usuario:** las filas de las listas solo se separan por una línea; quiere
  distinguirlas más. En Facturas, cada fila debe mostrar cliente, lo que se debe y el vencimiento
  con los días que faltan, con un indicador de vencida o por vencer.
- **Qué se hizo:** `PbListCard` (isla por fila, 10 dp entre tarjetas, franja `accent` opcional);
  `PbItemRow`, `SaleRow`, `PendingRow` y las filas de los buscadores la usan. `SaleRow`: cliente
  como título, total y estatus; con `RowDebt` (de `receivables`) añade "DEBE", vencimiento con días
  y la etiqueta Vencida / Por vencer / Vence hoy. Regla en `DueState.urgency()` con
  `DUE_SOON_DAYS = 3` (los días de los avisos).
- **Estado:** compila en Android sin avisos; **82 pruebas** en verde.
- **Qué mirar:** listas a 360 dp y letra grande (tarjetas, importes que bajan de línea); tema
  oscuro (franja y etiquetas); una factura vencida, una que vence hoy, otra en 2 días, otra en 10;
  una pagada (sin bloque "DEBE").

### 2026-09-27 (2) — Isotipo, cambio de tienda que se colgaba, configuración de tienda, fotos de productos
- **Problema del usuario:** en la cabecera quiere el isotipo de PayBille; al cambiar de tienda la
  hoja se quedaba cargando (y debe cerrarse); la hoja de tiendas ocupaba toda la pantalla (poner
  alto máximo). Configuración de la tienda en versión reducida. Cámara para la imagen del producto
  y marca, color, categoría como en el POS.
- **Qué se hizo:** `MainScreen(idMarket)` con clave por tienda; la hoja se cierra al terminar y
  tiene buscador con más de 6 tiendas. Toda `PbSheet` mide como mucho el 85 %. Cabecera con el
  isotipo. `feature/store`: configuración reducida (Mi perfil → Configurar tienda). Productos:
  imagen (`rememberImagePicker` expect/actual + `image/upload` multipart + `RemoteImage`),
  categoría/marca/color con `PbSelectField` + `PbOptionSheet` y alta rápida.
- **Descubierto:** Voyager guarda los `ScreenModel` por `screen.key` (sin el Navigator): con clave
  fija el armazón nuevo heredaba los modelos que el viejo desechaba → era el "cargando". El sobre
  `{ data, meta }` **solo** va en GET y POST con `isGet`; `mine`, `switch`, PUT, `postGeneric` e
  `image/upload` llegan sin él (el cliente ya lo toleraba). La factura PDF solo usa logo, nombre,
  dirección, RNC, teléfono y correo de la tienda. Marca y color van en `warehouse`; categoría en
  `products` (0 = ninguna). Compose Resources no admite subcarpetas en `drawable/`.
- **Estado:** compila en Android sin avisos; **76 pruebas** en verde. iOS (cámara) pendiente de
  Xcode. Los logos de bancos que añadió el usuario en `drawable/banks/` rompían el build (no se
  admiten subcarpetas): quedaron planos como `drawable/bank_*.{png,jpg}` (`git mv`) y se usan en
  Bancos y en la hoja de cuentas del editor (`core/ui/BankLogo.kt`). 79 pruebas.
- **Qué mirar:** cambiar de tienda (la hoja se cierra y todo se recarga); hoja de tiendas larga;
  configurar tienda con y sin red (logo PNG transparente → que no salga negro en el PDF); foto de
  producto con la cámara en vertical (que no salga girada), desde la galería, quitar; crear
  categoría/marca/color sin red; ficha del producto con imagen; 360 dp y tema oscuro.
