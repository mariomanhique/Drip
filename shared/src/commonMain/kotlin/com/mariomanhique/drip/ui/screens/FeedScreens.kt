package com.mariomanhique.drip.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mariomanhique.drip.domain.Category
import com.mariomanhique.drip.domain.DripState
import com.mariomanhique.drip.domain.Outfit
import com.mariomanhique.drip.domain.ReportTarget
import com.mariomanhique.drip.ui.Avatar
import com.mariomanhique.drip.ui.CategoryChips
import com.mariomanhique.drip.ui.DripLime
import com.mariomanhique.drip.ui.DripViewModel
import com.mariomanhique.drip.ui.EmptyState
import com.mariomanhique.drip.ui.OutfitArt
import com.mariomanhique.drip.ui.Pill
import com.mariomanhique.drip.ui.Screen
import com.mariomanhique.drip.ui.SectionTitle

@Composable
fun FeedScreen(state: DripState, vm: DripViewModel, listState: LazyListState = rememberLazyListState()) {
    var category by vm.feedCategory
    var followingOnly by vm.feedFollowingOnly
    val outfits = state.visibleOutfits()
        .filter { category == null || it.category == category }
        .filter { !followingOnly || state.isFollowing(it.authorId) }

    LazyColumn(Modifier.fillMaxWidth(), listState, contentPadding = com.mariomanhique.drip.ui.bottomBarListPadding(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            com.mariomanhique.drip.ui.HeroBlock({ vm.open(Screen.Create()) }, { vm.selectTab(com.mariomanhique.drip.ui.Tab.RANKS) })
        }
        item {
            Column {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(!followingOnly, { followingOnly = false }, { Text("For you") })
                    FilterChip(followingOnly, { followingOnly = true }, { Text("Following") })
                }
                CategoryChips(category, { category = it })
            }
        }
        if (outfits.isEmpty()) item { EmptyState(if (followingOnly) "Follow people to fill this feed" else "No fits here yet") }
        items(outfits, key = { it.id }) { OutfitCard(state, it, vm) }
    }
}

@Composable
fun OutfitCard(state: DripState, outfit: Outfit, vm: DripViewModel) {
    val author = state.user(outfit.authorId)
    val me = state.currentUserId
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column {
            Row(
                Modifier.fillMaxWidth().clickable { vm.openProfile(outfit.authorId) }.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                author?.let { Avatar(it) }
                Column(Modifier.weight(1f)) {
                    Text(author?.displayName ?: "Unknown", fontWeight = FontWeight.SemiBold)
                    Text("@${author?.handle}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Pill("${outfit.category.emoji} ${outfit.category.label}")
            }
            OutfitArt(
                outfit,
                Modifier.fillMaxWidth().height(260.dp).clickable { vm.open(Screen.OutfitDetail(outfit.id)) },
            )
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(outfit.caption)
                state.challenges.firstOrNull { it.id == outfit.challengeId }?.let {
                    Text("🏆 ${it.title}", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    val given = me != null && me in outfit.propsFrom
                    TextButton({ vm.repo.toggleProps(outfit.id) }, enabled = outfit.authorId != me) {
                        androidx.compose.material3.Icon(
                            if (given) com.mariomanhique.drip.ui.FireFilled else com.mariomanhique.drip.ui.FireOutlined,
                            contentDescription = "Fire",
                            tint = if (given) androidx.compose.ui.graphics.Color(0xFFFF6B1A) else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(24.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("${outfit.props}", color = MaterialTheme.colorScheme.onSurface)
                    }
                    TextButton({ vm.open(Screen.OutfitDetail(outfit.id)) }) {
                        androidx.compose.material3.Icon(
                            com.mariomanhique.drip.ui.CommentOutlined, "Comments",
                            tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(24.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("${outfit.comments.size}", color = MaterialTheme.colorScheme.onSurface)
                    }
                    Spacer(Modifier.weight(1f))
                    val saved = me != null && me in outfit.savedBy
                    androidx.compose.material3.IconButton({ vm.repo.toggleSave(outfit.id) }) {
                        androidx.compose.material3.Icon(
                            if (saved) com.mariomanhique.drip.ui.BookmarkFilled else com.mariomanhique.drip.ui.BookmarkOutlined,
                            "Save", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(24.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun OutfitDetailScreen(state: DripState, outfitId: String, vm: DripViewModel) {
    val outfit = state.outfit(outfitId)
    if (outfit == null) { EmptyState("Fit not found"); return }
    val me = state.currentUserId
    var comment by remember { mutableStateOf("") }
    var reporting by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = com.mariomanhique.drip.ui.LocalBottomBarInset.current), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutfitCard(state, outfit, vm)
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (outfit.authorId == me) {
                Button({ vm.startBattle(outfit.id) }) { Text("⚔️ Start a showdown") }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton({ reporting = true }) { Text("Report") }
                    OutlinedButton({ vm.block(outfit.authorId); vm.back() }) { Text("Block") }
                }
            }
            SectionTitle("Comments")
            if (outfit.comments.isEmpty()) Text("Be the first to comment", color = MaterialTheme.colorScheme.onSurfaceVariant)
            outfit.comments.forEach {
                val author = state.user(it.authorId)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    author?.let { a -> Avatar(a, 28.dp) }
                    Column {
                        Text("@${author?.handle}", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelMedium)
                        Text(it.text)
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(comment, { comment = it }, Modifier.weight(1f), placeholder = { Text("Add a comment") }, singleLine = true)
                Button({ vm.repo.addComment(outfit.id, comment); comment = "" }, enabled = comment.isNotBlank()) { Text("Send") }
            }
        }
    }

    if (reporting) ReportDialog({ reporting = false }) { reason ->
        vm.report(ReportTarget.OUTFIT, outfit.id, reason)
        reporting = false
    }
}

val reportReasons = listOf("Spam", "Stolen photo", "Nudity or explicit", "Hate or harassment", "Other")

@Composable
fun ReportDialog(onDismiss: () -> Unit, onSubmit: (String) -> Unit) {
    var reason by remember { mutableStateOf(reportReasons.first()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Report") },
        text = {
            Column {
                reportReasons.forEach {
                    Row(
                        Modifier.fillMaxWidth().selectable(reason == it, onClick = { reason = it }),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(reason == it, { reason = it })
                        Text(it)
                    }
                }
            }
        },
        confirmButton = { TextButton({ onSubmit(reason) }) { Text("Submit") } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
    )
}
