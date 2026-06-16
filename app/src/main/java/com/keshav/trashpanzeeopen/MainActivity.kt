package com.keshav.trashpanzeeopen

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Rect
import android.location.Location
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.outlined.AlternateEmail
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Mail
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.maplibre.android.MapLibre
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.annotations.Icon
import org.maplibre.android.annotations.IconFactory
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.location.modes.RenderMode
import java.util.UUID
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Check

class MainActivity : ComponentActivity() {

    private lateinit var cameraExecutor: ExecutorService
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var pinStorage: PinStorageManager

    private val trashCans = mutableStateListOf<TrashCan>()

    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val cameraGranted = permissions[Manifest.permission.CAMERA] ?: false
        val locationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false

        if (cameraGranted && locationGranted) {
            setupUI()
        } else {
            Toast.makeText(this, "Camera and Location permissions are required!", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        MapLibre.getInstance(this)
        cameraExecutor = Executors.newSingleThreadExecutor()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        pinStorage = PinStorageManager(this)
        trashCans.addAll(pinStorage.loadCans())

        // 🚀 SIMULATION: Dense local test cluster around Kanhangad
        if (trashCans.isEmpty()) {
            val random = java.util.Random()
            repeat(25) {
                val randomLat = 12.3186 + (random.nextDouble() - 0.5) * 0.08
                val randomLng = 75.0855 + (random.nextDouble() - 0.5) * 0.08

                val mockContributors = mutableListOf("Community Bot")
                if (random.nextBoolean()) mockContributors.add("User${random.nextInt(99)}")
                if (random.nextBoolean()) mockContributors.add("EcoWarrior")

                trashCans.add(
                    TrashCan(
                        id = UUID.randomUUID().toString(),
                        latitude = randomLat,
                        longitude = randomLng,
                        status = if (random.nextBoolean()) "Empty" else "Full",
                        contributors = mockContributors,
                        upvotes = random.nextInt(20),
                        downvotes = random.nextInt(8)
                    )
                )
            }
            pinStorage.saveCans(trashCans)
        }

        val hasCamera = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        val hasLocation = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

        if (hasCamera && hasLocation) {
            setupUI()
        } else {
            requestPermissionsLauncher.launch(
                arrayOf(
                    Manifest.permission.CAMERA,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    private fun setupUI() {
        setContent {
            var currentUserName by remember { mutableStateOf(pinStorage.userName) }

            if (currentUserName.isNullOrEmpty()) {
                UserRegistrationScreen(
                    onNameSaved = { newName ->
                        pinStorage.userName = newName
                        currentUserName = newName
                    }
                )
            } else {
                var currentScreen by remember { mutableStateOf("GLOBAL_MAP") }
                var isScannerOpen by remember { mutableStateOf(false) }
                var scannerTargetCanId by remember { mutableStateOf<String?>(null) }
                var selectedCan by remember { mutableStateOf<TrashCan?>(null) }
                val sheetState = rememberModalBottomSheetState()

                val myActiveUserName = currentUserName!!

                // ── Screen Router ───────────────────────────────
                when (currentScreen) {
                    "PROFILE" -> {
                        val myTotalContributions = trashCans.count { it.contributors.contains(myActiveUserName) }
                        UserProfileScreen(
                            userName = myActiveUserName,
                            totalContributions = myTotalContributions,
                            onBackClicked = { currentScreen = "GLOBAL_MAP" }
                        )
                    }
                    "MY_LOCATIONS" -> {
                        MyTrashLocationsScreen(onBackClicked = { currentScreen = "GLOBAL_MAP" })
                    }
                    "LEADERBOARD" -> {
                        LeaderboardScreen(onBackClicked = { currentScreen = "GLOBAL_MAP" })
                    }
                    "MESSAGES" -> {
                        MessagesScreen(onBackClicked = { currentScreen = "GLOBAL_MAP" })
                    }
                    else -> {
                        // ── GLOBAL_MAP (Home) ───────────────────
                        Box(modifier = Modifier.fillMaxSize()) {
                            if (isScannerOpen) {
                                CameraScannerOverlay(
                                    fusedLocationClient = fusedLocationClient,
                                    onCloseScanner = { isScannerOpen = false },
                                    onTrashCanLogged = { lat, lng, status ->
                                        if (scannerTargetCanId != null) {
                                            // Update existing can explicitly
                                            val index = trashCans.indexOfFirst { it.id == scannerTargetCanId }
                                            if (index != -1) {
                                                trashCans[index] = trashCans[index].copy(status = status)
                                            }
                                        } else {
                                            // Add new can or verify close existing
                                            val existingCanIndex = trashCans.indexOfFirst { existing ->
                                                val results = FloatArray(1)
                                                Location.distanceBetween(lat, lng, existing.latitude, existing.longitude, results)
                                                results[0] < 20f
                                            }

                                            if (existingCanIndex != -1) {
                                                val existingCan = trashCans[existingCanIndex]
                                                val updatedContributors = if (!existingCan.contributors.contains(myActiveUserName)) {
                                                    Toast.makeText(this@MainActivity, "You verified an existing can!", Toast.LENGTH_SHORT).show()
                                                    existingCan.contributors + myActiveUserName
                                                } else {
                                                    Toast.makeText(this@MainActivity, "You updated this can!", Toast.LENGTH_SHORT).show()
                                                    existingCan.contributors
                                                }
                                                trashCans[existingCanIndex] = existingCan.copy(status = status, contributors = updatedContributors)
                                            } else {
                                                val newCan = TrashCan(
                                                    id = UUID.randomUUID().toString(),
                                                    latitude = lat,
                                                    longitude = lng,
                                                    status = status,
                                                    contributors = listOf(myActiveUserName)
                                                )
                                                trashCans.add(newCan)
                                            }
                                        }

                                        pinStorage.saveCans(trashCans)
                                        isScannerOpen = false
                                        scannerTargetCanId = null
                                    }
                                )
                            } else {
                                SnapchatTrashMap(
                                    cans = trashCans.toList(),
                                    selectedCanId = selectedCan?.id,
                                    onCanClicked = { canId ->
                                        selectedCan = trashCans.find { it.id == canId }
                                    }
                                )

                                // ── Top Bar: Profile (left) + Messages (right) ──
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(140.dp)
                                        .background(
                                            Brush.verticalGradient(
                                                colors = listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent)
                                            )
                                        )
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 48.dp, start = 16.dp, end = 16.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        IconButton(
                                            onClick = { currentScreen = "PROFILE" },
                                            modifier = Modifier
                                                .size(48.dp)
                                                .background(Color.Black, CircleShape)
                                        ) {
                                            Icon(Icons.Outlined.Person, contentDescription = "Profile", tint = Color.White)
                                        }

                                        IconButton(
                                            onClick = { currentScreen = "MESSAGES" },
                                            modifier = Modifier
                                                .size(48.dp)
                                                .background(Color.Black, CircleShape)
                                        ) {
                                            Icon(Icons.Outlined.Mail, contentDescription = "Messages", tint = Color.White)
                                        }
                                    }
                                }

                                // ── Bottom Navigation Bar ───────────────────────
                                val navBarColor = Color(0xFF222222)
                                val activeColor = Color.White
                                val inactiveColor = Color.White

                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .fillMaxWidth()
                                ) {
                                    // Gradient shadow above the nav bar
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(180.dp)
                                            .align(Alignment.BottomCenter)
                                            .background(
                                                Brush.verticalGradient(
                                                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.9f))
                                                )
                                            )
                                    )

                                    // Flat bottom navigation bar
                                    Surface(
                                        modifier = Modifier
                                            .align(Alignment.BottomCenter)
                                            .fillMaxWidth()
                                            .height(60.dp),
                                        color = navBarColor
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxSize(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceEvenly
                                        ) {
                                            // Tab 1: My Trash Locations
                                            IconButton(
                                                onClick = { currentScreen = "MY_LOCATIONS" }
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Outlined.AlternateEmail,
                                                    contentDescription = "My Trash Locations",
                                                    tint = inactiveColor,
                                                    modifier = Modifier.size(26.dp)
                                                )
                                            }

                                            // Tab 2: Global Map (Active)
                                            Box(
                                                modifier = Modifier
                                                    .clickable(
                                                        indication = null,
                                                        interactionSource = remember { MutableInteractionSource() }
                                                    ) { /* Already on Global Map */ }
                                                    .padding(vertical = 12.dp, horizontal = 16.dp)
                                                    .drawBehind {
                                                        // Thin white underline indicator
                                                        val underlineWidth = size.width * 0.7f
                                                        val underlineHeight = 1.dp.toPx()
                                                        drawRoundRect(
                                                            color = activeColor,
                                                            topLeft = Offset(
                                                                (size.width - underlineWidth) / 2f,
                                                                size.height - underlineHeight - 2.dp.toPx()
                                                            ),
                                                            size = Size(underlineWidth, underlineHeight),
                                                            cornerRadius = CornerRadius(0.5.dp.toPx())
                                                        )
                                                    },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Outlined.LocationOn,
                                                    contentDescription = "Global Map",
                                                    tint = activeColor,
                                                    modifier = Modifier.size(26.dp)
                                                )
                                            }

                                            // Tab 3: Leaderboard
                                            IconButton(
                                                onClick = { currentScreen = "LEADERBOARD" }
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Outlined.BarChart,
                                                    contentDescription = "Leaderboard",
                                                    tint = inactiveColor,
                                                    modifier = Modifier.size(26.dp)
                                                )
                                            }
                                        }
                                    }

                                    // Floating Camera Button (Overlaps the bottom nav bar)
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomCenter)
                                            .padding(bottom = 100.dp) // Adjusted to match the floating overlap
                                            .size(76.dp)
                                            .background(Color(0xFF333333), CircleShape)
                                            .clickable(
                                                indication = null,
                                                interactionSource = remember { MutableInteractionSource() }
                                            ) { 
                                                scannerTargetCanId = null
                                                isScannerOpen = true 
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.PhotoCamera,
                                            contentDescription = "Scan Trash Can",
                                            tint = Color.White,
                                            modifier = Modifier.size(32.dp)
                                        )
                                    }
                                }
                            }

                            // ── Trash Can Detail Bottom Sheet ───────────────
                            if (selectedCan != null) {
                                ModalBottomSheet(
                                    onDismissRequest = { selectedCan = null },
                                    sheetState = sheetState,
                                    containerColor = Color(0xFF1E1E1E),
                                    contentColor = Color.White,
                                    dragHandle = {
                                        Box(
                                            modifier = Modifier
                                                .padding(top = 12.dp)
                                                .width(48.dp)
                                                .height(4.dp)
                                                .background(Color(0xFF333333), RoundedCornerShape(2.dp))
                                        )
                                    }
                                ) {
                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Spacer(modifier = Modifier.height(16.dp))

                                        // 1. Trash Can ID
                                        val displayId = selectedCan!!.id.take(6).uppercase()
                                        Text(
                                            text = "TrashCan ID :$displayId",
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = Color.White
                                        )

                                        Spacer(modifier = Modifier.height(4.dp))

                                        // 2. Status with blip indicator
                                        val statusColor = when(selectedCan!!.status) {
                                            "Empty" -> Color(0xFF4CAF50)
                                            "Low" -> Color(0xFF8BC34A)
                                            "Half-filled" -> Color(0xFFFFEB3B)
                                            "High" -> Color(0xFFFF9800)
                                            else -> Color(0xFFE91E63)
                                        }

                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.Center
                                        ) {
                                            Text(
                                                text = "status : ",
                                                fontSize = 14.sp,
                                                color = Color(0xFF9E9E9E)
                                            )
                                            Box(
                                                modifier = Modifier
                                                    .size(14.dp)
                                                    .background(statusColor, CircleShape)
                                            )
                                        }

                                        Spacer(modifier = Modifier.height(48.dp))

                                        // 3. Camera button — to update findings
                                        Box(
                                            modifier = Modifier
                                                .size(76.dp)
                                                .background(Color(0xFF333333), CircleShape)
                                                .clickable(
                                                    indication = null,
                                                    interactionSource = remember { MutableInteractionSource() }
                                                ) {
                                                    scannerTargetCanId = selectedCan?.id
                                                    selectedCan = null
                                                    isScannerOpen = true
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.PhotoCamera,
                                                contentDescription = "Update Findings",
                                                tint = Color.White,
                                                modifier = Modifier.size(32.dp)
                                            )
                                        }

                                        Spacer(modifier = Modifier.height(48.dp))

                                        // 4. Bottom action band (Upvote / Warning / Downvote)
                                        val canForVote = selectedCan!!
                                        val hasUpvoted = canForVote.upvotedUsers?.contains(myActiveUserName) == true
                                        val hasDownvoted = canForVote.downvotedUsers?.contains(myActiveUserName) == true

                                        var showReportDialog by remember { mutableStateOf(false) }

                                        val ctx = LocalContext.current
                                        if (showReportDialog) {
                                            AlertDialog(
                                                onDismissRequest = { showReportDialog = false },
                                                containerColor = Color(0xFF2B2B2B),
                                                titleContentColor = Color.White,
                                                textContentColor = Color.LightGray,
                                                title = { Text("Report Trash Can") },
                                                text = {
                                                    Column {
                                                        Text("What is the issue with this trash can location?")
                                                        Spacer(modifier = Modifier.height(16.dp))
                                                        val reports = listOf(
                                                            "Trash can is missing/removed",
                                                            "Location is inaccurate",
                                                            "Trash can is damaged",
                                                            "Not a public trash can"
                                                        )
                                                        reports.forEach { reportText ->
                                                            TextButton(
                                                                onClick = { 
                                                                    showReportDialog = false
                                                                    android.widget.Toast.makeText(ctx, "Report submitted: $reportText", android.widget.Toast.LENGTH_SHORT).show()
                                                                },
                                                                modifier = Modifier.fillMaxWidth()
                                                            ) {
                                                                Text(reportText, color = Color(0xFFE91E63), textAlign = androidx.compose.ui.text.style.TextAlign.Start, modifier = Modifier.fillMaxWidth())
                                                            }
                                                        }
                                                    }
                                                },
                                                confirmButton = {
                                                    TextButton(onClick = { showReportDialog = false }) { Text("Cancel", color = Color.White) }
                                                }
                                            )
                                        }

                                        val hasVotedAny = hasUpvoted || hasDownvoted
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(Color(0xFF171717))
                                                .padding(vertical = 16.dp, horizontal = 24.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                // Upvote button
                                                val upText = if (hasVotedAny) "👍 ${canForVote.upvotes}" else "👍Upvote"
                                                Button(
                                                    onClick = {
                                                        val index = trashCans.indexOfFirst { it.id == canForVote.id }
                                                        if (index != -1) {
                                                            val currentCan = trashCans[index]
                                                            val upUsers = currentCan.upvotedUsers?.toMutableList() ?: mutableListOf()
                                                            val downUsers = currentCan.downvotedUsers?.toMutableList() ?: mutableListOf()
                                                            var newUpvotes = currentCan.upvotes
                                                            var newDownvotes = currentCan.downvotes
                                                            
                                                            if (hasUpvoted) {
                                                                upUsers.remove(myActiveUserName)
                                                                newUpvotes--
                                                            } else {
                                                                upUsers.add(myActiveUserName)
                                                                newUpvotes++
                                                                if (hasDownvoted) {
                                                                    downUsers.remove(myActiveUserName)
                                                                    newDownvotes--
                                                                }
                                                            }
                                                            val updated = currentCan.copy(
                                                                upvotes = newUpvotes,
                                                                downvotes = newDownvotes,
                                                                upvotedUsers = upUsers,
                                                                downvotedUsers = downUsers
                                                            )
                                                            trashCans[index] = updated
                                                            selectedCan = updated
                                                            pinStorage.saveCans(trashCans)
                                                        }
                                                    },
                                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF385A4A)),
                                                    shape = RoundedCornerShape(20.dp),
                                                    modifier = Modifier.height(44.dp).weight(1f)
                                                ) {
                                                    Text(upText, color = Color.White, fontSize = 14.sp)
                                                }

                                                Spacer(modifier = Modifier.width(16.dp))

                                                // Warning / Note indicator (Report Button)
                                                Box(
                                                    modifier = Modifier
                                                        .size(44.dp)
                                                        .background(Color(0xFF59513B), CircleShape)
                                                        .clickable { showReportDialog = true },
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Outlined.WarningAmber,
                                                        contentDescription = "Report Trash Can",
                                                        tint = Color(0xFFDED5B5),
                                                        modifier = Modifier.size(24.dp)
                                                    )
                                                }

                                                Spacer(modifier = Modifier.width(16.dp))

                                                // Downvote button
                                                val downText = if (hasVotedAny) "👎 ${canForVote.downvotes}" else "👎Downvote"
                                                Button(
                                                    onClick = {
                                                        val index = trashCans.indexOfFirst { it.id == canForVote.id }
                                                        if (index != -1) {
                                                            val currentCan = trashCans[index]
                                                            val upUsers = currentCan.upvotedUsers?.toMutableList() ?: mutableListOf()
                                                            val downUsers = currentCan.downvotedUsers?.toMutableList() ?: mutableListOf()
                                                            var newUpvotes = currentCan.upvotes
                                                            var newDownvotes = currentCan.downvotes
                                                            
                                                            if (hasDownvoted) {
                                                                downUsers.remove(myActiveUserName)
                                                                newDownvotes--
                                                            } else {
                                                                downUsers.add(myActiveUserName)
                                                                newDownvotes++
                                                                if (hasUpvoted) {
                                                                    upUsers.remove(myActiveUserName)
                                                                    newUpvotes--
                                                                }
                                                            }
                                                            val updated = currentCan.copy(
                                                                upvotes = newUpvotes,
                                                                downvotes = newDownvotes,
                                                                upvotedUsers = upUsers,
                                                                downvotedUsers = downUsers
                                                            )
                                                            trashCans[index] = updated
                                                            selectedCan = updated
                                                            pinStorage.saveCans(trashCans)
                                                        }
                                                    },
                                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF5A3838)),
                                                    shape = RoundedCornerShape(20.dp),
                                                    modifier = Modifier.height(44.dp).weight(1f)
                                                ) {
                                                    Text(downText, color = Color.White, fontSize = 14.sp)
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(32.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun updateCanStatus(id: String, newStatus: String) {
        val index = trashCans.indexOfFirst { it.id == id }
        if (index != -1) {
            trashCans[index] = trashCans[index].copy(status = newStatus)
            pinStorage.saveCans(trashCans)
        }
    }

    // ==========================================
    // UI COMPONENTS
    // ==========================================

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun UserRegistrationScreen(onNameSaved: (String) -> Unit) {
        var text by remember { mutableStateOf("") }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF5F5F5))
                .padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Trashpanzee", fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF007AFF))
            Spacer(modifier = Modifier.height(8.dp))
            Text("Create your local profile to start mapping.", fontSize = 16.sp, color = Color.Gray)
            Spacer(modifier = Modifier.height(48.dp))

            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Choose a Username") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    if (text.isNotBlank()) {
                        onNameSaved(text.trim())
                    }
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF007AFF))
            ) {
                Text("Start Mapping", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }
    }

    @SuppressLint("MissingPermission")
    @Composable
    fun SnapchatTrashMap(cans: List<TrashCan>, selectedCanId: String?, onCanClicked: (String) -> Unit) {
        val mapView = rememberMapViewWithLifecycle()
        val context = LocalContext.current

        // 🚀 DYNAMIC INJECTION: Swaps textures on the map layer depending on status flag
        LaunchedEffect(cans, selectedCanId) {
            mapView.getMapAsync { map ->
                map.clear()
                val iconEmpty = getScaledPngIcon(context, R.drawable.ic_emptycan, 100, 100)
                val iconFull = getScaledPngIcon(context, R.drawable.ic_fullcan, 100, 100)
                val iconEmptyFocused = getScaledPngIcon(context, R.drawable.ic_emptycan, 140, 140)
                val iconFullFocused = getScaledPngIcon(context, R.drawable.ic_fullcan, 140, 140)

                cans.forEach { can ->
                    val isSelected = can.id == selectedCanId
                    val icon = if (isSelected) {
                        if (can.status == "Full") iconFullFocused else iconEmptyFocused
                    } else {
                        if (can.status == "Full") iconFull else iconEmpty
                    }
                    
                    map.addMarker(
                        MarkerOptions()
                            .position(LatLng(can.latitude, can.longitude))
                            .icon(icon)
                            .snippet(can.id)
                    )
                }
            }
        }

        AndroidView(
            factory = { mapView.apply {
                getMapAsync { maplibreMap ->
                    maplibreMap.setStyle("asset://minimalist_style.json") { style ->
                        // Disable default UI elements
                        maplibreMap.uiSettings.isCompassEnabled = false
                        maplibreMap.uiSettings.isLogoEnabled = false
                        maplibreMap.uiSettings.isAttributionEnabled = false // Hide attribution for ultra-minimalist look

                        val locationComponent = maplibreMap.locationComponent
                        locationComponent.activateLocationComponent(
                            LocationComponentActivationOptions.builder(context, style).build()
                        )
                        locationComponent.isLocationComponentEnabled = true
                        locationComponent.cameraMode = CameraMode.TRACKING
                        locationComponent.renderMode = RenderMode.COMPASS

                        // Camera bounds tracking local grid context
                        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                            val targetLocation = if (location != null) LatLng(location.latitude, location.longitude) else LatLng(12.3186, 75.0855)
                            val position = CameraPosition.Builder()
                                .target(targetLocation)
                                .zoom(13.5)
                                .build()
                            maplibreMap.animateCamera(CameraUpdateFactory.newCameraPosition(position))
                        }

                        maplibreMap.setOnMarkerClickListener { marker ->
                            marker.snippet?.let { canId -> onCanClicked(canId) }
                            maplibreMap.animateCamera(CameraUpdateFactory.newLatLng(marker.position), 300)
                            true
                        }
                    }
                }
            } },
            update = { },
            modifier = Modifier.fillMaxSize()
        )
    }

    @SuppressLint("MissingPermission")
    @Composable
    fun CameraScannerOverlay(
        fusedLocationClient: FusedLocationProviderClient,
        onCloseScanner: () -> Unit,
        onTrashCanLogged: (Double, Double, String) -> Unit
    ) {
        var statusText by remember { mutableStateOf("Scan the Trash Can") }
        var imageCapture: ImageCapture? by remember { mutableStateOf(null) }
        var capturedBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
        var sliderValue by remember { mutableFloatStateOf(2f) }

        if (capturedBitmap == null) {
            Box(modifier = Modifier.fillMaxSize()) {
                CameraPreviewWindow(onImageCaptureReady = { capture -> imageCapture = capture })

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .background(Color.Black.copy(alpha = 0.7f))
                        .padding(16.dp)
                ) {
                    Text(text = statusText, color = Color.White, fontSize = 16.sp)
                }

                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(32.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Button(
                        onClick = { onCloseScanner() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)
                    ) {
                        Text("Cancel")
                    }

                    Button(
                        onClick = {
                            val capture = imageCapture ?: return@Button
                            statusText = "Capturing..."

                            capture.takePicture(
                                cameraExecutor,
                                object : ImageCapture.OnImageCapturedCallback() {
                                    override fun onCaptureSuccess(imageProxy: ImageProxy) {
                                        val buffer = imageProxy.planes[0].buffer
                                        val bytes = ByteArray(buffer.remaining())
                                        buffer.get(bytes)
                                        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                        imageProxy.close()
                                        
                                        // Update state on main thread
                                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                                            capturedBitmap = bitmap
                                        }
                                    }
                                    override fun onError(exception: ImageCaptureException) {
                                        Log.e("Trashpanzee", "Capture failed", exception)
                                    }
                                }
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF007AFF))
                    ) {
                        Text("Capture")
                    }
                }
            }
        } else {
            // Review UI
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF1E1E1E))
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(modifier = Modifier.height(64.dp))
                    
                    // Clicked photo preview
                    androidx.compose.foundation.Image(
                        bitmap = capturedBitmap!!.asImageBitmap(),
                        contentDescription = "Captured Trash Can",
                        modifier = Modifier
                            .fillMaxWidth(0.8f)
                            .aspectRatio(3f/4f)
                            .clip(RoundedCornerShape(24.dp)),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                    )
                    
                    Spacer(modifier = Modifier.height(48.dp))
                    
                    // Slider
                    androidx.compose.material3.Slider(
                        value = sliderValue,
                        onValueChange = { sliderValue = it },
                        valueRange = 0f..4f,
                        steps = 3,
                        modifier = Modifier.fillMaxWidth(0.8f),
                        colors = androidx.compose.material3.SliderDefaults.colors(
                            thumbColor = Color(0xFFFFC107),
                            activeTrackColor = Color.White,
                            inactiveTrackColor = Color.DarkGray
                        )
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(0.8f),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Empty", color = Color.LightGray, fontSize = 12.sp)
                        Text("Half-filled", color = Color.LightGray, fontSize = 12.sp)
                        Text("Full", color = Color.LightGray, fontSize = 12.sp)
                    }
                    
                    Spacer(modifier = Modifier.weight(1f))
                    
                    // Bottom Buttons
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 64.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Back/Cancel
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .background(Color.Black, CircleShape)
                                .clickable { capturedBitmap = null },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Retake", tint = Color.White)
                        }
                        
                        // Retake
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .background(Color.Black, CircleShape)
                                .clickable { capturedBitmap = null },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Retake", tint = Color.White, modifier = Modifier.size(32.dp))
                        }
                        
                        // Submit
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .background(Color(0xFF4CAF50), CircleShape)
                                .clickable {
                                    val statusString = when(sliderValue.toInt()) {
                                        0 -> "Empty"
                                        1 -> "Low"
                                        2 -> "Half-filled"
                                        3 -> "High"
                                        else -> "Full"
                                    }
                                    
                                    fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                                        .addOnSuccessListener { location ->
                                            if (location != null) {
                                                onTrashCanLogged(location.latitude, location.longitude, statusString)
                                            }
                                        }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Check, contentDescription = "Submit", tint = Color.White)
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}

