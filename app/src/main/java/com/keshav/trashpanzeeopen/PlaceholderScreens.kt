package com.keshav.trashpanzeeopen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ── Shared Colors ───────────────────────────────────────────
private val ScreenBackground = Color(0xFF121212)
private val SubtitleGray = Color(0xFF6B6B6B)

@Composable
private fun PlaceholderScreen(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onBackClicked: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ScreenBackground)
            .padding(horizontal = 24.dp)
    ) {
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

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 100.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = SubtitleGray,
                modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = title,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                letterSpacing = 0.5.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = subtitle,
                fontSize = 14.sp,
                color = SubtitleGray,
                letterSpacing = 1.sp
            )
        }
    }
}

@Composable
fun MyTrashLocationsScreen(onBackClicked: () -> Unit) {
    PlaceholderScreen(
        title = "My Trash Locations",
        subtitle = "COMING SOON",
        icon = Icons.Default.Map,
        onBackClicked = onBackClicked
    )
}

@Composable
fun LeaderboardScreen(onBackClicked: () -> Unit) {
    PlaceholderScreen(
        title = "Leaderboard",
        subtitle = "COMING SOON",
        icon = Icons.Default.EmojiEvents,
        onBackClicked = onBackClicked
    )
}

@Composable
fun MessagesScreen(onBackClicked: () -> Unit) {
    PlaceholderScreen(
        title = "Messages",
        subtitle = "YOUR INBOX IS EMPTY",
        icon = Icons.Default.Mail,
        onBackClicked = onBackClicked
    )
}
