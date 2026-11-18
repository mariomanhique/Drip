package com.mariomanhique.drip.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.mariomanhique.drip.domain.Badge
import com.mariomanhique.drip.domain.Category
import com.mariomanhique.drip.domain.Outfit
import com.mariomanhique.drip.domain.User

val DripViolet = Color(0xFF8B5CF6)
val DripLime = Color(0xFFA6FF00)
val DripPink = Color(0xFFE91EAD)

private val darkScheme: ColorScheme = darkColorScheme(
    primary = DripLime,
    onPrimary = Color.Black,
    secondary = DripLime,
    onSecondary = Color.Black,
    background = Color(0xFF0A0A0D),
    onBackground = Color(0xFFF4F4F5),
    surface = Color(0xFF111116),
    onSurface = Color(0xFFF4F4F5),
    surfaceVariant = Color(0xFF1F1F29),
    onSurfaceVariant = Color(0xFFA1A1AA),
    error = Color(0xFFFB7185),
)

private val lightScheme: ColorScheme = lightColorScheme(
    primary = Color(0xFF4D7C0F),
    onPrimary = Color.White,
    secondary = Color(0xFF4D7C0F),
    onSecondary = Color.White,
    background = Color(0xFFFFFFFF),
    onBackground = Color(0xFF0A0A0D),
    surface = Color(0xFFFAFAFA),
    onSurface = Color(0xFF0A0A0D),
    surfaceVariant = Color(0xFFEFEFF2),
    onSurfaceVariant = Color(0xFF52525B),
    error = Color(0xFFBE123C),
)

@Composable
fun DripTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) darkScheme else lightScheme,
        shapes = androidx.compose.material3.Shapes(
            extraSmall = RoundedCornerShape(0.dp), small = RoundedCornerShape(0.dp),
            medium = RoundedCornerShape(0.dp), large = RoundedCornerShape(0.dp), extraLarge = RoundedCornerShape(0.dp),
        ),
        content = content,
    )
}

@Composable
fun OutfitArt(
    outfit: Outfit,
    modifier: Modifier = Modifier,
    emojiSize: Int = 72,
    contentScale: ContentScale = ContentScale.Crop,
    alignment: Alignment = Alignment.BottomCenter,
) {
    val colors = outfit.palette.map { Color(it) }.ifEmpty { listOf(Color.DarkGray, Color.Gray) }
    Box(
        modifier.background(Brush.verticalGradient(if (colors.size == 1) colors + colors else colors)),
        contentAlignment = Alignment.Center,
    ) {
        if (outfit.photoUri != null) {
            // Bottom-aligned crop keeps the outfit and trims the top of the frame, where faces usually are.
            AsyncImage(
                model = outfit.photoUri, contentDescription = outfit.caption,
                modifier = Modifier.matchParentSize(), contentScale = contentScale, alignment = alignment,
            )
        } else Text(outfit.emoji, fontSize = emojiSize.sp)
        if (outfit.hidden) {
            Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.7f)), Alignment.Center) {
                Text("Hidden pending review", color = Color.White)
            }
        }
    }
}

@Composable
fun Avatar(user: User, size: Dp = 36.dp) {
    Box(
        Modifier.size(size).clip(CircleShape).background(Brush.linearGradient(listOf(DripViolet, DripLime))),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            user.displayName.take(1).uppercase(),
            color = Color.Black,
            fontWeight = FontWeight.Bold,
            fontSize = (size.value * 0.45f).sp,
        )
    }
}

@Composable
fun UserRow(user: User, onClick: () -> Unit, trailing: @Composable () -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Avatar(user)
        Column(Modifier.weight(1f)) {
            Text(user.displayName, fontWeight = FontWeight.SemiBold)
            Text("@${user.handle}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        }
        trailing()
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = modifier.padding(vertical = 8.dp))
}

@Composable
fun CategoryChips(selected: Category?, onSelect: (Category?) -> Unit, allowAll: Boolean = true) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (allowAll) FilterChip(selected == null, { onSelect(null) }, { Text("All") })
        Category.entries.forEach {
            FilterChip(selected == it, { onSelect(it) }, { Text("${it.emoji} ${it.label}") })
        }
    }
}

@Composable
fun BadgeChip(badge: Badge, earned: Boolean = true) {
    AssistChip(
        onClick = {},
        label = { Text("${badge.emoji} ${badge.title} · ${badge.rarity.label}") },
        modifier = Modifier.padding(end = 6.dp),
        enabled = earned,
    )
}

@Composable
fun Pill(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier.clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 10.dp, vertical = 4.dp),
    ) { Text(text, fontSize = 12.sp) }
}

@Composable
fun EmptyState(text: String) {
    Box(Modifier.fillMaxWidth().height(160.dp), Alignment.Center) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
