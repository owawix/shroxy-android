package com.v2ray.ang.dto

import com.v2ray.ang.dto.entities.SubscriptionUsageInfo

data class GroupMapItem(
    var id: String,
    var remarks: String,
    val usageInfo: SubscriptionUsageInfo? = null,
    /** Last successful subscription update; -1 or 0 means never updated. */
    val lastUpdated: Long = 0,
    /** Subscription URL for external open (globe action); null/blank hides the action. */
    val subscriptionUrl: String? = null,
    /** Stable error code of the last failed update, or null; mapped to localized text in the UI. */
    val lastError: String? = null,
)