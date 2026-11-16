package com.mariomanhique.drip.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mariomanhique.drip.domain.DripState

@Composable
fun MonoLabel(text: String, modifier: Modifier = Modifier, color: Color = Color.Unspecified, size: Int = 12, spacing: Float = 3f) {
    Text(
        text.uppercase(), modifier, color = color, fontSize = size.sp, fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Medium, letterSpacing = spacing.sp,
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Ticker() {
    Box(Modifier.fillMaxWidth().background(DripLime).statusBarsPadding().padding(vertical = 8.dp)) {
        MonoLabel(
            "◆ Post your ensemble   ◆ Rank style points   ◆ Cop the crown   ◆ Drop the fit   ◆ Post your ensemble   ◆ Rank style points",
            Modifier.basicMarquee(iterations = Int.MAX_VALUE, velocity = 40.dp),
            color = Color.Black, size = 11, spacing = 4f,
        )
    }
}

@Composable
fun DripHeader(state: DripState, vm: DripViewModel, showBack: Boolean) {
    Column {
        Ticker()
        Row(
            Modifier.fillMaxWidth().height(60.dp).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (showBack) Text("‹", fontSize = 28.sp, modifier = Modifier.clickable { vm.back() })
            Text("🔥", fontSize = 20.sp)
            Text("DRIP", fontSize = 26.sp, fontWeight = FontWeight.Black, letterSpacing = (-1).sp)
            Box(Modifier.weight(1f))
            HeaderLink("Feed") { vm.selectTab(Tab.FEED) }
            HeaderLink("Ranks") { vm.selectTab(Tab.RANKS) }
            Box(
                Modifier.background(DripLime).clickable { vm.selectTab(Tab.PROFILE) }.padding(horizontal = 14.dp, vertical = 10.dp),
            ) { MonoLabel("@" + (state.currentUser?.handle ?: "me"), color = Color.Black, size = 12, spacing = 1f) }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF26262E)))
    }
}

@Composable
private fun HeaderLink(label: String, onClick: () -> Unit) {
    MonoLabel(label, Modifier.clickable(onClick = onClick), color = MaterialTheme.colorScheme.onSurfaceVariant, size = 13, spacing = 2f)
}

@Composable
fun HeroBlock(onDrop: () -> Unit, onRanks: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().border(1.dp, Color(0xFF26262E)).background(Color(0xFF0D0D11)).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        //MonoLabel("Vol. 01 — Daily Ensemble", color = DripLime, size = 13, spacing = 5f)
        val big = TextStyle(fontSize = 60.sp, lineHeight = 56.sp, fontWeight = FontWeight.Black, letterSpacing = (-3).sp)
        Column {
            Text("DROP THE", style = big, color = Color(0xFFF4F1E8))
            Text("FIT.", style = big, color = DripLime)
            Text("COP THE", style = big.copy(drawStyle = Stroke(width = 3f)), color = Color(0xFFF4F1E8))
            Text("CROWN.", style = big, color = DripPink)
        }
        //Text(
          //  "Drop today's fit. Bank style points on every prop. Climb the ranks and unlock fashion icon badges. The drip community is watching.",
         //   color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 18.sp, lineHeight = 29.sp,
        //)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.background(DripLime).clickable(onClick = onDrop).padding(horizontal = 20.dp, vertical = 18.dp)) {
                MonoLabel("✦  Drop a fit", color = Color.Black, size = 14, spacing = 2f)
            }
            Box(Modifier.border(1.dp, Color(0xFF3A3A44)).clickable(onClick = onRanks).padding(horizontal = 20.dp, vertical = 18.dp)) {
                MonoLabel("♛  See the ranks", size = 14, spacing = 2f)
            }
        }
    }
}
