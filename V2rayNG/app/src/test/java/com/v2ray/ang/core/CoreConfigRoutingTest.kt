package com.v2ray.ang.core

import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.V2rayConfig
import com.v2ray.ang.dto.entities.RulesetItem
import com.v2ray.ang.handler.EffectiveRoutingSource
import com.v2ray.ang.handler.MmkvTestStorage
import com.v2ray.ang.handler.SettingsManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.reset
import org.mockito.kotlin.whenever

class CoreConfigRoutingTest {

    private val settings = MmkvTestStorage.settings
    private val settingsValues = mutableMapOf<String, String>()
    private val settingsBoolValues = mutableMapOf<String, Boolean>()

    @BeforeEach
    fun prepareStorage() {
        settingsValues.clear()
        settingsBoolValues.clear()

        reset(settings)
        whenever(settings.decodeString(any<String>())).thenAnswer { settingsValues[it.getArgument<String>(0)] }
        whenever(settings.decodeString(any<String>(), any<String>())).thenAnswer {
            settingsValues[it.getArgument<String>(0)] ?: it.getArgument<String?>(1)
        }
        whenever(settings.decodeBool(any<String>(), any<Boolean>())).thenAnswer {
            settingsBoolValues[it.getArgument<String>(0)] ?: it.getArgument<Boolean>(1)
        }
        whenever(settings.encode(any<String>(), any<Boolean>())).thenAnswer {
            settingsBoolValues[it.getArgument(0)] = it.getArgument(1)
            true
        }
    }

    @Test
    fun `global mode generates as-is strategy and proxy rules without ext geo paths`() {
        val effective = SettingsManager.getEffectiveRoutingRulesets(mock())

        assertEquals(EffectiveRoutingSource.SHROXY_GLOBAL, effective.source)
        assertEquals("AsIs", CoreConfigManager.routingDomainStrategy(effective.source))

        val rules = effective.rulesets.map { CoreConfigManager.buildRoutingRule(it, effective.source)!! }

        assertEquals(
            listOf("Block udp443", "Direct LAN IP", "Direct LAN domains", "Final proxy"),
            effective.rulesets.map { it.remarks },
        )
        assertEquals(
            listOf(AppConfig.TAG_BLOCKED, AppConfig.TAG_DIRECT, AppConfig.TAG_DIRECT, AppConfig.TAG_PROXY),
            rules.map { it.outboundTag },
        )
        assertEquals("443", rules[0].port)
        assertEquals("udp", rules[0].network)
        assertEquals(listOf(AppConfig.GEOIP_PRIVATE), rules[1].ip)
        assertEquals(listOf(AppConfig.GEOSITE_PRIVATE), rules[2].domain)
        assertEquals("0-65535", rules[3].port)
        assertNoExtGeoPaths(rules)
    }

    @Test
    fun `bypass mode forces IPIfNonMatch and keeps ru geo tags untouched`() {
        SettingsManager.setShroxyBypassRu(true)
        val effective = SettingsManager.getEffectiveRoutingRulesets(mock())

        assertEquals(EffectiveRoutingSource.SHROXY_BYPASS, effective.source)
        assertEquals("IPIfNonMatch", CoreConfigManager.routingDomainStrategy(effective.source))

        val rules = effective.rulesets.map { CoreConfigManager.buildRoutingRule(it, effective.source)!! }

        assertEquals(
            listOf("Block udp443", "Direct LAN IP", "Direct LAN domains", "Bypass Russia domains", "Bypass Russia IP"),
            effective.rulesets.map { it.remarks },
        )
        assertEquals(
            listOf(AppConfig.TAG_BLOCKED, AppConfig.TAG_DIRECT, AppConfig.TAG_DIRECT, AppConfig.TAG_DIRECT, AppConfig.TAG_DIRECT),
            rules.map { it.outboundTag },
        )
        assertEquals(listOf(AppConfig.GEOIP_PRIVATE), rules[1].ip)
        assertEquals(listOf(AppConfig.GEOSITE_PRIVATE), rules[2].domain)
        assertEquals(listOf("geosite:category-ru"), rules[3].domain)
        assertEquals(listOf("geoip:ru"), rules[4].ip)
        assertNoExtGeoPaths(rules)
    }

    @Test
    fun `shroxy modes ignore the routing domain strategy pref`() {
        settingsValues[AppConfig.PREF_ROUTING_DOMAIN_STRATEGY] = "IPOnDemand"

        assertEquals("AsIs", CoreConfigManager.routingDomainStrategy(EffectiveRoutingSource.SHROXY_GLOBAL))
        assertEquals("IPIfNonMatch", CoreConfigManager.routingDomainStrategy(EffectiveRoutingSource.SHROXY_BYPASS))

        // Legacy (null) source keeps reading the pref.
        assertEquals("IPOnDemand", CoreConfigManager.routingDomainStrategy(null))
        settingsValues.remove(AppConfig.PREF_ROUTING_DOMAIN_STRATEGY)
        assertEquals("AsIs", CoreConfigManager.routingDomainStrategy(null))
    }

    @Test
    fun `legacy source still rewrites geoip cn and private to ext file`() {
        val legacy = RulesetItem(
            remarks = "Legacy direct",
            outboundTag = AppConfig.TAG_DIRECT,
            ip = listOf(AppConfig.GEOIP_CN, AppConfig.GEOIP_PRIVATE, "geoip:ru"),
        )

        val rule = CoreConfigManager.buildRoutingRule(legacy, null)!!

        assertEquals(
            listOf(
                "ext:${AppConfig.GEOIP_ONLY_CN_PRIVATE_DAT}:cn",
                "ext:${AppConfig.GEOIP_ONLY_CN_PRIVATE_DAT}:private",
                "geoip:ru",
            ),
            rule.ip,
        )
    }

    @Test
    fun `shroxy sources never rewrite geoip entries to ext file`() {
        val legacyShaped = RulesetItem(
            remarks = "Legacy shaped",
            outboundTag = AppConfig.TAG_DIRECT,
            ip = listOf(AppConfig.GEOIP_CN, AppConfig.GEOIP_PRIVATE, "geoip:ru"),
        )

        for (source in EffectiveRoutingSource.entries) {
            val rule = CoreConfigManager.buildRoutingRule(legacyShaped, source)!!
            assertEquals(listOf(AppConfig.GEOIP_CN, AppConfig.GEOIP_PRIVATE, "geoip:ru"), rule.ip)
        }
    }

    private fun assertNoExtGeoPaths(rules: List<V2rayConfig.RoutingBean.RulesBean>) {
        val allIps = rules.flatMap { it.ip.orEmpty() }
        assertTrue(allIps.none { it.startsWith("ext:") }) { "unexpected ext geo path in $allIps" }
    }

    private fun mock() = org.mockito.kotlin.mock<android.content.Context>()

    companion object {
        @BeforeAll
        @JvmStatic
        fun initializeStorage() {
            MmkvTestStorage.initialize()
        }
    }
}
