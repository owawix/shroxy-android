package com.v2ray.ang.handler

import com.v2ray.ang.dto.entities.ProfileItem

internal object ProfileReplacement {

    /**
     * Outcome of the selection search over a replacement batch.
     */
    sealed class ReplacementMatch {
        /** A batch profile was rematched to the replaced selection; it becomes selected. */
        data class Matched(val guid: String) : ReplacementMatch()

        /**
         * The store has no current selection, or the replaced selection has no counterpart
         * in the batch. The caller selects the top row of the published batch; no GUID is
         * guessed here.
         */
        object NoMatch : ReplacementMatch()

        /** The current selection lives outside the replaced batch; it stays untouched. */
        object KeepCurrent : ReplacementMatch()
    }

    /**
     * Finds the profile that should become selected after publishing a replacement batch.
     *
     * [ReplacementMatch.Matched] carries the batch GUID rematched to the replaced selection.
     * [ReplacementMatch.NoMatch] signals that the previous selection yields no GUID (no
     * selection at all, or the replaced one has no counterpart), so the caller must select
     * the top row of the published batch. [ReplacementMatch.KeepCurrent] means the current
     * selection is outside this batch and must not be changed.
     */
    fun findSelectedReplacement(
        profiles: Map<String, ProfileItem>,
        currentSelection: String?,
        selectedProfile: ProfileItem?,
    ): ReplacementMatch {
        if (profiles.isEmpty()) return ReplacementMatch.NoMatch
        if (currentSelection.isNullOrBlank()) return ReplacementMatch.NoMatch
        if (selectedProfile == null) return ReplacementMatch.KeepCurrent

        if (selectedProfile.remarks.isNotBlank()) {
            profiles.entries.firstOrNull { (_, candidate) ->
                isSameText(candidate.remarks, selectedProfile.remarks) &&
                        isSameText(candidate.server, selectedProfile.server) &&
                        isSameText(candidate.serverPort, selectedProfile.serverPort) &&
                        isSameText(candidate.password, selectedProfile.password)
            }?.key?.let { return ReplacementMatch.Matched(it) }

            profiles.entries.firstOrNull { (_, candidate) ->
                isSameText(candidate.remarks, selectedProfile.remarks)
            }?.key?.let { return ReplacementMatch.Matched(it) }
        }

        profiles.entries.firstOrNull { (_, candidate) ->
            isSameText(candidate.server, selectedProfile.server) &&
                    isSameText(candidate.serverPort, selectedProfile.serverPort) &&
                    isSameText(candidate.password, selectedProfile.password)
        }?.key?.let { return ReplacementMatch.Matched(it) }

        profiles.entries.firstOrNull { (_, candidate) ->
            isSameText(candidate.server, selectedProfile.server) &&
                    isSameText(candidate.serverPort, selectedProfile.serverPort)
        }?.key?.let { return ReplacementMatch.Matched(it) }

        profiles.entries.firstOrNull { (_, candidate) ->
            isSameText(candidate.server, selectedProfile.server)
        }?.key?.let { return ReplacementMatch.Matched(it) }

        return ReplacementMatch.NoMatch
    }

    /**
     * Finds replaced payloads that are safe to remove.
     *
     * A null cross-group reference set means that at least one raw group index could not
     * be read. In that case deletion fails closed.
     */
    fun findRemovablePayloads(
        replacedServers: Collection<String>,
        replacementServers: Set<String>,
        protectedServer: String?,
        serversReferencedByOtherGroups: Set<String>?,
    ): Set<String> {
        if (serversReferencedByOtherGroups == null) return emptySet()

        return replacedServers.filterTo(linkedSetOf()) { guid ->
            guid != protectedServer &&
                    guid !in replacementServers &&
                    guid !in serversReferencedByOtherGroups
        }
    }

    private fun isSameText(left: String?, right: String?): Boolean {
        if (left.isNullOrBlank() || right.isNullOrBlank()) return false
        return left.trim().equals(right.trim(), ignoreCase = true)
    }
}
