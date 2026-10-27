package com.v2ray.ang.ui.shroxy.components

import androidx.annotation.VisibleForTesting
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import java.util.Locale
import java.util.regex.Pattern

/**
 * Pure country → ISO code resolution and flag drawable naming. Kept free of Compose state
 * and of compile-time R references to the generated flag vectors, so the mapping logic is
 * unit-testable on the JVM. Drawable ids are looked up by name through [flagDrawableRes]
 * and cached, because 270 generated vectors must not be wired into R call sites.
 */
object CountryFlags {
    private const val REGIONAL_INDICATOR_START = 0x1F1E6
    private const val REGIONAL_INDICATOR_COUNT = 26

    /**
     * Codes with a bundled shroxy_flag_* vector: ISO-3166-1 alpha-2, a few extra codes the
     * flag set ships (eu, un, xk, xx, supranational and territory flags), and underscore-named
     * subdivisions. Used to validate direct code matches; a code outside this set still gets
     * a drawable-name candidate so a lookup miss degrades to the code badge, never a crash.
     */
    val knownCodes: Set<String> = setOf(
        "ad", "ae", "af", "ag", "ai", "al", "am", "ao", "aq", "ar", "arab", "as", "asean", "at", "au", "aw", "ax",
        "az", "ba", "bb", "bd", "be", "bf", "bg", "bh", "bi", "bj", "bl", "bm", "bn", "bo",
        "bq", "br", "bs", "bt", "bv", "bw", "by", "bz", "ca", "cc", "cd", "cefta", "cf", "cg", "ch",
        "ci", "ck", "cl", "cm", "cn", "co", "cp", "cr", "cu", "cv", "cw", "cx", "cy", "cz", "de",
        "dg", "dj", "dk", "dm", "do", "dz", "eac", "ec", "ee", "eg", "eh", "er", "es", "et", "eu", "fi", "fj",
        "fk", "fm", "fo", "fr", "ga", "gb", "gd", "ge", "gf", "gg", "gh", "gi", "gl", "gm",
        "gn", "gp", "gq", "gr", "gs", "gt", "gu", "gw", "gy", "hk", "hm", "hn", "hr", "ht", "hu",
        "ic", "id", "ie", "il", "im", "in", "io", "iq", "ir", "is", "it", "je", "jm", "jo", "jp",
        "ke", "kg", "kh", "ki", "km", "kn", "kp", "kr", "kw", "ky", "kz", "la", "lb", "lc",
        "li", "lk", "lr", "ls", "lt", "lu", "lv", "ly", "ma", "mc", "md", "me", "mf", "mg",
        "mh", "mk", "ml", "mm", "mn", "mo", "mp", "mq", "mr", "ms", "mt", "mu", "mv", "mw",
        "mx", "my", "mz", "na", "nc", "ne", "nf", "ng", "ni", "nl", "no", "np", "nr", "nu",
        "nz", "om", "pa", "pc", "pe", "pf", "pg", "ph", "pk", "pl", "pm", "pn", "pr", "ps", "pt",
        "pw", "py", "qa", "re", "ro", "rs", "ru", "rw", "sa", "sb", "sc", "sd", "se", "sg",
        "sh", "si", "sj", "sk", "sl", "sm", "sn", "so", "sr", "ss", "st", "sv", "sx", "sy",
        "sz", "tc", "td", "tf", "tg", "th", "tj", "tk", "tl", "tm", "tn", "to", "tr", "tt",
        "tv", "tw", "tz", "ua", "ug", "un", "us", "uy", "uz", "va", "vc", "ve", "vg", "vi", "vn",
        "vu", "wf", "ws", "xk", "xx", "ye", "yt", "za", "zm", "zw",
        // Hyphenated subdivisions use underscores in the bundled file names.
        "gb_eng", "gb_nir", "gb_sct", "gb_wls", "es_ct", "es_ga", "es_pv", "sh_ac", "sh_hl", "sh_ta",
    )

