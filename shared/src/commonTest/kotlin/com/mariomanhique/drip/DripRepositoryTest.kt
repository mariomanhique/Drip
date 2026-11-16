package com.mariomanhique.drip

import com.mariomanhique.drip.data.Seed.DEMO_PASSWORD
import com.mariomanhique.drip.data.DripRepository
import com.mariomanhique.drip.data.Seed
import com.mariomanhique.drip.domain.BattleStatus
import com.mariomanhique.drip.domain.User
import com.mariomanhique.drip.domain.Badge
import com.mariomanhique.drip.domain.Category
import com.mariomanhique.drip.domain.ReportTarget
import com.mariomanhique.drip.domain.Rules
import com.mariomanhique.drip.domain.badgesFor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import com.mariomanhique.drip.domain.challengeStandings
import com.mariomanhique.drip.domain.nextChallengePair
import com.mariomanhique.drip.domain.shareable
import com.mariomanhique.drip.domain.votesNeeded

class DripRepositoryTest {
    private fun repoAs(handle: String) = DripRepository().also { assertNull(it.login(handle, DEMO_PASSWORD)) }
    private var t = 100L * Rules.DAY_MS
    private fun crowdRepo(): DripRepository {
        val base = Seed.state(t)
        val crowd = (1..25).map { User("v$it", "voter$it", "Voter $it", password = DEMO_PASSWORD, joinedAt = 0) }
        return DripRepository(now = { t }, initial = base.copy(users = base.users + crowd))
    }
    private fun DripRepository.voteAs(handle: String, battle: String, outfit: String): String? {
        logout(); login(handle, DEMO_PASSWORD); t += 10 * 60_000
        return vote(battle, outfit)
    }
    private val palette = listOf(0xFF000000, 0xFFFFFFFF)

    @Test
    fun registerValidatesInput() {
        val repo = DripRepository()
        assertNotNull(repo.register("", "newbie", "secret1", true))
        assertNotNull(repo.register("New", "ab", "secret1", true))
        assertNotNull(repo.register("New", "ama", "secret1", true))
        assertNotNull(repo.register("New", "newbie", "123", true))
        assertNotNull(repo.register("New", "newbie", "secret1", false))
        assertNull(repo.register("New", "newbie", "secret1", true))
        assertEquals("newbie", repo.state.value.currentUser?.handle)
    }

    @Test
    fun loginRejectsWrongPassword() {
        assertNotNull(DripRepository().login("ama", "nope"))
    }

    @Test
    fun quorumClosesBattleAfterWindowAndAwardsPoints() {
        val repo = crowdRepo()
        val before = repo.state.value.points("u4")
        for (i in 1..20) assertNull(repo.voteAs("voter$i", "b1", if (i <= 14) "o4" else "o3"))
        t += 2 * Rules.DAY_MS
        repo.tick()
        val b = repo.state.value.battles.first { it.id == "b1" }
        assertEquals(BattleStatus.CLOSED, b.status)
        assertEquals("o4", b.winnerOutfitId)
        assertEquals(before + Rules.BATTLE_WIN_POINTS, repo.state.value.points("u4"))
        assertTrue(repo.state.value.users.first { it.id == "u4" }.rating > Rules.START_RATING)
    }

    @Test
    fun closeVoteIsADraw() {
        val repo = crowdRepo()
        for (i in 1..20) repo.voteAs("voter$i", "b1", if (i <= 10) "o4" else "o3")
        t += 2 * Rules.DAY_MS
        repo.tick()
        val b = repo.state.value.battles.first { it.id == "b1" }
        assertTrue(b.draw)
    }

    @Test
    fun elevenToNineIsStillADraw() {
        val repo = crowdRepo()
        for (i in 1..20) repo.voteAs("voter$i", "b1", if (i <= 11) "o4" else "o3")
        t += 2 * Rules.DAY_MS
        repo.tick()
        assertTrue(repo.state.value.battles.first { it.id == "b1" }.draw)
    }

