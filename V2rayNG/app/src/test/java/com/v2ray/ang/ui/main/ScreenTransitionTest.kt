package com.v2ray.ang.ui.main

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ScreenTransitionTest {

    @Test
    fun homeToSettingsSlidesForward() {
        val direction = shroxyScreenTransitionDirection(ShroxyScreen.HOME, ShroxyScreen.SETTINGS)

        assertEquals(ShroxyScreenTransitionDirection.FORWARD, direction)
    }

    @Test
    fun settingsToLanguageSlidesForward() {
        val direction = shroxyScreenTransitionDirection(ShroxyScreen.SETTINGS, ShroxyScreen.LANGUAGE)

        assertEquals(ShroxyScreenTransitionDirection.FORWARD, direction)
    }

    @Test
    fun languageToSettingsSlidesBackward() {
        val direction = shroxyScreenTransitionDirection(ShroxyScreen.LANGUAGE, ShroxyScreen.SETTINGS)

        assertEquals(ShroxyScreenTransitionDirection.BACKWARD, direction)
    }

    @Test
    fun settingsToHomeSlidesBackward() {
        val direction = shroxyScreenTransitionDirection(ShroxyScreen.SETTINGS, ShroxyScreen.HOME)

        assertEquals(ShroxyScreenTransitionDirection.BACKWARD, direction)
    }

    @Test
    fun sameScreenResolvesToForwardDefault() {
        // AnimatedContent never runs a transition for equal states, so from == to is unreachable
        // through the UI; the pure function still resolves it to the benign FORWARD default.
        assertEquals(ShroxyScreenTransitionDirection.FORWARD, shroxyScreenTransitionDirection(ShroxyScreen.HOME, ShroxyScreen.HOME))
        assertEquals(ShroxyScreenTransitionDirection.FORWARD, shroxyScreenTransitionDirection(ShroxyScreen.SETTINGS, ShroxyScreen.SETTINGS))
        assertEquals(ShroxyScreenTransitionDirection.FORWARD, shroxyScreenTransitionDirection(ShroxyScreen.LANGUAGE, ShroxyScreen.LANGUAGE))
    }
}
