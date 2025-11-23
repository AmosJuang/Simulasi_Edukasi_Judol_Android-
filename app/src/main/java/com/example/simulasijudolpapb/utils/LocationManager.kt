package com.example.simulasijudolpapb.utils

import android.content.Context
import android.location.Geocoder
import android.os.Handler
import android.os.Looper
import com.example.simulasijudolpapb.data.RiskZone
import java.util.Locale

class LocationManager(private val context: Context) {

    // Dummy risk zones - data zona risiko statis
    private val riskZones = listOf(
        RiskZone("Mall Besar Jakarta", -6.2088, 106.8456, 500f,
            "⚠️ Waspada: Area mall sering menjadi tempat akses judi online!"),
        RiskZone("Kawasan Hiburan Malam Kemang", -6.1751, 106.8650, 300f,
            "🚨 Zona Risiko Tinggi: Banyak kasus kecanduan judi dimulai di area hiburan malam."),
        RiskZone("Game Center Mangga Dua", -6.2293, 106.8140, 200f,
            "⚠️ Perhatian: Game center bisa menjadi gerbang ke perjudian online."),
        RiskZone("Pusat Perbelanjaan Sudirman", -6.2250, 106.8200, 400f,
            "⚠️ Area ramai dengan banyak akses internet - zona pemicu judi online."),
        RiskZone("Kawasan Hiburan Blok M", -6.2443, 106.7988, 350f,
            "🚨 Zona Risiko: Tempat berkumpul yang sering dikaitkan dengan aktivitas judi.")
    )

    // Dummy current locations - include Surabaya (Jawa Timur)
    private val dummyUserLocations = listOf(
        Pair(-6.2088, 106.8456), // Jakarta
        Pair(-6.1751, 106.8650), // Jakarta
        Pair(-6.2000, 106.8300), // Jakarta area
        Pair(-7.2575, 112.7521), // Surabaya, Jawa Timur
        Pair(-2.5489, 140.6917)  // Papua (example)
    )

    // Hard-coded province risk mapping for all 34 provinces in Indonesia.
    // true = RISK, false = NON-RISK
    private val provinceRisk: Map<String, Boolean> = mapOf(
        // Sumatera
        "Aceh" to true,
        "Sumatera Utara" to true,
        "Sumatera Barat" to true,
        "Riau" to true,
        "Kepulauan Riau" to true,
        "Jambi" to true,
        "Bengkulu" to true,
        "Sumatera Selatan" to true,
        "Bangka Belitung" to true,
        "Lampung" to true,
        // Jawa
        "Banten" to true,
        "DKI Jakarta" to true,
        "Jawa Barat" to true,
        "Jawa Tengah" to true,
        "DI Yogyakarta" to true,
        "Jawa Timur" to true,
        // Kalimantan
        "Kalimantan Barat" to true,
        "Kalimantan Tengah" to true,
        "Kalimantan Selatan" to true,
        "Kalimantan Timur" to true,
        "Kalimantan Utara" to true,
        // Sulawesi (NON-RISK per request)
        "Sulawesi Utara" to false,
        "Sulawesi Tengah" to false,
        "Sulawesi Selatan" to false,
        "Sulawesi Tenggara" to false,
        "Sulawesi Barat" to false,
        "Gorontalo" to false,
        // Bali & Nusa Tenggara
        "Bali" to true,
        "Nusa Tenggara Barat" to true,
        "Nusa Tenggara Timur" to true,
        // Maluku
        "Maluku" to true,
        "Maluku Utara" to true,
        // Papua (NON-RISK per request)
        "Papua" to false,
        "Papua Barat" to false
    )

