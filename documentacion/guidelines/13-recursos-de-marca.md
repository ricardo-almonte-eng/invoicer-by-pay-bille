# 13 — Recursos de marca

Todo sale de `C:\repos\PayBille_POS`. Nada se rediseña.

## Logo

**Logo de la app (2026-09-27):** el usuario entregó el logo de Invoicer ("PayBille" sobre
"INVOICER"), en dos tamaños del mismo dibujo:

| Archivo | Tamaño | Uso |
|---|---|---|
| `drawable/invoicer_logo.png` (era `Logo02.png`) | 589×189 | Login (`PbBrand`, 64 dp de alto) |
| `drawable/invoicer_logo_small.png` (era `Logo01.png`) | 202×61 | `PbLogo` por debajo de 22 dp. Hoy sin uso: la cabecera volvió al isotipo (2026-09-27) |

Se renombraron porque Compose Resources pide minúsculas y `_`. El PNG es gris oscuro y marino sobre
transparente: en tema oscuro `PbLogo` le aplica una matriz de color (inversión + giro de tono de
180°) que lo aclara sin perder el azul y el rojo. Si llega una versión para fondo oscuro, sustituye
a la matriz.

Del POS (sigue valiendo para el isotipo y el icono de la app):

⚠️ **Corrección (2026-09-16):** la versión anterior de esta guía señalaba
`assets/img/LogoNotBG.png` como "el bueno". **Ese archivo es el logo viejo de VëntEX POS**, no el
de PayBille. El login del POS usa `public/Logo_letter_White.fw.png`.

| Archivo en el POS | Tamaño | Qué es | Uso aquí |
|---|---|---|---|
| `public/Logo_white.fw.png` | 96×90 | **Isotipo** (la "dp" azul y roja), fondo transparente | `drawable/paybille_isotipo.png`: cabecera de los destinos (32 dp) y base del icono de la app |
| `public/Logo_letter_White.fw.png` | 430×90 | Isotipo + "PayBille" en azul marino | No: el texto marino no se lee en tema oscuro. `PbBrand` escribe el nombre con la fuente |
| `public/Logo_letter_Dark.fw.png` | — | Cuadro azul con isotipo + "PayBille" | Referencia |
| `public/Icon_White.fw.png` | 287×288 | Icono cuadrado azul con "PayBille" | Base del icono de la app (hay que regenerarlo a 1024×1024) |
| `assets/img/LogoNotBG.png` | — | **VëntEX POS** | ❌ No usar |

**El icono de la app está pendiente.** Android e iOS piden un PNG cuadrado y opaco de 1024×1024
con márgenes propios; escalar el de 287 px se ve mal. Se genera desde el isotipo sobre el azul de
`Icon_White.fw.png` (toma el valor exacto de ese archivo) y, para Android, un *adaptive icon* con el isotipo centrado.
La plantilla de Xcode traía el icono de otra app: se quitó y el `AppIcon` quedó vacío a propósito.

## Tipografía — Google Sans Flex

**Licencia:** SIL Open Font License 1.1 (la versión abierta, no la Google Sans propietaria). Se
puede empaquetar; su `LICENSE` va en `composeResources/files/licenses/google_sans_flex_ofl.txt`.

El POS usa `.woff2` **variable**; la app usa **cuatro TTF estáticos**, ya generados en
`composeResources/font/` → [05](05-diseno-y-tema.md).

### Receta (por si hay que regenerarlos)

Origen: `PayBille_POS/node_modules/@fontsource-variable/google-sans-flex/files/
google-sans-flex-latin-wght-normal.woff2` (el subset latino cubre `áéíóúñÑ¿¡`).

```bash
pip install fonttools brotli
python -m fontTools.ttLib.woff2 decompress google-sans-flex-latin-wght-normal.woff2
for p in "400 regular" "500 medium" "600 semibold" "700 bold"; do
  set -- $p
  python -m fontTools.varLib.instancer google-sans-flex-latin-wght-normal.ttf wght=$1     -o google_sans_flex_$2.ttf
done
```

Los nombres van en minúsculas y con `_`: Compose Resources no acepta guiones ni mayúsculas.

## Iconos — Material Symbols Rounded (la fuente)

