# 05 — Diseño y tema

La identidad visual es **la misma de PayBille POS**, portada a Compose Multiplatform. Los valores
son copia literal de `PayBille_POS/css/Colors.css`; si allí cambian, aquí también.

**De dónde sale:** el POS web se portó del Claude Design **"POS Loteria – Aproximacion"**
(proyecto `806334b5-…` en claude.ai/design, sección *1c · Design System*). De ese diseño se toman
**las reglas** (islas, bordes, rótulos, alturas, movimiento, dos botones llenos) y **no sus
colores**: su primario `#3A46E0` es, en el POS y aquí, `primary #16426f`, y el relleno de botón
sigue siendo la tinta (`btnFill`). Su fuente (Plus Jakarta Sans) tampoco: se queda Google Sans
Flex, la del POS. Decisión del usuario, 2026-09-27.

Código: `composeApp/src/commonMain/kotlin/com/paybille/invoicer/core/designsystem/theme/`.

## La regla que define el aspecto

> **Islas blancas sobre lienzo azulado, separadas 10–12 dp y delimitadas por un borde de 1 dp.
> Nunca sombra.**

La jerarquía la dan **el borde y el relleno**, no la profundidad. En Compose esto significa:

- **Nada de `Modifier.shadow()` ni elevación.** Para separar dos superficies,
  `.border(PbControl.border, PbTheme.colors.outline, shape)`.
- El fondo de pantalla es `canvas`; las tarjetas son `island` con borde (`PbCard`).
- **Sin Material.** No se añade `compose.material3`: sus componentes traen elevación, ripple y
  rellenos que habría que desactivar uno por uno.

Y su corolario: **solo dos botones llenos por pantalla** (`Fill` y, como mucho, `Accent`). Todo lo
demás es `Outline` o `Ghost`.

## Los dos azules

No los confundas, es el error más común al portar:

| Token | Valor (claro) | Papel |
|---|---|---|
| `primary` | `#16426f` | **Acento**: selección, foco, enlaces, iconos destacados, el nombre "PayBille" |
| `btnFill` | `#101A42` | **Relleno de botón primario**. Más oscuro a propósito. Siempre con texto blanco encima; **nunca** como color de texto |

## Tokens — `Colors.kt` y `Tokens.kt`

`PbColors` (`LightPbColors` / `DarkPbColors`) tiene **todos** los tokens de `Colors.css`: tinta
(`ink`, `ink2`, `muted`, `muted2`, `disabledFg`), acento (`primary*`), relleno (`btnFill*`),
naranja reservado (`accent*`), semánticos (`success*`, `error*`, `warning`), superficies
(`canvas`, `island`, `surface2`, `surface3`, `surfaceSubtle`, `rowHover`), bordes (`outline*`),
`inputContainer` y `backdrop`.

**`Colors.kt` es el único archivo con colores literales.** Si necesitas uno que no existe,
añádelo a **los dos** bloques.

Único token que no está en `Colors.css`: **`onPrimary`** (`#FFFFFF`; oscuro `#0B0F1C`), el texto
sobre un relleno `primary` (chip seleccionado). En oscuro el primario es claro y el blanco no se lee.

```kotlin
object PbRadius    { sm = 8.dp;  md = 10.dp; lg = 12.dp; xl = 14.dp }
object PbSpace     { s1 = 4; s2 = 6; s3 = 8; s4 = 10; s5 = 12; s6 = 16; s7 = 20; s8 = 24; s10 = 32 }  // dp
object PbIconSize  { sm = 14.dp; md = 16.dp; lg = 20.dp; xl = 24.dp }
object PbControl   { h = 48.dp; hSm = 36.dp; minTouch = 44.dp; border = 1.dp; borderFocus = 2.dp
                     field = 52.dp; maxFormWidth = 440.dp }
```

Radios en uso: botón y fila `md` (10) · **isla, tarjeta, KPI, caja de total y campo `lg` (12)** ·
hoja `xl` (14) arriba · chip `pill`.

### Por qué las alturas de control cambian

El POS usa `--control-h: 44px` y `--control-h-sm: 29px`, medidas de ratón. **En táctil, 29 px no
se acierta.** El mínimo son 44 pt (Apple HIG) / 48 dp (Material). Por eso:

- alto de control **48**, y **nunca** por debajo de **44** para nada pulsable;
- `hSm: 36` solo para *chips* y etiquetas **no pulsables**.

