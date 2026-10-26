package com.v2ray.ang.handler

import android.content.Context
import com.v2ray.ang.AppConfig
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.reset
import org.mockito.kotlin.whenever
import java.io.File
import java.io.IOException
import java.security.MessageDigest

class GeoUpdateManagerTest {

    private val main = MmkvTestStorage.main
    private val settings = MmkvTestStorage.settings

    private val mainValues = mutableMapOf<String, String>()
    private val mainLongValues = mutableMapOf<String, Long>()
    private val settingsValues = mutableMapOf<String, String>()
    private val settingsBoolValues = mutableMapOf<String, Boolean>()

    @TempDir
    lateinit var assetDir: File

    private lateinit var downloader: FakeGeoDownloader

    @BeforeEach
    fun prepareStorage() {
        mainValues.clear()
        mainLongValues.clear()

        reset(main)
        whenever(main.decodeString(any())).thenAnswer { mainValues[it.getArgument<String>(0)] }
        whenever(main.allKeys()).thenAnswer { mainValues.keys.toTypedArray() }
        whenever(main.decodeLong(any<String>(), any<Long>())).thenAnswer {
            mainLongValues[it.getArgument<String>(0)] ?: it.getArgument<Long>(1)
        }
        whenever(main.encode(any<String>(), any<String>())).thenAnswer {
            val value = it.getArgument<String?>(1)
            if (value == null) {
                mainValues.remove(it.getArgument<String>(0))
            } else {
                mainValues[it.getArgument(0)] = value
            }
            true
        }
        whenever(main.encode(any<String>(), any<Long>())).thenAnswer {
            mainLongValues[it.getArgument(0)] = it.getArgument(1)
            true
        }

        reset(settings)
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

        downloader = FakeGeoDownloader()
    }

    @Test
    fun `304 not modified keeps files and etag state untouched`() = runBlocking {
        MmkvManager.encodeGeoLastETag("etag-1")
        MmkvManager.encodeGeoLastCheckedAt(1000L)
        downloader.releaseResponse = GeoReleaseResponse(304, "etag-2", null)

        val result = manager().checkAndUpdate()

        assertEquals(GeoUpdateResult.UpToDate, result)
        assertEquals(listOf<String?>("etag-1"), downloader.releaseRequests)
        assertTrue(downloader.downloads.isEmpty())
        assertTrue(assetDir.listFiles()!!.isEmpty())
        // ETag is not replaced by the 304 response; only the check timestamp moves.
        assertEquals("etag-1", MmkvManager.decodeGeoLastETag())
        assertEquals(FIXED_TIME, MmkvManager.decodeGeoLastCheckedAt())
    }

    @Test
    fun `tag change downloads verifies and swaps both geo files`() = runBlocking {
        MmkvManager.encodeGeoLastETag("etag-1")
        MmkvManager.encodeGeoLastTag("v20240101")
        writeOldFiles()

        val geoip = "new geoip payload".toByteArray()
        val geosite = "new geosite payload".toByteArray()
        downloader.enqueueRelease(tag = "v20240601", eTag = "etag-2")
        downloader.enqueueAsset("geoip.dat", geoip)
        downloader.enqueueAsset("geosite.dat", geosite)

        val result = manager().checkAndUpdate()

        assertEquals(GeoUpdateResult.Updated("v20240601"), result)
        assertEquals(
            listOf(
                "$DOWNLOAD_BASE/geoip.dat",
                "$DOWNLOAD_BASE/geoip.dat.sha256sum",
                "$DOWNLOAD_BASE/geosite.dat",
                "$DOWNLOAD_BASE/geosite.dat.sha256sum",
            ),
            downloader.downloads.map { it.first },
        )
        assertArrayEquals(geoip, File(assetDir, "geoip.dat").readBytes())
        assertArrayEquals(geosite, File(assetDir, "geosite.dat").readBytes())
        assertNoTempFiles()
        assertEquals("v20240601", MmkvManager.decodeGeoLastTag())
        assertEquals("etag-2", MmkvManager.decodeGeoLastETag())
        assertEquals(FIXED_TIME, MmkvManager.decodeGeoLastCheckedAt())
    }

