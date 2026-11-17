package com.mariomanhique.drip.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.border
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import com.mariomanhique.drip.domain.Constraint
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mariomanhique.drip.domain.Badge
import com.mariomanhique.drip.domain.Category
import com.mariomanhique.drip.domain.DripState
import com.mariomanhique.drip.domain.Outfit
import com.mariomanhique.drip.domain.ReportStatus
import com.mariomanhique.drip.domain.ReportTarget
import com.mariomanhique.drip.domain.Rules
import com.mariomanhique.drip.domain.badgesFor
import com.mariomanhique.drip.domain.statsFor
import com.mariomanhique.drip.ui.Avatar
import com.mariomanhique.drip.ui.BadgeChip
import com.mariomanhique.drip.ui.CategoryChips
import com.mariomanhique.drip.ui.DripLime
import com.mariomanhique.drip.ui.DripViewModel
import com.mariomanhique.drip.ui.EmptyState
import com.mariomanhique.drip.ui.OutfitArt
import com.mariomanhique.drip.ui.Pill
import com.mariomanhique.drip.ui.Screen
import com.mariomanhique.drip.ui.SectionTitle

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileScreen(state: DripState, userId: String, vm: DripViewModel) {
    val user = state.user(userId)
    if (user == null) { EmptyState("User not found"); return }
    val isMe = userId == state.currentUserId
    val stats = state.statsFor(userId)
    val badges = state.badgesFor(userId)
    val outfits = state.outfits.filter { it.authorId == userId && (!it.hidden) }
    var showLedger by remember { mutableStateOf(false) }
    var reporting by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Avatar(user, 72.dp)
            Column(Modifier.weight(1f)) {
                Text(user.displayName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("@${user.handle}${if (user.country.isNotBlank()) " · ${user.country}" else ""}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (user.bio.isNotBlank()) Text(user.bio)
        if (user.interests.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            user.interests.forEach { Pill("${it.emoji} ${it.label}") }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Stat("${outfits.size}", "Looks")
            Stat("${state.followers(userId)}", "Followers")
            Stat("${state.following(userId)}", "Following")
            Stat("${stats.points}", "Drip Points")
            Stat(stats.dripScore?.toString() ?: "–", "Drip Score")
        }
        if (isMe) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button({ vm.open(Screen.Create()) }) { Text("＋ Drop a fit") }
                OutlinedButton({ vm.open(Screen.EditProfile) }) { Text("Edit") }
                OutlinedButton({ vm.open(Screen.Settings) }) { Text("Settings") }
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button({ vm.repo.toggleFollow(userId) }) { Text(if (state.isFollowing(userId)) "Following" else "Follow") }
                OutlinedButton({ vm.block(userId) }) { Text(if (state.isBlocked(userId)) "Unblock" else "Block") }
                OutlinedButton({ reporting = true }) { Text("Report") }
            }
        }

        SectionTitle("Badges")
        FlowRow(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (isMe) Badge.entries.forEach { BadgeChip(it, it in badges) }
            else if (badges.isEmpty()) Text("No badges yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
            else badges.forEach { BadgeChip(it) }
        }
        if (isMe) {
            Text(
                "Next up: " + (Badge.entries.firstOrNull { it !in badges }?.let { "${it.title} — ${it.criteria}" } ?: "you've earned everything!"),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton({ showLedger = !showLedger }) { Text(if (showLedger) "Hide points history" else "Show points history") }
            if (showLedger) {
                val events = state.ledger.filter { it.userId == userId }.reversed()
                if (events.isEmpty()) Text("No points yet — enter a challenge or play a showdown.")
                events.forEach {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(it.reason, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                        Text("+${it.points}", color = DripLime)
                    }
                }
            }
        }

        SectionTitle("Fits")
        if (outfits.isEmpty()) EmptyState("No fits dropped yet")
        outfits.sortedByDescending { it.createdAt }.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { o -> OutfitThumb(o, Modifier.weight(1f)) { vm.open(Screen.OutfitDetail(o.id)) } }
                repeat(3 - row.size) { Box(Modifier.weight(1f)) }
            }
        }
    }
    if (reporting) ReportDialog({ reporting = false }) {
        vm.report(ReportTarget.USER, userId, it); reporting = false
    }
}

