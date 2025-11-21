package com.project.wifi_loc_protoype.utils

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.project.wifi_loc_protoype.utils.ScanRecord
import java.io.File

object ScanStorage {
    private const val fileName = "wifi_scan_data.json"
    private val gson = Gson()

    fun save(context: Context, data: List<ScanRecord>, bssids: Set<String>) {
        val wrapper = mapOf(
            "records" to data,
            "bssids" to bssids.toList()
        )
        File(context.filesDir, fileName).writeText(gson.toJson(wrapper))
    }

    fun load(context: Context): Pair<MutableList<ScanRecord>, MutableSet<String>> {
        val file = File(context.filesDir, fileName)
        if (!file.exists()) return Pair(mutableListOf(), mutableSetOf())

        val json = file.readText()
        val type = object : TypeToken<Map<String, Any>>() {}.type
        val map: Map<String, Any> = gson.fromJson(json, type)

        val recordsJson = gson.toJson(map["records"])
        val bssidsJson = gson.toJson(map["bssids"])

        val recordsType = object : TypeToken<MutableList<ScanRecord>>() {}.type
        val bssidsType = object : TypeToken<MutableList<String>>() {}.type

        val records: MutableList<ScanRecord> = gson.fromJson(recordsJson, recordsType)
        val bssids: MutableSet<String> = gson.fromJson<MutableList<String>>(bssidsJson, bssidsType).toMutableSet()

        return Pair(records, bssids)
    }

    fun clear(context: Context) {
        File(context.filesDir, fileName).delete()
    }
}