    // Map common English geocoder names to Indonesian province names
    private val englishToIndo = mapOf(
        "east java" to "Jawa Timur",
        "central java" to "Jawa Tengah",
        "west java" to "Jawa Barat",
        "yogyakarta" to "DI Yogyakarta",
        "jakarta" to "DKI Jakarta",
        "north sumatra" to "Sumatera Utara",
        "south sumatra" to "Sumatera Selatan",
        "west sumatra" to "Sumatera Barat",
        "west papua" to "Papua Barat",
        "papua" to "Papua",
        "north sulawesi" to "Sulawesi Utara",
        "south sulawesi" to "Sulawesi Selatan",
        "central sulawesi" to "Sulawesi Tengah",
        "southeast sulawesi" to "Sulawesi Tenggara",
        "west sulawesi" to "Sulawesi Barat",
        "gorontalo" to "Gorontalo"
    )

    private fun normalizeProvinceName(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        var s = raw.trim()
        s = s.replace(Regex("(?i)provinsi\\s+"), "")
        s = s.replace(Regex("(?i)province\\s+"), "")
        val lower = s.lowercase(Locale.getDefault())
        return englishToIndo[lower] ?: s
    }

    private fun getProvinceRiskByName(name: String?): Boolean? {
        val normalized = normalizeProvinceName(name) ?: return null
        provinceRisk.forEach { (k, v) ->
            if (k.equals(normalized, ignoreCase = true)) return v
        }
        return null
    }

    /**
     * Public API: check whether a given lat/lon is inside a province marked as risk or not.
     * Uses Geocoder to resolve the administrative province name; falls back to simple
     * bounding-box check for Sulawesi and Papua if Geocoder fails.
     * The result is returned asynchronously via the callback: (provinceName, isRisk)
     */
    fun checkProvinceRisk(lat: Double, lon: Double, onResult: (String?, Boolean) -> Unit) {
        Thread {
            var province: String? = null
            try {
                val geocoder = Geocoder(context, Locale.getDefault())
                val results = geocoder.getFromLocation(lat, lon, 1)
                if (!results.isNullOrEmpty()) {
                    province = results[0].adminArea ?: results[0].subAdminArea
                }
            } catch (_: Exception) {
                // ignore geocoder failures
            }

            if (province.isNullOrBlank()) {
                province = fallbackProvinceByBoundingBox(lat, lon)
            }

            val mapped = getProvinceRiskByName(province)
            val isRisk = mapped ?: true // default to risk if unknown

            Handler(Looper.getMainLooper()).post {
                onResult(province, isRisk)
            }
        }.start()
    }

    // Simple bounding-box fallback for broad regions (Sulawesi / Papua)
    private fun fallbackProvinceByBoundingBox(lat: Double, lon: Double): String? {
        val sulawesiMinLat = -6.0
        val sulawesiMaxLat = 3.5
        val sulawesiMinLon = 118.5
        val sulawesiMaxLon = 125.5

        val papuaMinLat = -10.0
        val papuaMaxLat = 0.0
        val papuaMinLon = 129.0
        val papuaMaxLon = 141.0

        return when {
            lat in sulawesiMinLat..sulawesiMaxLat && lon in sulawesiMinLon..sulawesiMaxLon -> "Sulawesi"
            lat in papuaMinLat..papuaMaxLat && lon in papuaMinLon..papuaMaxLon -> "Papua"
            else -> null
        }
    }

    /**
     * Simulasi pengecekan zona risiko menggunakan dummy data
     */
    fun checkRiskZone(onResult: (String?) -> Unit) {
        Handler(Looper.getMainLooper()).postDelayed({
            val randomLocation = dummyUserLocations.random()
            val warning = findNearestRiskZone(randomLocation.first, randomLocation.second)
            onResult(warning)
        }, 500)
    }

    private fun findNearestRiskZone(lat: Double, lon: Double): String? {
        for (zone in riskZones) {
            val distance = calculateDistance(lat, lon, zone.latitude, zone.longitude)
            if (distance <= zone.radiusMeters) {
                return "${zone.name}\n\n${zone.warning}\n\nJarak: ${distance.toInt()}m dari pusat zona risiko."
            }
        }
        return null
    }

    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        val earthRadius = 6371000f
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return (earthRadius * c).toFloat()
    }

    fun getDummyLocation(): Pair<Double, Double> = dummyUserLocations.random()

    fun getAllRiskZones(): List<RiskZone> = riskZones
}