@Composable
private fun OutfitThumb(o: Outfit, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.clickable(onClick = onClick)) {
        OutfitArt(o, Modifier.fillMaxWidth().height(110.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(10.dp)), 40)
        Text("🔥 ${o.props}", style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun Stat(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun EditProfileScreen(state: DripState, vm: DripViewModel) {
    val me = state.currentUser ?: return
    var name by remember { mutableStateOf(me.displayName) }
    var bio by remember { mutableStateOf(me.bio) }
    var interests by remember { mutableStateOf(me.interests) }
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Display name") }, singleLine = true)
        OutlinedTextField(bio, { bio = it }, Modifier.fillMaxWidth(), label = { Text("Bio") })
        SectionTitle("Style interests")
        Text("Used to personalise your feed and challenges.", style = MaterialTheme.typography.bodySmall)
        InterestPicker(interests) { interests = it }
        Button({ vm.saveProfile(name, bio, interests) }, Modifier.fillMaxWidth()) { Text("Save") }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InterestPicker(selected: Set<Category>, onChange: (Set<Category>) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Category.entries.forEach { c ->
            androidx.compose.material3.FilterChip(
                c in selected,
                { onChange(if (c in selected) selected - c else selected + c) },
                { Text("${c.emoji} ${c.label}") },
            )
        }
    }
}

private val palettes = listOf(
    listOf(0xFF1F1B3A, 0xFF8B5CF6), listOf(0xFF111827, 0xFF374151), listOf(0xFFF59E0B, 0xFFEF4444),
    listOf(0xFF065F46, 0xFF34D399), listOf(0xFF0EA5E9, 0xFF1E3A8A), listOf(0xFFF9A8D4, 0xFFFDE68A),
)
private val emojis = listOf("🧥", "👟", "👗", "🕶️", "👔", "🧢", "👜", "⌚", "🧣", "👖")

@Composable
fun CreateOutfitScreen(state: DripState, challengeId: String?, vm: DripViewModel) {
    var caption by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(state.challenges.firstOrNull { it.id == challengeId }?.category ?: Category.STREETWEAR) }
    var palette by remember { mutableStateOf(palettes.first()) }
    var emoji by remember { mutableStateOf(emojis.first()) }
    val challenge = state.challenges.firstOrNull { it.id == challengeId }
    var photoUri by remember { mutableStateOf<String?>(null) }
    val picker = com.mariomanhique.drip.ui.LocalPhotoPicker.current
    val preview = Outfit("preview", "", caption, category, palette, emoji, 0, photoUri = photoUri)

    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        challenge?.let { Pill("🏆 Entering: ${it.title}") }
        Box(Modifier.fillMaxWidth().height(320.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp))) {
            OutfitArt(preview, Modifier.matchParentSize())
            if (photoUri != null) {
                Box(Modifier.fillMaxWidth().height(70.dp).background(Color.Black.copy(alpha = 0.45f)), Alignment.Center) {
                    Text("Face zone: crop it out", color = Color.White, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button({ picker.pick { if (it != null) photoUri = it } }, Modifier.weight(1f)) {
                Text(if (photoUri == null) "Choose from gallery" else "Change photo")
            }
            if (photoUri != null) androidx.compose.material3.OutlinedButton({ photoUri = null }) { Text("Remove") }
        }
        Text(
            "Neck-down photos only (RULES.md): keep faces out of frame, no beauty filters. Without a photo your look uses a colour mood and hero piece.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(caption, { caption = it }, Modifier.fillMaxWidth(), label = { Text("Caption") })
        SectionTitle("Category")
        CategoryChips(category, { it?.let { c -> category = c } }, allowAll = false)
        if (photoUri == null) SectionTitle("Colour mood")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            palettes.forEach { p ->
                Box(
                    Modifier.size(36.dp).clip(CircleShape)
                        .background(androidx.compose.ui.graphics.Brush.verticalGradient(p.map { Color(it) }))
                        .border(if (p == palette) 3.dp else 0.dp, DripLime, CircleShape)
                        .clickable { palette = p },
                )
            }
        }
        SectionTitle("Hero piece")
        FlowRow2(emojis, emoji) { emoji = it }
        val pledged = challenge != null && challenge.constraint != Constraint.OPEN
        var pledgeOk by remember { mutableStateOf(false) }
        if (pledged) {
            Row(Modifier.clickable { pledgeOk = !pledgeOk }, verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.Checkbox(pledgeOk, { pledgeOk = it })
                Text("${challenge!!.constraint.label}: ${challenge.constraint.pledge}", style = MaterialTheme.typography.bodyMedium)
            }
        }
        Button({ vm.publish(caption, category, palette, emoji, challengeId, pledgeOk, photoUri) }, Modifier.fillMaxWidth(), enabled = caption.isNotBlank() && (!pledged || pledgeOk)) {
            Text("DROP IT 🔥")
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowRow2(items: List<String>, selected: String, onSelect: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach {
            Box(
                Modifier.size(48.dp).clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(if (it == selected) 2.dp else 0.dp, DripLime, CircleShape)
                    .clickable { onSelect(it) },
                Alignment.Center,
            ) { Text(it, fontSize = 24.sp) }
        }
    }
}

@Composable
fun SettingsScreen(vm: DripViewModel) {
    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedButton({ vm.open(Screen.Rules) }, Modifier.fillMaxWidth()) { Text("Community rules & point system") }
        OutlinedButton({ vm.open(Screen.Moderation) }, Modifier.fillMaxWidth()) { Text("Moderation queue (demo)") }
        Button({ vm.logout() }, Modifier.fillMaxWidth()) { Text("Log out") }
    }
}

@Composable
fun RulesScreen() {
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle("Community rules")
        Rules.communityRules.forEach { Text("• $it") }
        SectionTitle("How Drip Points work")
        Text("• Showdown: win +${Rules.BATTLE_WIN_POINTS}, draw +${Rules.BATTLE_DRAW_POINTS}, loss +${Rules.BATTLE_LOSS_POINTS}")
        Text("• Challenge podium: ${Rules.PODIUM_POINTS.joinToString(" / ") { "+$it" }}; other entries +${Rules.CHALLENGE_ENTRY_POINTS}")
        Text("• Voting earns no points. Daily cap: ${Rules.DAILY_POINTS_CAP} points.")
        Text("• Drip Score appears after ${Rules.MIN_RATED_BATTLES_FOR_SCORE} showdowns and only moves with results.")
        Text("• A showdown needs ${Rules.QUORUM} valid votes, otherwise it ends with no result.")
        Text("• ${Rules.REPORTS_TO_AUTO_HIDE} reports hide an outfit until a moderator reviews it.")
    }
}

@Composable
fun ModerationScreen(state: DripState, vm: DripViewModel) {
    val open = state.reports.filter { it.status == ReportStatus.OPEN }
    val openChallenges = state.challenges.filter { !it.closed }
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionTitle("Open reports")
        if (open.isEmpty()) Text("Nothing to review 🎉")
        open.forEach { r ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    val label = if (r.targetType == ReportTarget.OUTFIT) state.outfit(r.targetId)?.caption else state.user(r.targetId)?.handle?.let { "@$it" }
                    Text("${r.targetType.name.lowercase()}: ${label ?: r.targetId}", fontWeight = FontWeight.SemiBold)
                    Text("Reason: ${r.reason}", style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button({ vm.repo.resolveReport(r.id, true) }) { Text("Remove") }
                        OutlinedButton({ vm.repo.resolveReport(r.id, false) }) { Text("Dismiss") }
                    }
                }
            }
        }
        SectionTitle("Close a challenge & award podium")
        openChallenges.forEach { OutlinedButton({ vm.closeChallenge(it.id) }, Modifier.fillMaxWidth()) { Text("Close: ${it.title}") } }
    }
}
