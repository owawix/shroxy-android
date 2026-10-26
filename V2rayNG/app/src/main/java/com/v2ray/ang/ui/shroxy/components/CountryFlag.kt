package com.v2ray.ang.ui.shroxy.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.remember
import com.v2ray.ang.R

/**
 * Pure country → flag resolution. Kept free of Compose state so the mapping is unit-testable.
 * Resolution order (see [CountryFlag]): regional-indicator emoji pair, country name/code
 * dictionary, then a placeholder decided by the caller.
 */
object CountryFlags {
    private const val REGIONAL_INDICATOR_START = 0x1F1E6
    private const val REGIONAL_INDICATOR_COUNT = 26

    /** Two-letter ISO country code → bundled vector flag. */
    val codeToRes: Map<String, Int> = mapOf(
        "ru" to R.drawable.shroxy_flag_ru,
        "us" to R.drawable.shroxy_flag_us,
        "nl" to R.drawable.shroxy_flag_nl,
        "de" to R.drawable.shroxy_flag_de,
        "se" to R.drawable.shroxy_flag_se,
        "fi" to R.drawable.shroxy_flag_fi,
        "gb" to R.drawable.shroxy_flag_gb,
        "fr" to R.drawable.shroxy_flag_fr,
        "ch" to R.drawable.shroxy_flag_ch,
        "at" to R.drawable.shroxy_flag_at,
        "es" to R.drawable.shroxy_flag_es,
        "it" to R.drawable.shroxy_flag_it,
        "pl" to R.drawable.shroxy_flag_pl,
        "cz" to R.drawable.shroxy_flag_cz,
        "lt" to R.drawable.shroxy_flag_lt,
        "lv" to R.drawable.shroxy_flag_lv,
        "ee" to R.drawable.shroxy_flag_ee,
        "tr" to R.drawable.shroxy_flag_tr,
        "jp" to R.drawable.shroxy_flag_jp,
        "sg" to R.drawable.shroxy_flag_sg,
    )

    /** Lower-cased English/Russian country names and aliases → ISO code. */
    private val nameToCode: Map<String, String> = mapOf(
        "russia" to "ru", "россия" to "ru",
        "usa" to "us", "us" to "us", "united states" to "us",
        "united states of america" to "us", "сша" to "us", "америка" to "us",
        "netherlands" to "nl", "holland" to "nl", "нидерланды" to "nl", "голландия" to "nl",
        "germany" to "de", "германия" to "de",
        "sweden" to "se", "швеция" to "se",
        "finland" to "fi", "финляндия" to "fi",
        "united kingdom" to "gb", "great britain" to "gb", "britain" to "gb", "uk" to "gb",
        "england" to "gb", "великобритания" to "gb", "англия" to "gb",
        "france" to "fr", "франция" to "fr",
        "switzerland" to "ch", "швейцария" to "ch",
        "austria" to "at", "австрия" to "at",
        "spain" to "es", "испания" to "es",
        "italy" to "it", "италия" to "it",
        "poland" to "pl", "польша" to "pl",
        "czechia" to "cz", "czech republic" to "cz", "чехия" to "cz",
        "lithuania" to "lt", "литва" to "lt",
        "latvia" to "lv", "латвия" to "lv",
        "estonia" to "ee", "эстония" to "ee",
        "turkey" to "tr", "türkiye" to "tr", "турция" to "tr",
        "japan" to "jp", "япония" to "jp",
        "singapore" to "sg", "сингапур" to "sg",
    )

    fun isRegionalIndicator(codePoint: Int): Boolean =
        codePoint in REGIONAL_INDICATOR_START until REGIONAL_INDICATOR_START + REGIONAL_INDICATOR_COUNT

    /**
     * Returns the emoji flag when [remark] starts with a regional-indicator pair (🇺🇸-style),
     * otherwise null.
     */
    fun extractEmojiFlag(remark: String): String? {
        if (remark.isEmpty()) return null
        val first = remark.codePointAt(0)
        if (!isRegionalIndicator(first)) return null
        val secondIndex = Character.charCount(first)
        if (secondIndex >= remark.length) return null
        val second = remark.codePointAt(secondIndex)
        if (!isRegionalIndicator(second)) return null
        return String(Character.toChars(first)) + String(Character.toChars(second))
    }

    /** Returns the bundled flag drawable for an ISO code or a known EN/RU country name. */
    fun resolveFlagRes(country: String?): Int? {
        if (country.isNullOrBlank()) return null
        val key = country.trim().lowercase()
        codeToRes[key]?.let { return it }
        val code = nameToCode[key] ?: return null
        return codeToRes[code]
    }

    /**
     * Splits a leading regional-indicator emoji pair off a location title.
     * [emoji] is the flag to render as the icon (null when the title does not lead with a
     * valid pair — the caller then falls back to name/code resolution and keeps the
     * original text); [cleanTitle] is the title without the emoji for display.
     */
    data class LeadingFlag(val emoji: String?, val cleanTitle: String)

    /**
     * Strips a leading emoji flag (and the whitespace separating it from the name) from
     * [title]. When the title carries no valid emoji pair, or consists of nothing but the
     * emoji, the text is returned unchanged with a null emoji so nothing is lost.
     */
    fun stripLeadingFlag(title: String): LeadingFlag {
        val emoji = extractEmojiFlag(title) ?: return LeadingFlag(null, title)
        val cleanTitle = title.substring(emoji.length).trimStart()
        if (cleanTitle.isEmpty()) return LeadingFlag(null, title)
        return LeadingFlag(emoji, cleanTitle)
    }
}

/**
 * Country flag: emoji from the server remark when it leads with a regional-indicator pair,
 * otherwise a bundled vector flag resolved from the country name/code, otherwise a
 * layer2 rounded placeholder with a flag icon.
 */
@Composable
fun CountryFlag(
    remark: String,
    country: String?,
    modifier: Modifier = Modifier,
) {
    val emoji = remember(remark) { CountryFlags.extractEmojiFlag(remark) }
    val flagRes = remember(country) { CountryFlags.resolveFlagRes(country) }
    val shape = RoundedCornerShape(4.dp)
    when {
        emoji != null -> Text(
            text = emoji,
            modifier = modifier.clip(shape),
        )

        flagRes != null -> Image(
            painter = rememberVectorPainter(ImageVector.vectorResource(flagRes)),
            contentDescription = null,
            modifier = modifier.clip(shape),
            contentScale = ContentScale.Crop,
        )

        else -> Box(
            modifier = modifier
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.Flag,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}
