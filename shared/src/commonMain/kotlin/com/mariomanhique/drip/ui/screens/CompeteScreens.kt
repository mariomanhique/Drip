package com.mariomanhique.drip.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mariomanhique.drip.domain.DripState
import com.mariomanhique.drip.domain.Constraint
import com.mariomanhique.drip.domain.challengeShare
import com.mariomanhique.drip.ui.ShareRow
import com.mariomanhique.drip.domain.Rules
import com.mariomanhique.drip.domain.challengeStandings
import com.mariomanhique.drip.domain.nextChallengePair
import com.mariomanhique.drip.domain.rankChallenge
import com.mariomanhique.drip.domain.statsFor
import com.mariomanhique.drip.ui.Avatar
import com.mariomanhique.drip.ui.DripLime
import com.mariomanhique.drip.ui.DripViewModel
import com.mariomanhique.drip.ui.EmptyState
import com.mariomanhique.drip.ui.OutfitArt
import com.mariomanhique.drip.ui.PhotoViewer
import com.mariomanhique.drip.ui.Pill
import com.mariomanhique.drip.ui.Screen
import com.mariomanhique.drip.ui.SectionTitle

@Composable
fun ChallengesScreen(state: DripState, vm: DripViewModel) {
    LazyColumn(contentPadding = com.mariomanhique.drip.ui.bottomBarListPadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items(state.challenges.sortedBy { it.closed }, key = { it.id }) { c ->
            Card(
                Modifier.fillMaxWidth().clickable { vm.open(Screen.ChallengeDetail(c.id)) },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Pill(if (c.closed) "Ended" else "Open")
                        if (c.constraint != Constraint.OPEN) Pill("🎯 ${c.constraint.label}")
                        c.category?.let { Pill("${it.emoji} ${it.label}") }
                        c.sponsor?.let { Pill("Sponsored · $it") }
                    }
                    Text(c.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(c.description, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${state.rankChallenge(c.id).size} entries", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
fun ChallengeDetailScreen(state: DripState, challengeId: String, vm: DripViewModel) {
    val c = state.challenges.firstOrNull { it.id == challengeId }
    if (c == null) { EmptyState("Challenge not found"); return }
    val standings = state.challengeStandings(c.id).filter { !state.isBlocked(it.outfit.authorId) }
    val ranked = standings.map { it.outfit }
    val me = state.currentUserId
    val pair = if (me != null && !c.closed) state.nextChallengePair(me, c.id, vm.nowMs()) else null
    val entered = ranked.any { it.authorId == state.currentUserId }
    LazyColumn(contentPadding = com.mariomanhique.drip.ui.bottomBarListPadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(c.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(c.description)
                Text("Rules: ${c.rules}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (c.constraint != Constraint.OPEN) Pill("🎯 ${c.constraint.label}: ${c.constraint.pledge}")
                Text(
                    "Ranked by anonymous A/B votes; Fire never counts. Each entry needs ${Rules.QUORUM} votes to be ranked. Podium earns ${Rules.PODIUM_POINTS.joinToString("/")} Drip Points; other entries earn ${Rules.CHALLENGE_ENTRY_POINTS} when it closes.",
                    style = MaterialTheme.typography.bodySmall,
                )
                if (!c.closed) {
                    Button({ vm.open(Screen.Create(c.id)) }, enabled = !entered) {
                        Text(if (entered) "You're in ✅" else "Enter the challenge")
                    }
                }
                if (pair != null) {
                    SectionTitle("Which outfit is better?")
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        listOf("Look A" to pair.first, "Look B" to pair.second).forEach { (label, o) ->
                            val other = if (o == pair.first) pair.second else pair.first
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                var viewing by remember { mutableStateOf(false) }
                                if (viewing) PhotoViewer(o) { viewing = false }
                                OutfitArt(o, Modifier.height(160.dp).fillMaxWidth().clickable { viewing = true }, 56)
                                Button({ vm.voteChallenge(c.id, o.id, other.id) }, Modifier.fillMaxWidth()) { Text(label) }
                            }
                        }
                    }
                    Text("Vote on the outfit, not the person.", style = MaterialTheme.typography.bodySmall)
                }
                if (c.closed && me != null) state.challengeShare(c.id, me)?.let { ShareRow(it) { m -> vm.toast(m) } }
                SectionTitle(if (c.closed) "Final results" else "Entries")
            }
        }
        if (ranked.isEmpty()) item { EmptyState("No fits yet — be the first to drop") }
        items(standings.withIndex().toList(), key = { it.value.outfit.id }) { (i, st) ->
            val o = st.outfit
            Row(
                Modifier.fillMaxWidth().clickable { vm.open(Screen.OutfitDetail(o.id)) },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (c.closed) Text(
                    when { !st.ranked -> "–"; i == 0 -> "🥇"; i == 1 -> "🥈"; i == 2 -> "🥉"; else -> "${i + 1}" },
                    Modifier.padding(horizontal = 4.dp), style = MaterialTheme.typography.titleMedium,
                )
                OutfitArt(o, Modifier.height(64.dp).fillMaxWidth(0.2f), 28)
                Column(Modifier.weight(1f)) {
                    Text("@${state.user(o.authorId)?.handle}", fontWeight = FontWeight.SemiBold)
                    Text(o.caption, maxLines = 1, style = MaterialTheme.typography.bodySmall)
                }
                if (c.closed) Text(if (st.ranked) "${(st.winRate * 100).toInt()}% win" else "Not enough votes", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
fun RanksScreen(state: DripState, vm: DripViewModel) {
    var mode by remember { mutableStateOf(0) }
    val me = state.currentUserId
    val users = state.users.filter { !state.isBlocked(it.id) }.filter {
        when (mode) {
            1 -> it.joinedOrder >= 4
            2 -> it.id == me || state.isFollowing(it.id)
            3 -> it.city == Rules.LAUNCH_CITY
            else -> true
        }
    }.sortedByDescending { state.points(it.id) }
    LazyColumn(contentPadding = com.mariomanhique.drip.ui.bottomBarListPadding(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Global", "Newcomers", "Friends", "${Rules.LAUNCH_CITY} 🏙️").forEachIndexed { i, l ->
                        FilterChip(mode == i, { mode = i }, { Text(l) })
                    }
                }
                Text(
                    "Ranked by Drip Points earned in challenges and showdowns — never by followers.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (users.isEmpty()) item { EmptyState("Nobody here yet") }
        items(users.withIndex().toList(), key = { it.value.id }) { (i, u) ->
            val stats = state.statsFor(u.id)
            Row(
                Modifier.fillMaxWidth().clickable { vm.openProfile(u.id) }.padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("${i + 1}", Modifier.padding(end = 4.dp), fontWeight = FontWeight.Bold)
                Avatar(u)
                Column(Modifier.weight(1f)) {
                    Text(u.displayName + if (u.id == me) " (you)" else "", fontWeight = FontWeight.SemiBold)
                    Text("Drip Score ${stats.dripScore ?: "–"} · ${stats.battleWins}W-${stats.battleLosses}L", style = MaterialTheme.typography.bodySmall)
                }
                Text("${stats.points} pts", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
        }
    }
}