    /** Lower-cased English/Russian country names and aliases → ISO code (subdivisions included). */
    private val nameToCode: Map<String, String> = mapOf(
        "russia" to "ru", "россия" to "ru", "российская федерация" to "ru",
        "usa" to "us", "united states" to "us", "united states of america" to "us",
        "america" to "us", "сша" to "us", "америка" to "us",
        "соединенные штаты" to "us", "соединённые штаты" to "us",
        "united kingdom" to "gb", "great britain" to "gb", "britain" to "gb", "uk" to "gb",
        "великобритания" to "gb", "британия" to "gb", "англия" to "gb",
        "germany" to "de", "германия" to "de",
        "netherlands" to "nl", "holland" to "nl", "нидерланды" to "nl", "голландия" to "nl",
        "sweden" to "se", "швеция" to "se",
        "finland" to "fi", "финляндия" to "fi",
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
        "turkey" to "tr", "türkiye" to "tr", "turkiye" to "tr", "турция" to "tr",
        "japan" to "jp", "япония" to "jp",
        "singapore" to "sg", "сингапур" to "sg",
        "hong kong" to "hk", "гонконг" to "hk",
        "united arab emirates" to "ae", "uae" to "ae", "оаэ" to "ae", "эмираты" to "ae",
        "canada" to "ca", "канада" to "ca",
        "norway" to "no", "норвегия" to "no",
        "denmark" to "dk", "дания" to "dk",
        "iceland" to "is", "исландия" to "is",
        "ireland" to "ie", "ирландия" to "ie",
        "belgium" to "be", "бельгия" to "be",
        "hungary" to "hu", "венгрия" to "hu",
        "romania" to "ro", "румыния" to "ro",
        "bulgaria" to "bg", "болгария" to "bg",
        "greece" to "gr", "греция" to "gr",
        "ukraine" to "ua", "украина" to "ua",
        "kazakhstan" to "kz", "казахстан" to "kz",
        "moldova" to "md", "молдова" to "md",
        "serbia" to "rs", "сербия" to "rs",
        "croatia" to "hr", "хорватия" to "hr",
        "slovakia" to "sk", "словакия" to "sk",
        "slovenia" to "si", "словения" to "si",
        "portugal" to "pt", "португалия" to "pt",
        "cyprus" to "cy", "кипр" to "cy",
        "luxembourg" to "lu", "люксембург" to "lu",
        "israel" to "il", "израиль" to "il",
        "india" to "in", "индия" to "in",
        "brazil" to "br", "бразилия" to "br",
        "australia" to "au", "австралия" to "au",
        "new zealand" to "nz", "новая зеландия" to "nz",
        "south korea" to "kr", "korea" to "kr", "южная корея" to "kr", "корея" to "kr",
        "china" to "cn", "китай" to "cn",
        "taiwan" to "tw", "тайвань" to "tw",
        "thailand" to "th", "таиланд" to "th",
        "vietnam" to "vn", "viet nam" to "vn", "вьетнам" to "vn",
        "indonesia" to "id", "индонезия" to "id",
        "malaysia" to "my", "малайзия" to "my",
        "argentina" to "ar", "аргентина" to "ar",
        "chile" to "cl", "чили" to "cl",
        "mexico" to "mx", "мексика" to "mx",
        "south africa" to "za", "юар" to "za", "южная африка" to "za",
        "egypt" to "eg", "египет" to "eg",
        "georgia" to "ge", "грузия" to "ge",
        "armenia" to "am", "армения" to "am",
        "azerbaijan" to "az", "азербайджан" to "az",
        "uzbekistan" to "uz", "узбекистан" to "uz",
        "kyrgyzstan" to "kg", "киргизия" to "kg", "кыргызстан" to "kg",
        "england" to "gb_eng", "scotland" to "gb_sct", "wales" to "gb_wls",
        "northern ireland" to "gb_nir",
        "catalonia" to "es_ct",
    )

    /**
     * Standalone alpha-2 code (case-insensitive). Letters, digits, and underscores all count
     * as glue: "US West" → us and "AT-1" → at match, while "v2at5" and "node_at6" do not.
     */
    private val codeTokenPattern: Pattern =
        Pattern.compile("(?<![A-Za-z0-9_])([A-Za-z]{2})(?![A-Za-z0-9_])")

    /** Subdivision token such as gb-eng / gb_eng (ISO-3166-2 style) → underscored code. */
    private val subdivisionPattern: Pattern =
        Pattern.compile("(?<![A-Za-z])([A-Za-z]{2})[-_]([A-Za-z]{3})(?![A-Za-z])")

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

