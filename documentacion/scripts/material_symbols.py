"""Genera la fuente de iconos de la app y `PbSymbols.kt` desde Material Symbols Rounded.

Uso (desde la raíz del repo):

    python documentacion/scripts/material_symbols.py <MaterialSymbolsRounded[FILL,GRAD,opsz,wght].ttf>

La fuente completa (≈15 MB, Apache-2.0) está en github.com/google/material-design-icons,
carpeta `variablefont`. El script:

1. Fija GRAD 0 · opsz 24 · wght 400 y deja variable solo FILL (el icono activo va relleno).
   Conserva todos los glifos: ≈2 MB → composeResources/font/material_symbols_rounded.ttf
2. Escribe `material-symbols-rounded.codepoints` (nombre → codepoint) junto a este script,
   sacado del cmap de la propia fuente: nunca copies un codepoint de memoria (guía 13).
3. Regenera `PbSymbols.kt` con los nombres de `NAMES`.

Para añadir un icono SIN volver a descargar la fuente: busca su codepoint en
`material-symbols-rounded.codepoints` y añádelo a mano a `PbSymbols.kt` (y a `NAMES`).
"""
import os
import sys

from fontTools.ttLib import TTFont
from fontTools.varLib.instancer import instantiateVariableFont

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
RES = os.path.join(ROOT, "composeApp", "src", "commonMain", "composeResources", "font",
                   "material_symbols_rounded.ttf")
KT = os.path.join(ROOT, "composeApp", "src", "commonMain", "kotlin", "com", "paybille", "invoicer",
                  "core", "designsystem", "theme", "PbSymbols.kt")
CODEPOINTS = os.path.join(os.path.dirname(__file__), "material-symbols-rounded.codepoints")

# Nombres de glifo (los de fonts.google.com/icons) que usa la app.
NAMES = """
account_balance account_balance_wallet account_circle add add_a_photo add_box add_card arrow_back
arrow_downward arrow_upward badge bar_chart barcode calendar_month call category check check_circle
chevron_right close cloud_off cloud_upload content_copy credit_card currency_exchange delete description download edit
error event expand_less expand_more group hide_image history home image inbox info inventory_2 link lock logout
mail monitoring more_horiz notifications notifications_active open_in_new palette payments percent person person_add
photo_camera photo_library picture_as_pdf pin_drop qr_code_scanner receipt_long remove request_quote savings
schedule search sell settings share storefront swap_vert sync trending_down trending_up visibility
visibility_off warning
""".split()


def pascal(name: str) -> str:
    return "".join(part[:1].upper() + part[1:] for part in name.split("_"))


def main(src: str) -> None:
    font = TTFont(src)
    # Un glifo puede tener varios codepoints: vale cualquiera, se toma el menor.
    by_glyph: dict[str, int] = {}
    for cp, glyph in font.getBestCmap().items():
        if glyph not in by_glyph or cp < by_glyph[glyph]:
            by_glyph[glyph] = cp

    missing = [n for n in NAMES if n not in by_glyph]
    if missing:
        sys.exit(f"No existen en la fuente: {missing}")

    with open(CODEPOINTS, "w", encoding="utf-8", newline="\n") as out:
        for glyph in sorted(by_glyph):
            out.write(f"{glyph} {by_glyph[glyph]:04x}\n")

    lines = [
        "package com.paybille.invoicer.core.designsystem.theme",
        "",
        "// Generado por documentacion/scripts/material_symbols.py (codepoints del cmap de la fuente).",
        "// Para añadir uno a mano: material-symbols-rounded.codepoints, junto al script.",
        "",
        "/** Icono de Material Symbols Rounded: el codepoint de su glifo en `material_symbols_rounded.ttf`. */",
        "@kotlin.jvm.JvmInline",
        "value class PbSymbol(val codepoint: Int)",
        "",
        "/** Los iconos que usa la app, por su nombre en fonts.google.com/icons. */",
        "object PbSymbols {",
    ]
    for name in sorted(NAMES):
        lines.append(f"    /** `{name}` */ val {pascal(name)} = PbSymbol(0x{by_glyph[name]:04X})")
    lines.append("}")
    with open(KT, "w", encoding="utf-8", newline="\n") as out:
        out.write("\n".join(lines) + "\n")

    inst = instantiateVariableFont(font, {"GRAD": 0, "opsz": 24, "wght": 400})
    inst.save(RES)
    print(f"{RES}: {os.path.getsize(RES) / 1e6:.1f} MB · {len(NAMES)} iconos en PbSymbols")


if __name__ == "__main__":
    main(sys.argv[1])
