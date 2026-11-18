package com.mariomanhique.drip.ui.screens

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.mariomanhique.drip.ui.PhotoViewer
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mariomanhique.drip.domain.Battle
import com.mariomanhique.drip.domain.battleShare
import com.mariomanhique.drip.ui.ShareRow
import androidx.compose.foundation.layout.Box
import com.mariomanhique.drip.domain.BattleStatus
import com.mariomanhique.drip.domain.DripState
import com.mariomanhique.drip.domain.Outfit
import com.mariomanhique.drip.domain.Rules
import com.mariomanhique.drip.domain.participantsOf
import com.mariomanhique.drip.domain.votableBattles
import com.mariomanhique.drip.ui.EmptyState
import com.mariomanhique.drip.ui.OutfitArt

/**
 * Anonymous, randomised voting (RULES.md section 3). Voters see "Look A / Look B" with no names, no live counts
 * and only battles they are eligible for. Participants see a read-only status card for their own showdowns.
 */
@Composable
fun BattlesScreen(state: DripState, vm: com.mariomanhique.drip.ui.DripViewModel) {
    val me = state.currentUserId ?: return
    val now = vm.nowMs()
    val toVote = state.votableBattles(me, now)
    val mine = state.battles.filter { me in state.participantsOf(it) || (it.status == BattleStatus.WAITING && it.creatorId == me) }
        .reversed()
    LazyColumn(contentPadding = com.mariomanhique.drip.ui.bottomBarListPadding(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Text(
                "Pick the harder fit. Votes are anonymous and results stay hidden until the showdown closes.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (toVote.isEmpty()) item { EmptyState("Nothing to vote on right now — check back soon") }
        items(toVote, key = { "v${it.id}" }) { VoteCard(state, it, vm) }
        if (mine.isNotEmpty()) {
            item { Text("Your showdowns", style = MaterialTheme.typography.titleMedium) }
            items(mine, key = { "m${it.id}" }) { OwnBattleCard(state, it, me, vm) }
        }
    }
}

@Composable
private fun VoteCard(state: DripState, battle: Battle, vm: com.mariomanhique.drip.ui.DripViewModel) {
    val a = state.outfit(battle.outfitAId) ?: return
    val b = state.outfit(battle.outfitBId ?: return) ?: return
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Side("Look A", a, Modifier.weight(1f)) { vm.vote(battle.id, a.id) }
            Side("Look B", b, Modifier.weight(1f)) { vm.vote(battle.id, b.id) }
        }
    }
}

@Composable
private fun Side(label: String, outfit: Outfit, modifier: Modifier, onVote: () -> Unit) {
    var viewing by remember { mutableStateOf(false) }
    if (viewing) PhotoViewer(outfit) { viewing = false }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        OutfitArt(outfit, Modifier.fillMaxWidth().height(180.dp).clickable { viewing = true }, 56)
        Text(outfit.caption, maxLines = 2, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button(onVote, Modifier.fillMaxWidth()) { Text("Vote $label") }
    }
}

@Composable
private fun OwnBattleCard(state: DripState, battle: Battle, me: String, vm: com.mariomanhique.drip.ui.DripViewModel) {
    val mineId = listOf(battle.outfitAId, battle.outfitBId).firstOrNull { state.outfit(it ?: "")?.authorId == me } ?: battle.outfitAId
    val won = battle.winnerOutfitId == mineId
    val status = when (battle.status) {
        BattleStatus.WAITING -> "⏳ Finding a fair opponent"
        BattleStatus.VOTING -> "🗳️ Voting is open — you can't interact with it"
        BattleStatus.UNDER_REVIEW -> "🔍 Under review"
        BattleStatus.VOID -> "No result (not enough votes or no opponent)"
        BattleStatus.CLOSED -> when {
            battle.draw -> "🤝 Draw"
            won -> "🏆 You won"
            else -> "You lost this one"
        }
    }
    val opponent = (listOf(battle.outfitAId, battle.outfitBId).firstOrNull { it != null && it != mineId })
        ?.let { state.outfit(it) }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            state.outfit(mineId)?.let { OutfitArt(it, Modifier.weight(1f).height(100.dp), 36) }
            if (opponent != null && battle.closed) OutfitArt(opponent, Modifier.weight(1f).height(100.dp), 36)
            Text(status, Modifier.weight(2f), style = MaterialTheme.typography.bodyMedium)
        }
        state.battleShare(battle, me)?.let { Box(Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp)) { ShareRow(it) { m -> vm.toast(m) } } }
    }
}
