package com.v2ray.ang.ui.main

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MainTestRequestsTest {
    @Test
    fun onlyTheLatestCurrentRequestCanFinishOnce() {
        val requests = MainTestRequests()
        assertFalse(requests.isTesting)
        assertFalse(requests.completeCurrent(""))
        val first = requests.beginCurrent()
        val second = requests.beginCurrent()
        assertNotEquals(first, second)
        assertFalse(requests.completeCurrent(first))
        assertTrue(requests.isTesting)
        assertTrue(requests.completeCurrent(second))
        assertFalse(requests.completeCurrent(second))
        assertFalse(requests.isTesting)
    }

    @Test
    fun stopAndRestartInvalidateThePreviousConnectionRequest() {
        val requests = MainTestRequests()
        val stopped = requests.beginCurrent()
        requests.invalidateCurrent()
        assertFalse(requests.completeCurrent(stopped))
        val restarted = requests.beginCurrent()
        assertFalse(requests.completeCurrent(stopped))
        assertTrue(requests.completeCurrent(restarted))
    }

    @Test
    fun bulkCancellationAndReplacementRejectLateCompletionEvenInTheSameGroup() {
        val requests = MainTestRequests()
        val cancelled = requests.beginBulk("group")
        requests.cancelBulk()
        assertNull(requests.completeBulk(cancelled.id))
        val replaced = requests.beginBulk("group")
        val current = requests.beginBulk("group")
        assertNull(requests.completeBulk(replaced.id))
        assertEquals(current, requests.bulk)
        assertEquals(current, requests.completeBulk(current.id))
        assertNull(requests.completeBulk(current.id))
        assertFalse(requests.isTesting)
    }

    @Test
    fun currentResultDoesNotFinishAnIndependentBulkTest() {
        val requests = MainTestRequests()
        val bulk = requests.beginBulk("") // All servers is a valid group ID.
        val current = requests.beginCurrent()
        assertTrue(requests.completeCurrent(current))
        assertTrue(requests.isTesting)
        assertEquals(bulk, requests.completeBulk(bulk.id))
        assertFalse(requests.isTesting)
    }

    @Test
    fun testingGroupIdFollowsTheNewestBulkAndClearsWhenItCompletes() {
        val requests = MainTestRequests()
        assertNull(requests.testingGroupId)

        val first = requests.beginBulk("group-1")
        assertEquals("group-1", requests.testingGroupId)

        // A replacement bulk moves the flag to the new group; a late reply for the
        // replaced bulk must not clear it.
        val second = requests.beginBulk("group-2")
        assertEquals("group-2", requests.testingGroupId)
        assertNull(requests.completeBulk(first.id))
        assertEquals("group-2", requests.testingGroupId)

        // onTestsFinished completes the live bulk: the flag clears with it.
        assertEquals(second, requests.completeBulk(second.id))
        assertNull(requests.testingGroupId)
        assertFalse(requests.isTesting)
    }

    @Test
    fun cancelBulkClearsTestingGroupId() {
        // cancelAllPing cancels the live bulk: the flag clears with it.
        val requests = MainTestRequests()
        requests.beginBulk("group-1")
        assertEquals("group-1", requests.testingGroupId)
        requests.cancelBulk()
        assertNull(requests.testingGroupId)
        assertFalse(requests.isTesting)
    }
}
