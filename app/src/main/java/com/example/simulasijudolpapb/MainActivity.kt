package com.example.simulasijudolpapb

import android.Manifest
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.location.LocationServices
import com.example.simulasijudolpapb.utils.LocationManager as AppLocationManager
import java.util.Locale
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.random.Random

// New imports for scrolling
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll

// Firebase imports
import com.google.firebase.ktx.Firebase
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.auth.ktx.auth
import kotlinx.coroutines.launch

sealed class Screen {
    object Home : Screen()
    object Simulator : Screen()
    object Camera : Screen()
    object Location : Screen()
    object Education : Screen()
    object Result : Screen()
}

data class SpinResult(
    val id: Int,
    val bet: Int,
    val win: Int,
    val isWin: Boolean,
    val nearMiss: Boolean,
    val timestamp: Long
)

class SimulatorViewModel : ViewModel() {
    private val initialBalance = 1000
    var balance by mutableStateOf(initialBalance)
        private set

    var houseEdge by mutableStateOf(0.12)
    var baseWinProb by mutableStateOf(0.15)
    private var spinCounter = 0

    var history = mutableStateListOf<SpinResult>()
        private set

    // Store the actual symbols displayed
    var currentSymbols by mutableStateOf(listOf(0, 0, 0))
        private set

    private val firestore = Firebase.firestore

    fun spin(bet: Int = 10) {
        spinCounter++
        val manipulatedEdge = houseEdge + (spinCounter / 50) * 0.01
        val dynamicWinProb = baseWinProb * (1.0 - manipulatedEdge)
        val r = Random.nextDouble()

        // Generate 3 random symbols (0, 1, 2)
        val symbols = List(3) { Random.nextInt(3) }
        currentSymbols = symbols

        // Win ONLY if all 3 symbols are the same
        val isWin = symbols[0] == symbols[1] && symbols[1] == symbols[2]

        // Near miss: two symbols match but not all three
        val nearMiss = !isWin && (
            (symbols[0] == symbols[1]) ||
            (symbols[1] == symbols[2]) ||
            (symbols[0] == symbols[2])
        )

        // Win amount calculation - only if isWin is true
        val winAmount = if (isWin) {
            // Higher multiplier for winning (2x to 5x)
            (bet * (2 + (Random.nextDouble() * 3))).toInt()
        } else {
            0
        }

        balance = balance - bet + winAmount

        val spinResult = SpinResult(
            id = spinCounter,
            bet = bet,
            win = winAmount,
            isWin = isWin,
            nearMiss = nearMiss,
            timestamp = System.currentTimeMillis()
        )
        history.add(spinResult)

        // Save to Firebase Firestore
        try {
            val uid = Firebase.auth.currentUser?.uid ?: "anonymous"
            val doc = hashMapOf<String, Any>(
                "id" to spinResult.id,
                "bet" to spinResult.bet,
                "win" to spinResult.win,
                "isWin" to spinResult.isWin,
                "nearMiss" to spinResult.nearMiss,
                "timestamp" to spinResult.timestamp,
                "deviceId" to uid,
                "symbols" to symbols,
                "balance" to balance
            )
            firestore.collection("spins").add(doc)
                .addOnSuccessListener {
                    // Successfully saved to Firestore
                }
                .addOnFailureListener { e ->
                    // Log error if needed
                }
        } catch (_: Exception) {
            // Handle exception silently
        }
    }

    fun reset() {
        balance = initialBalance
        history.clear()
        spinCounter = 0
    }

    fun totalLoss(): Int {
        val spent = history.sumOf { it.bet }
        val won = history.sumOf { it.win }
        return spent - won
    }
}

class CameraViewModel : ViewModel() {
    var lastPhoto by mutableStateOf<Bitmap?>(null)
        private set

    fun setPhoto(bmp: Bitmap?) {
        lastPhoto = bmp
    }
}

class LocationViewModel : ViewModel() {
    val riskZones = listOf(
        Triple(-6.200000, 106.816666, "Pusat Hiburan Malam A"),
        Triple(-6.21, 106.82, "Game Center B"),
        Triple(-6.19, 106.81, "Area Pusat Kota - Zona Risiko")
    )

