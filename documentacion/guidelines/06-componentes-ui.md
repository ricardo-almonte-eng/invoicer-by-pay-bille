# 06 — Componentes de interfaz

Equivalencias con los `Custom*` del POS (`PayBille_POS/components/Custom/`), y lo que hay que
construir. **Antes de crear un componente nuevo, mira si ya está aquí.**

## Primitivas — `core/designsystem/components/`

Componentes **propios** sobre `compose.foundation`, sin Material. Todos leen color y tipografía de
`PbTheme`. **✅ = ya existe.**

| Componente | Equivale a (POS) | Qué hace |
|---|---|---|
| ✅ `PbText` | `<CustomLabel>` | `BasicText` que hereda `body`/`ink` del tema |
| ✅ `PbIcon` | `<CustomIcon>` | Glifo de la fuente Material Symbols (`PbSymbols.*`), `size` 14/16/20/24, `tint`, `filled` |
| ✅ `PbButton` | `.BtnMain` / `.BtnBicolor` | `variant: Fill · Outline · Ghost · Danger · Accent`, `loading`, `leadingIcon`. 48 dp. El indicador va dentro y el botón no cambia de tamaño |
| ✅ `PbTextField` | `<CustomInput>` | Rótulo encima en mayúsculas (con `*` si `required`), icono, error, `isPassword` con mostrar/ocultar. 52 dp. Foco: borde 2 dp `primary` + fondo `primaryTint`, como el POS |
| ✅ `PbOverline` | rótulo del diseño | "USUARIO", "TOTAL": `overline` + `muted`, pone el texto en mayúsculas. Para campos sueltos, secciones y grupos |
| ✅ `PbCard` | `<CustomCard>` | La "isla": `island`, borde 1 dp, radio 12, padding 20. **Sin sombra** |
| ✅ `PbBanner` | `<MessageBox>` (en línea) | `tone: Error · Success · Offline`. Anuncia el cambio al lector de pantalla |
| ✅ `PbListRow` | fila del `DataGridView` | Título, detalle, icono y chevron. 56 dp mínimo. `enabled = false` la apaga; `trailingText` sustituye al chevron ("Próximamente") |
| ✅ `PbSpinner` | `<CustomLoading>` | Indicador circular. Va **dentro** de lo que carga |
| ✅ `PbScreen` | `.page` | Lienzo, barras del sistema + teclado, scroll, ancho máximo 440 dp, `centered` |
| ✅ `PbLogo` / `PbBrand` | logo | `PbLogo(height)`: logo de Invoicer (elige el PNG pequeño hasta 22 dp); en oscuro, inversión que conserva el tono. `PbBrand`: logo grande + bajada, para el login. La cabecera usa el **isotipo** |
| ✅ `PbIconButton` | botón de icono | 44 dp, `contentDescription` obligatorio, `onClick = null` = inerte |
| ✅ `PbTopBar` | `<AppBar>` | `navigation` · `title` · `actions`. Sin fondo propio |
| ✅ `PbAppBar` | barra superior del POS | `leading` · `center` · `actions`, con el centro **centrado en la pantalla** (no en el hueco). Cabecera fija de `MainScreen` |
| ✅ `PbTabRow` | `<Tabs>` | Pestañas de texto, alineadas a la izquierda y desplazables. El subrayado **se desliza** a la pestaña activa |
| ✅ `PbTag` | `<CustomTags>` | `tone: Success · Warning · Neutral · Danger · Info`. No pulsable |
| ✅ `PbEmptyState` | — | Icono en círculo + título + frase + acción opcional |
| ✅ `PbBottomBar` | menú lateral del POS | Barra inferior: isla con borde superior, icono relleno + acento en el destino activo y **barra de 3 dp arriba que se desliza** entre destinos, respeta la barra de gestos. Caben **seis** a 360 dp: etiqueta de una palabra, `caption` en los dos estados |
| ✅ `PbFab` | — | Botón flotante de 56 dp (`btnFill`), **sin sombra**: lo separa un anillo de 2 dp del color del lienzo. Las listas reservan `PbFabClearance` al final |
| ✅ `PbListCard` | fila del `DataGridView` | **Cada fila de lista es su propia isla**: borde de 1 dp, radio 12, 10 dp entre tarjetas (`PbListCardGap`), 16 dp a los lados. `accent`: franja de 4 dp a la izquierda para lo que pide atención, siempre con texto o etiqueta al lado. Sustituye a las filas pegadas con línea (pedido del usuario, 2026-09-27) |
| ✅ `PbItemRow` | fila del `DataGridView` | Sobre `PbListCard`: icono (o `leading`), título, subtítulo y lo de la derecha (`PbRowAmount`, `PbTag`). `dimmed` para lo inactivo |
| ✅ `PbStatusBanner` / `PbSpinnerRow` / `PbListHeader` | — | Aviso con "Reintentar", carga centrada y título pequeño de grupo. `syncStatusItem` (`core/ui`) elige cuál según `SyncState` |
| ✅ `PbSearchField` | buscador del `DataGridView` | Caja de búsqueda de las listas; **no** pide el foco al abrir |
| ✅ `PbStackHeader` | `<AppBar>` con ← | Cabecera de las pantallas apiladas: ← · título · acciones, y `below` (chips) dentro de la isla |
| ✅ `PbFormScreen` | modales `Create*` del POS | Formulario de alta/edición: scroll, botón **fijo abajo** que sube con el teclado, hoja "¿Descartar los cambios?" si hay cambios. `overlay` para sus propias hojas |
| ✅ `PbStatTile` | tarjetas KPI del dashboard | Rótulo, importe grande (`amountTitle`) y pista. Borde 1 dp |
| ✅ `PbTotalBox` | caja TOTAL del diseño | Rótulo + `amountLarge` sobre `surface2` con borde. El total del editor y del detalle |
| ✅ `PbAreaChart` | `DashboardChart` (lightweight-charts) | Gráfico de área con `Canvas`, colores del tema, `description` para el lector de pantalla |
| ✅ `PbMeterBar` | barras de "más vendidos" | Barra proporcional 0..1 |
| ✅ `PbSheet` | `<ToolsModals>` | Hoja inferior sobre la pantalla: **sube y baja deslizándose** (el velo se funde), cabecera fija con título `heading` y línea de 1 dp, **como mucho el 85 % del alto** y el contenido desplaza dentro. *Atrás* y tocar fuera la cierran |
| ✅ `PbSelectField` | `<CustomSelect>` | Parece un campo (rótulo, 52 dp, borde) y abre una hoja: nunca un desplegable |
| ✅ `PbOptionSheet` | select + "+" del POS | Hoja con buscador, fila "Sin …" y alta rápida ("Crear “texto”"). Para catálogos |
| ✅ `ImageField` (`core/ui`) | modal de imagen del POS | Vista previa + "Tomar foto" · "Galería" · "Quitar". La foto nueva se guarda en el estado y se sube al guardar |
| ✅ `BankLogo` (`core/ui`) | logos de `Models/Bancos.js` | Logo por `cuentas.BankName` (tolera acentos, mayúsculas y "Banco …") sobre placa blanca (`logoPlate`: los archivos traen fondo blanco). Sin logo conocido, el icono de respaldo. `PbItemRow` y `PbListRow` tienen `leading` para ponerlo |
| ✅ `RemoteImage` (`core/ui`) | `<img>` | Imagen por URL con hueco mientras carga o sin red. `RemoteImageLoader`: cliente **sin token** (la URL puede ser de otro servidor) y caché en memoria de 40 |
| ✅ `PbNumberField` | — | Teclado decimal, guarda su texto mientras se escribe, acepta coma. `prefix`/`suffix` (`$`, `%`). **Para todo importe** |
| ✅ `PbChip` / `PbChipGroup` | — | Píldora seleccionable: la elegida, rellena de `primary`; el grupo baja de línea si no cabe |
| ✅ `PbSwitchRow` | `<CustomCheckBox>` | Interruptor propio con su texto; toda la fila es pulsable |
| ✅ `PbStepper` | — | Cantidad con − y +, botones de 44 dp |
| ✅ `PbSection` | — | Isla con cabecera (icono, **rótulo**, algo a la derecha) |
| `PbMoneyField` | — | Teclado numérico, formato al salir, `amount`. **Para todo importe** |
| `PbTextArea` | `<CustomTextarea>` | |
| `PbSwitch` / `PbCheckbox` | `<CustomCheckBox>` | `Switch` para ajustes; `Checkbox` solo en listas |
| `PbConfirm` | `<CustomDialog>` | Confirmación destructiva |

