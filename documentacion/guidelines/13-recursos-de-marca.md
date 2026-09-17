# 13 — Recursos de marca

Todo sale de `C:\repos\PayBille_POS`. Nada se rediseña.

## Logo

| Archivo en el POS | Tamaño | Uso aquí |
|---|---|---|
| `assets/img/LogoNotBG.png` | 88 KB | **El bueno**: sin fondo. Cabecera, splash, PDF |
| `assets/img/Logo.fw.png` | 77 KB | Con fondo. Solo si hace falta sobre lienzo claro |
| `assets/img/favicon.fw.png` | 58 KB | Base para el icono de la app |

```bash
mkdir -p assets/img
cp C:/repos/PayBille_POS/assets/img/LogoNotBG.png  assets/img/logo.png
cp C:/repos/PayBille_POS/assets/img/favicon.fw.png assets/img/icono-base.png
```

**El icono de la app hay que generarlo aparte**: Android e iOS piden PNG cuadrados y opacos
(1024×1024) con márgenes propios. Un favicon de web escalado se ve mal. Genera el icono desde el
logo sobre fondo `#101A42` (`ink`) — el marino de la marca — y deja el `adaptive-icon` de Android
con el logo centrado y sin fondo.

En `app.json`:

```json
{
  "expo": {
    "name": "Invoicer By PayBille",
    "slug": "invoicer-by-paybille",
    "scheme": "invoicer",
    "icon": "./assets/img/icono.png",
    "userInterfaceStyle": "automatic",
    "splash": { "image": "./assets/img/logo.png", "backgroundColor": "#101A42" },
    "android": {
      "adaptiveIcon": { "foregroundImage": "./assets/img/icono-adaptativo.png",
                        "backgroundColor": "#101A42" }
    }
  }
}
```

`userInterfaceStyle: "automatic"` es obligatorio para que el tema `system` de
[05](05-diseno-y-tema.md) funcione.

## Tipografía — Google Sans Flex

**Licencia:** SIL Open Font License 1.1. Es la versión que Google publicó abierta, **no** la Google
Sans propietaria de sus productos. Se puede empaquetar en la app sin problema; incluye el archivo
`LICENSE` del paquete junto a las fuentes.

### El problema, y cómo se resuelve

El POS usa `.woff2` **variable**. React Native:

- **no carga `woff2`** (necesita `.ttf` / `.otf`),
- **no controla ejes variables** desde `StyleSheet`.

Hay que producir **cuatro TTF estáticos**: Regular (400), Medium (500), SemiBold (600) y Bold (700).

### Receta

Del paquete npm que el POS ya tiene instalado
(`node_modules/@fontsource-variable/google-sans-flex/files/`), el archivo con el eje de peso para
alfabeto latino es `google-sans-flex-latin-wght-normal.woff2` (y su variante `latin-ext`).

```bash
pip install fonttools brotli

# 1) woff2 → ttf variable
fonttools ttLib.woff2 decompress google-sans-flex-latin-wght-normal.woff2

# 2) instanciar cada peso del eje wght
for w in 400 500 600 700; do
  fonttools varLib.instancer google-sans-flex-latin-wght-normal.ttf wght=$w \
    -o GoogleSansFlex-$w.ttf
done
```

Renombra a `GoogleSansFlex-Regular.ttf`, `-Medium`, `-SemiBold`, `-Bold` y ponlos en
`assets/fonts/`. Cárgalos con `useFonts` → [05](05-diseno-y-tema.md).

> Si `varLib.instancer` da problemas, la alternativa es descargar las instancias estáticas desde el
> repositorio de Google Fonts. **Lo que no vale es cargar el woff2 tal cual**: falla en silencio y
> la app se queda con la fuente del sistema, que es lo que más delata que "esto no es PayBille".

### Comprobación

Después de cargarlas, mira un texto en negrita en **Android**. Si se ve engordado y deforme, estás
usando `fontWeight: '700'` sobre la Regular y el sistema está sintetizando la negrita. La solución
es `fontFamily: 'GoogleSans-Bold'`. Es exactamente el mismo problema que en el POS se arregló
declarando `font-weight: 1 1000`.

## Iconos — Material Symbols Rounded

El POS tiene la fuente **ya subseteada** a ~107 iconos:

```
assets/fonts/material-symbols-rounded.woff2   (91 KB, generada por scripts/generar-iconos.py)
scripts/iconos-map.json                       (mapa nombre-legado → nombre Material)
```

**Para el MVP no la uses.** Empieza con `@expo/vector-icons` → `MaterialIcons`: cubre los nombres
Material sin mantener nada. Se ve *Filled* en vez de *Rounded*, ligeramente más duro que el POS,
pero es una diferencia que solo nota quien tenga las dos pantallas al lado.

Cuando la marca importe, el camino es:

1. `fonttools ttLib.woff2 decompress material-symbols-rounded.woff2` → TTF.
2. Generar el mapa de *codepoints* (nombre → carácter) del subset.
3. `createIconSet(glyphMap, 'MaterialSymbolsRounded', 'material-symbols-rounded.ttf')`.

Y si añades un icono que no está en el subset, hay que **regenerarlo** (`scripts/iconos-map.json` +
`scripts/generar-iconos.py` en el POS). La señal de que se te olvidó: el icono se pinta como texto
crudo.

**Material no tiene iconos de marca**: no hay logo de WhatsApp. El POS mapea `i-whatsapp → chat`.
Aquí, para compartir, usa el icono `share` del sistema y deja que el diálogo nativo muestre la
marca.

## Logos de bancos

`assets/bancos/` del POS tiene 14 logos (Popular, BHD, Reservas, APAP, Scotiabank, Promerica,
Santa Cruz, Caribe, Cibao, Vimenca, Azul, CardNet, Qik, Mío). Cópialos **solo cuando** la pantalla
de Cuentas muestre el banco de cada cuenta; hasta entonces son 14 imágenes de peso muerto en el
bundle.

## Paleta y tokens

No hay nada que copiar a mano: los valores están en [05](05-diseno-y-tema.md), ya portados desde
`css/Colors.css`. Si el POS cambia un color, se cambia aquí también — son la misma marca.

## Resumen: qué copiar y cuándo

| Recurso | Cuándo | Origen |
|---|---|---|
| `logo.png` | Fase 0 | `assets/img/LogoNotBG.png` |
| Icono y splash | Fase 0 | Generado desde el logo sobre `#101A42` |
| 4 TTF de Google Sans Flex | Fase 0 | Receta de arriba |
| Iconos Material | Fase 0 (`@expo/vector-icons`) | npm |
| Fuente Material subseteada | Cuando la marca importe | `assets/fonts/material-symbols-rounded.woff2` |
| Logos de bancos | Solo con la pantalla de Cuentas | `assets/bancos/` |