Los tamaños de icono y los radios **sí se conservan** tal cual.

### Movimiento — `PbMotion`

```
ease: CubicBezierEasing(0.2f, 0f, 0f, 1f)   exit: CubicBezierEasing(0.3f, 0f, 1f, 1f)
control 140 ms · fila 120 ms · modal 180 ms · página 260 ms · indicador 260 ms · hoja 240 ms
PAGE_SLIDE_FRACTION = 0.25
```

> *"Nada rebota, nada escala. El usuario debe percibir respuesta, no animación."*

Los **controles** solo animan **color de fondo, borde y opacidad** (`animateColorAsState` con
`PbMotion`). Nada de `spring` con rebote, nada de `scale` en los botones.

**Tres desplazamientos permitidos** (pedidos por el usuario el 2026-09-27; sustituyen al "sin
desplazamientos de más de 4 px" del diseño), todos con la misma curva y sin rebote — sensación de
banca: corto, firme, sin adornos:

| Qué | Cómo |
|---|---|
| Cambio de destino (`MainScreen`) | Entra ¼ de ancho desde el lado al que se avanza + fundido (260 ms, `ease`); el viejo sale ¼ al otro lado con `exit` y se funde en la mitad del tiempo. La cabecera y el "+" no se mueven |
| Indicador de destino / pestaña | La barra de 3 dp (barra inferior) y el subrayado (`PbTabRow`) se deslizan al nuevo activo (260 ms) |
| Hojas (`PbSheet`) | Suben desde abajo (240 ms, `ease`) y bajan más rápido (180 ms, `exit`); el velo solo se funde |

**La respuesta al toque es `PbPressIndication`**, instalada por `PbTheme` como `LocalIndication`:
un velo del color de la tinta al 8 % que aparece y se va, en vez del ripple de Material. Se pinta
sobre el rectángulo del nodo, así que **el componente recorta (`clip(shape)`) antes de
`clickable`**.

### Reglas del Claude Design que se aplican

1. **Islas blancas sobre lienzo azulado, borde de 1 dp, nunca sombra.** También cada fila de una
   lista: tarjeta propia con 10 dp de separación (`PbListCard`), no filas pegadas con una línea.
2. **Rótulos en mayúsculas** (`PbOverline`) para nombrar campos, secciones y cifras; el texto de
   interfaz va en su tamaño normal. El título de una sección no grita: se nombra.
3. **Solo dos botones llenos por pantalla**; todo lo demás es contorno. El secundario (`Outline`)
   usa `ink2`; el destructivo (`Danger`) es contorno rojo claro con texto rojo, sin relleno.
4. **Chip seleccionado = relleno `primary` + `onPrimary`**; los demás, contorno.
5. **Campo en foco**: borde 2 dp `primary` + fondo `primaryTint`. Alto 52 (`PbControl.field`).
6. **El total que se cobra va en su caja** (`PbTotalBox`): rótulo "TOTAL" + `amountLarge` sobre
   `surface2` con borde. Se distingue por el relleno, no por el color.
7. **Todo número en cifras tabulares.**

## Tema claro / oscuro — `PbTheme`

```kotlin
PbTheme {                     // sigue al sistema: isSystemInDarkTheme()
    PbTheme.colors.canvas     // tokens de color
    PbTheme.typography.body   // estilos de texto
}
```

Ojo con lo que ya está resuelto en el POS y es fácil de romper: **el lienzo oscuro no puede ser
marino** porque el relleno de botón lo es y se fundirían. Por eso en oscuro el lienzo baja a
`#0B0F1C`, el relleno de botón sube a `#2C4A73` y el acento se aclara a `#649CD8`. No los
unifiques.

## Tipografía — Google Sans Flex

SIL Open Font License 1.1 (la versión abierta, no la Google Sans propietaria). Licencia en
`composeResources/files/licenses/`.

El POS sirve la fuente como **`.woff2` variable**. Compose no controla ejes variables de forma
portable, así que hay **cuatro TTF estáticos** instanciados del mismo archivo (receta en
[13](13-recursos-de-marca.md)) en `composeResources/font/`, registrados en una sola `FontFamily`:

```kotlin
FontFamily(
    Font(Res.font.google_sans_flex_regular,  FontWeight.Normal),
    Font(Res.font.google_sans_flex_medium,   FontWeight.Medium),
    Font(Res.font.google_sans_flex_semibold, FontWeight.SemiBold),
    Font(Res.font.google_sans_flex_bold,     FontWeight.Bold),
)
```

Cada peso tiene su archivo, así que `FontWeight` elige el TTF real y **no se sintetiza ninguna
negrita** (el problema que en React Native obligaba a usar `fontFamily` por peso). Aun así, **los
componentes no inventan pesos ni tamaños: usan `PbTheme.typography`.**

### Estilos — `PbTypography`

| Estilo | Tamaño / peso | Uso |
|---|---|---|
| `display` | 28 Bold | (ya no la usa la marca: el logo es imagen) |
| `title` | 20 SemiBold | Título de pantalla |
| `heading` | 17 Bold | Título de hoja y de la tarjeta del login (17/800 del diseño) |
| `subtitle` | 16 SemiBold | Título de fila |
| `body` / `bodyStrong` | 14 Regular / Medium | Texto general |
| `input` | 16 Regular | Dentro de los campos |
| `label` | 13 SemiBold | Etiqueta de campo (`.CustomInputNameLabel` del POS) |
| `caption` | 12 Regular | Ayudas y errores de campo |
| `overline` | 11 Bold, +0.12 em | **Rótulo** del diseño, siempre en mayúsculas con `PbOverline`: etiqueta de campo ("USUARIO"), título de sección/isla ("CLIENTE", "TOTAL"), cabecera de grupo, etiqueta de KPI |
| `button` | 15 Bold | Botones (15/800 del diseño; 700 es el peso más alto empaquetado) |
| `amount` / `amountTitle` / `amountLarge` | 14 Medium / 20 Bold / 28 Bold, **cifras tabulares** | **Todo** importe, cantidad u hora (`amountTitle`: total de una fila) |

Los tamaños del POS son de escritorio (12–13 px); **en móvil suben un punto**: 14 de base y 16 en
campos. Los **iconos no cambian**: 14 / 16 / 20 / 24.

**Todo número lleva `amount`** (`fontFeatureSettings = "tnum"`), para que las columnas de dinero
no bailen.

## Iconos — Material Symbols Rounded

Los mismos del POS, pero **dibujados con la fuente** (decisión del usuario, 2026-09-18), no con
vectores sueltos: `composeResources/font/material_symbols_rounded.ttf`, el TTF variable de Google
(`google/material-design-icons`, carpeta `variablefont`, Apache-2.0) reducido a
`wght 400 · GRAD 0 · opsz 24` con **solo el eje `FILL` variable** y **todos** los glifos (≈2 MB).

- `PbIcon(icon: PbSymbol, …, filled = false)`: `PbSymbol` es el **codepoint** del glifo, sacado del
  `cmap` de la propia fuente. `filled = true` es la variante rellena (el destino activo de la
  barra). En Android 7 (API 24–25) no hay ejes variables: el relleno no se ve, el color sí.
- El tamaño va en dp y **no crece con la letra grande del sistema** (como un vector).
- `PbSymbols` (`core/designsystem/theme/PbSymbols.kt`) tiene una constante por icono usado:
  `PbSymbols.ReceiptLong`, `PbSymbols.Add`… Es **generado**; receta en [13](13-recursos-de-marca.md).
- Ya no hay `ic_*.xml`: en `drawable` solo queda el isotipo.

**Material no trae iconos de marca**: no hay logo de WhatsApp. Para compartir, usa `share` y deja
que el diálogo nativo muestre la marca.

## Qué revisar en cada cambio visual

El POS se verifica en `1920x1080`, `1366x768` e `iPhone 17 Pro Max`. Aquí:

| Caso | Por qué |
|---|---|
| **Pantalla pequeña** (iPhone SE / 360 dp de ancho) | Es donde se rompen las filas de importes |
| **Pantalla grande** (iPhone Pro Max / tablet) | Que el contenido no se estire feo (`PbScreen` limita a 440 dp) |
| **Tema oscuro** | Todo cambio de color |
| **Tipografía grande del sistema** (accesibilidad) | Los usuarios mayores la activan |
| **Teclado abierto sobre el formulario** | La factura se teclea con el teclado tapando media pantalla |

**Esa comprobación la hace el usuario**, no Claude → [12](12-flujo-de-trabajo.md).