    fun isNearRiskZone(lat: Double, lon: Double, thresholdMeters: Double = 200.0): String? {
        for ((zlat, zlon, name) in riskZones) {
            val dLat = (zlat - lat) * 111000.0
            val dLon = (zlon - lon) * 111000.0
            val dist = sqrt(dLat * dLat + dLon * dLon)
            if (dist <= thresholdMeters) return name
        }
        return null
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface {
                    AppRoot()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRoot() {
    var currentScreen by remember { mutableStateOf<Screen>(Screen.Home) }
    val simulatorVM: SimulatorViewModel = viewModel()
    val cameraVM: CameraViewModel = viewModel()
    val locationVM: LocationViewModel = viewModel()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Anti-Judi Simulator") })
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            when (currentScreen) {
                is Screen.Home -> HomeScreen(onNavigate = { currentScreen = it })
                is Screen.Simulator -> SimulatorScreen(simulatorVM,
                    onBack = { currentScreen = Screen.Home },
                    onShowResult = { currentScreen = Screen.Result })
                is Screen.Camera -> CameraScreen(cameraVM, onBack = { currentScreen = Screen.Home })
                is Screen.Location -> LocationScreen(locationVM, onBack = { currentScreen = Screen.Home })
                is Screen.Education -> EducationScreen(onBack = { currentScreen = Screen.Home })
                is Screen.Result -> ResultScreen(simulatorVM, onBack = { currentScreen = Screen.Home }) { text ->
                    val send = Intent().apply {
                        action = Intent.ACTION_SEND
                        putExtra(Intent.EXTRA_TEXT, text)
                        type = "text/plain"
                    }
                    context.startActivity(Intent.createChooser(send, "Share anti-judi"))
                }
            }
        }
    }
}

@Composable
fun HomeScreen(onNavigate: (Screen) -> Unit) {
    val context = LocalContext.current
    val locationVM: LocationViewModel = viewModel()
    val appLoc = remember { AppLocationManager(context) }
    val fused = LocationServices.getFusedLocationProviderClient(context)

    var showDialog by remember { mutableStateOf(false) }
    var dialogTitle by remember { mutableStateOf("") }
    var dialogMessage by remember { mutableStateOf("") }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted: Boolean ->
        if (granted) {
            try {
                fused.lastLocation.addOnSuccessListener { loc ->
                    if (loc != null) {
                        val lat = loc.latitude
                        val lon = loc.longitude
                        val zone = locationVM.isNearRiskZone(lat, lon)
                        appLoc.checkProvinceRisk(lat, lon) { prov: String?, isRisk: Boolean ->
                            val provName = prov ?: "Tidak diketahui"
                            val provStatus = if (isRisk) "ZONA RISIKO" else "AMAN (NON-RISK)"
                            val zoneText = zone?.let { "Nearby fixed zone: $it\n" } ?: "No nearby fixed zone.\n"
                            dialogTitle = "Hasil Pemeriksaan Lokasi"
                            dialogMessage = "Provinsi: $provName\nStatus provinsi: $provStatus\n\n$zoneText Lat: ${"%.5f".format(lat)}, Lon: ${"%.5f".format(lon)}"
                            showDialog = true
                        }
                    } else {
                        Toast.makeText(context, "Lokasi tidak tersedia. Pastikan GPS aktif.", Toast.LENGTH_LONG).show()
                    }
                }.addOnFailureListener {
                    Toast.makeText(context, "Gagal mengambil lokasi: ${it.message}", Toast.LENGTH_LONG).show()
                }
            } catch (e: SecurityException) {
                Toast.makeText(context, "Izin lokasi ditolak.", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(context, "Izin lokasi ditolak.", Toast.LENGTH_SHORT).show()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF3F4F6))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "🚫 ANTI-JUDI SIMULATOR",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFD32F2F),
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )

        Button(
            onClick = { onNavigate(Screen.Simulator) },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
        ) { Text("Mulai Simulasi", color = Color.White) }

        Button(
            onClick = { onNavigate(Screen.Camera) },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3))
        ) { Text("Gunakan Kamera Edukasi", color = Color.White) }

        Button(
            onClick = { onNavigate(Screen.Education) },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFC107))
        ) { Text("Edukasi Bahaya Judi", color = Color.Black) }

        Button(
            onClick = { onNavigate(Screen.Result) }, // CHANGED: Analytics -> Result
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF9C27B0))
        ) { Text("Analisis Kerugian", color = Color.White) }

        Button(
            onClick = {
                permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0288D1))
        ) {
            Text("📍 Check Risk Zone Lokasi Saya", color = Color.White)
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text("Tekan tombol di atas untuk memeriksa zona risiko berdasarkan lokasi Anda.", fontSize = 12.sp)
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(dialogTitle) },
            text = { Text(dialogMessage) },
            confirmButton = {
                TextButton(onClick = { showDialog = false }) { Text("OK") }
            }
        )
    }
}