// ==========================================
// UTILITIES & DATA ARCHITECTURE
// ==========================================

fun getScaledPngIcon(context: Context, resourceId: Int, width: Int, height: Int): Icon {
    val originalBitmap = BitmapFactory.decodeResource(context.resources, resourceId)
    val scaledBitmap = Bitmap.createScaledBitmap(originalBitmap, width, height, false)

    // Add subtle shadow: extra padding for shadow blur
    val padding = 20
    val outBitmap = Bitmap.createBitmap(width + padding * 2, height + padding * 2, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(outBitmap)

    // Draw manual drop shadow using the alpha channel of the bitmap
    val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.argb(80, 0, 0, 0) // Soft semi-transparent black shadow
        maskFilter = android.graphics.BlurMaskFilter(8f, android.graphics.BlurMaskFilter.Blur.NORMAL)
    }
    val alphaBitmap = scaledBitmap.extractAlpha()
    
    // Draw the blurred shadow with a slight offset (dx = 0, dy = 6)
    canvas.drawBitmap(alphaBitmap, padding.toFloat(), padding.toFloat() + 6f, shadowPaint)
    
    // Clean up temporary alpha bitmap
    alphaBitmap.recycle()

    // Draw the original scaled bitmap on top
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    canvas.drawBitmap(scaledBitmap, padding.toFloat(), padding.toFloat(), paint)

    return IconFactory.getInstance(context).fromBitmap(outBitmap)
}