### Lo que sustituye al `DataGridView`

En el POS, **69 pantallas** consumen `<CustomDataGridView>`: tabla + filtros + paginación +
búsqueda + acciones por fila. En móvil no hay tabla. Su equivalente es:

```
PbListScreen
  ├── PbSearchBar        búsqueda con espera (debounce) → like sobre Room y la API
  ├── PbFilterChips      4 filtros como máximo, en línea y desplazables
  ├── LazyColumn         lee de Room; al llegar al final pide la página siguiente
  │     └── PbListRow    tres datos y un estatus
  └── PbEmptyState
```

**Una fila muestra tres cosas y un estatus. Ni una más.** Para una factura: cliente · total ·
fecha, y el `PbTag` de estatus. Todo lo demás está en el detalle.

## Piezas de negocio — `feature/<x>/presentation/components/`

| Componente | Qué hace |
|---|---|
| `LineaFactura` | Una línea del borrador: nombre, cantidad ± , precio, total. Deslizar para borrar |
| `SelectorProducto` | Buscador sobre `warehouse` + alta rápida si no existe |
| `SelectorCliente` | Buscador `like` sobre `clients` + alta rápida (nombre y teléfono) |
| ✅ `SaleRow` (`feature/sales/presentation`) | Tarjeta de venta: **cliente** (título) y total; número · fecha · NCF y estatus. Con `debt` (saldo de `receivables`): bloque "DEBE" con el saldo, la fecha de vencimiento y los días que faltan o que pasaron, etiqueta **Vencida** (rojo) o **Por vencer / Vence hoy** (naranja) y la franja del mismo color. "Por vencer" = hoy o en `DUE_SOON_DAYS` (3) días o menos (`DueState.urgency()`) |
| `ResumenTotales` | Subtotal · impuesto · descuento · **Total**. Lee de `core/billing/Tax.kt`, no calcula nada |
| `PanelCobro` | Efectivo / transferencia / tarjeta + cuenta destino + faltante |
| ✅ `SaleStatus.label()` | Texto y tono del estatus: Pagada (`Success`), Pendiente (`Warning`, naranja), Cotización (`Neutral`), Anulada (`Danger`); otro valor, tal cual en `Neutral` |
| `ListaAbonos` | Historial de pagos de un documento con su saldo |
| `VistaDocumento` | La factura "en papel" → hoy es `HtmlView` + la plantilla (ver abajo) |