@Composable
fun SimulatorScreen(vm: SimulatorViewModel, onBack: () -> Unit, onShowResult: () -> Unit) {
    val history = vm.history
    val lossPercent = min(1f, (vm.totalLoss().toFloat() / 1000f))
    val animated = animateFloatAsState(targetValue = lossPercent, label = "")

    var isSpinning by remember { mutableStateOf(false) }
    val slotSymbols = remember { listOf("🍒", "🍋", "🔔") }
    var displaySymbols by remember { mutableStateOf(listOf("🍒", "🍒", "🍒")) }
    val coroutineScope = rememberCoroutineScope()

    // Update display symbols when VM symbols change
    LaunchedEffect(vm.currentSymbols) {
        if (!isSpinning) {
            displaySymbols = vm.currentSymbols.map { slotSymbols[it] }
        }
    }

    // Make the whole screen scrollable so users on small devices can reach the bottom
    Column(modifier = Modifier
        .fillMaxSize()
        .padding(16.dp)
        .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text("Simulator", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            TextButton(onClick = onBack) { Text("Kembali") }
        }
        Text("Balance: ${vm.balance}", fontSize = 16.sp)
        Text("House edge: ${(vm.houseEdge * 100).toInt()}%  • Prob menang (dasar): ${(vm.baseWinProb * 100).toInt()}%")
        LinearProgressIndicator(progress = { animated.value }, modifier = Modifier.fillMaxWidth().height(8.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                if (!isSpinning) {
                    isSpinning = true
                    coroutineScope.launch {
                        // Animation phase
                        repeat(15) {
                            displaySymbols = List(3) { slotSymbols.random() }
                            kotlinx.coroutines.delay(100)
                        }
                        // Actual spin
                        vm.spin(10)
                        // Show final result
                        displaySymbols = vm.currentSymbols.map { slotSymbols[it] }
                        isSpinning = false
                    }
                }
            }) { Text("Spin (Bet 10)") }

            Button(onClick = {
                if (!isSpinning) {
                    isSpinning = true
                    coroutineScope.launch {
                        // Animation phase
                        repeat(15) {
                            displaySymbols = List(3) { slotSymbols.random() }
                            kotlinx.coroutines.delay(100)
                        }
                        // Actual spin
                        vm.spin(50)
                        // Show final result
                        displaySymbols = vm.currentSymbols.map { slotSymbols[it] }
                        isSpinning = false
                    }
                }
            }) { Text("Spin (Bet 50)") }

            Button(onClick = { vm.reset() }) { Text("Reset") }
            Spacer(modifier = Modifier.weight(1f))
            Button(onClick = onShowResult) { Text("Hasil Edukasi") }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(horizontalArrangement = Arrangement.Center) {
                displaySymbols.forEach { symbol ->
                    Text(
                        text = symbol,
                        fontSize = 64.sp,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text("Riwayat terbaru:", fontWeight = FontWeight.Medium)
        if (history.isEmpty()) {
            Text("Belum ada spin. Tekan Spin untuk mulai.")
        } else {
            LazyColumn(modifier = Modifier.fillMaxWidth().height(250.dp)) {
                items(history.asReversed()) { s ->
                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Spin #${s.id} • Bet ${s.bet} • Win ${s.win}")
                                if (s.nearMiss) {
                                    Text("⚠️ Near-miss: 2 simbol sama!", color = Color(0xFFF57C00))
                                }
                                if (s.isWin) {
                                    Text("🎉 MENANG! 3 simbol sama!", color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold)
                                }
                            }
                            Text(if (s.isWin) "+${s.win}" else "-${s.bet}", fontWeight = FontWeight.Bold, color = if (s.isWin) Color(0xFF2E7D32) else Color(0xFFD32F2F))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CameraScreen(vm: CameraViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }

    // Check if device has any camera available
    val packageManager = context.packageManager
    val cameraSupported = remember { packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_CAMERA_ANY) }

    // Launcher that actually takes a small preview bitmap
    val previewLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bmp: Bitmap? ->
        if (bmp != null) {
            bitmap = bmp
            vm.setPhoto(bmp)
        } else {
            Toast.makeText(context, "Foto dibatalkan", Toast.LENGTH_SHORT).show()
        }
    }

    // Runtime permission launcher for CAMERA
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted: Boolean ->
        if (granted) {
            previewLauncher.launch(null)
        } else {
            Toast.makeText(context, "Izin kamera ditolak.", Toast.LENGTH_SHORT).show()
        }
    }

    Column(modifier = Modifier
        .fillMaxSize()
        .padding(16.dp)
        .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text("Kamera Edukasi", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            TextButton(onClick = onBack) { Text("Kembali") }
        }
        Text("Ambil foto barang berharga yang bisa hilang jika kecanduan judi (HP, motor, laptop).")

        Button(onClick = {
            if (!cameraSupported) {
                Toast.makeText(context, "Perangkat tidak memiliki kamera.", Toast.LENGTH_SHORT).show()
                return@Button
            }
            val hasPermission = context.checkSelfPermission(Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (hasPermission) {
                previewLauncher.launch(null)
            } else {
                // Ask for permission, will launch camera on grant
                permissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }) { Text("Ambil Foto") }

        Spacer(modifier = Modifier.height(8.dp))
        if (bitmap != null) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.TopStart) {
                Image(bitmap = bitmap!!.asImageBitmap(), contentDescription = "Foto", modifier = Modifier
                    .fillMaxWidth()
                    .height(320.dp))
                Surface(
                    color = Color(0x99000000),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(12.dp)
                        .clip(RoundedCornerShape(8.dp))
                ) {
                    Text(
                        "Ini salah satu barang yang sering dijual korban judi.",
                        color = Color.White,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }
        } else {
            Text("Belum ada foto.")
        }
    }
}

@Composable
fun LocationScreen(vm: LocationViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    var locationText by remember { mutableStateOf<String?>("Klik 'Ambil Lokasi Dummy' untuk mengecek zona risiko.") }
    var showRisk by remember { mutableStateOf<String?>(null) }
    var provinceName by remember { mutableStateOf<String?>(null) }
    var isProvinceRisk by remember { mutableStateOf<Boolean?>(null) }

    val dummyLocationManager: AppLocationManager = remember { AppLocationManager(context) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text("Zona Risiko (Demo)", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            TextButton(onClick = onBack) { Text("Kembali") }
        }
        
        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0))) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("ℹ️ Mode Demo", fontWeight = FontWeight.Bold, color = Color(0xFFE65100))
                Text("Aplikasi menggunakan lokasi dummy untuk demonstrasi fitur zona risiko.", fontSize = 12.sp)
            }
        }
        
        Text(locationText ?: "")
        
        Button(onClick = {
            val dummyLoc = dummyLocationManager.getDummyLocation()
            locationText = "Lokasi Dummy:\nLat: ${String.format(Locale.getDefault(), "%.4f", dummyLoc.first)}, Lon: ${String.format(Locale.getDefault(), "%.4f", dummyLoc.second)}"

            val zone = vm.isNearRiskZone(dummyLoc.first, dummyLoc.second)
            showRisk = zone

            dummyLocationManager.checkProvinceRisk(dummyLoc.first, dummyLoc.second) { prov: String?, risk: Boolean ->
                provinceName = prov
                isProvinceRisk = risk
            }
        }) {
            Text(" Ambil Lokasi Dummy & Cek Provinsi")
        }
        
        if (showRisk != null) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Waspada: $showRisk", color = Color(0xFFD32F2F), fontWeight = FontWeight.Bold)
                    Text("Tempat ini sering memicu aktivitas judi online. Pertimbangkan menjauh atau menghindari jam tertentu.")
                }
            }
        } else {
            Card(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Aman", color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold)
                    Text("Tidak terdeteksi zona risiko di lokasi ini.")
                }
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))

        when (isProvinceRisk) {
            true -> Card(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE))) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("⚠️ PROVINSI: ${provinceName ?: "Tidak diketahui"}", color = Color(0xFFD32F2F), fontWeight = FontWeight.Bold)
                    Text("Berdasarkan data provinsi tetap aplikasi, provinsi ini DINYATAKAN SEBAGAI ZONA RISIKO.")
                }
            }
            false -> Card(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9))) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("✅ PROVINSI: ${provinceName ?: "Tidak diketahui"}", color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold)
                    Text("Berdasarkan data provinsi tetap aplikasi, provinsi ini DINYATAKAN AMAN (non-risk).")
                }
            }
            null -> { }
        }

        if (provinceName != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = {
                val status = if (isProvinceRisk == true) "ZONA RISIKO" else "AMAN (NON-RISK)"
                val text = "Lokasi saya berada di provinsi: ${provinceName}\nStatus: $status"
                val send = Intent().apply {
                    action = Intent.ACTION_SEND
                    putExtra(Intent.EXTRA_TEXT, text)
                    type = "text/plain"
                }
                context.startActivity(Intent.createChooser(send, "Bagikan status provinsi"))
            }, modifier = Modifier.fillMaxWidth()) {
                Text("📤 Bagikan Status Provinsi")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("📍 Zona Risiko Terdaftar:", fontWeight = FontWeight.Bold)
                vm.riskZones.forEach { (_, _, name) ->
                    Text("• $name", fontSize = 12.sp)
                }
            }
        }
        
        Spacer(modifier = Modifier.weight(1f))
        Text("Catatan: Ini adalah demo edukasi dengan data dummy, tidak menggunakan GPS real.", fontSize = 11.sp, color = Color.Gray)
    }
}

