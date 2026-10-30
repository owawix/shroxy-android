package com.v2ray.ang.service

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkerParameters
import androidx.work.multiprocess.RemoteWorkManager
import com.v2ray.ang.AppConfig
import com.v2ray.ang.handler.GeoUpdateManager
import com.v2ray.ang.handler.GeoUpdateResult
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.util.LogUtil
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * Daily background check for geoip.dat/geosite.dat updates (Loyalsoldier releases).
 *
 * Scheduled with [RemoteWorkManager] just like [com.v2ray.ang.handler.SubscriptionUpdater],
 * so the work lands in the app-configured default WorkManager process (`:bg`,
 * see [com.v2ray.ang.AngApplication]); MMKV stores are multi-process, so results are
 * visible everywhere. Pure background work: no UI, no notification, no foreground service.
 */
class GeoUpdateWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        LogUtil.i(TAG, "GeoUpdateWorker: geo update check starting")
        return when (val result = GeoUpdateManager.fromContext(applicationContext).checkAndUpdate()) {
            is GeoUpdateResult.UpToDate -> {
                LogUtil.i(TAG, "GeoUpdateWorker: geo files up to date")
                Result.success()
            }

            is GeoUpdateResult.Updated -> {
                LogUtil.i(TAG, "GeoUpdateWorker: geo files updated to tag=${result.tag}")
                Result.success()
            }

            is GeoUpdateResult.Failed -> {
                // GeoUpdateResult does not distinguish transient network failures from
                // permanent ones; Result.retry() would risk an infinite loop. The previous
                // geo files keep working, so log and let the next periodic run retry.
                LogUtil.w(TAG, "GeoUpdateWorker: geo update failed (${result.reason}), next periodic run retries")
                Result.success()
            }
        }
    }

    companion object {
        private const val TAG = AppConfig.TAG

        const val PERIODIC_WORK_NAME = "shroxy_geo_update"
        const val CATCHUP_WORK_NAME = "shroxy_geo_update_catchup"
        const val CHECK_INTERVAL_HOURS = 24L
        val CHECK_INTERVAL_MILLIS: Long = TimeUnit.HOURS.toMillis(CHECK_INTERVAL_HOURS)

        /**
         * Current schedule version. Version 2 anchored the periodic task to the next
         * local 01:37; lower versions carry the legacy "next app start" phase.
         */
        internal const val SCHEDULE_VERSION = 2

        /**
         * A check is due when it never ran or the last one is older than the interval.
         */
        fun isCheckDue(lastCheckedAt: Long, now: Long): Boolean =
            lastCheckedAt <= 0L || now - lastCheckedAt >= CHECK_INTERVAL_MILLIS

        /**
         * True when the stored schedule version predates the current 01:37-anchored
         * schedule, so the periodic task must be re-enqueued with a fresh initial delay.
         */
        internal fun shouldReschedule(storedVersion: Int): Boolean =
            storedVersion < SCHEDULE_VERSION

        /**
         * Ensures the periodic 24h task exists and, when the last check is stale or
         * absent, enqueues a one-time catch-up so the first update happens soon after
         * install. Safe to call on every app start and UI launch.
         *
         * On the first sync after the schedule-version migration the legacy periodic
         * task is cancelled and re-enqueued (REPLACE) with an initial delay pointing
         * at the next local 01:37; persisting the version keeps every later sync on
         * the KEEP path so the standing 24h cycle is never reset by an app restart.
         */
        fun sync(context: Context) {
            val remoteWorkManager = RemoteWorkManager.getInstance(context)
            if (shouldReschedule(MmkvManager.decodeGeoScheduleVersion())) {
                val initialDelay = GeoUpdateManager.millisUntilNextDaily(
                    System.currentTimeMillis(),
                    ZoneId.systemDefault()
                )
                remoteWorkManager.cancelUniqueWork(PERIODIC_WORK_NAME)
                val periodic = PeriodicWorkRequestBuilder<GeoUpdateWorker>(
                    CHECK_INTERVAL_HOURS,
                    TimeUnit.HOURS
                )
                    .setConstraints(networkConstraints())
                    .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
                    .build()
                remoteWorkManager.enqueueUniquePeriodicWork(
                    PERIODIC_WORK_NAME,
                    ExistingPeriodicWorkPolicy.REPLACE,
                    periodic
                )
                MmkvManager.encodeGeoScheduleVersion(SCHEDULE_VERSION)
                LogUtil.i(
                    TAG,
                    "GeoUpdateWorker: periodic geo update rescheduled to 01:37 anchor (initialDelay=$initialDelay ms)"
                )
            } else {
                val periodic = PeriodicWorkRequestBuilder<GeoUpdateWorker>(
                    CHECK_INTERVAL_HOURS,
                    TimeUnit.HOURS
                )
                    .setConstraints(networkConstraints())
                    .build()
                remoteWorkManager.enqueueUniquePeriodicWork(
                    PERIODIC_WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    periodic
                )
                LogUtil.i(TAG, "GeoUpdateWorker: periodic geo update ensured")
            }

            if (isCheckDue(MmkvManager.decodeGeoLastCheckedAt(), System.currentTimeMillis())) {
                val catchUp = OneTimeWorkRequestBuilder<GeoUpdateWorker>()
                    .setConstraints(networkConstraints())
                    .build()
                RemoteWorkManager.getInstance(context)
                    .enqueueUniqueWork(
                        CATCHUP_WORK_NAME,
                        ExistingWorkPolicy.KEEP,
                        catchUp
                    )
                LogUtil.i(TAG, "GeoUpdateWorker: catch-up geo update enqueued")
            }
        }

        private fun networkConstraints(): Constraints =
            Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
    }
}
