package com.example.simulasijudolpapb.utils

import android.content.Context
import com.example.simulasijudolpapb.data.RiskZone
import kotlin.random.Random

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
    
    // Dummy current location - simulasi lokasi pengguna saat ini
    private val dummyUserLocations = listOf(
        Pair(-6.2088, 106.8456), // Di Mall Besar (dalam zona risiko)
        Pair(-6.1751, 106.8650), // Di Kawasan Hiburan Malam (dalam zona risiko)
        Pair(-6.2000, 106.8300), // Lokasi aman
        Pair(-6.2293, 106.8140), // Di Game Center (dalam zona risiko)
        Pair(-6.1800, 106.8500)  // Lokasi aman
    )
    
    /**
     * Simulasi pengecekan zona risiko menggunakan dummy data
     * Akan random memilih salah satu lokasi dummy untuk simulasi
     */
    fun checkRiskZone(onResult: (String?) -> Unit) {
        // Simulasi delay untuk membuat terasa seperti mengambil data GPS
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            // Ambil random lokasi dummy
            val randomLocation = dummyUserLocations.random()
            val userLat = randomLocation.first
            val userLon = randomLocation.second
            
            // Cek apakah lokasi dummy ada di zona risiko
            val warning = findNearestRiskZone(userLat, userLon)
            onResult(warning)
        }, 500) // delay 500ms untuk simulasi
    }
    
    /**
     * Cari zona risiko terdekat dari koordinat yang diberikan
     */
    private fun findNearestRiskZone(lat: Double, lon: Double): String? {
        for (zone in riskZones) {
            val distance = calculateDistance(lat, lon, zone.latitude, zone.longitude)
            if (distance <= zone.radiusMeters) {
                return "${zone.name}\n\n${zone.warning}\n\nJarak: ${distance.toInt()}m dari pusat zona risiko."
            }
        }
        return null
    }
    
    /**
     * Hitung jarak antara dua koordinat (Haversine formula simplified)
     */
    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        val earthRadius = 6371000f // meter
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)
        
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return (earthRadius * c).toFloat()
    }
    
    /**
     * Mendapatkan lokasi dummy saat ini untuk ditampilkan
     */
    fun getDummyLocation(): Pair<Double, Double> {
        return dummyUserLocations.random()
    }
    
    /**
     * Mendapatkan semua zona risiko untuk ditampilkan di peta/list
     */
    fun getAllRiskZones(): List<RiskZone> {
        return riskZones
    }
}