@Composable
fun EducationScreen(onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF3F4F6))
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header with back button
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                "📚 Edukasi Anti-Judi",
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                color = Color(0xFFD32F2F)
            )
            IconButton(onClick = onBack) {
                Text("❌", fontSize = 20.sp)
            }
        }

        // Hero Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE)),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "⚠️ BAHAYA JUDI ONLINE",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 18.sp,
                    color = Color(0xFFD32F2F)
                )
                Text(
                    "Judi online adalah ancaman serius yang dapat merusak masa depan, keuangan, dan kesehatan mental. Kenali bahayanya sebelum terlambat!",
                    fontSize = 14.sp,
                    color = Color(0xFF424242),
                    lineHeight = 20.sp
                )
            }
        }

        // Section 1: Statistik & Fakta
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("📊", fontSize = 24.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Statistik & Fakta",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = Color(0xFF1976D2)
                    )
                }

                Divider(color = Color(0xFFE0E0E0), thickness = 1.dp)

                InfoItem(
                    icon = "🎲",
                    title = "3 Juta+ Warga Indonesia",
                    description = "Terlibat dalam aktivitas judi online setiap tahunnya (data Polri 2023)"
                )

                InfoItem(
                    icon = "💸",
                    title = "Triliunan Rupiah Hilang",
                    description = "Kerugian ekonomi mencapai Rp 10+ triliun per tahun akibat judi online"
                )

                InfoItem(
                    icon = "📱",
                    title = "Akses Mudah = Bahaya Besar",
                    description = "80% kasus dimulai dari smartphone dan aplikasi chat"
                )

                Text(
                    "Sumber: Badan Reserse Kriminal Polri, Kementerian Kominfo RI (2023-2024)",
                    fontSize = 10.sp,
                    color = Color(0xFF757575),
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }

        // Section 2: Mengapa Judi Selalu Merugikan?
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🧠", fontSize = 24.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Mengapa Judi Selalu Merugikan?",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = Color(0xFF1976D2)
                    )
                }

                Divider(color = Color(0xFFE0E0E0), thickness = 1.dp)

                NumberedItem(
                    number = "1",
                    title = "House Edge (Keuntungan Bandar)",
                    description = "Sistem dirancang agar bandar SELALU untung jangka panjang. Rata-rata house edge 5-15%, artinya dari Rp 100.000 taruhan, bandar untung Rp 5.000-15.000."
                )

                NumberedItem(
                    number = "2",
                    title = "Efek Psikologis: Near-Miss",
                    description = "\"Hampir menang\" membuat otak melepas dopamine, menciptakan ilusi bisa menang. Padahal ini manipulasi untuk membuat ketagihan."
                )

                NumberedItem(
                    number = "3",
                    title = "Intermittent Rewards (Hadiah Sesekali)",
                    description = "Kemenangan sesekali membuat pemain terus bermain, meski total kerugian terus bertambah. Sama seperti tikus laboratorium yang dilatih menekan tuas."
                )

                NumberedItem(
                    number = "4",
                    title = "Sunk Cost Fallacy",
                    description = "\"Sudah rugi banyak, harus balik modal!\" Pemikiran ini justru membuat kerugian semakin besar. Keputusan yang salah."
                )

                Text(
                    "Sumber: Journal of Gambling Studies, American Psychiatric Association - DSM-5 (Diagnostic Manual for Gambling Disorder)",
                    fontSize = 10.sp,
                    color = Color(0xFF757575),
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }

        // Section 3: Dampak Nyata Kecanduan Judi
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0)),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("💔", fontSize = 24.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Dampak Nyata Kecanduan Judi",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = Color(0xFFE65100)
                    )
                }

                Divider(color = Color(0xFFFFB74D), thickness = 1.dp)

                DamageItem(
                    emoji = "💰",
                    category = "Finansial",
                    effects = listOf(
                        "Hutang menumpuk dari bank, pinjol, keluarga",
                        "Menjual aset berharga (motor, mobil, rumah)",
                        "Kehilangan tabungan & investasi",
                        "Bangkrut dan sulit bangkit"
                    )
                )

                DamageItem(
                    emoji = "🧠",
                    category = "Mental & Kesehatan",
                    effects = listOf(
                        "Depresi, kecemasan, insomnia",
                        "Pikiran bunuh diri (40% penderita)",
                        "Penyalahgunaan alkohol & narkoba",
                        "Gangguan makan & tekanan darah"
                    )
                )

                DamageItem(
                    emoji = "👨‍👩‍👧",
                    category = "Keluarga & Sosial",
                    effects = listOf(
                        "Perceraian dan keretakan rumah tangga",
                        "Anak-anak terabaikan dan trauma",
                        "Kehilangan kepercayaan teman & keluarga",
                        "Dikucilkan dari lingkungan sosial"
                    )
                )

                DamageItem(
                    emoji = "⚖️",
                    category = "Hukum",
                    effects = listOf(
                        "Pidana 10 tahun penjara (UU ITE)",
                        "Denda hingga Rp 10 miliar",
                        "Catatan kriminal seumur hidup",
                        "Sulit mendapat pekerjaan"
                    )
                )

                Text(
                    "Sumber: Kemenkes RI, National Council on Problem Gambling (NCPG), UU No. 19 Tahun 2016 tentang ITE",
                    fontSize = 10.sp,
                    color = Color(0xFF757575),
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }

        // Section 4: Tanda-Tanda Kecanduan
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🚨", fontSize = 24.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Tanda-Tanda Kecanduan Judi",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = Color(0xFFD32F2F)
                    )
                }

                Divider(color = Color(0xFFE0E0E0), thickness = 1.dp)

                ChecklistItem("Terus memikirkan judi (obsesi)")
                ChecklistItem("Butuh taruhan lebih besar untuk puas")
                ChecklistItem("Gagal berhenti meski sudah coba berkali-kali")
                ChecklistItem("Gelisah atau marah saat tidak bisa berjudi")
                ChecklistItem("Berjudi untuk lari dari masalah/stres")
                ChecklistItem("\"Kejar kekalahan\" - terus main untuk balik modal")
                ChecklistItem("Berbohong tentang aktivitas judi")
                ChecklistItem("Merusak hubungan karena judi")
                ChecklistItem("Bergantung pada orang lain untuk uang")

                Text(
                    "⚠️ Jika Anda atau orang terdekat mengalami 4+ tanda di atas, segera cari bantuan profesional!",
                    fontSize = 12.sp,
                    color = Color(0xFFD32F2F),
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFFFEBEE), RoundedCornerShape(8.dp))
                        .padding(12.dp)
                )

                Text(
                    "Sumber: DSM-5 Gambling Disorder Criteria (American Psychiatric Association)",
                    fontSize = 10.sp,
                    color = Color(0xFF757575),
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                )
            }
        }

        // Section 5: Cara Berhenti & Pulih
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9)),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("✅", fontSize = 24.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Langkah-Langkah Berhenti & Pulih",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = Color(0xFF2E7D32)
                    )
                }

                Divider(color = Color(0xFF81C784), thickness = 1.dp)

                RecoveryStep(
                    step = "1",
                    title = "Akui Masalahnya",
                    description = "Langkah pertama adalah mengakui bahwa Anda memiliki masalah. Tidak ada yang salah dengan meminta bantuan."
                )

                RecoveryStep(
                    step = "2",
                    title = "Cari Dukungan Profesional",
                    description = "Hubungi psikolog, konselor kecanduan, atau join support group seperti Gamblers Anonymous."
                )

                RecoveryStep(
                    step = "3",
                    title = "Blokir Akses Judi",
                    description = "Hapus semua aplikasi judi, blokir website, minta keluarga kelola keuangan Anda sementara."
                )

                RecoveryStep(
                    step = "4",
                    title = "Hindari Pemicu (Triggers)",
                    description = "Identifikasi situasi yang memicu keinginan berjudi (stres, bosan, tempat tertentu) dan hindari."
                )

                RecoveryStep(
                    step = "5",
                    title = "Temukan Aktivitas Pengganti",
                    description = "Olahraga, hobi baru, volunteer, quality time bersama keluarga - isi waktu dengan hal positif."
                )

                RecoveryStep(
                    step = "6",
                    title = "Kelola Keuangan dengan Bijak",
                    description = "Buat budget ketat, bayar hutang bertahap, hindari pinjaman baru, mulai menabung kembali."
                )

                Text(
                    "Sumber: National Council on Problem Gambling (NCPG), Kementerian Kesehatan RI - Direktorat Kesehatan Jiwa",
                    fontSize = 10.sp,
                    color = Color(0xFF757575),
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }

        // Section 6: Hotline & Bantuan
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFE3F2FD)),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(" ", fontSize = 24.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Hotline Bantuan Kecanduan",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = Color(0xFF1565C0)
                    )
                }

                Divider(color = Color(0xFF90CAF9), thickness = 1.dp)

                HotlineItem(
                    title = "Sehat Jiwa - Kemenkes RI",
                    contact = "☎️ 119 ext. 8",
                    description = "Layanan konseling kesehatan jiwa 24/7 gratis"
                )

                HotlineItem(
                    title = "Polri - Laporan Judi Online",
                    contact = "☎️ 110 / patrolisiber@polri.go.id",
                    description = "Laporkan situs/aplikasi judi online ilegal"
                )

                HotlineItem(
                    title = "Kementerian Kominfo",
                    contact = "📧 aduankonten@mail.kominfo.go.id",
                    description = "Aduan konten negatif & situs judi"
                )

                HotlineItem(
                    title = "Into The Light Indonesia",
                    contact = "💬 WA: 08131-12000-68 / 08131-12000-93",
                    description = "Peer support untuk kesehatan mental"
                )

                Text(
                    "INGAT: Anda tidak sendirian! Ribuan orang berhasil pulih dari kecanduan judi. Jangan menyerah!",
                    fontSize = 13.sp,
                    color = Color(0xFF1565C0),
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White, RoundedCornerShape(8.dp))
                        .padding(12.dp)
                )
            }
        }

        // Section 7: Sumber Referensi
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5)),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "📚 Sumber & Referensi Ilmiah",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = Color(0xFF424242)
                )

                Text(
                    "1. Badan Reserse Kriminal Polri - Data Kejahatan Siber 2023\n" +
                    "2. Kementerian Komunikasi dan Informatika RI\n" +
                    "3. Kementerian Kesehatan RI - Direktorat Kesehatan Jiwa\n" +
                    "4. UU No. 19 Tahun 2016 tentang Informasi dan Transaksi Elektronik\n" +
                    "5. American Psychiatric Association - DSM-5 (Gambling Disorder)\n" +
                    "6. National Council on Problem Gambling (NCPG) - USA\n" +
                    "7. Journal of Gambling Studies - Research Articles\n" +
                    "8. World Health Organization (WHO) - ICD-11 Gaming Disorder",
                    fontSize = 11.sp,
                    color = Color(0xFF616161),
                    lineHeight = 18.sp
                )
            }
        }

        // Back Button at Bottom
        Button(
            onClick = onBack,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF9C27B0)),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
        ) {
            Text("Kembali ke Menu Utama", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

// Helper Composables for Education Screen

@Composable
fun InfoItem(icon: String, title: String, description: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(icon, fontSize = 32.sp, modifier = Modifier.padding(top = 4.dp))
        Column {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF212121))
            Text(description, fontSize = 12.sp, color = Color(0xFF616161), lineHeight = 18.sp)
        }
    }
}

