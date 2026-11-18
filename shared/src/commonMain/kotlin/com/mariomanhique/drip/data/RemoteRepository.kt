package com.mariomanhique.drip.data

import com.mariomanhique.drip.data.Backend.Companion.SERVER_TIME
import com.mariomanhique.drip.domain.Battle
import com.mariomanhique.drip.domain.BattleStatus
import com.mariomanhique.drip.domain.Category
import com.mariomanhique.drip.domain.Challenge
import com.mariomanhique.drip.domain.ChallengeVote
import com.mariomanhique.drip.domain.Comment
import com.mariomanhique.drip.domain.Constraint
import com.mariomanhique.drip.domain.DripState
import com.mariomanhique.drip.domain.Outfit
import com.mariomanhique.drip.domain.PointEvent
import com.mariomanhique.drip.domain.ReportTarget
import com.mariomanhique.drip.domain.User
import com.mariomanhique.drip.domain.Vote
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Firestore-backed [DripStore]. Listens to the collections the UI needs and rebuilds [DripState]
 * on every snapshot. Writes are optimistic (the Firestore SDK echoes them locally at once); server
 * rejections arrive later through [messages]. All [Backend] callbacks are expected on one thread.
 */
@OptIn(ExperimentalTime::class)
class RemoteRepository(private val backend: Backend) : DripStore {
    private class Doc(val id: String, val path: String, val data: JsonObject) {
        val segments get() = path.split('/')
    }

