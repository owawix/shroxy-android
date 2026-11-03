package com.v2ray.ang.handler

import android.content.Context
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import com.tencent.mmkv.MMKV
import com.tencent.mmkv.MMKVHandler
import com.tencent.mmkv.MMKVLogLevel
import com.tencent.mmkv.MMKVRecoverStrategic
import com.v2ray.ang.AppConfig.DEFAULT_SUBSCRIPTION_ID
import com.v2ray.ang.AppConfig.PREF_IS_BOOTED
import com.v2ray.ang.AppConfig.PREF_ROUTING_RULESET
import com.v2ray.ang.AppConfig.TAG
import com.v2ray.ang.BuildConfig
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.extension.isGroupType
import com.v2ray.ang.dto.entities.RulesetItem
import com.v2ray.ang.dto.entities.ServerAffiliationInfo
import com.v2ray.ang.dto.entities.SubscriptionCache
import com.v2ray.ang.dto.entities.SubscriptionItem
import com.v2ray.ang.util.JsonUtil
import com.v2ray.ang.util.Utils
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import java.security.SecureRandom
import java.util.Random

internal class ProfileStorageException(message: String) : IllegalStateException(message)

object MmkvManager {

    //region private

    private const val ID_MAIN = "MAIN"
    private const val ID_PROFILE_FULL_CONFIG = "PROFILE_FULL_CONFIG"
    private const val ID_SERVER_RAW = "SERVER_RAW"
    private const val ID_SERVER_AFF = "SERVER_AFF"
    private const val ID_SUB = "SUB"
    private const val ID_SETTING = "SETTING"
    private const val KEY_SELECTED_SERVER = "SELECTED_SERVER"
    private const val KEY_ANG_CONFIGS = "ANG_CONFIGS"
    private const val KEY_SUB_SERVER_PREFIX = "SUB_SERVERS_"
    private const val KEY_SUB_IDS = "SUB_IDS"
    private const val KEY_GEO_LAST_CHECKED_AT = "pref_shroxy_geo_last_checked_at"
    private const val KEY_GEO_LAST_ETAG = "pref_shroxy_geo_last_etag"
    private const val KEY_GEO_LAST_TAG = "pref_shroxy_geo_last_tag"
    private const val KEY_GEO_IP_LAST_SUCCESS_AT = "pref_shroxy_geo_ip_last_success_at"
    private const val KEY_GEO_SITE_LAST_SUCCESS_AT = "pref_shroxy_geo_site_last_success_at"
    private const val KEY_GEO_IP_STATUS_OK = "pref_shroxy_geo_ip_status_ok"
    private const val KEY_GEO_SITE_STATUS_OK = "pref_shroxy_geo_site_status_ok"
    private const val KEY_GEO_SCHEDULE_VERSION = "pref_shroxy_geo_schedule_version"
    private const val KEY_HWID = "pref_shroxy_hwid"
    private const val HWID_LENGTH = 15
    private const val HWID_ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789"

    private val recoveryHandler = object : MMKVHandler {
        override fun onMMKVCRCCheckFail(mmapID: String) =
            recoverFromStorageError(mmapID, "CRC check")

        override fun onMMKVFileLengthError(mmapID: String) =
            recoverFromStorageError(mmapID, "file length check")

        override fun wantLogRedirecting(): Boolean = false

        override fun mmkvLog(
            level: MMKVLogLevel,
            file: String,
            line: Int,
            function: String,
            message: String
        ) = Unit
    }

    private val mainStorage by lazy { MMKV.mmkvWithID(ID_MAIN, MMKV.MULTI_PROCESS_MODE) }
    private val profileFullStorage by lazy { MMKV.mmkvWithID(ID_PROFILE_FULL_CONFIG, MMKV.MULTI_PROCESS_MODE) }
    private val serverRawStorage by lazy { MMKV.mmkvWithID(ID_SERVER_RAW, MMKV.MULTI_PROCESS_MODE) }
    private val serverAffStorage by lazy { MMKV.mmkvWithID(ID_SERVER_AFF, MMKV.MULTI_PROCESS_MODE) }
    private val subStorage by lazy { MMKV.mmkvWithID(ID_SUB, MMKV.MULTI_PROCESS_MODE) }
    private val settingsStorage by lazy { MMKV.mmkvWithID(ID_SETTING, MMKV.MULTI_PROCESS_MODE) }

