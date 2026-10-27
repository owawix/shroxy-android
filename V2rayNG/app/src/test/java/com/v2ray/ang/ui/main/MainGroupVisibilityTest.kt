package com.v2ray.ang.ui.main

import com.v2ray.ang.dto.entities.SubscriptionCache
import com.v2ray.ang.dto.entities.SubscriptionItem
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MainGroupVisibilityTest {

    private fun sub(guid: String, remarks: String = guid, lastError: String? = null) =
        SubscriptionCache(guid, SubscriptionItem(remarks = remarks, lastError = lastError))

    @Test
    fun pseudoAllGroupIsKeptWhenItHasServers() {
        val subscriptions = listOf(sub(""))

        val visible = visibleGroupSubscriptions(subscriptions) { true }

        assertEquals(listOf(""), visible.map { it.guid })
    }

    @Test
    fun pseudoAllGroupIsDroppedWithoutServers() {
        val subscriptions = listOf(sub(""))

        val visible = visibleGroupSubscriptions(subscriptions) { false }

        assertEquals(emptyList<String>(), visible.map { it.guid })
    }

    @Test
    fun subscriptionWithServersIsKept() {
        val subscriptions = listOf(sub("sub-1"))

        val visible = visibleGroupSubscriptions(subscriptions) { true }

        assertEquals(listOf("sub-1"), visible.map { it.guid })
    }

    @Test
    fun emptySubscriptionIsFilteredOut() {
        val subscriptions = listOf(sub("sub-1"))

        val visible = visibleGroupSubscriptions(subscriptions) { false }

        assertEquals(emptyList<String>(), visible.map { it.guid })
    }

    @Test
    fun errorSubscriptionIsVisibleWithoutServers() {
        val subscriptions = listOf(sub("sub-1", lastError = "network"))

        val visible = visibleGroupSubscriptions(subscriptions) { false }

        assertEquals(listOf("sub-1"), visible.map { it.guid })
    }

    @Test
    fun emptySubscriptionWithoutErrorIsFilteredOut() {
        val subscriptions = listOf(sub("sub-1", lastError = null), sub("sub-2", lastError = "http_403"))

        val visible = visibleGroupSubscriptions(subscriptions) { false }

        assertEquals(listOf("sub-2"), visible.map { it.guid })
    }

    @Test
    fun filteringResolvesServersByGuidNotByPosition() {
        val subscriptions = listOf(sub("empty-a"), sub("full-b"), sub("empty-c"))
        val queried = mutableListOf<String>()

        val visible = visibleGroupSubscriptions(subscriptions) { guid ->
            queried += guid
            guid == "full-b"
        }

        assertEquals(listOf("empty-a", "full-b", "empty-c"), queried)
        assertEquals(listOf("full-b"), visible.map { it.guid })
    }
}
