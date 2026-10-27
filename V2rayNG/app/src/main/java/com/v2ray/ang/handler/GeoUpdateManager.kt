package com.v2ray.ang.handler

import android.content.Context
import com.v2ray.ang.AppConfig
import com.v2ray.ang.util.JsonUtil
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.util.Utils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Result of a geo files update check.
 */
sealed interface GeoUpdateResult {
    /** The stored release tag matches the latest release, or the API answered 304. */
    data object UpToDate : GeoUpdateResult

    /** New release downloaded, verified and installed for the given tag. */
    data class Updated(val tag: String) : GeoUpdateResult

    /** The check or install failed; previous geo files are untouched. */
    data class Failed(val reason: String) : GeoUpdateResult
}

/**
 * Response of a conditional GET against the GitHub latest-release endpoint.
 *
 * @property statusCode HTTP status code (304 when the stored ETag still matches).
 * @property eTag ETag header of the response, null if absent.
 * @property body Response body, null for 304.
 */
data class GeoReleaseResponse(val statusCode: Int, val eTag: String?, val body: String?)

/**
 * Injectable network boundary for [GeoUpdateManager]. The production implementation is
 * [OkHttpGeoUpdateDownloader]; tests substitute a fake.
 */
interface GeoUpdateDownloader {
    /**
     * Performs a conditional GET against the release metadata endpoint.
     *
     * @param ifNoneMatch Stored ETag, sent as If-None-Match, or null.
     * @return The response; 304 carries a null body.
     * @throws IOException On network failure or a non-2xx/304 status code.
     */
    suspend fun fetchLatestRelease(ifNoneMatch: String?): GeoReleaseResponse

    /**
     * Downloads [url] fully into [targetFile].
     *
     * @throws IOException On network failure or a non-2xx status code.
     */
    suspend fun download(url: String, targetFile: File)
}

/**
 * OkHttp-based [GeoUpdateDownloader]. Lives next to the manager so the state machine
 * stays free of Android framework types; [HttpUtil][com.v2ray.ang.util.HttpUtil] is left
 * untouched because it has no ETag/conditional-GET support.
 */
class OkHttpGeoUpdateDownloader(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
        .readTimeout(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()
) : GeoUpdateDownloader {

    override suspend fun fetchLatestRelease(ifNoneMatch: String?): GeoReleaseResponse {
        val requestBuilder = Request.Builder()
            .url(GeoUpdateManager.LATEST_RELEASE_URL)
            .get()
            .header("Accept", "application/vnd.github+json")
            .header("Connection", "close")
        if (!ifNoneMatch.isNullOrBlank()) {
            requestBuilder.header("If-None-Match", ifNoneMatch)
        }
        client.newCall(requestBuilder.build()).execute().use { response ->
            if (response.code == HTTP_NOT_MODIFIED) {
                return GeoReleaseResponse(response.code, response.header("ETag"), null)
            }
            if (!response.isSuccessful) {
                throw IOException("Release check failed with status code ${response.code}")
            }
            return GeoReleaseResponse(
                response.code,
                response.header("ETag"),
                response.body?.string()
            )
        }
    }

    override suspend fun download(url: String, targetFile: File) {
        val request = Request.Builder()
            .url(url)
            .get()
            .header("Connection", "close")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Download failed with status code ${response.code}: $url")
            }
            val body = response.body ?: throw IOException("Empty response body: $url")
            body.byteStream().use { input ->
                targetFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        }
    }

    private companion object {
        const val TIMEOUT_MILLIS = 15_000L
        const val HTTP_NOT_MODIFIED = 304
    }
}

/**
 * Pure-logic state machine for the daily geoip.dat/geosite.dat update.
 *
 * Check flow: conditional GET on the Loyalsoldier latest-release endpoint with the
 * stored ETag (304 = no-op); a changed tag_name triggers a download of each
 * `<name>` + `<name>.sha256sum` pair, sha256 verification and an atomic
 * `<name>_temp` + rename swap. Any failure keeps the previously installed files,
 * deletes the temp files and is reported as [GeoUpdateResult.Failed]; nothing is
 * thrown out of [checkAndUpdate].
 *
 * @property assetDir Directory the geo files are installed into (context.getDir(AppConfig.DIR_ASSETS)).
 * @property downloader Injectable network boundary.
 * @property clock Time supplier for lastCheckedAt, epoch millis.
 */