    // JVM unit tests run without the MMKV native library, so the cross-process lock
    // (an unmockable native method) would throw UnsatisfiedLinkError there;
    // MmkvTestStorage substitutes no-op seams for the whole JVM run. Production always
    // takes the real native lock. Remove the seams only with the JVM MMKV test harness.
    internal var acquireProfileIndexLock: () -> Unit = { mainStorage.lock() }
    internal var releaseProfileIndexLock: () -> Unit = { mainStorage.unlock() }

    private inline fun <T> withProfileIndexLock(block: () -> T): T {
        return synchronized(mainStorage) {
            acquireProfileIndexLock()
            try {
                block()
            } finally {
                releaseProfileIndexLock()
            }
        }
    }

    // Same JVM-native rationale as the lock seams above: removeValuesForKeys is a
    // native method and cannot execute in JVM unit tests. Production always removes
    // payloads for real.
    internal var removeProfilePayloadsHandler: (Collection<String>) -> Unit =
        { removeProfilePayloads(it) }

    private fun removeProfilePayloads(guids: Collection<String>) {
        if (guids.isEmpty()) return
        val keys = guids.toTypedArray()
        profileFullStorage.removeValuesForKeys(keys)
        serverAffStorage.removeValuesForKeys(keys)
        serverRawStorage.removeValuesForKeys(keys)
    }

    private fun requireStorageWrite(success: Boolean, message: String) {
        if (!success) throw ProfileStorageException(message)
    }

    private fun persistServerList(serverList: List<String>, subscriptionId: String): Boolean {
        return mainStorage.encode(serverListKey(subscriptionId), JsonUtil.toJson(serverList))
    }

    private fun serverListKey(subscriptionId: String): String {
        return "$KEY_SUB_SERVER_PREFIX${getSubscriptionId(subscriptionId)}"
    }

    /**
     * Returns every server referenced outside the target group, or null if the raw indexes
     * cannot provide a complete view.
     */
    private fun decodeServersReferencedByOtherGroups(subscriptionId: String): Set<String>? {
        val targetKey = serverListKey(subscriptionId)
        val keys = mainStorage.allKeys() ?: return null
        if (targetKey !in keys) return null

        val referencedServers = mutableSetOf<String>()
        for (key in keys) {
            if (!key.startsWith(KEY_SUB_SERVER_PREFIX) || key == targetKey) continue

            val json = mainStorage.decodeString(key)
            if (json.isNullOrBlank()) return null
            val serverIds = JsonUtil.fromJsonSafe(json, Array<String>::class.java) ?: return null
            referencedServers.addAll(serverIds)
        }
        return referencedServers
    }

    //endregion

    /**
     * Initializes MMKV with best-effort recovery so a damaged store is not silently discarded.
     */
    fun initialize(context: Context) {
        val logLevel = if (BuildConfig.DEBUG) {
            MMKVLogLevel.LevelDebug
        } else {
            MMKVLogLevel.LevelInfo
        }
        MMKV.initialize(
            context,
            context.filesDir.resolve("mmkv").absolutePath,
            null,
            logLevel,
            recoveryHandler
        )
    }

    private fun recoverFromStorageError(mmapID: String, error: String): MMKVRecoverStrategic {
        Log.e(TAG, "MMKV $error failed for $mmapID; attempting data recovery")
        return MMKVRecoverStrategic.OnErrorRecover
    }

    //region Server

    /**
     * Reads the legacy server list from KEY_ANG_CONFIGS for migration.
     * This method is for migration purposes only.
     *
     * @return The JSON string of legacy server list, or null if not exists.
     */
    fun readLegacyServerList(): String? {
        return mainStorage.decodeString(KEY_ANG_CONFIGS)
    }


    /**
     * Gets the selected server GUID.
     *
     * @return The selected server GUID.
     */
    fun getSelectServer(): String? {
        return mainStorage.decodeString(KEY_SELECTED_SERVER)
    }

    /**
     * Sets the selected server GUID.
     *
     * @param guid The server GUID.
     */
    fun setSelectServer(guid: String) {
        withProfileIndexLock {
            mainStorage.encode(KEY_SELECTED_SERVER, guid)
        }
    }

    /**
     * Encodes the server list for a given subscription.
     * Saves to the subscription's serverList (including default subscription for ungrouped servers).
     *
     * @param serverList The list of server GUIDs.
     * @param subscriptionId The subscription ID.
     */
    fun encodeServerList(serverList: MutableList<String>, subscriptionId: String) {
        withProfileIndexLock {
            persistServerList(serverList, subscriptionId)
        }
    }


