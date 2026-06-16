package com.keshav.trashpanzeeopen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ============================================================
// CUSTOM FONT FAMILY DECLARATIONS
// ============================================================
//
// These FontFamily instances reference local .ttf font files
// bundled in the app resources. Place the files at:
//
//   app/src/main/res/font/helvetica_light.ttf
//   app/src/main/res/font/instrumental_serif_regular.ttf
//
// Rules for res/font/ filenames:
//   • Must be lowercase only
//   • Spaces and hyphens are NOT allowed — use underscores
//   • File extension must be .ttf or .otf
//
// After placing the files, Gradle will auto-generate the
// R.font.helvetica_light and R.font.instrumental_serif_regular
// resource references used below.
// ============================================================

val HelveticaLight = FontFamily(
    Font(R.font.helvetica_light, FontWeight.Light)
)

val InstrumentalSerif = FontFamily(
    Font(R.font.instrumental_serif_regular, FontWeight.Normal)
)

// ── Color Palette ───────────────────────────────────────────
private val CanvasBlack = Color(0xFF121212)
private val CardSurface = Color(0xFF2A2A2A)
private val MutedSlateGray = Color(0xFF6B6B6B)
private val SoftGray = Color(0xFF8A8A8A)
private val AvatarBorderColor = Color(0xFF3A3A3A)

// ── Reusable gradient brush for activity placeholder cells ──
private val CellGradient = Brush.verticalGradient(
    colorStops = arrayOf(
        0.4f to Color(0xFF1B1919),
        1.0f to Color(0xFF222222)
    )
)

@Composable
fun UserProfileScreen(
    userName: String,
    totalContributions: Int,
    onBackClicked: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CanvasBlack)
            .padding(horizontal = 24.dp)
    ) {

        // ── 1. Back Navigation Button ───────────────────────
        Spacer(modifier = Modifier.height(48.dp))

        IconButton(
            onClick = onBackClicked,
            modifier = Modifier.size(40.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ── 2. Avatar Placeholder Circle ────────────────────
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(CardSurface)
                .border(
                    width = 1.dp,
                    color = AvatarBorderColor,
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.Person,
                contentDescription = "Profile Avatar",
                tint = SoftGray,
                modifier = Modifier.size(32.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ── 3. Profile Text Stack ───────────────────────────
        Text(
            text = userName.uppercase(),
            fontFamily = HelveticaLight,
            fontWeight = FontWeight.SemiBold,
            fontSize = 18.sp,
            color = Color.White,
            letterSpacing = 1.sp
        )

        Text(
            text = "RANK",
            fontFamily = HelveticaLight,
            fontWeight = FontWeight.Light,
            fontSize = 11.sp,
            color = MutedSlateGray,
            letterSpacing = 1.5.sp
        )

        Spacer(modifier = Modifier.height(24.dp))

        // ── 4. Contribution Score Card ──────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(CardSurface)
        ) {
            // Core number — massive serif display
            Text(
                text = totalContributions.toString(),
                fontFamily = InstrumentalSerif,
                fontWeight = FontWeight.Normal,
                fontSize = 140.sp,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 24.dp)
            )

            // Interior bottom-right label stack
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 20.dp, bottom = 20.dp),
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = "TRASH",
                    fontFamily = HelveticaLight,
                    fontWeight = FontWeight.Light,
                    fontSize = 14.sp,
                    color = SoftGray,
                    letterSpacing = 2.sp,
                    textAlign = TextAlign.End
                )
                Text(
                    text = "POINTS",
                    fontFamily = HelveticaLight,
                    fontWeight = FontWeight.Light,
                    fontSize = 14.sp,
                    color = SoftGray,
                    letterSpacing = 2.sp,
                    textAlign = TextAlign.End
                )
            }
        }

        Spacer(modifier = Modifier.height(48.dp))

        // ── 5. Recent Activity Feed ─────────────────────────
        Text(
            text = "RECENTS",
            fontFamily = HelveticaLight,
            fontWeight = FontWeight.Light,
            fontSize = 13.sp,
            color = SoftGray,
            letterSpacing = 3.sp
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 4 empty gradient activity placeholder bars
        repeat(4) { index ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(brush = CellGradient)
            )
            if (index < 3) {
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}
