package com.v2ray.ang.dto

import com.v2ray.ang.dto.entities.SubscriptionUsageInfo

data class GroupMapItem(
    var id: String,
    var remarks: String,
    val usageInfo: SubscriptionUsageInfo? = null,
    /** Last successful subscription update; -1 or 0 means never updated. */
    val lastUpdated: Long = 0,
)