fun createDotIcon(context: Context, color: Int, sizePx: Int): Icon {
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint().apply {
        this.color = color
        isAntiAlias = true
    }
    canvas.drawCircle(sizePx / 2f, sizePx / 2f, sizePx / 2f, paint)
    return IconFactory.getInstance(context).fromBitmap(bitmap)
}

fun createCalloutIcon(context: Context, status: String): Icon {
    val width = 280
    val height = 280
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    
    val centerX = width / 2f
    val anchorY = height / 2f // 140
    
    // Card bounds
    val cardLeft = 30f
    val cardRight = 250f
    val cardTop = 30f
    val cardBottom = 110f
    val cornerRadius = 16f
    
    // 1. Path for card + tail
    val bubblePath = Path()
    val rectF = android.graphics.RectF(cardLeft, cardTop, cardRight, cardBottom)
    bubblePath.addRoundRect(rectF, cornerRadius, cornerRadius, Path.Direction.CW)
    
    // Tail pointing down to the anchor point
    bubblePath.moveTo(centerX - 16f, cardBottom)
    bubblePath.lineTo(centerX, anchorY)
    bubblePath.lineTo(centerX + 16f, cardBottom)
    bubblePath.close()
    
    // 2. Draw soft drop shadow under the bubble path
    val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.argb(55, 0, 0, 0)
        maskFilter = android.graphics.BlurMaskFilter(12f, android.graphics.BlurMaskFilter.Blur.NORMAL)
    }
    canvas.drawPath(bubblePath, shadowPaint)
    
    // 3. Fill the bubble with white
    val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        style = Paint.Style.FILL
    }
    canvas.drawPath(bubblePath, fillPaint)
    
    // 4. Draw border stroke matching status color
    val statusColor = if (status == "Full") {
        android.graphics.Color.parseColor("#E91E63") // Pink/Red
    } else {
        android.graphics.Color.parseColor("#4CAF50") // Green
    }
    
    val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = statusColor
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    canvas.drawPath(bubblePath, strokePaint)
    
    // 5. Draw the colored trash can icon (original colors)
    val iconRes = if (status == "Full") R.drawable.ic_fullcan else R.drawable.ic_emptycan
    val iconBitmap = BitmapFactory.decodeResource(context.resources, iconRes)
    if (iconBitmap != null) {
        val iconSize = 48
        val left = (cardLeft + 16).toInt()
        val top = (cardTop + (cardBottom - cardTop - iconSize) / 2).toInt()
        val destRect = Rect(left, top, left + iconSize, top + iconSize)
        
        canvas.drawBitmap(iconBitmap, null, destRect, Paint(Paint.ANTI_ALIAS_FLAG))
    }
    
    // 6. Draw Text Label
    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#2B2B2B")
        textSize = 22f
        typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
    }
    
    val subTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = statusColor
        textSize = 16f
        typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
    }
    
    val textStartX = cardLeft + 16f + 48f + 12f
    val textY1 = cardTop + 36f
    val textY2 = cardTop + 62f
    
    canvas.drawText(if (status == "Full") "Full Can" else "Empty Can", textStartX, textY1, textPaint)
    canvas.drawText(if (status == "Full") "Reported" else "Available", textStartX, textY2, subTextPaint)
    
    return IconFactory.getInstance(context).fromBitmap(bitmap)
}

