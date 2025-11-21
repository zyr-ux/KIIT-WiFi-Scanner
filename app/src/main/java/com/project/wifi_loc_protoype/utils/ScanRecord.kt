package com.project.wifi_loc_protoype.utils

data class ScanRecord(
    val rssiMap: MutableMap<String, Int>,  // BSSID -> RSSI
    val latitude: Double,
    val longitude: Double,
    val timestamp: Long,
    val building: String,
    val area: String,
    val floor: Int
)