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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
                        contributors = mockContributors
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
                var currentScreen by remember { mutableStateOf("MAP") }
                var isScannerOpen by remember { mutableStateOf(false) }
                var selectedCan by remember { mutableStateOf<TrashCan?>(null) }
                val sheetState = rememberModalBottomSheetState()

                val myActiveUserName = currentUserName!!

                if (currentScreen == "PROFILE") {
                    UserProfileScreen(
                        userName = myActiveUserName,
                        cansList = trashCans,
                        onBackClicked = { currentScreen = "MAP" }
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize()) {
                        if (isScannerOpen) {
                            CameraScannerOverlay(
                                fusedLocationClient = fusedLocationClient,
                                onCloseScanner = { isScannerOpen = false },
                                onTrashCanLogged = { lat, lng ->
                                    val existingCanIndex = trashCans.indexOfFirst { existing ->
                                        val results = FloatArray(1)
                                        Location.distanceBetween(lat, lng, existing.latitude, existing.longitude, results)
                                        results[0] < 20f
                                    }

                                    if (existingCanIndex != -1) {
                                        val existingCan = trashCans[existingCanIndex]
                                        if (!existingCan.contributors.contains(myActiveUserName)) {
                                            val updatedContributors = existingCan.contributors.toMutableList()
                                            updatedContributors.add(myActiveUserName)
                                            trashCans[existingCanIndex] = existingCan.copy(contributors = updatedContributors)
                                            Toast.makeText(this@MainActivity, "You verified an existing can!", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(this@MainActivity, "You already mapped this can!", Toast.LENGTH_SHORT).show()
                                        }
                                    } else {
                                        val newCan = TrashCan(
                                            id = UUID.randomUUID().toString(),
                                            latitude = lat,
                                            longitude = lng,
                                            status = "Empty",
                                            contributors = listOf(myActiveUserName)
                                        )
                                        trashCans.add(newCan)
                                    }

                                    pinStorage.saveCans(trashCans)
                                    isScannerOpen = false
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

                            // 🚀 Top Navigation Overlays
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
                                        .background(Color.White, CircleShape)
                                ) {
                                    Icon(Icons.Default.Person, contentDescription = "Profile", tint = Color.Black)
                                }

                                IconButton(
                                    onClick = { /* Search Overlay */ },
                                    modifier = Modifier
                                        .size(48.dp)
                                        .background(Color.White, CircleShape)
                                ) {
                                    Icon(Icons.Default.Search, contentDescription = "Search", tint = Color.Black)
                                }
                            }

                            // 🚀 Bottom Navigation Bar Surface (Matches Layout Blueprint)
                            Column(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth()
                            ) {
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(100.dp),
                                    color = Color(0xFFF8F8F8)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(horizontal = 32.dp, vertical = 16.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Explore,
                                            contentDescription = "Explore",
                                            tint = Color.Black,
                                            modifier = Modifier.size(28.dp)
                                        )

                                        Box(
                                            modifier = Modifier
                                                .size(44.dp)
                                                .background(Color.White, RoundedCornerShape(12.dp)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.LocationOn,
                                                contentDescription = "Saved Locations",
                                                tint = Color.LightGray,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }

                                        Button(
                                            onClick = { isScannerOpen = true },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2B2B2B)),
                                            shape = RoundedCornerShape(24.dp),
                                            modifier = Modifier.height(48.dp).width(120.dp)
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    Icons.Default.CameraAlt,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Spacer(Modifier.width(8.dp))
                                                Text("trash", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        if (selectedCan != null) {
                            ModalBottomSheet(
                                onDismissRequest = { selectedCan = null },
                                sheetState = sheetState
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text("Public Trash Can", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.height(8.dp))

                                    val contributorString = selectedCan!!.contributors.joinToString(", ")
                                    Text("Mapped by: $contributorString", fontSize = 14.sp, color = Color.Gray, fontWeight = FontWeight.Medium)
                                    Spacer(modifier = Modifier.height(16.dp))

                                    Text("Status: ${selectedCan!!.status}", fontSize = 18.sp, color = if (selectedCan!!.status == "Full") Color(0xFFE91E63) else Color(0xFF4CAF50), fontWeight = FontWeight.SemiBold)

                                    Spacer(modifier = Modifier.height(24.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceEvenly
                                    ) {
                                        Button(
                                            onClick = {
                                                updateCanStatus(selectedCan!!.id, "Empty")
                                                selectedCan = null
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                                        ) {
                                            Text("Mark Empty")
                                        }

                                        Button(
                                            onClick = {
                                                updateCanStatus(selectedCan!!.id, "Full")
                                                selectedCan = null
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE91E63))
                                        ) {
                                            Text("Report Full")
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(16.dp))
                                    HorizontalDivider()
                                    Spacer(modifier = Modifier.height(16.dp))

                                    if (selectedCan!!.contributors.contains(myActiveUserName)) {
                                        Button(
                                            onClick = {
                                                val updatedContributors = selectedCan!!.contributors.toMutableList()
                                                updatedContributors.remove(myActiveUserName)

                                                if (updatedContributors.isEmpty()) {
                                                    trashCans.removeAll { it.id == selectedCan!!.id }
                                                } else {
                                                    val index = trashCans.indexOfFirst { it.id == selectedCan!!.id }
                                                    if (index != -1) {
                                                        trashCans[index] = selectedCan!!.copy(contributors = updatedContributors)
                                                    }
                                                }

                                                pinStorage.saveCans(trashCans)
                                                selectedCan = null
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)
                                        ) {
                                            Text("Retract My Data", color = Color.White)
                                        }
                                    } else {
                                        Text("You haven't scanned this can yet.", fontSize = 12.sp, color = Color.LightGray)
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

    @Composable
    fun UserProfileScreen(userName: String, cansList: List<TrashCan>, onBackClicked: () -> Unit) {
        val myTotalContributions = cansList.count { it.contributors.contains(userName) }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF5F5F5))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                TextButton(onClick = onBackClicked) {
                    Text("← Back to Map", fontSize = 16.sp, color = Color(0xFF007AFF))
                }
            }

            Spacer(modifier = Modifier.height(48.dp))

            Box(
                modifier = Modifier
                    .size(100.dp)
                    .background(Color(0xFF007AFF), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = userName.firstOrNull()?.uppercase() ?: "?",
                    fontSize = 48.sp,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text(userName, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(modifier = Modifier.height(8.dp))
            Text("Citizen Mapper", fontSize = 16.sp, color = Color.Gray)

            Spacer(modifier = Modifier.height(48.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Total Contributions", fontSize = 14.sp, color = Color.Gray, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(myTotalContributions.toString(), fontSize = 48.sp, fontWeight = FontWeight.Black, color = Color(0xFF4CAF50))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Trash Cans Verified", fontSize = 14.sp, color = Color.Gray)
                }
            }
        }
    }

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

                cans.forEach { can ->
                    val isSelected = can.id == selectedCanId
                    val icon = if (isSelected) {
                        createCalloutIcon(context, can.status)
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
        onTrashCanLogged: (Double, Double) -> Unit
    ) {
        var statusText by remember { mutableStateOf("Scan the Trash Can") }
        var imageCapture: ImageCapture? by remember { mutableStateOf(null) }

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
                        statusText = "Syncing with Census..."

                        capture.takePicture(
                            cameraExecutor,
                            object : ImageCapture.OnImageCapturedCallback() {
                                override fun onCaptureSuccess(imageProxy: ImageProxy) {
                                    imageProxy.close()
                                    fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                                        .addOnSuccessListener { location ->
                                            if (location != null) {
                                                onTrashCanLogged(location.latitude, location.longitude)
                                            } else {
                                                statusText = "GPS warming up. Step outside!"
                                            }
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
                    Text("Map it!")
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
    val contributors: List<String>
)

class PinStorageManager(context: Context) {
    // 🚀 Version bumped to v5 to clear old structural schemas and rebuild the simulation pins
    private val prefs: SharedPreferences = context.getSharedPreferences("TrashpanzeeCans_v5", Context.MODE_PRIVATE)
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