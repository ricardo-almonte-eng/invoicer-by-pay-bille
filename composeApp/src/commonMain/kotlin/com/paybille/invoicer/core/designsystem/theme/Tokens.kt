package com.paybille.invoicer.core.designsystem.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.ui.unit.dp

/** Radios del POS, sin cambios. */
object PbRadius {
    val sm = 8.dp
    val md = 10.dp
    val lg = 12.dp
    val xl = 14.dp
    val pill = 999.dp
}

/** Escala de espacios del POS (`space` 1–6) más dos pasos de pantalla. */
object PbSpace {
    val s1 = 4.dp
    val s2 = 6.dp
    val s3 = 8.dp
    val s4 = 10.dp
    val s5 = 12.dp
    val s6 = 16.dp
    val s7 = 20.dp
    val s8 = 24.dp
    val s10 = 32.dp
}

object PbIconSize {
    val sm = 14.dp
    val md = 16.dp
    val lg = 20.dp
    val xl = 24.dp
}

/**
 * El POS usa 44/29 px, medidas de ratón. En táctil 29 no se acierta: el mínimo pulsable es
 * 44 pt (Apple) / 48 dp (Material). `hSm` es solo para chips y etiquetas NO pulsables.
 */
object PbControl {
    val h = 48.dp
    val hSm = 36.dp
    val minTouch = 44.dp
    val border = 1.dp
    val borderFocus = 2.dp

    /** Alto de un campo de texto: 52 del diseño, algo más que un botón. */
    val field = 52.dp

    /** Ancho máximo de un formulario: en tablet no se estira de lado a lado. */
    val maxFormWidth = 440.dp
}

/**
 * "Nada rebota, nada escala. El usuario debe percibir respuesta, no animación."
 *
 * Los controles solo animan color de fondo, borde y opacidad. Las tres excepciones las pidió
 * el usuario (2026-09-27) y siguen la misma curva, sin rebote: el cambio de destino desliza
 * ([PAGE_MS]), la barra que marca el destino activo se desplaza ([INDICATOR_MS]) y las hojas
 * entran y salen desde abajo ([SHEET_MS]). Sensación de banca: corto, firme y sin adornos.
 */
object PbMotion {
    val ease = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    /** Salida: acelera sin frenar, para que lo que se va no compita con lo que entra. */
    val exit = CubicBezierEasing(0.3f, 0f, 1f, 1f)
    const val CONTROL_MS = 140
    const val ROW_MS = 120
    const val MODAL_MS = 180
    const val PAGE_MS = 260
    const val INDICATOR_MS = 260
    const val SHEET_MS = 240

    /**
     * Cuánto se desliza un destino al entrar o salir, en fracción del ancho. No es la
     * pantalla entera: un carrusel se siente a juego, un cuarto se siente a documento.
     */
    const val PAGE_SLIDE_FRACTION = 0.25f
}