    /**
     * Decodes the server list for a given subscription.
     * If subscriptionId is empty, returns ungrouped servers.
     * Otherwise, returns servers from the specified subscription's serverList.
     *
     * @param subscriptionId The subscription ID.
     * @return The list of server GUIDs.
     */
    fun decodeServerList(subscriptionId: String): MutableList<String> {
        val json = mainStorage.decodeString(serverListKey(subscriptionId))
        return if (json.isNullOrBlank()) {
            mutableListOf()
        } else {
            JsonUtil.fromJsonSafe(json, Array<String>::class.java)?.toMutableList() ?: mutableListOf()
        }
    }

    /**
     * Decodes all server list (merged from all subscriptions including default subscription).
     * Use this when you need the complete server list.
     *
     * @return The list of all server GUIDs.
     */
    fun decodeAllServerList(): MutableList<String> {
        val allServers = mutableListOf<String>()
        val subsList = decodeSubsList()

        // If DEFAULT_SUBSCRIPTION_ID is not in the subscriptions list, add its servers
        if (!subsList.contains(DEFAULT_SUBSCRIPTION_ID)) {
            allServers.addAll(decodeServerList(DEFAULT_SUBSCRIPTION_ID))
        }

        // Add servers from all subscriptions
        subsList.forEach { guid ->
            allServers.addAll(decodeServerList(guid))
        }

        return allServers
    }


    /**
     * Decodes the server configuration.
     *
     * @param guid The server GUID.
     * @return The server configuration.
     */
    fun decodeServerConfig(guid: String): ProfileItem? {
        if (guid.isBlank()) {
            return null
        }
        val json = profileFullStorage.decodeString(guid)
        if (json.isNullOrBlank()) {
            return null
        }
        return JsonUtil.fromJsonSafe(json, ProfileItem::class.java)
    }


    /**
     * Encodes the server configuration.
     *
     * @param guid The server GUID.
     * @param config The server configuration.
     * @return The server GUID.
     */
    fun encodeServerConfig(guid: String, config: ProfileItem): String {
        val key = guid.ifBlank { Utils.getUuid() }
        withProfileIndexLock {
            requireStorageWrite(
                profileFullStorage.encode(key, JsonUtil.toJson(config)),
                "Failed to save profile payload",
            )

            // Use default subscription for servers without subscription
            val subId = getSubscriptionId(config.subscriptionId)
            val serverList = decodeServerList(subId)

            if (!serverList.contains(key)) {
                serverList.add(0, key)
                requireStorageWrite(
                    persistServerList(serverList, subId),
                    "Failed to publish profile index",
                )
                if (getSelectServer().isNullOrBlank()) {
                    requireStorageWrite(
                        mainStorage.encode(KEY_SELECTED_SERVER, key),
                        "Failed to update selected profile",
                    )
                }
            }
        }

        return key
    }

