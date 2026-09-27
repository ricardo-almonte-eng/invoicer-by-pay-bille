package com.paybille.invoicer.core.designsystem.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.paybille.invoicer.core.designsystem.theme.PbSpace
import com.paybille.invoicer.core.designsystem.theme.PbTheme
import com.paybille.invoicer.resources.Res
import com.paybille.invoicer.resources.invoicer_logo
import com.paybille.invoicer.resources.invoicer_logo_small
import org.jetbrains.compose.resources.painterResource

/**
 * Logo de Invoicer ("PayBille" sobre "INVOICER"). Dos tamaños del mismo dibujo:
 * `invoicer_logo_small` (202×61) para la cabecera y `invoicer_logo` (589×189) para el login;
 * cada uno se ve nítido a 3× en su sitio.
 *
 * El PNG es tinta gris y azul marino sobre transparente: en tema oscuro no se leería. Ahí se
 * pinta con una inversión que conserva el tono (ver [DarkLogoFilter]): el gris pasa a gris
 * claro y el azul y el rojo del isotipo siguen siendo azul y rojo, más claros.
 */
@Composable
fun PbLogo(height: Dp, modifier: Modifier = Modifier) {
    val large = height > SMALL_LOGO_MAX_HEIGHT
    val dark = PbTheme.colors.isDark
    val filter = remember(dark) { if (dark) ColorFilter.colorMatrix(DarkLogoFilter) else null }
    Image(
        painter = painterResource(if (large) Res.drawable.invoicer_logo else Res.drawable.invoicer_logo_small),
        contentDescription = "Invoicer by PayBille",
        contentScale = ContentScale.Fit,
        colorFilter = filter,
        modifier = modifier.height(height),
    )
}

/** Hasta aquí alcanza el PNG pequeño (61 px de alto) sin verse borroso a 3×. */
private val SMALL_LOGO_MAX_HEIGHT = 22.dp

/**
 * Inversión "inteligente": invierte la luminosidad y gira el tono 180° para devolver cada
 * color a su familia. Es la matriz de giro de tono de 180° aplicada a (255 − canal); las
 * filas de ese giro suman 1, así que el desplazamiento de cada canal queda en 255.
 * Ej.: gris #333333 → #CCCCCC · azul #16426F → #9DC9F6 · rojo #E83B3B → #FF7A7A.
 */
private val DarkLogoFilter = ColorMatrix(
    floatArrayOf(
        0.574f, -1.430f, -0.144f, 0f, 255f,
        -0.426f, -0.430f, -0.144f, 0f, 255f,
        -0.426f, -1.430f, 0.856f, 0f, 255f,
        0f, 0f, 0f, 1f, 0f,
    ),
)

/**
 * Marca del login: el logo grande y, debajo, qué es la app. El logo ya dice el nombre, así
 * que el texto es la bajada, como "Sistema de Punto de Venta" en el login del diseño.
 */
@Composable
fun PbBrand(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.semantics(mergeDescendants = true) { heading() },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(PbSpace.s3),
    ) {
        PbLogo(height = 64.dp)
        PbText(
            text = "Facturación para tu negocio",
            style = PbTheme.typography.label,
            color = PbTheme.colors.muted,
            textAlign = TextAlign.Center,
        )
    }
}