    /** Converts a regional-indicator emoji pair to its lower-case ISO alpha-2 code. */
    fun emojiToCode(emoji: String): String {
        val first = emoji.codePointAt(0) - REGIONAL_INDICATOR_START + 'a'.code
        val second = emoji.codePointAt(Character.charCount(emoji.codePointAt(0))) -
            REGIONAL_INDICATOR_START + 'a'.code
        return "${first.toChar()}${second.toChar()}"
    }

    /**
     * Resolves an ISO alpha-2 code from [country] (name or code) or [remark] (leading
     * emoji pair, standalone code token, or subdivision token). Returns null when nothing
     * resolvable is found — the caller then renders the generic placeholder.
     */
    fun resolveCode(country: String?, remark: String?): String? {
        if (!remark.isNullOrEmpty()) {
            extractEmojiFlag(remark)?.let { return emojiToCode(it) }
        }
        if (!country.isNullOrBlank()) {
            val key = country.trim().lowercase(Locale.ROOT)
            nameToCode[key]?.let { return it }
            normalizeToken(key)?.let { return it }
        }
        if (!remark.isNullOrBlank()) {
            val subdivision = subdivisionPattern.matcher(remark)
            if (subdivision.find()) {
                val subCode = "${subdivision.group(1)!!.lowercase(Locale.ROOT)}_" +
                    subdivision.group(2)!!.lowercase(Locale.ROOT)
                if (subCode in knownCodes) return subCode
            }
            val token = codeTokenPattern.matcher(remark)
            if (token.find()) {
                val code = token.group(1)!!.lowercase(Locale.ROOT)
                if (code in knownCodes) return code
            }
        }
        return null
    }

    /** Normalizes a direct code/subdivision token ("SE", "gb-eng") to a drawable code. */
    private fun normalizeToken(key: String): String? {
        if (key.length == 2) return key
        val subdivision = subdivisionPattern.matcher(key)
        if (subdivision.matches()) {
            return "${subdivision.group(1)}_${subdivision.group(2)}"
        }
        return null
    }

    /** Bundled vector drawable name for a code ("gb-eng" and "gb_eng" both → gb_eng file). */
    fun flagDrawableName(code: String): String =
        "shroxy_flag_" + code.trim().lowercase(Locale.ROOT).replace('-', '_')

    private val drawableIdCache = HashMap<String, Int?>()

    /** Test-only reset so cache assertions do not depend on shared object-state order. */
    @VisibleForTesting
    @Synchronized
    fun clearDrawableCacheForTest() {
        drawableIdCache.clear()
    }

    /**
     * Cached name-based drawable lookup. [lookup] is the Resources lookup (id, or 0/unknown
     * when absent); a miss is cached as null so repeated compositions of a code without a
     * bundled flag (e.g. um) stay cheap and degrade to the code badge.
     */
    @Synchronized
    fun flagDrawableRes(lookup: (String) -> Int, code: String): Int? {
        val name = flagDrawableName(code)
        // Null values are legitimate cache entries (miss → badge); getOrPut would re-query
        // them, so membership is checked explicitly.
        if (name in drawableIdCache) return drawableIdCache[name]
        val id = lookup(name)
        val result = if (id == 0) null else id
        drawableIdCache[name] = result
        return result
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
 * Country flag: a bundled vector resolved from a leading emoji pair, the country name/code,
 * or a code token in the server remark; when a code is known but has no bundled vector, a
 * rounded code badge; otherwise a layer2 rounded placeholder with a flag icon.
 */
@Composable
fun CountryFlag(
    remark: String,
    country: String?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val shape = RoundedCornerShape(4.dp)
    val code = remember(remark, country) { CountryFlags.resolveCode(country, remark) }
    val resId = remember(code, context) {
        code?.let {
            CountryFlags.flagDrawableRes({ name ->
                context.resources.getIdentifier(name, "drawable", context.packageName)
            }, it)
        }
    }
    when {
        code != null && resId != null -> Image(
            painter = rememberVectorPainter(ImageVector.vectorResource(resId)),
            contentDescription = null,
            modifier = modifier.clip(shape),
            contentScale = ContentScale.Crop,
        )

        code != null -> FlagCodeBadge(code = code, modifier = modifier, shape = shape)

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

/** Fallback for a known country without a bundled vector: code on a rounded surface box. */
@Composable
private fun FlagCodeBadge(code: String, modifier: Modifier, shape: RoundedCornerShape) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainer),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = code.replace('-', '_').take(2).uppercase(Locale.ROOT),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