    @Test
    fun dailyShowdownCapApplies() {
        val repo = crowdRepo()
        repo.login("lucia", DEMO_PASSWORD)
        val results = (1..Rules.MAX_BATTLES_PER_DAY + 1).map { i ->
            repo.publish("Look $i", Category.VINTAGE, palette, "🧥", null)
            val id = repo.state.value.outfits.filter { it.authorId == "u2" }.maxBy { it.createdAt }.id
            repo.startBattle(id)
        }
        assertNull(results[0])
        assertNotNull(results.last())
    }

    @Test
    fun noQuorumExtendsThenVoids() {
        val repo = crowdRepo()
        for (i in 1..3) repo.voteAs("voter$i", "b1", "o4")
        t += 2 * Rules.DAY_MS
        repo.tick()
        assertTrue(repo.state.value.battles.first { it.id == "b1" }.extended)
        t += 3 * Rules.DAY_MS
        repo.tick()
        assertEquals(BattleStatus.VOID, repo.state.value.battles.first { it.id == "b1" }.status)
    }

    @Test
    fun voteSpikeSendsBattleToReview() {
        val repo = crowdRepo()
        for (i in 1..20) { repo.logout(); repo.login("voter$i", DEMO_PASSWORD); t += 1000; repo.vote("b1", "o4") }
        t += 2 * Rules.DAY_MS
        repo.tick()
        assertEquals(BattleStatus.UNDER_REVIEW, repo.state.value.battles.first { it.id == "b1" }.status)
    }

    @Test
    fun cannotVoteInOwnBattleTwiceOrAsYoungAccount() {
        val repo = crowdRepo()
        assertNotNull(repo.voteAs("sana", "b1", "o4"))
        assertNull(repo.voteAs("voter1", "b1", "o4"))
        assertNotNull(repo.vote("b1", "o3"))
        repo.logout(); repo.register("Fresh", "fresh", "secret1", true)
        assertNotNull(repo.vote("b1", "o4"))
    }

    @Test
    fun followersCannotVote() {
        val repo = crowdRepo()
        assertNotNull(repo.voteAs("mei", "b1", "o4"))
    }

    @Test
    fun startBattleQueuesAndMatchesEligibleOpponent() {
        val repo = crowdRepo()
        repo.login("lucia", DEMO_PASSWORD)
        assertNotNull(repo.startBattle("o3"))
        assertNull(repo.startBattle("o2"))
        t += Rules.HOUR_MS
        repo.tick()
        val mine = repo.state.value.battles.first { it.outfitAId == "o2" || it.outfitBId == "o2" }
        assertNotNull(mine)
    }

    @Test
    fun waitingBattleExpires() {
        val repo = crowdRepo()
        t += Rules.MAX_WAIT_FOR_MATCH_MS + Rules.HOUR_MS
        repo.tick()
        assertEquals(BattleStatus.VOID, repo.state.value.battles.first { it.id == "b2" }.status)
    }

    @Test
    fun threeReportsHideOutfit() {
        val repo = repoAs("lucia")
        assertNull(repo.report(ReportTarget.OUTFIT, "o1", "Spam"))
        assertNotNull(repo.report(ReportTarget.OUTFIT, "o1", "Spam"))
        repo.logout(); repo.login("kofi", DEMO_PASSWORD); repo.report(ReportTarget.OUTFIT, "o1", "Spam")
        repo.logout(); repo.login("sana", DEMO_PASSWORD); repo.report(ReportTarget.OUTFIT, "o1", "Spam")
        assertTrue(repo.state.value.outfit("o1")!!.hidden)
        assertTrue(repo.state.value.visibleOutfits().none { it.id == "o1" })
    }

    @Test
    fun challengeEntryOnce() {
        val repo = repoAs("mei")
        assertNull(repo.publish("My look", Category.STREETWEAR, palette, "🧥", "c1"))
        assertNotNull(repo.publish("Again", Category.STREETWEAR, palette, "🧥", "c1"))
        assertNotNull(repo.publish(" ", Category.STREETWEAR, palette, "🧥", null))
    }

    private fun DripRepository.judgeAs(handle: String, challenge: String): String? {
        logout(); login(handle, DEMO_PASSWORD)
        val pair = state.value.nextChallengePair(state.value.currentUserId!!, challenge, t) ?: return "no pair"
        val (w, l) = listOf(pair.first, pair.second).sortedBy { it.id }
        return voteChallenge(challenge, w.id, l.id)
    }

