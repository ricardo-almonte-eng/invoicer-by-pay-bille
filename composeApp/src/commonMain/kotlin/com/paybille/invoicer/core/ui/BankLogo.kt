package com.paybille.invoicer.core.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.paybille.invoicer.core.designsystem.components.PbIcon
import com.paybille.invoicer.core.designsystem.theme.PbControl
import com.paybille.invoicer.core.designsystem.theme.PbRadius
import com.paybille.invoicer.core.designsystem.theme.PbSymbol
import com.paybille.invoicer.core.designsystem.theme.PbTheme
import com.paybille.invoicer.resources.Res
import com.paybille.invoicer.resources.bank_apap
import com.paybille.invoicer.resources.bank_azul
import com.paybille.invoicer.resources.bank_banreservas
import com.paybille.invoicer.resources.bank_bhd
import com.paybille.invoicer.resources.bank_cardnet
import com.paybille.invoicer.resources.bank_caribe
import com.paybille.invoicer.resources.bank_cibao
import com.paybille.invoicer.resources.bank_mio
import com.paybille.invoicer.resources.bank_popular
import com.paybille.invoicer.resources.bank_promerica
import com.paybille.invoicer.resources.bank_qik
import com.paybille.invoicer.resources.bank_santacruz
import com.paybille.invoicer.resources.bank_scotiabank
import com.paybille.invoicer.resources.bank_vimenca
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

/**
 * Logo de cada banco de `PayBille_POS/Models/Bancos.js`, por el MISMO texto que el POS guarda
 * en `cuentas.BankName`. Ojo: "Proamérica" usa `promerica.png` (así se llama el archivo en el POS).
 */
private val BANK_LOGOS: Map<String, DrawableResource> = mapOf(
    "APAP" to Res.drawable.bank_apap,
    "Azul" to Res.drawable.bank_azul,
    "Banreservas" to Res.drawable.bank_banreservas,
    "BHD" to Res.drawable.bank_bhd,
    "Cardnet" to Res.drawable.bank_cardnet,
    "Caribe" to Res.drawable.bank_caribe,
    "Cibao" to Res.drawable.bank_cibao,
    "MIO" to Res.drawable.bank_mio,
    "Popular" to Res.drawable.bank_popular,
    "Proamérica" to Res.drawable.bank_promerica,
    "Qik" to Res.drawable.bank_qik,
    "Santa Cruz" to Res.drawable.bank_santacruz,
    "Scotiabank" to Res.drawable.bank_scotiabank,
    "Vimenca" to Res.drawable.bank_vimenca,
).mapKeys { normalize(it.key) }

/** "Banco Popular", "popular", "Proamerica" → la misma clave: el POS deja escribir el banco a mano. */
private fun normalize(name: String): String = name.trim().lowercase()
    .replace('á', 'a').replace('é', 'e').replace('í', 'i').replace('ó', 'o').replace('ú', 'u')
    .removePrefix("banco ")
    .filter { it.isLetterOrDigit() }

fun bankLogo(bankName: String?): DrawableResource? = bankName?.let { BANK_LOGOS[normalize(it)] }

/**
 * Logo del banco sobre una placa blanca con borde de 1 dp (los archivos traen fondo blanco y en
 * tema oscuro no se pueden invertir). Sin logo conocido (banco escrito a mano, caja), `fallback`.
 */
@Composable
fun BankLogo(
    bankName: String?,
    fallback: PbSymbol,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
) {
    val colors = PbTheme.colors
    val shape = RoundedCornerShape(PbRadius.md)
    val logo = bankLogo(bankName)
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(if (logo != null) colors.logoPlate else colors.surface2, shape)
            .border(PbControl.border, colors.outline, shape),
        contentAlignment = Alignment.Center,
    ) {
        if (logo != null) {
            Image(
                painter = painterResource(logo),
                contentDescription = bankName,
                contentScale = ContentScale.Fit,
                modifier = Modifier.padding(size / 10).size(size),
            )
        } else {
            PbIcon(icon = fallback, contentDescription = null, tint = colors.primary)
        }
    }
}
