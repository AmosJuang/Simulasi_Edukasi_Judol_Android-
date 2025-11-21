package com.example.simulasijudolpapb.data

data class RiskZone(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Float,
    val warning: String
)
