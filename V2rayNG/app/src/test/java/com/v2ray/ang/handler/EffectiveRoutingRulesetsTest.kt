package com.v2ray.ang.handler

import com.tencent.mmkv.MMKV
import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.dto.entities.RulesetItem
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.util.JsonUtil
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.reset
import org.mockito.kotlin.whenever

class EffectiveRoutingRulesetsTest {

    private val main = MmkvTestStorage.main
    private val profileFull = MmkvTestStorage.profileFull
    private val settings = MmkvTestStorage.settings

    private val mainValues = mutableMapOf<String, String>()
    private val profileFullValues = mutableMapOf<String, String>()
    private val settingsValues = mutableMapOf<String, String>()
    private val settingsBoolValues = mutableMapOf<String, Boolean>()

    @BeforeEach
    fun prepareStorage() {
        mainValues.clear()
        profileFullValues.clear()
        settingsValues.clear()
        settingsBoolValues.clear()

        reset(main, profileFull, settings)
        whenever(main.decodeString(any())).thenAnswer { mainValues[it.getArgument<String>(0)] }
        whenever(main.allKeys()).thenAnswer { mainValues.keys.toTypedArray() }

        whenever(profileFull.decodeString(any())).thenAnswer { profileFullValues[it.getArgument<String>(0)] }

        whenever(settings.decodeString(any<String>())).thenAnswer { settingsValues[it.getArgument<String>(0)] }
        whenever(settings.decodeString(any<String>(), any<String>())).thenAnswer {
            settingsValues[it.getArgument<String>(0)] ?: it.getArgument<String?>(1)
        }
        whenever(settings.decodeBool(any<String>(), any<Boolean>())).thenAnswer {
            settingsBoolValues[it.getArgument<String>(0)] ?: it.getArgument<Boolean>(1)
        }
        whenever(settings.encode(any<String>(), any<String>())).thenAnswer {
            val value = it.getArgument<String?>(1)
            if (value == null) {
                settingsValues.remove(it.getArgument<String>(0))
            } else {
                settingsValues[it.getArgument(0)] = value
            }
            true
        }
        whenever(settings.encode(any<String>(), any<Boolean>())).thenAnswer {
            settingsBoolValues[it.getArgument(0)] = it.getArgument(1)
            true
        }

        // Select a non-custom server so routingRulesetsBypassLan reaches the ruleset check,
        // and neutralize the LAN-bypass override so it derives from the effective rulesets.
        mainValues["SELECTED_SERVER"] = "server-guid"
        profileFullValues["server-guid"] = JsonUtil.toJson(ProfileItem.create(EConfigType.VMESS))
        settingsValues[AppConfig.PREF_VPN_BYPASS_LAN] = "0"
    }

    @Test
    fun `bypass ru defaults to false`() {
        assertFalse(SettingsManager.isShroxyBypassRu())
        assertEquals(EffectiveRoutingSource.SHROXY_GLOBAL, effective().source)
    }

    @Test
    fun `setter persists bypass ru flag`() {
        SettingsManager.setShroxyBypassRu(true)
        assertTrue(SettingsManager.isShroxyBypassRu())
        assertEquals(EffectiveRoutingSource.SHROXY_BYPASS, effective().source)

        SettingsManager.setShroxyBypassRu(false)
        assertFalse(SettingsManager.isShroxyBypassRu())
        assertEquals(EffectiveRoutingSource.SHROXY_GLOBAL, effective().source)
    }

    @Test
    fun `global mode blocks udp443 and proxies everything else`() {
        val rulesets = globalRulesets()

        assertEquals(listOf("Block udp443", "Direct LAN IP", "Direct LAN domains", "Final proxy"),
            rulesets.map { it.remarks })
        assertEquals(AppConfig.TAG_BLOCKED, rulesets[0].outboundTag)
        assertEquals("443", rulesets[0].port)
        assertEquals("udp", rulesets[0].network)
        assertEquals(AppConfig.TAG_DIRECT, rulesets[1].outboundTag)
        assertEquals(listOf(AppConfig.GEOIP_PRIVATE), rulesets[1].ip)
        assertEquals(AppConfig.TAG_DIRECT, rulesets[2].outboundTag)
        assertEquals(listOf(AppConfig.GEOSITE_PRIVATE), rulesets[2].domain)
        assertEquals(AppConfig.TAG_PROXY, rulesets[3].outboundTag)
        assertEquals("0-65535", rulesets[3].port)
    }

    @Test
    fun `bypass mode routes ru domains and ips direct without catch all`() {
        SettingsManager.setShroxyBypassRu(true)
        val rulesets = effective().rulesets

        assertEquals(
            listOf("Block udp443", "Direct LAN IP", "Direct LAN domains", "Bypass Russia domains", "Bypass Russia IP"),
            rulesets.map { it.remarks },
        )
        assertEquals(AppConfig.TAG_BLOCKED, rulesets[0].outboundTag)
        assertEquals("443", rulesets[0].port)
        assertEquals("udp", rulesets[0].network)
        assertEquals(AppConfig.TAG_DIRECT, rulesets[1].outboundTag)
        assertEquals(listOf(AppConfig.GEOIP_PRIVATE), rulesets[1].ip)
        assertEquals(AppConfig.TAG_DIRECT, rulesets[2].outboundTag)
        assertEquals(listOf(AppConfig.GEOSITE_PRIVATE), rulesets[2].domain)
        assertEquals(AppConfig.TAG_DIRECT, rulesets[3].outboundTag)
        assertEquals(listOf("geosite:category-ru"), rulesets[3].domain)
        assertEquals(AppConfig.TAG_DIRECT, rulesets[4].outboundTag)
        assertEquals(listOf("geoip:ru"), rulesets[4].ip)

        assertTrue(rulesets.none { it.port == "0-65535" })
        assertTrue(rulesets.none { it.outboundTag == AppConfig.TAG_PROXY })
    }

    @Test
    fun `private resources go direct in both modes`() {
        for (rulesets in listOf(globalRulesets(), bypassRulesets())) {
            val directRules = rulesets.filter { it.outboundTag == AppConfig.TAG_DIRECT }
            assertTrue(directRules.any { it.ip?.contains(AppConfig.GEOIP_PRIVATE) == true })
            assertTrue(directRules.any { it.domain?.contains(AppConfig.GEOSITE_PRIVATE) == true })
        }
    }

    @Test
    fun `vpn lan bypass is enabled in both modes`() {
        assertTrue(SettingsManager.routingRulesetsBypassLan())

        SettingsManager.setShroxyBypassRu(true)
        assertTrue(SettingsManager.routingRulesetsBypassLan())
    }

    private fun effective() = SettingsManager.getEffectiveRoutingRulesets(mock())

    private fun globalRulesets() = effective().rulesets

    private fun bypassRulesets(): List<RulesetItem> {
        SettingsManager.setShroxyBypassRu(true)
        return effective().rulesets
    }

    companion object {
        @BeforeAll
        @JvmStatic
        fun initializeStorage() {
            MmkvTestStorage.initialize()
        }
    }
}
