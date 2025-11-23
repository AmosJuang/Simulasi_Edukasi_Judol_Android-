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
    object Analytics : Screen()
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
                is Screen.Analytics -> AnalyticsScreen(simulatorVM, onBack = { currentScreen = Screen.Home })
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
            onClick = { onNavigate(Screen.Analytics) },
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

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bmp: Bitmap? ->
        if (bmp != null) {
            bitmap = bmp
            vm.setPhoto(bmp)
        } else {
            Toast.makeText(context, "Foto dibatalkan", Toast.LENGTH_SHORT).show()
        }
    }
    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text("Kamera Edukasi", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            TextButton(onClick = onBack) { Text("Kembali") }
        }
        Text("Ambil foto barang berharga yang bisa hilang jika kecanduan judi (HP, motor, laptop).")
        Button(onClick = { launcher.launch(null) }) { Text("Ambil Foto") }
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
            Text("🎲 Ambil Lokasi Dummy & Cek Provinsi")
        }
        
        if (showRisk != null) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("⚠️ Waspada: $showRisk", color = Color(0xFFD32F2F), fontWeight = FontWeight.Bold)
                    Text("Tempat ini sering memicu aktivitas judi online. Pertimbangkan menjauh atau menghindari jam tertentu.")
                }
            }
        } else {
            Card(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("✅ Aman", color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold)
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
fun AnalyticsScreen(vm: SimulatorViewModel, onBack: () -> Unit) {
    val history = vm.history
    val points = remember(history) {
        var cum = 0
        history.map {
            cum += (it.bet - it.win)
            cum
        }
    }
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {

            Text("Analisis Kerugian", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            TextButton(onClick = onBack) { Text("Kembali") }
        }
        if (points.isEmpty()) {
            Text("Belum ada data. Lakukan beberapa spin untuk melihat grafik kerugian.")
        } else {
            Box(modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .padding(8.dp)) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    val maxVal = (points.maxOrNull() ?: 1).toFloat()
                    val minVal = (points.minOrNull() ?: 0).toFloat()
                    val range = maxVal - minVal
                    val stepX = w / maxOf(1f, (points.size - 1).toFloat())
                    var prevX = 0f
                    var prevY = if (range == 0f) h / 2f else (h - ((points[0] - minVal) / range) * h)
                    for (i in points.indices) {
                        val x = i * stepX
                        val y = if (range == 0f) h / 2f else (h - ((points[i] - minVal) / range) * h)
                        drawLine(
                            color = Color.Red, 
                            start = Offset(prevX, prevY),
                            end = Offset(x, y),
                            strokeWidth = 3f
                        )
                        prevX = x
                        prevY = y
                    }
                }
                Column(modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)) {
                    Text("Total kerugian: ${vm.totalLoss()}", fontWeight = FontWeight.Bold)
                    Text("Near-miss: pola di riwayat ditandai sebagai 'near-miss' pada riwayat.")
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text("Catatan pola: near-miss membuat pengguna merasa 'hampir menang' (mendorong putaran lanjutan). Intermittent rewards muncul tapi tidak mengubah hasil jangka panjang (house edge).")
        }
    }
}

@Composable
fun EducationScreen(onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text("Edukasi: Mengapa Judi Membuat Miskin?", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            TextButton(onClick = onBack) { Text("Kembali") }
        }
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("1. House edge memastikan keuntungan jangka panjang penyelenggara.", fontWeight = FontWeight.Medium)
                Text("2. Near-miss & intermittent rewards meningkatkan keterikatan tanpa menjamin keuntungan.")
                Text("3. Banyak korban menjual barang berharga atau meminjam uang untuk menutup kerugian.")
                Text("4. Judi dirancang untuk membuat pemain terus bermain meskipun kalah.")
            }
        }
        
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Tips berhenti:", fontWeight = FontWeight.Bold)
                Text("• Batasi waktu dan uang yang dipakai.")
                Text("• Hapus aplikasi / hindari zona pemicu.")
                Text("• Cari dukungan dari keluarga dan teman.")
                Text("• Fokus pada aktivitas positif seperti olahraga atau hobi baru.")
            }
        }
        
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Hotline Bantuan (Dummy):", fontWeight = FontWeight.Bold, color = Color(0xFF2196F3))
                Text("📞 0800-ANTIJUDI")
                Text("📍 Pusat Bantuan Lokal: Jl. Edukasi No. 123")
                Text("🌐 Website: www.antijudi.org")
            }
        }
    }
}

@Composable
fun ResultScreen(vm: SimulatorViewModel, onBack: () -> Unit, onShare: (String) -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text("Hasil Edukasi", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            TextButton(onClick = onBack) { Text("Kembali") }
        }
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE))
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("⚠️ RINGKASAN SIMULASI", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFFD32F2F))
                Text("Total Kerugian: Rp ${vm.totalLoss()}", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text("Total Spin: ${vm.history.size}")
                Text("Saldo Akhir: Rp ${vm.balance}")
            }
        }
        
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("📊 Analisis Pola:", fontWeight = FontWeight.Bold)
                Text("Simulasi ini menunjukkan bagaimana house edge dan manipulasi kecil membuat kerugian jangka panjang.")
                Text("Near-miss membuat Anda merasa 'hampir menang' dan mendorong untuk terus bermain.")
                Text("Kemenangan sesekali (intermittent rewards) tidak mengubah fakta bahwa sistem dirancang untuk menguntungkan bandar.")
            }
        }
        
        Button(
            onClick = {
                val poster = """
                    🚫 SAYA MENYELESAIKAN SIMULASI ANTI-JUDI!
                    
                    Total Kerugian: Rp ${vm.totalLoss()}
                    Total Spin: ${vm.history.size}
                    
                    Kesimpulan: Judi PASTI membuat rugi dalam jangka panjang!
                    
                    Mari waspada terhadap bahaya judi dan kecanduan.
                    
                    #AntiJudi #EdukasiBahajaJudi #SDG3 #SDG4
                """.trimIndent()
                onShare(poster)
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("📤 Share Poster Anti-Judi")
        }
        
        Button(
            onClick = {
                vm.reset()
                onBack()
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF9C27B0))
        ) {
            Text("🔄 Reset & Kembali")
        }
    }
}