## Reglas de componente

1. **Ninguno llama a la red ni a Room.** Reciben datos y lambdas. Quien carga es el
   `ScreenModel`.
2. **Ningún color literal.** Todo por `PbTheme.colors`.
3. **Todo lo pulsable mide 44 dp o más**, aunque el dibujo sea más pequeño.
4. **`clip(shape)` antes de `clickable`**, o el velo de `PbPressIndication` se sale de las
   esquinas.
5. **Nada de sombras ni elevación.** Borde de 1 dp → [05](05-diseno-y-tema.md).
6. **Textos que lee el lector de pantalla:** iconos decorativos con `contentDescription = null`;
   títulos con `semantics { heading() }`; errores de campo con `semantics { error(...) }`.
7. **Pensado para 360 dp y letra grande:** dos botones con texto no van lado a lado; se apilan.

## La factura: HTML generado en el teléfono (2026-10-06)

El POS imprime en térmica (ESC/POS + `print-agent`). **Aquí el "imprimir" es compartir**, y la
factura **la genera el teléfono**. Antes era el PDF que armaba el servidor con Puppeteer
(`GET ventas/factura/{id}`): tardaba, fallaba al mostrarse y, como `savePDF` lo guardaba por
nombre, tras un abono seguía con el saldo viejo. Ahora el servidor solo da **datos**.

