package com.v2ray.ang.service

import android.content.Context
import com.v2ray.ang.core.CoreConfigManager
import com.v2ray.ang.core.CoreNativeManager
import com.v2ray.ang.dto.RealPingEvent
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.extension.isComplexType
import com.v2ray.ang.extension.isNotNullEmpty
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.handler.SpeedtestManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.isActive
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

internal object RealPingExecutionLimiter {
    private val customConfigMutex = Mutex()

    suspend fun <T> run(configType: EConfigType, block: () -> T): T {
        // Custom profiles bypass speed-test trimming and start complete Xray configs.
        // Parallel teardown can abort the native probe process, so serialize their
        // JNI measurements globally across batches.
        return if (configType == EConfigType.CUSTOM) {
            customConfigMutex.withLock { block() }
        } else {
            block()
        }
    }
}

/** How a single list ping measures [ProfileItem]. */
internal enum class ListPingRoute {
    /** Raw TCP handshake against the profile's server:port. */
    TCP_CONNECT,

    /** Native Xray outbound delay measurement (complex/QUIC-UDP/h3 profiles). */
    NATIVE_MEASURE,
}

/**
 * True when the profile's reachability can be judged by a raw TCP handshake:
 * simple type, TCP-based protocol (no Hysteria2/WireGuard) and no HTTP/3 ALPN.
 * The check must mirror the TCP pre-condition in [startRealPing].
 */
internal fun tcpPingEligible(profile: ProfileItem): Boolean {
    return !profile.configType.isComplexType()
        && profile.configType != EConfigType.HYSTERIA2
        && profile.configType != EConfigType.WIREGUARD
        && profile.alpn?.startsWith("h3") != true
        && profile.server.isNotNullEmpty()
        && profile.serverPort?.toIntOrNull() != null
}

/**
 * Routes a list ping for [profile]. Profiles a TCP handshake cannot judge fall
 * back to the native measurement so the list test still yields a number.
 */
internal fun resolveListPingRoute(profile: ProfileItem): ListPingRoute {
    return if (tcpPingEligible(profile)) ListPingRoute.TCP_CONNECT else ListPingRoute.NATIVE_MEASURE
}

/**
 * Worker that runs a batch of real-ping tests independently.
 * Each batch owns its own CoroutineScope/dispatcher and can be cancelled separately.
 */
class RealPingWorkerService(
    private val context: Context,
    private val guids: List<String>,
    private val onlyTcp: Boolean = false,
    private val onEvent: (RealPingEvent) -> Unit = {}
) {
    private val job = SupervisorJob()
    private val concurrency = SettingsManager.getRealPingConcurrency()
    private val dispatcher = Executors.newFixedThreadPool(if (onlyTcp) concurrency * 2 else concurrency).asCoroutineDispatcher()
    private val scope = CoroutineScope(job + dispatcher + CoroutineName("RealPingBatchWorker"))

    private val runningCount = AtomicInteger(0)
    private val totalCount = AtomicInteger(0)

    fun start() {
        val jobs = guids.map { guid ->
            totalCount.incrementAndGet()
            scope.launch {
                runningCount.incrementAndGet()
                try {
                    val result = if (onlyTcp) startTcping(guid) else startRealPing(guid)
                    if (scope.isActive) {
                        onEvent(RealPingEvent.Result(guid, result))
                    }
                } catch (_: Throwable) {
                    // ignore
                } finally {
                    val count = totalCount.decrementAndGet()
                    val left = runningCount.decrementAndGet()
                    if (scope.isActive) {
                        onEvent(RealPingEvent.Progress("$left / $count"))
                    }
                }
            }
        }

        scope.launch {
            try {
                joinAll(*jobs.toTypedArray())
                if (isActive) {
                    onEvent(RealPingEvent.Finish("0"))
                }
            } catch (_: CancellationException) {
                // If cancelled, don't send finish event to avoid confusion
            } finally {
                close()
            }
        }
    }

    fun cancel() {
        job.cancel()
    }

    private fun close() {
        try {
            dispatcher.close()
        } catch (_: Throwable) {
            // ignore
        }
    }

    private suspend fun startRealPing(guid: String): Long {
        val retFailure = -1L

        val config = MmkvManager.decodeServerConfig(guid) ?: return retFailure
        if (tcpPingEligible(config)) {
            val url = config.server.orEmpty()
            val port = config.serverPort.orEmpty().toInt()
            val tcpTime = SpeedtestManager.socketConnectTime(url, port, 1000)
            if (tcpTime <= -1L) {
                return retFailure
            }
        }

        val configResult = CoreConfigManager.getV2rayConfig4Speedtest(context, guid)
        if (!configResult.status) {
            return retFailure
        }
        return RealPingExecutionLimiter.run(config.configType) {
            CoreNativeManager.measureOutboundDelay(configResult.content, SettingsManager.getDelayTestUrl())
        }
    }

    private suspend fun startTcping(guid: String): Long {
        val retFailure = -1L

        val config = MmkvManager.decodeServerConfig(guid) ?: return retFailure
        if (resolveListPingRoute(config) == ListPingRoute.TCP_CONNECT) {
            val url = config.server.orEmpty()
            val port = config.serverPort.orEmpty().toInt()
            return SpeedtestManager.socketConnectTime(url, port, 1000)
        }

        // The profile is not judgeable by a TCP handshake (complex/Hysteria2/WireGuard/h3):
        // measure through the same native Xray path as startRealPing, with the same limiter
        // policy, so the list test still yields a number instead of -1.
        val configResult = CoreConfigManager.getV2rayConfig4Speedtest(context, guid)
        if (!configResult.status) {
            return retFailure
        }
        return RealPingExecutionLimiter.run(config.configType) {
            CoreNativeManager.measureOutboundDelay(configResult.content, SettingsManager.getDelayTestUrl())
        }
    }
}
