# 07 — Navegación y pantallas

**Voyager**: cada pantalla es un `Screen` (`data object` si no lleva parámetros, `data class` si
los lleva) con su `ScreenModel`. **Seis destinos** en la barra inferior (2026-09-18); ya no hay
"Más".

```
App()                                   Tema + pantalla según SessionRepository.state
├── LoginScreen            ✅            feature/auth — usuario/contraseña + selector de tienda
└── MainScreen             ✅            feature/main — barra de 6 destinos + botón "+" en todos
    ├── InvoicesTab        ✅            FACTURAS (principal) · Todas / Ventas / Cotizaciones
    ├── SummaryTab         ✅            RESUMEN   · el dashboard del POS
    ├── ProductsTab        ✅            PRODUCTOS · inventario agrupado, buscar, filtrar
    ├── ClientsTab         ✅            CLIENTES  · buscar, quién debe, alta
    ├── ReportsTab         ✅            REPORTES  · 4 reportes
    └── BanksTab           ✅            BANCOS    · "Cuentas bancarias": balance y cuentas
    Pantallas que se apilan encima (navigator.push):
    ├── InvoiceEditorScreen(kind) ✅     Editor de factura y cotización (destino del "+")
    ├── ProductPickerScreen / ClientPickerScreen ✅  buscadores del editor (con alta rápida)
    ├── SaleDetailScreen(saleId | localId) ✅  detalle: factura (HTML), cobro, vencimiento, abonos
    ├── InvoiceViewerScreen(saleId, html) ✅  la factura a pantalla completa (scroll y zoom)
    ├── InvoiceSettingsScreen ✅         Mi perfil → "Diseño de factura" (invoiceconfig)
    ├── ProductDetailScreen(idProduct) ✅ / ProductEditorScreen(idProduct?, pickFor?) ✅
    ├── ClientDetailScreen(id, name?) ✅ / ClientEditorScreen(id?, pickFor?) ✅
    ├── BankAccountDetailScreen(id) ✅ / BankAccountEditorScreen(id?) ✅
    ├── ReportScreen(kind) ✅            Ventas por fecha · Productos vendidos · Saldos pendientes · Histórico del inventario
    ├── ProfileScreen      ✅            Mi perfil: usuario, tienda, sincronización, cerrar sesión
    │     └── StoreSettingsScreen ✅       Configuración de la tienda (versión reducida)
    ├── PaymentScreen(id)               (el abono vive hoy en SaleDetailScreen)
    ├── PurchasesScreen / PurchaseEditorScreen / PurchaseDetailScreen(id)
    └── SettingsScreen                  Tienda, tema
```