    private val _state = MutableStateFlow(DripState())
    override val state: StateFlow<DripState> = _state.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 16)
    override val messages: SharedFlow<String> = _messages.asSharedFlow()

    private var me: String? = null
    private var raw: Map<String, List<Doc>> = emptyMap()
    private var usersLoaded = false
    private var profileRequested = false
    private var emailHint = ""
    private var nameHint = ""
    private val listening = mutableSetOf<String>()

    override fun nowMs(): Long = Clock.System.now().toEpochMilliseconds()
    override fun tick() {}

    // --- Session --------------------------------------------------------------------------

    override fun register(displayName: String, handle: String, password: String, ageConfirmed: Boolean): String? =
        "Create your account with email or Google"

    override fun login(handle: String, password: String): String? = "Sign in with email or Google"

    override fun linkAccount(uid: String, email: String, displayName: String): String? {
        if (me == uid) return null
        stopAll()
        me = uid
        emailHint = email
        nameHint = displayName
        usersLoaded = false
        profileRequested = false
        raw = emptyMap()
        _state.value = DripState(currentUserId = uid)
        startAll(uid)
        return null
    }

    override fun logout() {
        stopAll()
        me = null
        raw = emptyMap()
        _state.value = DripState()
    }

    private fun stopAll() {
        listening.forEach { backend.stopListening(it) }
        listening.clear()
    }

    private fun startAll(uid: String) {
        listen("users", "users")
        listen("userStats", "userStats")
        listen("outfits", "outfits")
        listen("challenges", "challenges")
        listen("battles", "battles")
        listen("entryStats", "entryStats", group = true)
        listen("followsOut", "follows", field = "followerId", value = uid)
        listen("followsIn", "follows", field = "followedId", value = uid)
        listen("blocks", "blocks", field = "blockerId", value = uid)
        listen("points", "pointEvents", field = "userId", value = uid)
        listen("saves", "users/$uid/saves")
        listen("props", "props", group = true)
        listen("comments", "comments", group = true)
        listen("votes", "votes", group = true, field = "voterId", value = uid)
    }

    private fun listen(key: String, path: String, group: Boolean = false, field: String? = null, value: String? = null) {
        listening += key
        backend.listen(key, path, group, field, value, { json ->
            raw = raw + (key to parseDocs(json))
            if (key == "users") {
                usersLoaded = true
                ensureProfile()
            }
            rebuild()
        }, { error -> _messages.tryEmit("Sync problem ($key): $error") })
    }

    private fun parseDocs(json: String): List<Doc> =
        Json.parseToJsonElement(json).jsonArray.map {
            val o = it.jsonObject
            Doc(o.str("id").orEmpty(), o.str("path").orEmpty(), o["data"] as? JsonObject ?: JsonObject(emptyMap()))
        }

    // --- Profile bootstrap ----------------------------------------------------------------

    private fun ensureProfile() {
        val uid = me ?: return
        if (profileRequested || raw["users"].orEmpty().any { it.id == uid }) return
        profileRequested = true
        createProfile(uid, attempt = 0)
    }

    private fun createProfile(uid: String, attempt: Int) {
        val args = buildJsonObject {
            put("handle", suggestHandle(attempt))
            put("displayName", nameHint.ifBlank { emailHint.substringBefore('@').ifBlank { "Drip user" } })
            put("confirmedAge16", true)
        }
        backend.call("createProfile", args.toString()) { _, error ->
            when {
                error == null || me != uid -> {}
                error.contains("Profile already exists", ignoreCase = true) -> {}
                error.contains("Handle taken", ignoreCase = true) && attempt < 5 -> createProfile(uid, attempt + 1)
                else -> _messages.tryEmit(error)
            }
        }
    }

    private fun suggestHandle(attempt: Int): String {
        val base = emailHint.substringBefore('@').lowercase().filter { it.isLetterOrDigit() || it == '_' || it == '.' }
            .ifBlank { "drip" }.padEnd(3, '0')
        if (attempt == 0) return base.take(20)
        val suffix = Random.nextInt(100, 9999).toString()
        return base.take(20 - suffix.length) + suffix
    }

    // --- State assembly -------------------------------------------------------------------

    private fun rebuild() {
        val uid = me ?: return
        val stats = raw["userStats"].orEmpty().associateBy { it.id }
        val users = raw["users"].orEmpty().map { d ->
            val s = stats[d.id]?.data
            User(
                id = d.id,
                handle = d.data.str("handle").orEmpty(),
                displayName = d.data.str("displayName").orEmpty(),
                bio = d.data.str("bio").orEmpty(),
                interests = d.data.strings("interests").mapNotNull { category(it) }.toSet(),
                joinedAt = d.data.long("joinedAt") ?: 0,
                city = d.data.str("city") ?: "Porto",
                rating = s?.int("rating") ?: 1000,
                ratedBattles = s?.int("ratedBattles") ?: 0,
                flagged = s?.bool("flagged") ?: false,
                followerCount = s?.int("followerCount")?.coerceAtLeast(0),
                followingCount = s?.int("followingCount")?.coerceAtLeast(0),
                votesCast = s?.int("votesCast") ?: 0,
                showdownsStarted = s?.int("showdownsStarted") ?: 0,
                pointsTotal = s?.int("points") ?: 0,
            )
        }

        val propsBy = raw["props"].orEmpty().groupBy({ it.segments.getOrNull(1).orEmpty() }, { it.id })
        val commentsBy = raw["comments"].orEmpty().groupBy { it.segments.getOrNull(1).orEmpty() }
        val saved = raw["saves"].orEmpty().map { it.id }.toSet()
        val outfits = raw["outfits"].orEmpty().map { d ->
            val o = d.data
            Outfit(
                id = d.id,
                authorId = o.str("authorId").orEmpty(),
                caption = o.str("caption").orEmpty(),
                category = category(o.str("category")) ?: Category.CASUAL,
                palette = o.longs("palette").ifEmpty { listOf(0xFF111111, 0xFF888888) },
                emoji = o.str("emoji") ?: "👕",
                createdAt = o.long("createdAt") ?: 0,
                challengeId = o.str("challengeId"),
                propsFrom = propsBy[d.id].orEmpty().toSet(),
                savedBy = if (d.id in saved) setOf(uid) else emptySet(),
                comments = commentsBy[d.id].orEmpty().map { c ->
                    Comment(c.id, c.data.str("authorId").orEmpty(), c.data.str("text").orEmpty(), c.data.long("at") ?: 0)
                }.sortedBy { it.at },
                hidden = o.bool("hidden") ?: false,
                constraintConfirmed = o.bool("constraintConfirmed") ?: false,
                photoUri = o.str("photoUrl"),
            )
        }

        val myVotes = raw["votes"].orEmpty()
        val battleVotes = myVotes.filter { it.segments.firstOrNull() == "battles" }
            .associate { it.segments[1] to Vote(uid, it.data.str("outfitId").orEmpty(), it.data.long("at") ?: 0) }
        val challengeVotes = myVotes.filter { it.segments.firstOrNull() == "challenges" }.map {
            ChallengeVote(
                uid, it.segments[1], it.data.str("winnerId").orEmpty(), it.data.str("loserId").orEmpty(),
                it.data.long("at") ?: 0,
            )
        }

        val battles = raw["battles"].orEmpty().map { d ->
            val b = d.data
            Battle(
                id = d.id,
                creatorId = b.str("creatorId").orEmpty(),
                outfitAId = b.str("outfitAId").orEmpty(),
                outfitBId = b.str("outfitBId"),
                status = BattleStatus.entries.firstOrNull { it.name == b.str("status") } ?: BattleStatus.WAITING,
                createdAt = b.long("createdAt") ?: 0,
                matchedAt = b.long("matchedAt"),
                endsAt = b.long("endsAt"),
                extended = b.bool("extended") ?: false,
                votes = listOfNotNull(battleVotes[d.id]),
                winnerOutfitId = b.str("winnerOutfitId"),
                draw = b.bool("draw") ?: false,
                votesA = b.int("votesA"),
                votesB = b.int("votesB"),
            )
        }

        val challenges = raw["challenges"].orEmpty().map { d ->
            val c = d.data
            Challenge(
                id = d.id,
                title = c.str("title").orEmpty(),
                description = c.str("description").orEmpty(),
                rules = c.str("rules").orEmpty(),
                category = category(c.str("category")),
                sponsor = c.str("sponsor"),
                closed = c.bool("closed") ?: false,
                constraint = Constraint.entries.firstOrNull { it.name == c.str("constraint")?.uppercase() } ?: Constraint.OPEN,
            )
        }

        val entryStats = raw["entryStats"].orEmpty().associate {
            it.id to ((it.data.int("wins") ?: 0) to (it.data.int("appearances") ?: 0))
        }
        val follows = (raw["followsOut"].orEmpty() + raw["followsIn"].orEmpty()).map {
            it.data.str("followerId").orEmpty() to it.data.str("followedId").orEmpty()
        }.toSet()
        val blocks = raw["blocks"].orEmpty().map { it.data.str("blockerId").orEmpty() to it.data.str("blockedId").orEmpty() }.toSet()
        val ledger = raw["points"].orEmpty().map {
            PointEvent(uid, it.data.int("points") ?: 0, it.data.str("reason").orEmpty(), it.data.long("at") ?: 0)
        }.sortedByDescending { it.at }

        _state.value = DripState(
            currentUserId = uid, users = users, outfits = outfits, challenges = challenges, battles = battles,
            challengeVotes = challengeVotes, follows = follows, blocks = blocks, ledger = ledger, entryStats = entryStats,
        )
    }

    // --- Writes ---------------------------------------------------------------------------

    private fun fail(error: String?) { if (error != null) _messages.tryEmit(error) }

    private fun write(path: String, json: JsonObject, merge: Boolean = false) =
        backend.set(path, json.toString(), merge, ::fail)

    private fun call(name: String, args: JsonObject) =
        backend.call(name, args.toString()) { _, error -> fail(error) }

    override fun updateProfile(displayName: String, bio: String, interests: Set<Category>): String? {
        val uid = me ?: return "Not signed in"
        if (displayName.isBlank()) return "Name can't be empty"
        write(
            "users/$uid",
            buildJsonObject {
                put("displayName", displayName.trim())
                put("bio", bio.trim())
                putJsonArray("interests") { interests.forEach { add(JsonPrimitive(it.name)) } }
            },
            merge = true,
        )
        return null
    }

    override fun toggleFollow(userId: String) {
        val uid = me ?: return
        if (uid == userId) return
        val path = "follows/${uid}_$userId"
        if (state.value.isFollowing(userId)) backend.delete(path, ::fail)
        else write(path, buildJsonObject { put("followerId", uid); put("followedId", userId); put("at", SERVER_TIME) })
    }

    override fun toggleBlock(userId: String) {
        val uid = me ?: return
        if (uid == userId) return
        val path = "blocks/${uid}_$userId"
        if (state.value.isBlocked(userId)) {
            backend.delete(path, ::fail)
        } else {
            write(path, buildJsonObject { put("blockerId", uid); put("blockedId", userId); put("at", SERVER_TIME) })
            backend.delete("follows/${uid}_$userId", ::fail)
        }
    }

    override fun publish(
        caption: String,
        category: Category,
        palette: List<Long>,
        emoji: String,
        challengeId: String?,
        constraintOk: Boolean,
        photoUri: String?,
    ): String? {
        val uid = me ?: return "Not signed in"
        if (caption.isBlank()) return "Add a caption"
        val s = state.value
        val challenge = challengeId?.let { id -> s.challenges.firstOrNull { it.id == id } }
        if (challengeId != null) {
            when {
                challenge == null -> return "Challenge not found"
                challenge.closed -> return "This challenge has ended"
                s.outfits.any { it.challengeId == challengeId && it.authorId == uid } -> return "You already entered this challenge"
                challenge.constraint != Constraint.OPEN && !constraintOk -> return "Confirm the ${challenge.constraint.label} pledge to enter"
            }
        }
        val id = if (challengeId != null) "c_${challengeId}_$uid" else "o_${uid}_${nowMs()}"
        val confirmed = challenge != null && challenge.constraint != Constraint.OPEN
        fun save(photoUrl: String?) = write(
            "outfits/$id",
            buildJsonObject {
                put("authorId", uid)
                put("caption", caption.trim())
                put("category", category.name)
                putJsonArray("palette") { palette.forEach { add(JsonPrimitive(it)) } }
                put("emoji", emoji)
                put("hidden", false)
                put("createdAt", SERVER_TIME)
                put("challengeId", challengeId)
                put("constraintConfirmed", confirmed)
                put("photoUrl", photoUrl)
            },
        )
        if (photoUri == null) {
            save(null)
        } else {
            backend.upload("outfits/$uid/$id", photoUri) { url, error ->
                if (url != null) save(url) else fail(error ?: "Photo upload failed")
            }
        }
        return null
    }

    override fun toggleProps(outfitId: String) {
        val uid = me ?: return
        val path = "outfits/$outfitId/props/$uid"
        if (state.value.outfit(outfitId)?.propsFrom?.contains(uid) == true) backend.delete(path, ::fail)
        else write(path, buildJsonObject { put("at", SERVER_TIME) })
    }

    override fun toggleSave(outfitId: String) {
        val uid = me ?: return
        val path = "users/$uid/saves/$outfitId"
        if (state.value.outfit(outfitId)?.savedBy?.contains(uid) == true) backend.delete(path, ::fail)
        else write(path, buildJsonObject { put("at", SERVER_TIME) })
    }

    override fun addComment(outfitId: String, text: String) {
        val uid = me ?: return
        val outfit = state.value.outfit(outfitId) ?: return
        if (text.isBlank()) return
        write(
            "outfits/$outfitId/comments/cm_${uid}_${nowMs()}",
            buildJsonObject {
                put("authorId", uid)
                put("outfitAuthorId", outfit.authorId)
                put("text", text.trim().take(280))
                put("at", SERVER_TIME)
            },
        )
    }

    // --- Server-decided actions -----------------------------------------------------------

    override fun startBattle(myOutfitId: String): String? {
        if (me == null) return "Not signed in"
        call("startBattle", buildJsonObject { put("outfitId", myOutfitId) })
        return null
    }

    override fun vote(battleId: String, outfitId: String): String? {
        if (me == null) return "Not signed in"
        call("castVote", buildJsonObject { put("battleId", battleId); put("outfitId", outfitId) })
        return null
    }

    override fun voteChallenge(challengeId: String, winnerId: String, loserId: String): String? {
        if (me == null) return "Not signed in"
        call("castChallengeVote", buildJsonObject {
            put("challengeId", challengeId); put("winnerId", winnerId); put("loserId", loserId)
        })
        return null
    }

    override fun resolveBattleReview(battleId: String, valid: Boolean) {
        call("resolveBattleReview", buildJsonObject { put("battleId", battleId); put("valid", valid) })
    }

    override fun closeChallenge(challengeId: String): String? {
        if (me == null) return "Not signed in"
        call("closeChallenge", buildJsonObject { put("challengeId", challengeId) })
        return null
    }

    override fun report(targetType: ReportTarget, targetId: String, reason: String): String? {
        val uid = me ?: return "Not signed in"
        if (reason.isBlank()) return "Choose a reason"
        val outfitId = if (targetType == ReportTarget.OUTFIT) targetId else "u_$targetId"
        write(
            "reports/${outfitId}_$uid",
            buildJsonObject {
                put("reporterId", uid)
                put("outfitId", outfitId)
                put("targetType", targetType.name)
                put("targetId", targetId)
                put("reason", reason)
                put("status", "OPEN")
                put("at", SERVER_TIME)
            },
        )
        return null
    }

    override fun resolveReport(reportId: String, actioned: Boolean) {}
}

private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNull
private fun JsonObject.long(key: String): Long? = (this[key] as? JsonPrimitive)?.longOrNull
private fun JsonObject.int(key: String): Int? = long(key)?.toInt()
private fun JsonObject.bool(key: String): Boolean? = (this[key] as? JsonPrimitive)?.booleanOrNull
private fun JsonObject.strings(key: String): List<String> =
    (this[key] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.orEmpty()
private fun JsonObject.longs(key: String): List<Long> =
    (this[key] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.longOrNull }.orEmpty()
private fun category(name: String?): Category? = Category.entries.firstOrNull { it.name.equals(name, ignoreCase = true) }