    /**
     * Saves a profile batch before publishing its group index and removing replaced payloads.
     *
     * @param profiles Generated GUIDs and parsed profiles, in insertion order.
     * @param rawConfigs Optional raw configuration payloads keyed by profile GUID.
     * @param subscriptionId The destination subscription ID.
     * @param append Whether to append to the existing group index.
     */
    internal fun saveServerProfiles(
        profiles: Map<String, ProfileItem>,
        rawConfigs: Map<String, String>,
        subscriptionId: String,
        append: Boolean,
    ) {
        if (profiles.isEmpty()) return

        withProfileIndexLock {
            val replacedServers = if (append) {
                emptyList()
            } else {
                decodeServerList(subscriptionId).toList()
            }
            val previousSelection = getSelectServer()
            val selectedProfile = if (!append &&
                previousSelection != null &&
                previousSelection in replacedServers
            ) {
                decodeServerConfig(previousSelection)
            } else {
                null
            }
            val replacementSelection = ProfileReplacement.findSelectedReplacement(
                profiles = profiles,
                currentSelection = previousSelection,
                selectedProfile = selectedProfile,
            )

            profiles.forEach { (guid, profile) ->
                requireStorageWrite(
                    profileFullStorage.encode(guid, JsonUtil.toJson(profile)),
                    "Failed to save profile payload",
                )
                rawConfigs[guid]?.let { raw ->
                    requireStorageWrite(
                        serverRawStorage.encode(guid, raw),
                        "Failed to save raw profile payload",
                    )
                }
            }

            val serverList = if (append) {
                decodeServerList(subscriptionId)
            } else {
                replacedServers.filter { guid ->
                    decodeServerConfig(guid)?.configType?.isGroupType() == true
                }.toMutableList()
            }
            val indexedServers = serverList.toHashSet()
            profiles.keys.forEach { guid ->
                if (indexedServers.add(guid)) {
                    serverList.add(0, guid)
                }
            }
            requireStorageWrite(
                persistServerList(serverList, subscriptionId),
                "Failed to publish profile index",
            )
            val selectedGuid = when (val match = replacementSelection) {
                is ProfileReplacement.ReplacementMatch.Matched -> match.guid
                // No previous selection or the replaced one has no rematch: select the top
                // row of the published batch (first payload line), not the bottom one.
                ProfileReplacement.ReplacementMatch.NoMatch ->
                    serverList.first { guid -> profiles.containsKey(guid) }
                // The current selection lives outside this batch: leave it untouched.
                ProfileReplacement.ReplacementMatch.KeepCurrent -> null
            }
            selectedGuid?.let { guid ->
                requireStorageWrite(
                    mainStorage.encode(KEY_SELECTED_SERVER, guid),
                    "Failed to update selected profile",
                )
            }
            if (replacedServers.isEmpty()) return@withProfileIndexLock

            val protectedServer = selectedGuid ?: previousSelection
            val referencedByOtherGroups = decodeServersReferencedByOtherGroups(subscriptionId)
            val removablePayloads = ProfileReplacement.findRemovablePayloads(
                replacedServers = replacedServers,
                replacementServers = serverList.toSet(),
                protectedServer = protectedServer,
                serversReferencedByOtherGroups = referencedByOtherGroups,
            )
            removeProfilePayloadsHandler(removablePayloads)
        }
    }

    /**
     * Removes the server configuration.
     *
     * @param guid The server GUID.
     */
    fun removeServer(guid: String) {
        if (guid.isBlank()) {
            return
        }

        // Get config to determine which subscription to update
        val config = decodeServerConfig(guid)
        val subId = getSubscriptionId(config?.subscriptionId)

        // Remove from appropriate server list
        val serverList = decodeServerList(subId)
        serverList.remove(guid)
        encodeServerList(serverList, subId)

        // Clean up storage
        if (getSelectServer() == guid) {
            mainStorage.remove(KEY_SELECTED_SERVER)
        }
        profileFullStorage.remove(guid)
        serverAffStorage.remove(guid)
    }

    /**
     * Removes the server configurations via subscription ID.
     *
     * @param subscriptionId The subscription ID.
     */
    fun removeServerViaSubid(subscriptionId: String?) {
        val subId = getSubscriptionId(subscriptionId)
        val serverList = decodeServerList(subId)

        // Remove all servers in the list
        serverList.forEach { guid ->
            if (getSelectServer() == guid) {
                mainStorage.remove(KEY_SELECTED_SERVER)
            }
            profileFullStorage.remove(guid)
            serverAffStorage.remove(guid)
        }

        serverList.clear()
        encodeServerList(serverList, subId)
    }

    /**
     * Removes multiple server configurations from a subscription.
     *
     * @param guids The list of server GUIDs.
     * @param subscriptionId The subscription ID.
     */
    fun removeServers(guids: List<String>, subscriptionId: String) {
        if (guids.isEmpty()) return
        val subId = getSubscriptionId(subscriptionId)
        val serverList = decodeServerList(subId)
        if (serverList.removeAll(guids)) {
            encodeServerList(serverList, subId)
        }

        val selectedServer = getSelectServer()
        guids.forEach { guid ->
            if (selectedServer == guid) {
                mainStorage.remove(KEY_SELECTED_SERVER)
            }
            profileFullStorage.remove(guid)
            serverAffStorage.remove(guid)
            serverRawStorage.remove(guid)
        }
    }

    /**
     * Decodes the server affiliation information.
     *
     * @param guid The server GUID.
     * @return The server affiliation information.
     */
    fun decodeServerAffiliationInfo(guid: String): ServerAffiliationInfo? {
        if (guid.isBlank()) {
            return null
        }
        val json = serverAffStorage.decodeString(guid)
        if (json.isNullOrBlank()) {
            return null
        }
        return JsonUtil.fromJsonSafe(json, ServerAffiliationInfo::class.java)
    }

