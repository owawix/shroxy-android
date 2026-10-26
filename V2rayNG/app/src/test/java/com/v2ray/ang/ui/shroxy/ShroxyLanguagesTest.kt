package com.v2ray.ang.ui.shroxy

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ShroxyLanguagesTest {

    @Test
    fun explicitAppLocaleRuSelectsRussian() {
        assertEquals("ru", ShroxyLanguages.selectedLanguage("ru", "en"))
    }

    @Test
    fun explicitAppLocaleEnSelectsEnglish() {
        assertEquals("en", ShroxyLanguages.selectedLanguage("en", "ru"))
    }

    @Test
    fun explicitAppLocaleWithRegionResolvesByLanguage() {
        assertEquals("ru", ShroxyLanguages.selectedLanguage("ru-RU", "en"))
    }

    @Test
    fun noAppLocaleWithRussianSystemSelectsRussian() {
        assertEquals("ru", ShroxyLanguages.selectedLanguage(null, "ru"))
        assertEquals("ru", ShroxyLanguages.selectedLanguage("", "ru"))
    }

    @Test
    fun noAppLocaleWithNonRussianSystemSelectsEnglish() {
        assertEquals("en", ShroxyLanguages.selectedLanguage(null, "en"))
        assertEquals("en", ShroxyLanguages.selectedLanguage(null, "de"))
    }

    @Test
    fun unsupportedAppLocaleFallsBackToSystemLocale() {
        assertEquals("ru", ShroxyLanguages.selectedLanguage("de", "ru"))
        assertEquals("en", ShroxyLanguages.selectedLanguage("ja", "en"))
    }
}
