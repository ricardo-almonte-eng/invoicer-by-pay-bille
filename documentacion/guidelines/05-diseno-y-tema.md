# 05 — Diseño y tema

La identidad visual es **la misma de PayBille POS**, portada a React Native. Los valores de abajo
son copia literal de `PayBille_POS/css/Colors.css`; si allí cambian, aquí también.

## La regla que define el aspecto

> **Islas blancas sobre lienzo azulado, separadas 10–12 px y delimitadas por un borde de 1 px.
> Nunca sombra.**

La jerarquía la dan **el borde y el relleno**, no la profundidad. En React Native esto significa:

- **Nada de `elevation` en Android ni `shadow*` en iOS.** Si necesitas separar dos superficies,
  `borderWidth: 1, borderColor: theme.outline`.
- El fondo de pantalla es `canvas`; las tarjetas son `island` con borde.

Y su corolario: **solo dos botones llenos por pantalla** (el primario y, como mucho, el de acento).
Todo lo demás es contorno.

## Los dos azules

No los confundas, es el error más común al portar:

| Token | Valor (claro) | Papel |
|---|---|---|
| `primary` | `#16426f` | **Acento**: selección, foco, pestaña activa, enlaces, iconos destacados |
| `btnFill` | `#101A42` | **Relleno de botón primario**. Más oscuro a propósito. Siempre con texto blanco encima; **nunca** como color de texto |

## `theme/tokens.ts`

```ts
export const light = {
  // Texto, de más a menos peso
  ink: '#101A42', ink2: '#414D75', muted: '#5B6689', muted2: '#8A94B2',
  disabledFg: '#AEB8D0',

  // Acento
  primary: '#16426f', primaryHover: '#1B5088', primaryActive: '#0F3053',
  primary10: '#E4ECF3', primaryBorder: '#BFD2E3', primaryTint: '#F4F8FB',

  // Relleno de botón primario
  btnFill: '#101A42', btnFillHover: '#1C2A5E', btnText: '#FFFFFF',

  // Naranja RESERVADO: en el diseño original solo lo usan Imprimir y Salir.
  // No lo repartas por la interfaz.
  accent: '#F97A2B', accentHover: '#E56A1D', accentActive: '#CC5C15',
  accent10: '#FFEEE2', accentText: '#D2601A', accentBorder: '#F2D2C0',

  success: '#0B7A54', successHover: '#096A48', success10: '#EAF7F1', successBorder: '#C6E7D8',
  error: '#E83B3B', errorHover: '#C82F2F', error10: '#FFF5F5', errorBorder: '#FFCACA',
  warning: '#FFEB3B',

  // Superficies
  canvas: '#EEF2F9', island: '#FFFFFF', surface2: '#F5F7FC', surface3: '#F0F3F9',
  surfaceSubtle: '#FBFCFE', rowHover: '#F7F9FD',

  outline: '#c9d6e3', outlineStrong: '#CFD7EA', outlineStronger: '#C9D2E4',
  inputContainer: '#e5e9f3',
  backdrop: 'rgba(16, 26, 66, 0.35)',
};

export const dark = {
  ink: '#E6EAF5', ink2: '#C3CBE0', muted: '#A3AEC9', muted2: '#7D88A6',
  disabledFg: '#5C678A',

  primary: '#649CD8', primaryHover: '#7DAEE2', primaryActive: '#4E86C4',
  primary10: '#16283C', primaryBorder: '#2A4460', primaryTint: '#121D2B',

  // En oscuro el relleno NO puede ser el marino: se fundiría con el lienzo.
  btnFill: '#2C4A73', btnFillHover: '#375C8E', btnText: '#FFFFFF',

  accent: '#F97A2B', accentHover: '#E56A1D', accentActive: '#CC5C15',
  accent10: '#3A2416', accentText: '#F9A76B', accentBorder: '#5C3A22',

  success: '#12A06E', successHover: '#0E8C5F', success10: '#14301F', successBorder: '#1E4B33',
  error: '#F0605F', errorHover: '#D64B4A', error10: '#3A1C1C', errorBorder: '#5C2A2A',
  warning: '#FFD54F',

  canvas: '#0B0F1C', island: '#141A2C', surface2: '#1B2237', surface3: '#212942',
  surfaceSubtle: '#101524', rowHover: '#1C2439',

  outline: '#262E45', outlineStrong: '#354061', outlineStronger: '#414D73',
  inputContainer: '#1B2237',
  backdrop: 'rgba(4, 7, 16, 0.6)',
};

export const radius  = { sm: 8, md: 10, lg: 12, xl: 14, pill: 999 };
export const space   = { 1: 4, 2: 6, 3: 8, 4: 10, 5: 12, 6: 16 };
export const icon    = { sm: 14, md: 16, lg: 20, xl: 24 };
export const control = { h: 48, hSm: 36 };   // ← ver nota
```

### Por qué las alturas de control cambian

El POS usa `--control-h: 44px` y `--control-h-sm: 29px`, medidas de ratón. **En táctil, 29 px no
se acierta.** El mínimo son 44 pt (Apple HIG) / 48 dp (Material). Por eso:

- alto de control **48**, y **nunca** por debajo de **44** para nada pulsable;
- `hSm: 36` solo para *chips* y etiquetas **no pulsables**.