```
GET ventas/factura/{id}/data ──► cached_payloads "invoice-doc:{id}"   (offline first)
GET invoiceConfig/{IdMarket}  ──► cached_payloads "invoice-config"     (viene también dentro de /data)
logo de la tienda (bytes)     ──► cached_payloads "invoice-logo"       ({ url, dataUri })
                     │
   InvoiceHtml.render(plantilla, datos, config, assets)   ← puro, con pruebas
                     │
   HtmlView (expect/actual: WebView · WKWebView)  → vista previa y visor completo
   DocumentPlatform.htmlToPdf → InvoicePdfStore   → compartir / descargar
```

- **Plantilla:** `composeResources/files/invoice_template.html` (diseño del ejemplo de Bookipi con
  la identidad de PayBille). La rellena `MiniTemplate`, un Mustache mínimo: `{{campo}}` escapado,
  `{{{campo}}}` sin escapar, `{{#lista}}…{{/lista}}`, `{{^campo}}…{{/campo}}`. Si la tienda guardó
  su propia plantilla (`invoiceconfig.TemplateHtml`, texto), se usa esa.
- **Todo embebido en `data:`**: la tipografía Google Sans Flex (`@font-face`), el isotipo del pie
  y el logo de la tienda. La factura se ve y se convierte en PDF **sin red**. El logo se pide
  primero por `https://` (hay logos guardados como `http://`, que Android no deja cargar).
- **Es papel:** fondo blanco y tinta oscura también en tema oscuro. Se adapta al ancho: en el
  teléfono (<560 px) se apila y las columnas Precio/Cant. pasan bajo el nombre.
- **PDF en Android** (`AndroidHtmlPdf`): WebView fuera de pantalla a 794 px CSS (A4 a 96 dpi),
  dibujado en `PdfDocument`; corta las páginas por los bordes de `tr` y `.blk` (los mide con JS),
  nunca a mitad de una línea. Necesita `WebView.enableSlowWholeDocumentDraw()` antes del primer
  WebView (`WebViews.prepare()`). El texto sale rasterizado (no seleccionable).
- **PDF en iOS** (`IosHtmlPdf`): `UIPrintPageRenderer` sobre un `WKWebView`. ⚠️ Sin verificar
  en Xcode.
- La firma se dibuja con el dedo (`SignaturePad`) y se guarda como **SVG** (`SignatureSvg`): al
  pintarla se rehace con solo el `viewBox` y los `d` de los trazos (nada de `<script>`).
- **No reutilices `css/Receipt.css` ni `css/Factura.css`** del POS: están calibrados para papel
  térmico de 58/80 mm. La factura de esta app es A4 y es un documento distinto.

| Componente | Dónde | Para qué |
|---|---|---|
| `HtmlView(html, interactive)` | `core/platform` (expect/actual) | Visor HTML del sistema. `interactive = false`: sin scroll ni zoom; el toque es del Compose de encima |
| `SignaturePad` | `feature/document/presentation` | Firmar con el dedo; puntos en el sistema del `viewBox` (600 × 200) |
| `PbColors.paperInk` · `InvoiceAccentOptions` | `Colors.kt` | Tinta sobre papel (igual en oscuro) y los colores de acento que se pueden elegir |

## Añadidos 2026-09-27 (4)

| Componente | Dónde | Para qué |
|---|---|---|
| `PbCheckRow` | `PbControls.kt` | Fila con casilla para elegir **varios** (cuentas de "Dónde pagar", productos del catálogo). Toda la fila es pulsable, `Role.Checkbox`; casilla de 24 dp rellena del primario |
| `NotificationsBell` | `feature/notifications/presentation` | La campana de Facturas con punto rojo (borde `island`) si hay algo urgente; el número va en la descripción |