@Composable
fun NumberedItem(number: String, title: String, description: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(Color(0xFF1976D2), RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(number, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF212121))
            Text(description, fontSize = 12.sp, color = Color(0xFF616161), lineHeight = 18.sp)
        }
    }
}

@Composable
fun DamageItem(emoji: String, category: String, effects: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(emoji, fontSize = 24.sp)
            Spacer(modifier = Modifier.width(8.dp))
            Text(category, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFFE65100))
        }
        effects.forEach { effect ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("•", fontSize = 12.sp, color = Color(0xFF757575))
                Text(effect, fontSize = 12.sp, color = Color(0xFF424242), lineHeight = 18.sp)
            }
        }
    }
}

@Composable
fun ChecklistItem(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text("☑️", fontSize = 16.sp, modifier = Modifier.padding(top = 2.dp))
        Text(text, fontSize = 13.sp, color = Color(0xFF424242), lineHeight = 20.sp)
    }
}

@Composable
fun RecoveryStep(step: String, title: String, description: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(Color(0xFF2E7D32), RoundedCornerShape(18.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(step, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF1B5E20))
            Text(description, fontSize = 12.sp, color = Color(0xFF424242), lineHeight = 18.sp)
        }
    }
}

@Composable
fun HotlineItem(title: String, contact: String, description: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(8.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(title, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF1565C0))
        Text(contact, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFFD32F2F))
        Text(description, fontSize = 11.sp, color = Color(0xFF616161))
    }
}