data class TrashCan(
    val id: String,
    val latitude: Double,
    val longitude: Double,
    var status: String,
    val contributors: List<String>,
    val upvotes: Int = 0,
    val downvotes: Int = 0,
    val upvotedUsers: List<String>? = emptyList(),
    val downvotedUsers: List<String>? = emptyList()
)

class PinStorageManager(context: Context) {
    // 🚀 Version bumped to v6 to include upvote/downvote fields and rebuild simulation pins
    private val prefs: SharedPreferences = context.getSharedPreferences("TrashpanzeeCans_v7", Context.MODE_PRIVATE)
    private val gson = Gson()

    var userName: String?
        get() = prefs.getString("user_profile_name", null)
        set(value) = prefs.edit().putString("user_profile_name", value).apply()

    fun saveCans(cans: List<TrashCan>) {
        val jsonString = gson.toJson(cans)
        prefs.edit().putString("saved_cans", jsonString).apply()
    }

    fun loadCans(): List<TrashCan> {
        val jsonString = prefs.getString("saved_cans", null) ?: return emptyList()
        val type = object : TypeToken<List<TrashCan>>() {}.type
        return try {
            gson.fromJson(jsonString, type)
        } catch (e: Exception) {
            emptyList()
        }
    }
}

// ==========================================
// LIFECYCLE MANAGERS
// ==========================================

@Composable
fun rememberMapViewWithLifecycle(): MapView {
    val context = LocalContext.current
    val mapView = remember { MapView(context) }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    return mapView
}

@Composable
fun CameraPreviewWindow(onImageCaptureReady: (ImageCapture) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context) }

    LaunchedEffect(Unit) {
        val cameraProviderProvider = ProcessCameraProvider.getInstance(context)
        cameraProviderProvider.addListener({
            val cameraProvider = cameraProviderProvider.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
            val imageCapture = ImageCapture.Builder().build()
            onImageCaptureReady(imageCapture)

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageCapture
                )
            } catch (e: Exception) {
                Log.e("Trashpanzee", "Binding failed", e)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    AndroidView(
        factory = { previewView },
        modifier = Modifier.fillMaxSize()
    )
}