    /**
     * Encodes the server test delay in milliseconds.
     *
     * @param guid The server GUID.
     * @param testResult The test delay in milliseconds.
     */
    fun encodeServerTestDelayMillis(guid: String, testResult: Long) {
        if (guid.isBlank()) {
            return
        }
        val aff = decodeServerAffiliationInfo(guid) ?: ServerAffiliationInfo()
        aff.testDelayMillis = testResult
        serverAffStorage.encode(guid, JsonUtil.toJson(aff))
    }

    /**
     * Clears all test delay results.
     *
     * @param keys The list of server GUIDs.
     */
    fun clearAllTestDelayResults(keys: List<String>?) {
        keys?.forEach { key ->
            decodeServerAffiliationInfo(key)?.let { aff ->
                aff.testDelayMillis = 0
                serverAffStorage.encode(key, JsonUtil.toJson(aff))
            }
        }
    }

    /**
     * Removes all server configurations.
     *
     * @return The number of server configurations removed.
     */
    fun removeAllServer(): Int {
        val count = profileFullStorage.allKeys()?.count() ?: 0
        profileFullStorage.clearAll()
        serverAffStorage.clearAll()
        serverRawStorage.clearAll()

        decodeSubscriptions().forEach { sub ->
            encodeServerList(mutableListOf(), sub.guid)
        }
        return count
    }

    /**
     * Removes invalid server configurations.
     *
     * @param guid The server GUID.
     * @return The number of server configurations removed.
     */
    fun removeInvalidServer(guid: String): Int {
        var count = 0
        if (guid.isNotEmpty()) {
            decodeServerAffiliationInfo(guid)?.let { aff ->
                if (aff.testDelayMillis < 0L) {
                    removeServer(guid)
                    count++
                }
            }
        } else {
            serverAffStorage.allKeys()?.forEach { key ->
                decodeServerAffiliationInfo(key)?.let { aff ->
                    if (aff.testDelayMillis < 0L) {
                        removeServer(key)
                        count++
                    }
                }
            }
        }
        return count
    }

    /**
     * Encodes the raw server configuration.
     *
     * @param guid The server GUID.
     * @param config The raw server configuration.
     */
    fun encodeServerRaw(guid: String, config: String) {
        serverRawStorage.encode(guid, config)
    }

    /**
     * Decodes the raw server configuration.
     *
     * @param guid The server GUID.
     * @return The raw server configuration.
     */
    fun decodeServerRaw(guid: String): String? {
        return serverRawStorage.decodeString(guid)
    }

    //endregion

    //region Subscriptions

    private fun getSubscriptionId(subscriptionId: String?): String {
        return subscriptionId?.ifEmpty { DEFAULT_SUBSCRIPTION_ID } ?: DEFAULT_SUBSCRIPTION_ID
    }

    /**
     * Initializes the subscription list.
     */
    private fun initSubsList() {
        val subsList = decodeSubsList()
        if (subsList.isNotEmpty()) {
            return
        }
        subStorage.allKeys()?.forEach { key ->
            subsList.add(key)
        }
        encodeSubsList(subsList)
    }

    /**
     * Decodes the subscriptions.
     *
     * @return The list of subscriptions.
     */
    fun decodeSubscriptions(): List<SubscriptionCache> {
        initSubsList()

        val subscriptions = mutableListOf<SubscriptionCache>()
        decodeSubsList().forEach { key ->
            val json = subStorage.decodeString(key)
            if (!json.isNullOrBlank()) {
                val item = JsonUtil.fromJsonSafe(json, SubscriptionItem::class.java) ?: SubscriptionItem()
                subscriptions.add(SubscriptionCache(key, item))
            }
        }
        return subscriptions
    }

    /**
     * Removes the subscription.
     *
     * @param subid The subscription ID.
     */
    fun removeSubscription(subid: String) {
        subStorage.remove(subid)
        val subsList = decodeSubsList()
        subsList.remove(subid)
        encodeSubsList(subsList)

        removeServerViaSubid(subid)
    }

    /**
     * Encodes the subscription.
     *
     * @param guid The subscription GUID.
     * @param subItem The subscription item.
     */
    fun encodeSubscription(guid: String, subItem: SubscriptionItem) {
        val key = guid.ifBlank { Utils.getUuid() }
        subStorage.encode(key, JsonUtil.toJson(subItem))

        val subsList = decodeSubsList()
        if (!subsList.contains(key)) {
            subsList.add(key)
            encodeSubsList(subsList)
        }
    }

    /**
     * Decodes the subscription.
     *
     * @param subscriptionId The subscription ID.
     * @return The subscription item.
     */
    fun decodeSubscription(subscriptionId: String): SubscriptionItem? {
        val json = subStorage.decodeString(subscriptionId) ?: return null
        return JsonUtil.fromJsonSafe(json, SubscriptionItem::class.java)
    }