Desde el 2026-09-18 los iconos son **la fuente**, no vectores sueltos (decisión del usuario):
`composeResources/font/material_symbols_rounded.ttf`, sacada del TTF variable de Google
`MaterialSymbolsRounded[FILL,GRAD,opsz,wght].ttf` (`github.com/google/material-design-icons`,
carpeta `variablefont`, Apache-2.0, ≈15 MB).

Todo lo hace `documentacion/scripts/material_symbols.py`:

```bash
python documentacion/scripts/material_symbols.py "<ruta>/MaterialSymbolsRounded[FILL,GRAD,opsz,wght].ttf"
```

1. Fija `GRAD 0 · opsz 24 · wght 400` y deja variable **solo `FILL`**, con **todos** los glifos
   (≈2 MB). Las ligaduras (`rlig`) y el cambio a los glifos `.fill` (`rclt` con *feature
   variations*) se conservan.
2. Escribe `documentacion/scripts/material-symbols-rounded.codepoints` (nombre → codepoint),
   sacado del `cmap` de la propia fuente.
3. Regenera `PbSymbols.kt` con la lista `NAMES` del script.

### Añadir un icono

- **Sin descargar nada:** busca su nombre en `material-symbols-rounded.codepoints` y añade la línea
  a `PbSymbols.kt` **y** el nombre a `NAMES` (para que la próxima regeneración no lo pierda). La
  fuente ya tiene todos los glifos.
- **Con la fuente completa:** añade el nombre a `NAMES` y corre el script.
- **Nunca copies un codepoint de memoria**: varios codepoints apuntan al mismo glifo y otros han
  cambiado entre versiones. Algunos nombres también: `phone` es `call` y `location_on` es
  `pin_drop` en esta versión (el script avisa si un nombre no existe).

**Material no tiene iconos de marca**: no hay logo de WhatsApp. Para compartir, usa `share` y deja
que el diálogo nativo muestre la marca.

## Logos de bancos

`assets/bancos/` del POS tiene 14 logos (Popular, BHD, Reservas, APAP, Scotiabank, Promerica,
Santa Cruz, Caribe, Cibao, Vimenca, Azul, CardNet, Qik, Mío). Cópialos **solo cuando** la pantalla
de Cuentas muestre el banco de cada cuenta; hasta entonces son 14 imágenes de peso muerto en el
bundle.

⚠️ **Compose Resources no admite subcarpetas en `drawable/`**: `drawable/banks/popular.png` rompe
`generateResourceAccessorsForCommonMain` ("Acceso denegado" sobre la carpeta en `build/`). Van
planos y en minúsculas con `_`: `drawable/bank_popular.png`, `bank_promerica.png`… Y si quedó
una carpeta vieja en `build/generated/compose/resourceGenerator/preparedResources`, hay que
borrarla (la tarea no limpia lo que ya copió).

## Paleta y tokens

No hay nada que copiar a mano: los valores están en [05](05-diseno-y-tema.md), ya portados desde
`css/Colors.css`. Si el POS cambia un color, se cambia aquí también — son la misma marca.

## Resumen: qué copiar y cuándo

| Recurso | Estado | Origen |
|---|---|---|
| Isotipo | ✅ (para el icono) | `public/Logo_white.fw.png` |
| Logo de Invoicer | ✅ | Entregado por el usuario (`Logo01/02.png`) |
| 4 TTF de Google Sans Flex | ✅ | Receta de arriba |
| Iconos Material (fuente) | ✅ todos los glifos, eje FILL | `google/material-design-icons` → script |
| Icono de la app y arranque | Pendiente | Generado desde el isotipo |
| Logos de bancos | ✅ en uso (`core/ui/BankLogo.kt`, por el texto de `cuentas.BankName`; "Proamérica" → `promerica`): `drawable/bank_apap.jpg`, `bank_azul`, `bank_banreservas`, `bank_bhd`, `bank_cardnet`, `bank_caribe`, `bank_cibao`, `bank_mio`, `bank_popular`, `bank_promerica`, `bank_qik`, `bank_santacruz`, `bank_scotiabank`, `bank_vimenca` → `Res.drawable.bank_*` | `assets/bancos/` |
