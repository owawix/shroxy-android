package com.v2ray.ang.service

import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RealPingWorkerServiceTest {

    private fun profile(configType: EConfigType, alpn: String? = null): ProfileItem =
        ProfileItem.create(configType).apply {
            server = "example.com"
            serverPort = "443"
            this.alpn = alpn
        }

    // region tcpPingEligible

    @Test
    fun hysteria2IsNotTcpPingEligible() {
        assertFalse(tcpPingEligible(profile(EConfigType.HYSTERIA2)))
    }

    @Test
    fun wireguardIsNotTcpPingEligible() {
        assertFalse(tcpPingEligible(profile(EConfigType.WIREGUARD)))
    }

    @Test
    fun h3AlpnIsNotTcpPingEligible() {
        assertFalse(tcpPingEligible(profile(EConfigType.VLESS, alpn = "h3")))
    }

    @Test
    fun mixedH3AlpnIsNotTcpPingEligibleAndRoutesToNativeMeasure() {
        val mixed = profile(EConfigType.VLESS, alpn = "h3,h2")
        assertFalse(tcpPingEligible(mixed))
        assertEquals(ListPingRoute.NATIVE_MEASURE, resolveListPingRoute(mixed))
    }

    @Test
    fun complexTypesAreNotTcpPingEligible() {
        assertFalse(tcpPingEligible(profile(EConfigType.CUSTOM)))
        assertFalse(tcpPingEligible(profile(EConfigType.POLICYGROUP)))
        assertFalse(tcpPingEligible(profile(EConfigType.PROXYCHAIN)))
    }

    @Test
    fun simpleTcpProfilesWithoutH3AreTcpPingEligible() {
        assertTrue(tcpPingEligible(profile(EConfigType.VLESS)))
        assertTrue(tcpPingEligible(profile(EConfigType.TROJAN)))
        assertTrue(tcpPingEligible(profile(EConfigType.SHADOWSOCKS)))
        assertTrue(tcpPingEligible(profile(EConfigType.VMESS)))
    }

    @Test
    fun missingServerOrPortIsNotTcpPingEligible() {
        val noServer = profile(EConfigType.VLESS).apply { server = "" }
        val noPort = profile(EConfigType.VLESS).apply { serverPort = null }
        val badPort = profile(EConfigType.VLESS).apply { serverPort = "abc" }
        assertFalse(tcpPingEligible(noServer))
        assertFalse(tcpPingEligible(noPort))
        assertFalse(tcpPingEligible(badPort))
    }

    // endregion

    // region resolveListPingRoute

    @Test
    fun eligibleProfileRoutesToTcpConnect() {
        assertEquals(ListPingRoute.TCP_CONNECT, resolveListPingRoute(profile(EConfigType.VLESS)))
        assertEquals(ListPingRoute.TCP_CONNECT, resolveListPingRoute(profile(EConfigType.TROJAN)))
    }

    @Test
    fun nonEligibleProfilesRouteToNativeMeasureInsteadOfMinusOne() {
        assertEquals(ListPingRoute.NATIVE_MEASURE, resolveListPingRoute(profile(EConfigType.HYSTERIA2)))
        assertEquals(ListPingRoute.NATIVE_MEASURE, resolveListPingRoute(profile(EConfigType.WIREGUARD)))
        assertEquals(ListPingRoute.NATIVE_MEASURE, resolveListPingRoute(profile(EConfigType.VLESS, alpn = "h3")))
        assertEquals(ListPingRoute.NATIVE_MEASURE, resolveListPingRoute(profile(EConfigType.CUSTOM)))
        assertEquals(ListPingRoute.NATIVE_MEASURE, resolveListPingRoute(profile(EConfigType.POLICYGROUP)))
    }

    // endregion
}