    /**
     * Encodes the subscription list.
     *
     * @param subsList The list of subscription IDs.
     */
    fun encodeSubsList(subsList: MutableList<String>) {
        mainStorage.encode(KEY_SUB_IDS, JsonUtil.toJson(subsList))
    }

    /**
     * Decodes the subscription list.
     *
     * @return The list of subscription IDs.
     */
    fun decodeSubsList(): MutableList<String> {
        val json = mainStorage.decodeString(KEY_SUB_IDS)
        return if (json.isNullOrBlank()) {
            mutableListOf()
        } else {
            // Keep the first occurrence so a damaged index cannot produce duplicate Compose keys.
            JsonUtil.fromJsonSafe(json, Array<String>::class.java)?.distinct()?.toMutableList() ?: mutableListOf()
        }
    }

    //endregion

    //region Routing

    /**
     * Decodes the routing rulesets.
     *
     * @return The list of routing rulesets.
     */
    fun decodeRoutingRulesets(): MutableList<RulesetItem>? {
        val ruleset = settingsStorage.decodeString(PREF_ROUTING_RULESET)
        if (ruleset.isNullOrEmpty()) return null
        return JsonUtil.fromJsonSafe(ruleset, Array<RulesetItem>::class.java)?.toMutableList() ?: mutableListOf()
    }

    /**
     * Encodes the routing rulesets.
     *
     * @param rulesetList The list of routing rulesets.
     */
    fun encodeRoutingRulesets(rulesetList: MutableList<RulesetItem>?) {
        if (rulesetList.isNullOrEmpty())
            encodeSettings(PREF_ROUTING_RULESET, "")
        else
            encodeSettings(PREF_ROUTING_RULESET, JsonUtil.toJson(rulesetList))
    }

    //endregion

    //region settings
    /**
     * Encodes the settings.
     *
     * @param key The settings key.
     * @param value The settings value.
     * @return Whether the encoding was successful.
     */
    fun encodeSettings(key: String, value: String?): Boolean {
        return settingsStorage.encode(key, value)
    }

    /**
     * Encodes the settings.
     *
     * @param key The settings key.
     * @param value The settings value.
     * @return Whether the encoding was successful.
     */
    fun encodeSettings(key: String, value: Int): Boolean {
        return settingsStorage.encode(key, value)
    }

    /**
     * Encodes the settings.
     *
     * @param key The settings key.
     * @param value The settings value.
     * @return Whether the encoding was successful.
     */
    fun encodeSettings(key: String, value: Long): Boolean {
        return settingsStorage.encode(key, value)
    }

    /**
     * Encodes the settings.
     *
     * @param key The settings key.
     * @param value The settings value.
     * @return Whether the encoding was successful.
     */
    fun encodeSettings(key: String, value: Float): Boolean {
        return settingsStorage.encode(key, value)
    }

    /**
     * Encodes the settings.
     *
     * @param key The settings key.
     * @param value The settings value.
     * @return Whether the encoding was successful.
     */
    fun encodeSettings(key: String, value: Boolean): Boolean {
        return settingsStorage.encode(key, value)
    }

    /**
     * Encodes the settings.
     *
     * @param key The settings key.
     * @param value The settings value.
     * @return Whether the encoding was successful.
     */
    fun encodeSettings(key: String, value: MutableSet<String>): Boolean {
        return settingsStorage.encode(key, value)
    }

    /**
     * Decodes the settings string.
     *
     * @param key The settings key.
     * @return The settings value.
     */
    fun decodeSettingsString(key: String): String? {
        return settingsStorage.decodeString(key)
    }

    /**
     * Decodes the settings string.
     *
     * @param key The settings key.
     * @param defaultValue The default value.
     * @return The settings value.
     */
    fun decodeSettingsString(key: String, defaultValue: String?): String? {
        return settingsStorage.decodeString(key, defaultValue)
    }

    /**
     * Decodes the settings integer.
     *
     * @param key The settings key.
     * @param defaultValue The default value.
     * @return The settings value.
     */
    fun decodeSettingsInt(key: String, defaultValue: Int): Int {
        return settingsStorage.decodeInt(key, defaultValue)
    }

    /**
     * Decodes the settings long.
     *
     * @param key The settings key.
     * @param defaultValue The default value.
     * @return The settings value.
     */
    fun decodeSettingsLong(key: String, defaultValue: Long): Long {
        return settingsStorage.decodeLong(key, defaultValue)
    }

