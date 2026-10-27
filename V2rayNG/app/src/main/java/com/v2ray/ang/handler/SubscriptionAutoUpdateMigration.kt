package com.v2ray.ang.handler

import com.v2ray.ang.AppConfig
import com.v2ray.ang.util.LogUtil

/**
 * One-time migration that moves subscription records predating per-subscription
 * hourly scheduling onto the owner's auto-update policy: every subscription with a
 * non-empty URL gets autoUpdate=true and a 60-minute interval. Records without a URL
 * and all server data are left untouched.
 *
 * Idempotency is guarded by [AppConfig.PREF_SHROXY_SUB_MIGRATION_V1]; the flag is set
 * before rescheduling so a crash mid-reschedule never rewrites the fields. Call at app
 * start (AngApplication.onCreate) after WorkManager initialization.
 */
object SubscriptionAutoUpdateMigration {

    /**
     * Runs the migration when the persisted flag is absent.
     *
     * @param reschedule Re-schedules workers after the fields changed; the default
     * forces a full reschedule so the new intervals take effect without an app restart.
     * Replaceable seam for unit tests (RemoteWorkManager is unavailable on the JVM).
     * @return true when the migration ran, false when the flag already marked it done.
     */
    fun run(
        reschedule: () -> Unit = { SubscriptionUpdater.sync(forceReschedule = true) }
    ): Boolean {
        if (MmkvManager.decodeSettingsBool(AppConfig.PREF_SHROXY_SUB_MIGRATION_V1, false)) {
            return false
        }

        val migrated = MmkvManager.decodeSubscriptions()
            .filter { it.subscription.url.isNotEmpty() }
            .onEach { sub ->
                sub.subscription.autoUpdate = true
                sub.subscription.updateInterval = AppConfig.SUBSCRIPTION_DEFAULT_UPDATE_INTERVAL_MINUTES
                MmkvManager.encodeSubscription(sub.guid, sub.subscription)
            }
            .count()

        MmkvManager.encodeSettings(AppConfig.PREF_SHROXY_SUB_MIGRATION_V1, true)
        LogUtil.i(
            AppConfig.TAG,
            "SubscriptionAutoUpdateMigration: enabled hourly auto-update for $migrated subscriptions"
        )
        reschedule()
        return true
    }
}