class GeoUpdateManager(
    private val assetDir: File,
    private val downloader: GeoUpdateDownloader = OkHttpGeoUpdateDownloader(),
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val geoFileNames: List<String> = DEFAULT_GEO_FILE_NAMES
) {

    /**
     * Checks the latest Loyalsoldier release and, when the tag changed, downloads,
     * verifies and installs the geo files. Network and disk runs on [Dispatchers.IO].
     *
     * @return UpToDate / Updated(tag) / Failed(reason); never throws.
     */
    suspend fun checkAndUpdate(): GeoUpdateResult = withContext(Dispatchers.IO) {
        try {
            checkInternal()
        } catch (e: Exception) {
            cleanupTempFiles()
            LogUtil.e(TAG, "Geo update failed: release=${MmkvManager.decodeGeoLastTag()}", e)
            GeoUpdateResult.Failed(e.message ?: e.javaClass.simpleName)
        }
    }

    private suspend fun checkInternal(): GeoUpdateResult {
        val storedETag = MmkvManager.decodeGeoLastETag()
        val release = try {
            downloader.fetchLatestRelease(storedETag)
        } catch (e: Exception) {
            LogUtil.e(TAG, "Geo update: release check failed, component=release-check", e)
            return GeoUpdateResult.Failed(e.message ?: "release check failed")
        }

        if (release.statusCode == HTTP_NOT_MODIFIED) {
            MmkvManager.encodeGeoLastCheckedAt(clock())
            LogUtil.i(TAG, "Geo update: up to date (304), etag=$storedETag")
            return GeoUpdateResult.UpToDate
        }

        val tag = parseReleaseTag(release.body)
        if (tag == null) {
            LogUtil.e(TAG, "Geo update: release check returned no tag_name")
            return GeoUpdateResult.Failed("no tag_name in release response")
        }
        if (tag == MmkvManager.decodeGeoLastTag()) {
            // Genuine up-to-date: the release check itself succeeded, so the
            // release-check state may advance.
            MmkvManager.encodeGeoLastCheckedAt(clock())
            MmkvManager.encodeGeoLastETag(release.eTag)
            LogUtil.i(TAG, "Geo update: up to date, tag=$tag")
            return GeoUpdateResult.UpToDate
        }

        try {
            for (name in geoFileNames) {
                installFile(name, tag)
            }
        } catch (e: Exception) {
            LogUtil.e(TAG, "Geo update: install failed, tag=$tag", e)
            return GeoUpdateResult.Failed(e.message ?: "install failed")
        }

        // Persist the release-check state only after the install succeeded; a
        // failed install must leave it unchanged so the next check re-downloads
        // instead of being 304-suppressed by the stored ETag.
        MmkvManager.encodeGeoLastCheckedAt(clock())
        MmkvManager.encodeGeoLastETag(release.eTag)
        MmkvManager.encodeGeoLastTag(tag)
        LogUtil.i(TAG, "Geo update: installed, tag=$tag")
        return GeoUpdateResult.Updated(tag)
    }

    private suspend fun installFile(name: String, tag: String) {
        val targetTemp = File(assetDir, "${name}_temp")
        val sumTemp = File(assetDir, "${name}.sha256sum_temp")
        try {
            downloader.download(fileDownloadUrl(name), targetTemp)
            downloader.download(sumDownloadUrl(name), sumTemp)
            if (!verifySha256(targetTemp, sumTemp)) {
                throw IOException("sha256 mismatch for $name in release $tag")
            }
            val target = File(assetDir, name)
            if (!targetTemp.renameTo(target)) {
                throw IOException("Failed to rename ${targetTemp.name} to ${target.name}")
            }
        } finally {
            if (targetTemp.exists()) targetTemp.delete()
            if (sumTemp.exists()) sumTemp.delete()
        }
    }

    private fun cleanupTempFiles() {
        // Only delete this manager's own temp names; a blanket "*_temp" sweep
        // could clobber an in-flight manual user-asset download that
        // shares the same user-asset directory.
        for (name in geoFileNames) {
            File(assetDir, "${name}_temp").delete()
            File(assetDir, "${name}.sha256sum_temp").delete()
        }
    }

    companion object {
        const val LATEST_RELEASE_URL =
            "https://api.github.com/repos/Loyalsoldier/v2ray-rules-dat/releases/latest"

        val DEFAULT_GEO_FILE_NAMES = listOf("geoip.dat", "geosite.dat")

        /**
         * Creates a manager rooted at the app's user asset directory.
         */
        fun fromContext(
            context: Context,
            downloader: GeoUpdateDownloader = OkHttpGeoUpdateDownloader(),
            clock: () -> Long = { System.currentTimeMillis() }
        ): GeoUpdateManager {
            val dir = File(Utils.userAssetPath(context))
            return GeoUpdateManager(dir, downloader, clock)
        }

        private const val TAG = AppConfig.TAG
        private const val HTTP_NOT_MODIFIED = 304
        private const val LATEST_DOWNLOAD_BASE =
            "https://github.com/Loyalsoldier/v2ray-rules-dat/releases/latest/download"
        private const val SHA256_HEX_LENGTH = 64

        private fun parseReleaseTag(body: String?): String? {
            if (body.isNullOrBlank()) return null
            return runCatching { JsonUtil.parseString(body)?.get("tag_name")?.asString }.getOrNull()
        }

        private fun fileDownloadUrl(name: String): String = "$LATEST_DOWNLOAD_BASE/$name"

        private fun sumDownloadUrl(name: String): String = "$LATEST_DOWNLOAD_BASE/$name.sha256sum"

        /**
         * Verifies the sha256 of [file] against the hex digest in the [sidecar]
         * sha256sum file, tolerant of the "<hash>  <name>" format.
         */
        internal fun verifySha256(file: File, sidecar: File): Boolean {
            val expected = parseSha256Sum(sidecar.readText())
            if (expected == null) {
                LogUtil.e(TAG, "Geo update: unparseable sha256sum for ${file.name}")
                return false
            }
            return sha256Hex(file).equals(expected, ignoreCase = true)
        }

        /**
         * Parses the hex digest out of a sha256sum file line such as
         * "<hash>  <filename>"; null when no 64-char hex token is present.
         */
        internal fun parseSha256Sum(content: String): String? {
            return content.trim().split(Regex("\\s+"))
                .firstOrNull { it.length == SHA256_HEX_LENGTH && it.all { c -> c.isDigit() || c in 'a'..'f' || c in 'A'..'F' } }
                ?.lowercase()
        }

        private fun sha256Hex(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    digest.update(buffer, 0, read)
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }
    }
}