    /**
     * Decodes the settings float.
     *
     * @param key The settings key.
     * @param defaultValue The default value.
     * @return The settings value.
     */
    fun decodeSettingsFloat(key: String, defaultValue: Float): Float {
        return settingsStorage.decodeFloat(key, defaultValue)
    }

    /**
     * Decodes the settings boolean.
     *
     * @param key The settings key.
     * @return The settings value.
     */
    fun decodeSettingsBool(key: String): Boolean {
        return settingsStorage.decodeBool(key, false)
    }

    /**
     * Decodes the settings boolean.
     *
     * @param key The settings key.
     * @param defaultValue The default value.
     * @return The settings value.
     */
    fun decodeSettingsBool(key: String, defaultValue: Boolean): Boolean {
        return settingsStorage.decodeBool(key, defaultValue)
    }

    /**
     * Decodes the settings string set.
     *
     * @param key The settings key.
     * @return The settings value.
     */
    fun decodeSettingsStringSet(key: String): MutableSet<String>? {
        return settingsStorage.decodeStringSet(key)
    }


    /**
     * Encodes the start on boot setting.
     *
     * @param startOnBoot Whether to start on boot.
     */
    fun encodeStartOnBoot(startOnBoot: Boolean) {
        encodeSettings(PREF_IS_BOOTED, startOnBoot)
    }

    /**
     * Decodes the start on boot setting.
     *
     * @return Whether to start on boot.
     */
    fun decodeStartOnBoot(): Boolean {
        return decodeSettingsBool(PREF_IS_BOOTED, false)
    }

    //endregion

    //region Compose helpers for Settings

    /**
     * MMKV-backed String state, auto-persists and notifies on change.
     */
    @Composable
    fun rememberMmkvString(
        key: String,
        default: String = ""
    ): MutableState<String> {
        val state = remember(key) {
            mutableStateOf(decodeSettingsString(key, default) ?: default)
        }

        LaunchedEffect(key) {
            snapshotFlow { state.value }
                .drop(1)
                .distinctUntilChanged()
                .collectLatest { value ->
                    encodeSettings(key, value)
                    SettingsChangeManager.notifySettingChanged(key)
                }
        }
        return state
    }

    /**
     * MMKV-backed Boolean state, auto-persists and notifies on change.
     */
    @Composable
    fun rememberMmkvBool(
        key: String,
        default: Boolean = false
    ): MutableState<Boolean> {
        val state = remember(key) {
            mutableStateOf(decodeSettingsBool(key, default))
        }

        LaunchedEffect(key) {
            snapshotFlow { state.value }
                .drop(1)
                .distinctUntilChanged()
                .collectLatest { value ->
                    encodeSettings(key, value)
                    SettingsChangeManager.notifySettingChanged(key)
                }
        }
        return state
    }

    //endregion

    //region Shroxy geo update

    /**
     * Decodes the last geo update check timestamp.
     *
     * @return Epoch millis of the last successful geo update check, 0 if never checked.
     */
    fun decodeGeoLastCheckedAt(): Long {
        return mainStorage.decodeLong(KEY_GEO_LAST_CHECKED_AT, 0L)
    }

    /**
     * Encodes the last geo update check timestamp.
     *
     * @param value Epoch millis of the last successful geo update check.
     */
    fun encodeGeoLastCheckedAt(value: Long) {
        mainStorage.encode(KEY_GEO_LAST_CHECKED_AT, value)
    }

    /**
     * Decodes the ETag of the last geo release check.
     *
     * @return The stored ETag, or null if none.
     */
    fun decodeGeoLastETag(): String? {
        return mainStorage.decodeString(KEY_GEO_LAST_ETAG)
    }

    /**
     * Encodes the ETag of the last geo release check.
     *
     * @param value The ETag, or null to clear.
     */
    fun encodeGeoLastETag(value: String?) {
        mainStorage.encode(KEY_GEO_LAST_ETAG, value)
    }

    /**
     * Decodes the release tag of the last installed geo files.
     *
     * @return The stored release tag, or null if none.
     */
    fun decodeGeoLastTag(): String? {
        return mainStorage.decodeString(KEY_GEO_LAST_TAG)
    }

    /**
     * Encodes the release tag of the last installed geo files.
     *
     * @param value The release tag, or null to clear.
     */
    fun encodeGeoLastTag(value: String?) {
        mainStorage.encode(KEY_GEO_LAST_TAG, value)
    }