@Composable
fun ResultScreen(vm: SimulatorViewModel, onBack: () -> Unit, onShare: (String) -> Unit) {
    val history = vm.history
    val points = remember(history) {
        var cum = 0
        history.map {
            cum += (it.bet - it.win)
            cum
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF3F4F6))
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                "Analisis Kerugian",
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = Color(0xFFD32F2F)
            )
            TextButton(onClick = onBack) {
                Text("Kembali", color = Color(0xFF9C27B0))
            }
        }

        // Summary Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE)),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "RINGKASAN SIMULASI",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 16.sp,
                    color = Color(0xFFD32F2F)
                )

                Divider(color = Color(0xFFEF5350), thickness = 1.dp, modifier = Modifier.padding(vertical = 8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Total Kerugian:", fontSize = 14.sp, color = Color(0xFF424242))
                    Text(
                        "Rp ${vm.totalLoss()}",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFD32F2F)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Total Spin:", fontSize = 14.sp, color = Color(0xFF424242))
                    Text(
                        "${vm.history.size}",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF424242)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Saldo Akhir:", fontSize = 14.sp, color = Color(0xFF424242))
                    Text(
                        "Rp ${vm.balance}",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (vm.balance < 1000) Color(0xFFD32F2F) else Color(0xFF2E7D32)
                    )
                }

                val winCount = vm.history.count { it.isWin }
                val nearMissCount = vm.history.count { it.nearMiss }

                if (vm.history.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Menang:", fontSize = 12.sp, color = Color(0xFF616161))
                        Text("$winCount kali", fontSize = 12.sp, color = Color(0xFF2E7D32))
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Near-miss:", fontSize = 12.sp, color = Color(0xFF616161))
                        Text("$nearMissCount kali", fontSize = 12.sp, color = Color(0xFFF57C00))
                    }
                }
            }
        }

        // Graph Card
        if (points.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Grafik Kerugian",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = Color(0xFF1976D2)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Graph
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .background(Color(0xFFF5F5F5), RoundedCornerShape(8.dp))
                            .padding(16.dp)
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val w = size.width
                            val h = size.height

                            // Calculate bounds with safety checks
                            val maxVal = points.maxOrNull()?.toFloat() ?: 1f
                            val minVal = points.minOrNull()?.toFloat() ?: 0f
                            val range = maxVal - minVal

                            // Draw grid lines
                            for (i in 0..4) {
                                val y = (h / 4f) * i
                                drawLine(
                                    color = Color(0xFFE0E0E0),
                                    start = Offset(0f, y),
                                    end = Offset(w, y),
                                    strokeWidth = 1f
                                )
                            }

                            // Draw line graph
                            if (points.size > 1) {
                                val stepX = w / maxOf(1f, (points.size - 1).toFloat())
                                var prevX = 0f
                                var prevY = if (range == 0f) h / 2f else (h - ((points[0] - minVal) / maxOf(1f, range)) * h)

                                // Clamp prevY to valid range
                                prevY = prevY.coerceIn(0f, h)

                                for (i in 1 until points.size) {
                                    val x = i * stepX
                                    var y = if (range == 0f) h / 2f else (h - ((points[i] - minVal) / maxOf(1f, range)) * h)

                                    // Clamp y to valid range
                                    y = y.coerceIn(0f, h)

                                    drawLine(
                                        color = Color(0xFFEF5350),
                                        start = Offset(prevX, prevY),
                                        end = Offset(x, y),
                                        strokeWidth = 3f
                                    )

                                    prevX = x
                                    prevY = y
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Legend
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .background(Color(0xFFEF5350), RoundedCornerShape(2.dp))
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Kerugian Kumulatif", fontSize = 11.sp, color = Color(0xFF616161))
                        }
                        Text(
                            "Total: Rp ${vm.totalLoss()}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFD32F2F)
                        )
                    }
                }
            }
        } else {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0))
            ) {
                Text(
                    "Belum ada data. Lakukan beberapa spin untuk melihat grafik kerugian.",
                    modifier = Modifier.padding(20.dp),
                    fontSize = 14.sp,
                    color = Color(0xFFE65100)
                )
            }
        }

        // Analysis Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "💡 Analisis Pola Judi",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = Color(0xFF1976D2)
                )

                Divider(color = Color(0xFFE0E0E0), thickness = 1.dp)

                AnalysisPoint(
                    title = "House Edge (Keuntungan Bandar)",
                    description = "Simulasi ini menunjukkan bagaimana house edge ${(vm.houseEdge * 100).toInt()}% membuat kerugian jangka panjang. Bandar SELALU untung!"
                )

                if (vm.history.count { it.nearMiss } > 0) {
                    AnalysisPoint(
                        title = "Near-Miss (Hampir Menang)",
                        description = "Kamu mengalami ${vm.history.count { it.nearMiss }} near-miss. Ini membuat otak merasa 'hampir menang' dan mendorong untuk terus bermain. Padahal ini manipulasi psikologis!"
                    )
                }

                if (vm.history.count { it.isWin } > 0) {
                    AnalysisPoint(
                        title = "Intermittent Rewards",
                        description = "Kemenangan sesekali (${vm.history.count { it.isWin }} kali) tidak mengubah fakta: total kerugian Rp ${vm.totalLoss()}. Sistem dirancang menguntungkan bandar!"
                    )
                }
            }
        }

        // Warning Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE)),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("⚠️", fontSize = 32.sp)
                Column {
                    Text(
                        "INGAT!",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = Color(0xFFD32F2F)
                    )
                    Text(
                        "Judi PASTI membuat rugi dalam jangka panjang! Tidak ada strategi untuk menang melawan house edge. Jangan tertipu!",
                        fontSize = 12.sp,
                        color = Color(0xFF424242),
                        lineHeight = 18.sp
                    )
                }
            }
        }

        // Action Buttons
        Button(
            onClick = {
                val winRate = if (vm.history.isNotEmpty()) {
                    (vm.history.count { it.isWin }.toFloat() / vm.history.size * 100).toInt()
                } else {
                    0
                }

                val poster = """
🚫 HASIL SIMULASI ANTI-JUDI

📊 STATISTIK:
• Total Spin: ${vm.history.size}
• Total Kerugian:  ${vm.totalLoss()}
• Saldo Akhir:  ${vm.balance}
• Win Rate: $winRate%
• Near-Miss: ${vm.history.count { it.nearMiss }} kali

💡 KESIMPULAN:
Judi PASTI membuat rugi dalam jangka panjang!
House edge ${(vm.houseEdge * 100).toInt()}% membuat bandar selalu menang.

⚠️ Jangan tertipu oleh:
- Kemenangan sesekali
- Perasaan "hampir menang"
- Janji balik modal

🆘 BUTUH BANTUAN?
☎️ Sehat Jiwa: 119 ext. 8
📱 Into The Light: 08131-12000-68

#AntiJudi #StopJudiOnline #SDG3 #SDG4
Mari waspada dan lindungi diri dari bahaya judi!
                """.trimIndent()
                onShare(poster)
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3)),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("📤", fontSize = 20.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Share Hasil ke Teman", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }

        Button(
            onClick = {
                vm.reset()
                onBack()
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF9C27B0)),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("🔄", fontSize = 20.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Reset & Kembali", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun AnalysisPoint(title: String, description: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(Color(0xFF1976D2), RoundedCornerShape(4.dp))
                .padding(top = 6.dp)
        )
        Column {
            Text(
                title,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = Color(0xFF212121)
            )
            Text(
                description,
                fontSize = 12.sp,
                color = Color(0xFF616161),
                lineHeight = 18.sp
            )
        }
    }
}