**Login ↔ app no es una navegación**: `App` elige `Navigator(LoginScreen)` o el de la app según
haya fila de sesión en Room → [01](01-arquitectura.md#arranque-y-sesión-offline-first).

### Facturas (antes "Inicio"; 2026-09-16, según la referencia de Bookipi)

```
┌ isla ─────────────────────────────────────────┐
│ [logo]     Ana Pérez         [🔔]  [perfil]    │  MainHeader (fija, de MainScreen)
│            Colmado Ana ▾                       │  ← toca: "Cambiar de tienda"
│ Todas   Ventas   Cotizaciones                  │  PbTabRow (subrayado que se desliza)
└────────────────────────────────────────────────┘
  lista de la pestaña (HorizontalPager: se desliza entre pestañas), una tarjeta por factura
  ┌──────────────────────────────────────────────┐
  ▌Tony Stark                         $ 1,250.50 │
  ▌#0009 · 5 may. 2026 · B0100000012  [Pendiente]│
  ▌──────────────────────────────────────────────│  solo si tiene saldo
  ▌DEBE                               [Por vencer]│
  ▌$ 850.00            Vence en 2 días · 29 sep. │
  └──────────────────────────────────────────────┘
  ▌ = franja roja (vencida) o naranja (vence hoy o en ≤ 3 días)
```

El saldo y el vencimiento salen de `receivables` (guardados en el teléfono, sin llamada nueva);
una factura "Pendiente" cuyo documento todavía no se sincronizó se ve sin el bloque "DEBE".

| Pestaña | `Status` |
|---|---|
| Todas | `Complete`, `Pagos Pendientes`, `Cotizacion`, `Cancelada`, `Cancelado` |
| Ventas | `Complete`, `Pagos Pendientes` |
| Cotizaciones | `Cotizacion` |

- Los estatus internos del POS (`En Proceso`, `Suspendida…`, `Esperando Facturacion`) no salen:
  son ventas a medio hacer en el mostrador.
- **Notificaciones** (campana, 2026-09-27) abre `NotificationsScreen`: "Sin enviar" (rechazados
  con "Reintentar envío", y los que esperan red) y "Por cobrar" (vencidas, vencen hoy y por vencer
  en `DUE_SOON_DAYS`). Todo sale de Room (`receivables` + cola), también sin red; al abrir se
  refrescan los vencimientos. Punto rojo si hay algo urgente: vencida, vence hoy o rechazado
  (`Notice.urgent`). **Perfil** abre `ProfileScreen`, que es donde vive ahora "Cerrar sesión".
- Cada pestaña se refresca al mostrarse (si nunca cargó o lo último tiene más de un minuto) y
  pide la página siguiente al acercarse al final. Sin red enseña lo guardado con un aviso y
  "Reintentar".
- Tocar una fila todavía no abre nada: el detalle llega con la fase 2.
- Pendiente de la referencia: el "vence en / vencida hace N días" (sale de `accountdocs`).

### Barra inferior y "+" (2026-09-18)

**Facturas · Resumen · Productos · Clientes · Reportes · Bancos**, a petición del usuario (sustituye
a Inicio · Flujo de caja · Más, y a las "cuatro pestañas y nada más" de antes).

- **Facturas es el destino principal**: la app abre ahí y *atrás* desde cualquier otro destino
  vuelve ahí; desde Facturas, sale de la app. **No hay destino "Inicio"**: el dashboard se llama
  **Resumen**.
- Caben seis a 360 dp (~57 dp cada uno): etiquetas de **una palabra** y el mismo estilo de texto
  activo o no (en negrita "Productos" ya no cabría). Por eso la de cuentas dice **"Bancos"**; la
  pantalla se titula "Cuentas bancarias".
- Los destinos **no son pantallas de Voyager**: cambian dentro de `MainScreen` y cada uno conserva
  su estado (`SaveableStateHolder`). Sus `ScreenModel` viven con `MainScreen` y **no llaman a la
  red hasta que el destino se muestra**.
- **El "+" está en los seis destinos**: facturar es la función principal. Crea una
  **cotización** solo si el destino es Facturas y la pestaña visible es Cotizaciones; en el resto,
  una **factura**. Toda lista de un destino reserva `PbFabClearance` al final.
- **Mi perfil** (y cerrar sesión) se abre con el icono de perfil de la cabecera.

### Cabecera fija, cambio de destino y cambio de tienda (2026-09-27)

- **La cabecera es de `MainScreen`, no de cada destino** (`MainHeader` sobre `PbAppBar`):
  isotipo de PayBille (pedido del usuario; el logo de Invoicer queda para el login) · **usuario y
  tienda centrados** · la acción del destino (Nuevo producto /
  cliente / cuenta, la campana en Facturas) y Mi perfil. No se desliza al cambiar de destino: la
  identidad se queda quieta y solo se mueve lo que es del destino. Los destinos solo pintan
  `TabHeader(title) { buscador, chips }` debajo; `title` no se ve (lo dice la barra) pero el lector
  de pantalla lo anuncia (`paneTitle`).
- **Cambio de destino con *slide***: el nuevo entra por el lado hacia el que se avanza en la barra
  y el viejo sale por el otro, recorriendo **un cuarto del ancho** y fundiéndose (`PbMotion.PAGE_MS`,
  260 ms, sin rebote). El "+" y la barra no se mueven.
- **Barra indicadora**: 3 dp en el borde superior de la barra inferior, sobre el destino activo, que
  se desliza de uno a otro (`INDICATOR_MS`). Las pestañas de Facturas (Todas · Ventas ·
  Cotizaciones) hacen lo mismo con su subrayado, y tocar una pestaña desliza el `Pager`.
- **Cambiar de tienda**: tocar el usuario/tienda abre la hoja **"Cambiar de tienda"**
  (`feature/main/StoreSwitcher.kt`). Lista `marketbyuser/mine`; sin red enseña la tienda activa y
  "Reintentar". Elegir otra llama a `SessionRepository.switchStore` → [01](01-arquitectura.md).
  **Con documentos sin enviar no se puede** (saldrían con el token nuevo, a nombre de la otra
  tienda): la hoja lo dice y desactiva las filas. Al terminar, la hoja se cierra y `App` recrea el
  armazón (`MainScreen(idMarket)`) y todo se descarga de la tienda nueva. Con más de 6 tiendas (un
  Master las ve todas) aparece un buscador.
- **Toda hoja mide como mucho el 85 % del alto**: la cabecera queda fija y el contenido desplaza
  dentro.

### Configuración de la tienda (Mi perfil → "Configurar tienda")

Versión reducida de `configuracion/tienda.vue`. Entra **lo que el cliente ve en la factura** (la
factura pinta logo, nombre, dirección, RNC, teléfono y correo) y **el impuesto con el que
nace cada factura** (cómo se cobra, ITBIS/IVA y tasa). Queda en el POS: banco, garantía, redes,
parámetros del sistema, impresora, zona horaria y NCF. Se abre con lo guardado en el teléfono y
se completa con `markets/{id}` (el correo no está en Room); sin red se ve pero no se guarda. Al
guardar se sube el logo (si cambió), `PUT markets` con esos campos y `refreshProfile()`. Las
facturas ya emitidas **también** cambian: la app las genera al abrirlas (desde 2026-10-06).

### Diseño de factura (Mi perfil → "Diseño de factura") ✅

`invoiceconfig` de la tienda (sql/F5): título, color de acento, logo y datos del negocio, datos y
contacto del cliente, vencimiento, NCF, impuesto, saldo, pagos recibidos, dónde pagar,
instrucciones (y las de por defecto), condiciones, firma (lienzo + nombre) y mensaje final.
Arriba, una **factura de ejemplo** (a crédito con un abono, para que se vean todas las secciones)
que se rehace 300 ms después de cada cambio; tocarla abre el visor. Abre con lo guardado en el
teléfono y lo refresca con `GET invoiceConfig/{IdMarket}`; guardar (`POST`, upsert) necesita red.

### Resumen (el dashboard del POS, `pages/index.vue`)

- Periodo: Hoy · Ayer · 7 días · Mes (fechas en la zona del negocio). `POST dashboard/summary`.
- Cuatro tarjetas: Ventas de hoy (+ transacciones) · Gastos de hoy · **Te deben** (sustituye a
  "Turnos activos": los turnos no entran; sale de `receivables`, sin llamada nueva) · Total del
  periodo (+ ventas).
- "Ventas del periodo": gráfico de área (los días sin ventas en 0). "Productos más vendidos": top
  5 con barra proporcional (mínimo 8 %).
- **No hay botón "Vender"** como en el POS: ya está el "+".

### Productos, Clientes y Bancos

- **Lista** desde Room (copia completa, se busca en el teléfono) → [04](04-estado-y-stores.md).
  Botón de alta en la cabecera (el "+" es para facturar).
- **Productos**: filtro Todos · Con existencia · Agotados. La existencia tiene tres estados:
  normal ("Hay 5"), agotado y **negativa** (etiqueta de aviso, no de error). La ficha enseña
  precio, impuesto informativo, costo, ganancia por unidad, código de barras y "Entradas y
  salidas". **Solo se edita un producto general de un solo lote**; los únicos (IMEI/serie) y los
  de varios lotes se ven en solo lectura con el aviso "se edita en el POS".
- **Imagen, categoría, marca y color** (como la ficha rápida del POS): arriba del formulario, la
  imagen con "Tomar foto", "Galería" y "Quitar"; se sube al guardar, antes que el producto (si el
  guardado falla después, no se vuelve a subir). Categoría, marca y color se eligen en una hoja con
  buscador y **alta rápida** ("Crear “Rojo”"); los catálogos se guardan en el teléfono. La ficha
  enseña la imagen (sin red, su hueco) y los tres nombres.
- **Clientes**: nombre, teléfono o cédula (con máscara `000-0000000-0`), y lo que debe. La ficha
  enseña contacto, "Te debe" (documentos con saldo) y sus facturas; "Nueva factura" abre el editor
  **con ese cliente puesto**.
- **Bancos**: cada cuenta con el **logo de su banco** (lista, ficha, hoja "Cuenta donde entra el
  dinero" del editor); en el alta, los bancos se eligen en una cuadrícula de logos. Balance total de las activas; si no hay caja activa, "Crear Efectivo General" (como
  el POS). La ficha: balance, lo que entró y salió en 7 · 30 · 60 días, movimientos y "Nuevo
  movimiento" (Ingreso/Egreso manual, `ReferenceType: Ajuste`). **No se borran cuentas** desde el
  teléfono, y no hay transferencia entre cuentas (el backend no la tiene).
- **Alta desde el editor de factura**: los buscadores tienen un icono de alta. El cliente nuevo
  queda puesto en el borrador; el producto nuevo, como línea. Se vuelve directo al editor.
- Formularios con `PbFormScreen`: lo que casi nunca se toca va plegado ("Más datos") y se abre
  solo si ya trae algo; *atrás* con cambios pregunta.

### Reportes

Cuatro, elegidos por el usuario: **Ventas por fecha** (tarjetas y ventas pagadas), **Productos
vendidos** (vendido, costo, descuentos, ganancia; filas sumadas por producto), **Saldos
pendientes** (quién debe, cuánto y desde cuándo; toca → ficha del cliente) e **Histórico del
inventario**. Periodo: Hoy · Ayer · 7 días · Mes · **Mes anterior** (no hay rango personalizado:
necesitaría un selector de fechas que aún no existe). Sin exportar a Excel/PDF.

### Login

Como el login "versión teclado" del Claude Design: logo de Invoicer arriba con su bajada, **una
tarjeta** con el título ("Iniciar sesión" / "Elige la tienda"), rótulos en mayúsculas y un único
botón lleno a lo ancho. Sin iconos dentro de los campos ni asteriscos (los dos son obligatorios).
Lo del diseño que no aplica al móvil —teclado numérico en pantalla, Configuración, Apagar, la
píldora "Conectado" (no hay monitor de red)— no se trae.

1. Usuario y contraseña (los dos obligatorios; el teclado pasa de uno a otro y "Listo" envía).
2. Si el usuario tiene varias tiendas, la misma pantalla cambia a **"Elige la tienda"**. El botón
   o gesto *atrás* vuelve a las credenciales (no cierra la app) y "Usar otra cuenta" también.
3. Errores en línea: credenciales malas (se vacía la contraseña), sin conexión (aviso gris de
   "sin conexión"), tienda no permitida (mensaje del servidor).

No hay enlace "¿Olvidaste tu contraseña?": el POS lo tiene (`/credentials/restore`), pero aquí no
existe todavía esa pantalla y un enlace muerto es peor que ninguno.

## Las cuatro pestañas (plan original, sustituido: primero por tres destinos y el 2026-09-18 por seis)

| Pestaña | Qué muestra | Acción principal |
|---|---|---|
| **Inicio** | Vendido hoy · cobrado hoy · te deben · productos por agotarse | Botón grande **Nueva factura** |
| **Facturas** | Lista con chips: Todas · Pendientes · Pagadas · Cotizaciones | Toca una → detalle |
| **Inventario** | Existencias con su cantidad; en rojo las que están por debajo de `MinAmountQty` | **+** alta rápida |
| **Más** | Clientes · Saldos pendientes · Compras · Cuentas · Notas de crédito · Ajustes | — |

~~**Inicio no es un dashboard.**~~ Revertido por el usuario el 2026-09-18: el dashboard existe, en
su propio destino (Resumen), y la app abre en Facturas.

## El editor de factura, en detalle ✅

Es **la** pantalla de la app (`feature/invoice`). Una sola, con scroll, en este orden:

```
┌─ Cliente ───────────────  toca → buscador; vacío = "Consumidor final"
├─ Líneas ────────────────  cada una: nombre · cant ± · precio · total
│    + Agregar producto     → buscador de warehouse / alta rápida
├─ Totales ───────────────  subtotal · ITBIS 18 % · descuento · TOTAL (grande)
│    el % es tocable ──────  hoja con la tasa editable, prellenada con la del negocio
│    moneda ───────────────  DOP · USD · EUR   + tasa de cambio (oculta si es DOP)
├─ Cobro ─────────────────  Efectivo | Transferencia | Tarjeta  + cuenta destino
│    Faltante: $ 0.00      ← si > 0, la factura nace "Pagos Pendientes"
├─ (plegado) Comprobante ──  NCF sí/no + tipo · RNC
└─ Vencimiento (si queda saldo) ── sin fecha · 7 · 15 · 30 días (plan de cuotas: pendiente)
```

Cómo quedó (2026-09-16):

- **El borrador vive en Room** y se guarda en cada cambio; uno por tipo (factura y cotización
  no se pisan). *Atrás* con líneas escritas pregunta: seguir, salir guardando o descartar.
- **Productos y clientes** se buscan en pantallas propias (`ProductPickerScreen`,
  `ClientPickerScreen`); elegir escribe en el borrador y vuelve. **Necesitan red** por ahora.
- Tocar una línea abre una hoja con cantidad, precio y descuento (en la moneda del documento;
  se guardan en base). Vender más de lo que hay **avisa** ("Hay 3") pero deja.
- La tasa de impuesto y el tipo (`with_tax` · `included` · `no_tax`) se cambian en una hoja
  desde los Totales. La moneda, con chips y la tasa de cambio al lado.
- **Guardar no espera a la red:** pasa el borrador a la **cola de envíos** y abre el detalle,
  que muestra "Enviando…" o "Guardada en el teléfono" hasta que llega → guía 02.

Reglas heredadas del rediseño del POS (`PayBille_POS/CLAUDE.md`, 2026-09-01):

- **Lo secundario va plegado**, y **se despliega solo si ya trae valor**. NCF, RNC y notas casi
  nunca se tocan; ocupar pantalla con ellos es el error que se corrigió en el editor de cuentas.
- El **título de la pantalla es dinámico**: "Nueva factura" / "Nueva cotización" / "Nota de
  crédito". Sale del parámetro `kind` del `Screen`, **disponible desde el primer frame** — ver el
  título cambiar delante es un fallo que ya se cometió una vez en el POS.
- El botón de guardar es **fijo abajo**, siempre visible, con el total escrito dentro:
  `Cobrar $ 1,250.00`.
- **La tasa de impuesto se toca desde los Totales, no desde un ajuste.** Se enseña siempre junto
  al importe (`ITBIS 18 %`) porque es parte de la cifra que el cliente va a pagar; esconderla en
  un plegado obliga a abrirlo solo para comprobar que está bien. Cambiarla recalcula el total
  delante del usuario → [08](08-reglas-de-negocio.md) §1.
- **La moneda vive al lado del impuesto, y por la misma razón:** las dos deciden la cifra que se
  va a cobrar. Cambiarla **repinta todas las líneas**, no solo el total — si el usuario ve el
  total en euros y los precios en pesos, no se fía de ninguno de los dos.
- **Con moneda extranjera, el total del botón lleva el código**: `Cobrar USD 25.00`. Y debajo, en
  pequeño, el equivalente en pesos. Es la cifra que va a quedar guardada, y ocultarla es
  prepararle una sorpresa al usuario cuando abra el POS → [08](08-reglas-de-negocio.md) §11.

## El detalle de factura ✅ (`feature/detail`)

```
Barra:     ← Factura #CO2026…                [descargar] [compartir]
HERO:      la factura (HTML generado en el teléfono) en una hoja A4, hasta 420 dp de ancho
           (toca → InvoiceViewerScreen, con scroll y zoom)
           [Compartir]  (lleno)
           [Descargar PDF]  (contorno)
Cobro:     estatus · Total · Pagado · SALDO PENDIENTE · "Vence en 3 días" / "Vencida hace 5"
           [Registrar pago]  ← solo si hay saldo
           cuotas, si las hay
Abonos:    método · fecha · referencia · importe
Detalle:   cliente · fecha · NCF · líneas · subtotal/impuesto/total · cómo se cobró
```

- Se abre desde una fila de Facturas (`saleId`), desde un aviso de vencimiento, o **al guardar en
  el editor** (`localId`): entonces espera en la cola y, cuando el envío llega, carga la factura
  y su factura sola.
- **La factura la genera el teléfono** con `GET ventas/factura/{id}/data` (guardado en
  `cached_payloads`) y el diseño de la tienda → [06](06-componentes-ui.md). Se pinta al instante
  con lo guardado; sin red y sin copia, lo dice y ofrece reintentar. Compartir y Descargar generan
  el PDF en ese momento (menos de un segundo) con lo que se ve.
- **Compartir** abre la hoja del sistema (Android: `FileProvider` + `ACTION_SEND`; iOS:
  `UIActivityViewController`). **Descargar**: Android 10+ lo guarda en *Descargas*; Android 9 o
  menos, por la hoja de compartir; iOS abre el selector de *Archivos*.
- **Registrar pago:** monto (prellenado con el saldo), método, cuenta y referencia. Lo
  recalcula el servidor; tras pagar se relee la factura, la fila del Inicio y los avisos.
- Tras un abono se vuelven a pedir los datos: la factura enseña el saldo nuevo enseguida.
- Anular y nota de crédito: pendientes (fase 5).

## Dónde pagar (editor) ✅

Sección del editor bajo "Cobro" en cuanto la factura queda debiendo, y en toda cotización
(`InvoiceDraft.asksForPayment`). Casillas (`PbCheckRow`) con las cuentas que pueden recibir
transferencias (Room: activas, no `Caja`, con número), "Agregar cuenta" (abre el editor de
cuentas) e "Instrucciones de pago" (hasta 400 caracteres). La factura siguiente empieza con lo
último elegido. Se manda como `Sales.PaymentAccounts`/`PaymentNote` → [03](03-modelo-de-datos.md).

## Catálogo en línea ✅

`CatalogScreen`, desde Productos (icono de tienda en la cabecera) y desde Mi perfil. Arriba, el
enlace (`CatalogLink`, web en `paybille.webBaseUrl`) con "Compartir enlace" (hoja del sistema) y
"Ver como cliente" (navegador). Si el nombre de la tienda tiene caracteres que la API no admite
(un punto, un `&`), no hay enlace y se dice cuáles. Debajo, el inventario de Room con una casilla
por producto (Todos · En el catálogo · Fuera); marcar necesita red. Cambiar el nombre de la tienda
**cambia el enlace**: los que ya se compartieron dejan de abrir.

La vitrina es del POS (`pages/catalogo/[storeName].vue`): categorías, y marca, color, "Disponibles"
y orden como píldoras grises rellenas (la elegida en tinta, no en primario), panel de opciones,
conteo de resultados, "Agotado" y compartir.

## Avisos de vencimiento y cobro ✅

Avisos **locales** del teléfono (sin servidor), a partir de `accountdocs/get` con saldo:

| Cuándo | Aviso |
|---|---|
| Día antes, 9:00 | "Mañana vence la factura #N" |
| El día, 9:00 | "Hoy vence la factura #N" |
| 3 días después, 9:00 | "Cobra la factura #N" |
| Ya vencidas al sincronizar | Un solo resumen mañana a las 9:00 ("Tienes N facturas vencidas") |

- Hora en la zona del negocio. Se reprograman al abrir la app, al refrescar "Todas" y tras un
  abono (`ReceivablesRepository` → `ReminderPlanner` → `ReminderScheduler`).
- Android: WorkManager (sobrevive a reinicios; puede llegar unos minutos tarde) y permiso
  `POST_NOTIFICATIONS` en Android 13+. iOS: `UNUserNotificationCenter`, máximo 60 pendientes.
- Tocar el aviso abre el detalle de esa factura (`NotificationRouter`).
- Cerrar sesión los cancela.

## Reglas de navegación

1. **`navigator.push()` para avanzar, `pop()` para volver.** `replaceAll()` solo para cambiar de
   raíz, y la raíz la cambia la sesión, no un botón.
2. **Nada de navegación por rol, y nada de ocultar por rol.** El POS redirige según `rol.Name`
   porque reparte cinco perfiles entre varios empleados; **aquí hay un solo usuario, el dueño**, y
   todas las pantallas están siempre disponibles → [08](08-reglas-de-negocio.md) §8.
3. **Un flujo de creación no deja la pantalla hasta confirmar.** Si el usuario retrocede con
   líneas escritas, se le pregunta (`NavigationBackHandler`). El borrador se guarda igual, pero
   hay que avisar (el POS hace lo mismo con `useFormStore().unSavedChanges`).
4. **Los parámetros de un `Screen` son ids, no objetos.** Voyager los guarda al pasar la app a
   segundo plano; un objeto grande (o una contraseña) no debe ir ahí.
5. **Deep links** (`invoicer://factura/{id}`) para abrir una factura desde una notificación o un
   mensaje. Requieren `expect`/`actual` (intent-filter en Android, URL scheme en iOS): se montan
   cuando exista el detalle de factura.