    /**
     * Decodes the success timestamp of the last geoip.dat installation.
     *
     * @return Epoch millis of the last successful geoip.dat install, 0 if never installed.
     */
    fun decodeGeoIpLastSuccessAt(): Long {
        return mainStorage.decodeLong(KEY_GEO_IP_LAST_SUCCESS_AT, 0L)
    }

    /**
     * Encodes the success timestamp of the last geoip.dat installation.
     *
     * @param value Epoch millis of the last successful geoip.dat install.
     */
    fun encodeGeoIpLastSuccessAt(value: Long) {
        mainStorage.encode(KEY_GEO_IP_LAST_SUCCESS_AT, value)
    }

    /**
     * Decodes the success timestamp of the last geosite.dat installation.
     *
     * @return Epoch millis of the last successful geosite.dat install, 0 if never installed.
     */
    fun decodeGeoSiteLastSuccessAt(): Long {
        return mainStorage.decodeLong(KEY_GEO_SITE_LAST_SUCCESS_AT, 0L)
    }

    /**
     * Encodes the success timestamp of the last geosite.dat installation.
     *
     * @param value Epoch millis of the last successful geosite.dat install.
     */
    fun encodeGeoSiteLastSuccessAt(value: Long) {
        mainStorage.encode(KEY_GEO_SITE_LAST_SUCCESS_AT, value)
    }

    /**
     * Decodes the last geoip.dat update status.
     *
     * @return True if the last geoip.dat update attempt succeeded.
     */
    fun decodeGeoIpStatusOk(): Boolean {
        return mainStorage.decodeBool(KEY_GEO_IP_STATUS_OK, false)
    }

    /**
     * Encodes the last geoip.dat update status.
     *
     * @param value True if the last geoip.dat update attempt succeeded.
     */
    fun encodeGeoIpStatusOk(value: Boolean) {
        mainStorage.encode(KEY_GEO_IP_STATUS_OK, value)
    }

    /**
     * Decodes the last geosite.dat update status.
     *
     * @return True if the last geosite.dat update attempt succeeded.
     */
    fun decodeGeoSiteStatusOk(): Boolean {
        return mainStorage.decodeBool(KEY_GEO_SITE_STATUS_OK, false)
    }

    /**
     * Encodes the last geosite.dat update status.
     *
     * @param value True if the last geosite.dat update attempt succeeded.
     */
    fun encodeGeoSiteStatusOk(value: Boolean) {
        mainStorage.encode(KEY_GEO_SITE_STATUS_OK, value)
    }

    /**
     * Decodes the geo update schedule version.
     *
     * @return The stored schedule version, 0 if never recorded.
     */
    fun decodeGeoScheduleVersion(): Int {
        return mainStorage.decodeInt(KEY_GEO_SCHEDULE_VERSION, 0)
    }

    /**
     * Encodes the geo update schedule version.
     *
     * @param value The schedule version to persist.
     */
    fun encodeGeoScheduleVersion(value: Int) {
        mainStorage.encode(KEY_GEO_SCHEDULE_VERSION, value)
    }

    //endregion

    //region Shroxy HWID

    /**
     * Generates a device HWID: 15 characters drawn from [a-z0-9].
     * Pure function of the given random source so tests can seed it.
     *
     * @param random The source of randomness.
     * @return The generated HWID.
     */
    internal fun generateHwid(random: Random): String {
        val chars = CharArray(HWID_LENGTH) { HWID_ALPHABET[random.nextInt(HWID_ALPHABET.length)] }
        return String(chars)
    }

    /**
     * Decodes the persisted device HWID.
     *
     * @return The stored HWID, or null if none.
     */
    fun decodeHwid(): String? {
        return mainStorage.decodeString(KEY_HWID)
    }

    /**
     * Encodes the device HWID.
     *
     * @param value The HWID to persist.
     */
    fun encodeHwid(value: String) {
        mainStorage.encode(KEY_HWID, value)
    }

    /**
     * Returns the persisted device HWID, generating and persisting a new one on first use.
     * Called lazily at first need (first subscription fetch / first empty-screen show),
     * never at app start. Synchronized so concurrent first uses settle on one value.
     *
     * @return The device HWID.
     */
    @Synchronized
    fun ensureHwid(): String {
        decodeHwid()?.let { return it }
        return generateHwid(SecureRandom()).also { encodeHwid(it) }
    }

    //endregion
}
