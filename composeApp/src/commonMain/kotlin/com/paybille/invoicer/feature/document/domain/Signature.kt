package com.paybille.invoicer.feature.document.domain

import kotlin.math.roundToInt

/** Un punto de la firma, en el sistema de coordenadas del lienzo donde se dibujó. */
data class SignaturePoint(val x: Float, val y: Float)

/**
 * La firma se guarda como SVG (texto) en `invoiceconfig.Signature`: pesa poco, escala sin
 * pixelarse en el PDF y se pinta igual en Android, iOS y el POS.
 */
object SignatureSvg {

    /** Trazos → SVG. `null` si no hay nada dibujado. */
    fun fromStrokes(strokes: List<List<SignaturePoint>>, width: Float, height: Float): String? {
        val paths = strokes.filter { it.isNotEmpty() }.map { stroke ->
            buildString {
                append("M${n(stroke[0].x)} ${n(stroke[0].y)}")
                if (stroke.size == 1) append(" l0.1 0.1") // un toque: un punto visible
                stroke.drop(1).forEach { append(" L${n(it.x)} ${n(it.y)}") }
            }
        }
        if (paths.isEmpty() || width <= 0f || height <= 0f) return null
        return svg(n(width), n(height), paths)
    }

    /**
     * Rehace el SVG guardado con SOLO el `viewBox` y los `d` de sus trazos. Lo que venga en la
     * base se pinta sin escapar dentro de la factura, así que nada que no sea un trazo pasa
     * (ni `<script>` ni atributos `on…`).
     */
    fun sanitize(svg: String?): String? {
        if (svg.isNullOrBlank()) return null
        val viewBox = VIEW_BOX.find(svg)?.groupValues?.get(1)?.split(' ', ',')?.filter { it.isNotBlank() }
            ?.takeIf { it.size == 4 && it.all { v -> v.toDoubleOrNull() != null } } ?: return null
        val paths = PATH_D.findAll(svg).map { it.groupValues[1] }.filter { SAFE_D.matches(it) }.toList()
        if (paths.isEmpty()) return null
        return svg(viewBox[2], viewBox[3], paths)
    }

    /** Los trazos de un SVG ya guardado, para volver a pintarlo en el lienzo. */
    fun toStrokes(svg: String?): List<List<SignaturePoint>> {
        val clean = sanitize(svg) ?: return emptyList()
        return PATH_D.findAll(clean).map { match ->
            COMMAND.findAll(match.groupValues[1]).mapNotNull { c ->
                val x = c.groupValues[2].toFloatOrNull() ?: return@mapNotNull null
                val y = c.groupValues[3].toFloatOrNull() ?: return@mapNotNull null
                if (c.groupValues[1] == "l") null else SignaturePoint(x, y)
            }.toList()
        }.filter { it.isNotEmpty() }.toList()
    }

    /** Ancho y alto del `viewBox`, o null. */
    fun size(svg: String?): Pair<Float, Float>? {
        val parts = VIEW_BOX.find(svg ?: return null)?.groupValues?.get(1)?.split(' ', ',')?.filter { it.isNotBlank() } ?: return null
        val w = parts.getOrNull(2)?.toFloatOrNull() ?: return null
        val h = parts.getOrNull(3)?.toFloatOrNull() ?: return null
        return w to h
    }

    private fun svg(width: String, height: String, paths: List<String>) = buildString {
        append("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 $width $height\" fill=\"none\" ")
        append("stroke=\"#101A42\" stroke-width=\"2.4\" stroke-linecap=\"round\" stroke-linejoin=\"round\">")
        paths.forEach { append("<path d=\"").append(it).append("\"/>") }
        append("</svg>")
    }

    private fun n(value: Float): String {
        val tenths = (value * 10).roundToInt()
        return if (tenths % 10 == 0) (tenths / 10).toString() else "${tenths / 10}.${kotlin.math.abs(tenths % 10)}"
    }

    private val VIEW_BOX = Regex("viewBox=\"([^\"]+)\"")
    private val PATH_D = Regex("<path[^>]*\\sd=\"([^\"]*)\"")
    private val SAFE_D = Regex("[MLlm0-9 .\\-]+")
    private val COMMAND = Regex("([MLl])(-?[0-9.]+) (-?[0-9.]+)")
}