    @Test
    fun `sha256 mismatch keeps old files and removes temp files`() = runBlocking {
        MmkvManager.encodeGeoLastTag("v20240101")
        writeOldFiles()

        downloader.enqueueRelease(tag = "v20240601", eTag = "etag-2")
        downloader.enqueueAssetWithBadSum("geoip.dat", "tampered payload".toByteArray())

        val result = manager().checkAndUpdate()

        assertTrue(result is GeoUpdateResult.Failed)
        assertEquals("old geoip", File(assetDir, "geoip.dat").readText())
        assertEquals("old geosite", File(assetDir, "geosite.dat").readText())
        assertNoTempFiles()
        // The installed tag must not advance past the files that are still on disk.
        assertEquals("v20240101", MmkvManager.decodeGeoLastTag())
    }

    @Test
    fun `release check network failure is a no-op`() = runBlocking {
        MmkvManager.encodeGeoLastETag("etag-1")
        MmkvManager.encodeGeoLastTag("v20240101")
        writeOldFiles()
        downloader.releaseError = IOException("release check timed out")

        val result = manager().checkAndUpdate()

        assertTrue(result is GeoUpdateResult.Failed)
        assertTrue((result as GeoUpdateResult.Failed).reason.contains("timed out"))
        assertTrue(downloader.downloads.isEmpty())
        assertOldFilesIntact()
        assertNoTempFiles()
        assertEquals("etag-1", MmkvManager.decodeGeoLastETag())
    }

    @Test
    fun `download failure keeps old files and removes temp files`() = runBlocking {
        MmkvManager.encodeGeoLastTag("v20240101")
        writeOldFiles()

        downloader.enqueueRelease(tag = "v20240601", eTag = "etag-2")
        downloader.enqueueAsset("geosite.dat", "new geosite payload".toByteArray())
        // Fail the very first file download: nothing has been swapped yet, so the
        // previously installed files must remain untouched.
        downloader.failUrls += "$DOWNLOAD_BASE/geoip.dat"

        val result = manager().checkAndUpdate()

        assertTrue(result is GeoUpdateResult.Failed)
        assertOldFilesIntact()
        assertNoTempFiles()
        assertEquals("v20240101", MmkvManager.decodeGeoLastTag())
    }

    @Test
    fun `failed install leaves release state unchanged so next check re-downloads`() = runBlocking {
        MmkvManager.encodeGeoLastETag("etag-1")
        MmkvManager.encodeGeoLastTag("v20240101")
        MmkvManager.encodeGeoLastCheckedAt(1000L)
        writeOldFiles()

        downloader.enqueueRelease(tag = "v20240601", eTag = "etag-2")
        downloader.enqueueAssetWithBadSum("geoip.dat", "tampered payload".toByteArray())

        val first = manager().checkAndUpdate()

        assertTrue(first is GeoUpdateResult.Failed)
        // A failed install must not persist the new ETag/check time; otherwise the
        // next run would send "etag-2" and get 304-suppressed without re-downloading.
        assertEquals("etag-1", MmkvManager.decodeGeoLastETag())
        assertEquals("v20240101", MmkvManager.decodeGeoLastTag())
        assertEquals(1000L, MmkvManager.decodeGeoLastCheckedAt())

        downloader.enqueueAsset("geoip.dat", "new geoip payload".toByteArray())
        downloader.enqueueAsset("geosite.dat", "new geosite payload".toByteArray())

        val second = manager().checkAndUpdate()

        assertEquals(GeoUpdateResult.Updated("v20240601"), second)
        // Both checks sent the old ETag, proving the failed install did not persist it.
        assertEquals(listOf<String?>("etag-1", "etag-1"), downloader.releaseRequests)
        assertEquals("etag-2", MmkvManager.decodeGeoLastETag())
        assertEquals(FIXED_TIME, MmkvManager.decodeGeoLastCheckedAt())
    }