    @Test
    fun challengeRanksByPairwiseWinsNotFire() {
        val repo = crowdRepo()
        val entries = repo.state.value.outfits.filter { it.challengeId == "c1" }.map { it.id }
        assertTrue(entries.size >= 2)
        val fireLeader = repo.state.value.outfits.filter { it.challengeId == "c1" }.maxBy { it.props }.id
        // every voter always prefers the lowest id, whatever Fire says
        repeat(60) { i -> repo.judgeAs("voter${i % 25 + 1}", "c1"); t += 1000 }
        val top = repo.state.value.challengeStandings("c1").first()
        assertTrue(top.ranked)
        assertEquals(entries.min(), top.outfit.id)
        assertNull(repo.closeChallenge("c1"))
        assertNotNull(repo.closeChallenge("c1"))
        assertTrue(fireLeader.isNotEmpty())
    }

    @Test
    fun entriesBelowQuorumGetOnlyEntryPoints() {
        val repo = repoAs("ama")
        val authors = repo.state.value.outfits.filter { it.challengeId == "c1" }.map { it.authorId }
        val before = authors.associateWith { repo.state.value.points(it) }
        assertNull(repo.closeChallenge("c1"))
        authors.forEach { assertEquals(before[it]!! + Rules.CHALLENGE_ENTRY_POINTS, repo.state.value.points(it)) }
    }

    @Test
    fun challengeVoteRules() {
        val repo = crowdRepo()
        repo.logout(); repo.login("voter1", DEMO_PASSWORD)
        val (a, b) = repo.state.value.nextChallengePair("v1", "c1", t)!!
        assertNull(repo.voteChallenge("c1", a.id, b.id))
        assertNotNull(repo.voteChallenge("c1", b.id, a.id))
        repo.logout(); repo.login("ama", DEMO_PASSWORD)
        val own = repo.state.value.outfits.first { it.authorId == "u1" && it.challengeId == "c1" }
        val other = repo.state.value.outfits.first { it.challengeId == "c1" && it.authorId != "u1" }
        assertNotNull(repo.voteChallenge("c1", own.id, other.id))
    }

    @Test
    fun voteToPlayRequiresVotesFirst() {
        val repo = crowdRepo()
        repo.logout(); repo.login("voter1", DEMO_PASSWORD)
        repo.publish("Look", Category.VINTAGE, palette, "🧥", null)
        val id = repo.state.value.outfits.first { it.authorId == "v1" }.id
        assertNotNull(repo.startBattle(id))
        assertEquals(Rules.VOTES_PER_SHOWDOWN, repo.state.value.votesNeeded("v1"))
    }

    @Test
    fun sharingOpensOnlyAfterClose() {
        val repo = crowdRepo()
        assertFalse(repo.state.value.battles.first { it.id == "b1" }.shareable)
    }

    @Test
    fun blockingRemovesTheirOutfitsFromFeed() {
        val repo = repoAs("ama")
        repo.toggleBlock("u2")
        assertTrue(repo.state.value.visibleOutfits().none { it.authorId == "u2" })
    }

    @Test
    fun debutBadgeForPublishers() {
        val repo = repoAs("ama")
        assertTrue(Badge.DEBUT in repo.state.value.badgesFor("u1"))
    }

    @Test
    fun constraintChallengeRequiresPledge() {
        val repo = repoAs("ama")
        assertNotNull(repo.publish("Closet look", Category.STREETWEAR, palette, "🧥", "c2"))
        val other = repoAs("rui")
        assertNull(other.publish("Closet look", Category.STREETWEAR, palette, "🧥", "c2", constraintOk = true))
        assertTrue(other.state.value.outfits.any { it.caption == "Closet look" && it.constraintConfirmed })
    }

    @Test
    fun testersSeededMostlyInPorto() {
        val users = Seed.state(t).users
        assertEquals(20, users.size)
        assertTrue(users.count { it.city == Rules.LAUNCH_CITY } >= 15)
    }
}