Los tamaños de icono y los radios **sí se conservan** tal cual.

### Movimiento

```
ease: cubic-bezier(0.2, 0, 0, 1)   → Easing.bezier(0.2, 0, 0, 1)
control 140ms · fila 120ms · modal 180ms
```

> *"Nada rebota, nada escala. Sin desplazamientos de más de 4 px. El usuario debe percibir
> respuesta, no animación."*

Solo se animan **color de fondo, borde y opacidad**. Nada de `spring` con rebote, nada de `scale`
en los botones. La excepción razonable en móvil es la **transición de pantalla** de expo-router,
que se deja en la nativa por defecto.

## Tema claro / oscuro

`ThemeProvider` + `useTheme()`. Modo `system` por defecto (`useColorScheme()`), con opción manual
persistida en AsyncStorage.

Ojo con lo que ya está resuelto en el POS y es fácil de romper: **el lienzo oscuro no puede ser
marino** porque el relleno de botón lo es y se fundirían. Por eso en oscuro el lienzo baja a
`#0B0F1C`, el relleno de botón sube a `#2C4A73` y el acento se aclara a `#649CD8`. No los
unifiques.

**Cero colores literales en los componentes.** Todo sale de `useTheme()`. Si necesitas un color que
no existe, añádelo a **los dos** bloques.

## Tipografía

La fuente del sistema es **Google Sans Flex** (paquete `@fontsource-variable/google-sans-flex`,
SIL Open Font License 1.1 — la versión abierta, no la Google Sans propietaria).

### ⚠️ Lo que NO se puede copiar tal cual

El POS sirve la fuente como **`.woff2` variable** con `font-weight: 1 1000`. En React Native:

1. **`woff2` no se carga.** `expo-font` acepta **`.ttf` / `.otf`**.
2. **Los ejes variables no se controlan** desde `StyleSheet`.

Así que hay que **registrar una familia por peso**, con instancias estáticas TTF:

```ts
useFonts({
  'GoogleSans-Regular':  require('../assets/fonts/GoogleSansFlex-Regular.ttf'),
  'GoogleSans-Medium':   require('../assets/fonts/GoogleSansFlex-Medium.ttf'),
  'GoogleSans-SemiBold': require('../assets/fonts/GoogleSansFlex-SemiBold.ttf'),
  'GoogleSans-Bold':     require('../assets/fonts/GoogleSansFlex-Bold.ttf'),
});
```

Y en los estilos se pone `fontFamily: 'GoogleSans-SemiBold'`, **no** `fontWeight: '600'`. Mezclar
las dos cosas en Android produce negritas sintéticas deformadas — exactamente el problema que en
web se resolvió declarando `font-weight: 1 1000`.

Cómo obtener los TTF → [13-recursos-de-marca.md](13-recursos-de-marca.md).

### Tamaños

Los del POS son medidas de escritorio (12–13 px en botones y celdas). **En móvil suben un punto**:
14 px de base, 16 px en campos de formulario (por debajo de 16, iOS hace zoom al enfocar un input
en WebView y el usuario percibe la app como pequeña). Los **iconos no cambian**: 14 / 16 / 20 / 24.

**Todo número —importe, cantidad, hora, subtotal— lleva `fontVariant: ['tabular-nums']`**, para que
las columnas de dinero no bailen. Es la regla del POS y aquí importa más, porque la pantalla es
estrecha.

## Iconos

El POS usa **Material Symbols Rounded** subseteada a ~107 iconos, generada por
`scripts/generar-iconos.py` desde `scripts/iconos-map.json`.

Para la app, y por este orden:

1. **`@expo/vector-icons` → `MaterialIcons`** para el MVP. Cubre los nombres de Material y no hay
   que mantener ninguna fuente. Contra: es la variante *Filled*, no *Rounded*, así que se ve
   ligeramente más dura que el POS.
2. Cuando la marca importe, generar **`material-symbols-rounded.ttf`** (subset) + su mapa de
   *codepoints* y cargarla con `createIconSet(glyphMap, 'MaterialSymbolsRounded', ttf)`. El
   subset y el mapa ya existen en el POS: se reutiliza `scripts/iconos-map.json`.

**Material no trae iconos de marca**: no hay logo de WhatsApp. El POS mapea `i-whatsapp → chat`.
Como aquí compartir por WhatsApp es una acción principal, usa el icono `share` del sistema y deja
que sea el diálogo nativo el que muestre la marca.

## Qué revisar en cada cambio visual

El POS se verifica en `1920x1080`, `1366x768` e `iPhone 17 Pro Max`. Aquí:

| Caso | Por qué |
|---|---|
| **Pantalla pequeña** (iPhone SE / 360 dp de ancho) | Es donde se rompen las filas de importes |
| **Pantalla grande** (iPhone Pro Max / tablet) | Que el contenido no se estire feo |
| **Tema oscuro** | Todo cambio de color |
| **Tipografía grande del sistema** (accesibilidad) | Los usuarios mayores la activan |
| **Teclado abierto sobre el formulario** | La factura se teclea con el teclado tapando media pantalla |

**Esa comprobación la hace el usuario**, no Claude → [12](12-flujo-de-trabajo.md).