    @Test
    fun `bypass ruleset pins exactly the bundled geo tags`() {
        settingsBoolValues[AppConfig.PREF_SHROXY_BYPASS_RU] = true
        val rulesets = SettingsManager.getEffectiveRoutingRulesets(mock<Context>()).rulesets

        val pinned = listOf(
            AppConfig.GEOIP_PRIVATE,
            AppConfig.GEOSITE_PRIVATE,
            "geosite:category-ru",
            "geoip:ru",
        )
        val referenced = rulesets.flatMap { listOfNotNull(it.ip, it.domain).flatten() }
            .filter { it.startsWith("geoip:") || it.startsWith("geosite:") }

        // Loyalsoldier renaming a tag (e.g. category-ru) would break core start; this
        // pins the bypass ruleset to the tags bundled in assets/geoip.dat + geosite.dat.
        assertEquals(pinned, referenced)
        assertFalse(referenced.any { it.startsWith("ext:") })
    }

    private fun manager() = GeoUpdateManager(
        assetDir = assetDir,
        downloader = downloader,
        clock = { FIXED_TIME },
    )

    private fun writeOldFiles() {
        File(assetDir, "geoip.dat").writeText("old geoip")
        File(assetDir, "geosite.dat").writeText("old geosite")
    }

    private fun assertOldFilesIntact() {
        assertEquals("old geoip", File(assetDir, "geoip.dat").readText())
        assertEquals("old geosite", File(assetDir, "geosite.dat").readText())
    }

    private fun assertNoTempFiles() {
        assertTrue(
            assetDir.listFiles()!!.none { it.name.endsWith("_temp") || it.name.endsWith(".sha256sum_temp") },
            "leftover temp files: ${assetDir.list()!!.toList()}",
        )
    }

    private class FakeGeoDownloader : GeoUpdateDownloader {
        val releaseRequests = mutableListOf<String?>()
        val downloads = mutableListOf<Pair<String, File>>()
        val fileContents = mutableMapOf<String, ByteArray>()
        val sumContents = mutableMapOf<String, String>()
        val failUrls = mutableSetOf<String>()
        var releaseResponse: GeoReleaseResponse? = null
        var releaseError: IOException? = null

        fun enqueueRelease(tag: String, eTag: String) {
            releaseResponse = GeoReleaseResponse(200, eTag, """{"tag_name":"$tag"}""")
        }

        fun enqueueAsset(name: String, content: ByteArray) {
            fileContents["$DOWNLOAD_BASE/$name"] = content
            sumContents["$DOWNLOAD_BASE/$name.sha256sum"] = "${sha256Hex(content)}  $name\n"
        }

        /** Enqueues an asset whose sidecar carries a digest that does not match the payload. */
        fun enqueueAssetWithBadSum(name: String, content: ByteArray) {
            fileContents["$DOWNLOAD_BASE/$name"] = content
            sumContents["$DOWNLOAD_BASE/$name.sha256sum"] =
                "${"0".repeat(64)}  $name\n"
        }

        override suspend fun fetchLatestRelease(ifNoneMatch: String?): GeoReleaseResponse {
            releaseRequests += ifNoneMatch
            releaseError?.let { throw it }
            return releaseResponse ?: error("No release response queued")
        }

        override suspend fun download(url: String, targetFile: File) {
            downloads += url to targetFile
            if (url in failUrls) throw IOException("Download failed: $url")
            if (url.endsWith(".sha256sum")) {
                targetFile.writeText(sumContents.getValue(url))
            } else {
                targetFile.writeBytes(fileContents.getValue(url))
            }
        }
    }

    companion object {
        private const val FIXED_TIME = 1_700_000_000_000L
        private const val DOWNLOAD_BASE =
            "https://github.com/Loyalsoldier/v2ray-rules-dat/releases/latest/download"

        @BeforeAll
        @JvmStatic
        fun initializeStorage() {
            MmkvTestStorage.initialize()
            // Route LogUtil to the "off" level so unit tests never touch android.util.Log.
            val field = LogUtil::class.java.getDeclaredField("cachedMinPriority")
            field.isAccessible = true
            field.set(LogUtil, Int.MAX_VALUE)
        }

        private fun sha256Hex(content: ByteArray): String {
            return MessageDigest.getInstance("SHA-256").digest(content)
                .joinToString("") { "%02x".format(it) }
        }
    }
}
