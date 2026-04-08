package com.project.wifi_loc_protoype

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Rect
import android.location.Location
import android.location.LocationManager
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresPermission
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.Priority
import com.project.wifi_loc_protoype.databinding.ActivityMainBinding
import com.project.wifi_loc_protoype.utils.PermissionHelper
import com.project.wifi_loc_protoype.utils.PermissionResultCallback
import com.project.wifi_loc_protoype.utils.ScanRecord
import com.project.wifi_loc_protoype.utils.ScanStorage
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : AppCompatActivity() {
    private var binding: ActivityMainBinding? = null
    private lateinit var mFusedLocationClient: FusedLocationProviderClient
    private var longitude: Double = 0.0
    private var latitude: Double = 0.0
    private val scanRecords: MutableList<ScanRecord> = mutableListOf()
    private val allBSSIDs: MutableSet<String> = mutableSetOf()
    private var scanIntervalMillis = 2000L // change this to change the scan interval timing
    private val handler = android.os.Handler(Looper.getMainLooper())
    private var scanRepeater: Runnable? = null
    private var isScanning = false
    private var currentReceiver: BroadcastReceiver? = null
    private var building: String = "KP-5"
    private var wifiSSID: String = "KIIT-WIFI-NET."
    private lateinit var selected: String
    private var items: List<String> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding!!.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding!!.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        binding?.WifiScanStopBtn?.visibility = View.GONE
        binding?.WifiScanBtn?.visibility = View.VISIBLE
        mFusedLocationClient = com.google.android.gms.location.LocationServices.getFusedLocationProviderClient(this)

        items = loadLocationsFromJson()

        // Dropdown
        val adapter = ArrayAdapter(this, R.layout.dropdown_menu_item, R.id.dropdown_text, items)
        val autoComplete = binding!!.autoComplete
        autoComplete.setAdapter(adapter)
        autoComplete.setOnItemClickListener { _, _, position, _ -> // Handle selection
            selected = items[position]
            Log.e("Autocomplete",selected)
        }

        val (loadedRecords, loadedBssids) = ScanStorage.load(this)
        scanRecords.addAll(loadedRecords)
        allBSSIDs.addAll(loadedBssids)

        binding?.apCountText?.text = "Scans Collected : ${scanRecords.size}"

        updateExportButtonVisibility()

        binding?.WifiScanBtn?.setOnClickListener {
            checkGPS()
        }

        binding?.WifiScanStopBtn?.setOnClickListener {
            stopRepeatingScan()
            binding?.WifiScanStopBtn?.visibility = View.GONE
            binding?.WifiScanBtn?.visibility = View.VISIBLE
            binding!!.autoComplete.isEnabled = true
            binding!!.textInputLayout.isEnabled = true
            binding?.currentFloorET?.isEnabled = true
            binding?.textInputLayout2?.isEnabled = true
        }

        binding?.exportBtn?.setOnClickListener {
            exportToCSV(scanRecords)
        }

        binding?.buildingtv?.text=building
    }

    private fun loadLocationsFromJson(): List<String> {
        return try {
            val jsonText = assets.open("locations.json").bufferedReader().use { it.readText() }
            val jsonObject = JSONObject(jsonText)
            building = jsonObject.getString("building")
            val jsonArray = jsonObject.getJSONArray("locations")
            val list = mutableListOf<String>()
            for (i in 0 until jsonArray.length()) {
                list.add(jsonArray.getString(i))
            }
            list
        } catch (e: Exception) {
            Log.e("MainActivity", "Failed to load locations", e)
            emptyList()
        }
    }

    private fun isLocationEnabled(): Boolean {
        val locationManager: LocationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    private fun checkGPS() {
        if (!isLocationEnabled()) {
            Toast.makeText(this, "Enable Location", Toast.LENGTH_SHORT).show()
            val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            startActivity(intent)
        } else {
            wifi_scan_permission_check()
        }
    }

    private fun wifi_scan_permission_check() {
        PermissionHelper.requestPermissions(
            this,
            listOf(
                android.Manifest.permission.ACCESS_FINE_LOCATION,
                android.Manifest.permission.ACCESS_COARSE_LOCATION,
                android.Manifest.permission.NEARBY_WIFI_DEVICES,
                android.Manifest.permission.ACCESS_WIFI_STATE,
                android.Manifest.permission.CHANGE_WIFI_STATE
            ),
            object : PermissionResultCallback {
                override fun onAllPermissionsGranted() {
                    binding?.WifiScanBtn?.visibility = View.GONE
                    binding?.WifiScanStopBtn?.visibility = View.VISIBLE
                    startRepeatingWifiScan()
                }

                override fun onPermissionsDenied(denied: List<String>, permanentlyDenied: List<String>) {
                    if (permanentlyDenied.isNotEmpty()) {
                        Toast.makeText(this@MainActivity, "Enjoy not using the app!", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this@MainActivity, "Please grant all permissions to use this feature.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
    }

    @SuppressLint("MissingPermission")
    private fun startRepeatingWifiScan() {
        val floorInput = binding?.currentFloorET?.text?.toString()?.toIntOrNull()

        if (!::selected.isInitialized || floorInput == null) {
            binding?.WifiScanStopBtn?.visibility = View.GONE
            binding?.WifiScanBtn?.visibility = View.VISIBLE

            AlertDialog.Builder(this)
                .setTitle("Missing Info")
                .setMessage("Please enter the area and the floor info before starting scan.")
                .setPositiveButton("OK", null)
                .show()
            return
        }

        // pause interactions
        binding!!.autoComplete.isEnabled = false
        binding!!.textInputLayout.isEnabled = false
        binding?.currentFloorET?.isEnabled = false
        binding?.textInputLayout2?.isEnabled = false

        isScanning = true

        val wifiManager = this.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

        val receiver = object : BroadcastReceiver() {
            @RequiresPermission(Manifest.permission.ACCESS_FINE_LOCATION)
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (!isScanning) return
                val results = wifiManager.scanResults
                val timestamp = System.currentTimeMillis()
                val rssiMap = mutableMapOf<String, Int>()

                for (result in results) {
                    if (result.SSID == wifiSSID) {
                        rssiMap[result.BSSID] = result.level
                        allBSSIDs.add(result.BSSID)
                    }
                }

                scanRecords.add(
                    ScanRecord(
                        rssiMap = rssiMap,
                        latitude = latitude,
                        longitude = longitude,
                        timestamp = timestamp,
                        building = building,
                        area = selected,
                        floor = floorInput
                    )
                )

                ScanStorage.save(this@MainActivity, scanRecords, allBSSIDs)
                updateExportButtonVisibility()
                binding?.apCountText?.text = "Scans Collected : ${scanRecords.size}"
            }
        }

        currentReceiver = receiver
        val intentFilter = IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
        this.registerReceiver(receiver, intentFilter)

        scanRepeater = object : Runnable {
            override fun run() {
                if (!isScanning) return
                getLocation()
                wifiManager.startScan()
                handler.postDelayed(this, scanIntervalMillis)
            }
        }
        handler.post(scanRepeater!!)
    }

    private fun stopRepeatingScan() {
        isScanning = false
        scanRepeater?.let {
            handler.removeCallbacks(it)
        }
        scanRepeater = null

        // unregister receiver if still active
        currentReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (_: Exception) {}
        }
        currentReceiver = null

        Toast.makeText(this, "Scan stopped", Toast.LENGTH_SHORT).show()
        binding?.WifiScanStopBtn?.visibility = View.GONE
        binding?.WifiScanBtn?.visibility = View.VISIBLE
    }

    @SuppressLint("MissingPermission")
    private fun getLocation() {
        mFusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
            .addOnSuccessListener { location: Location? ->
                if (location != null) {
                    latitude = location.latitude
                    longitude = location.longitude
                    Log.e("Lat", "$latitude")
                    Log.e("Long", "$longitude")
                }
            }
    }

    private fun exportToCSV(records: List<ScanRecord>) {
        val recordsCopy = records.toList()
        val bssidList = allBSSIDs.toList().sorted()

        Thread {
            val resolver = contentResolver
            val csvName = "${building}_wifi_scan_export_${System.currentTimeMillis()}.csv"

            val contentValues = android.content.ContentValues().apply {
                put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, csvName)
                put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "text/csv")
                put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, "Documents/WiFiScans")
            }

            val uri = resolver.insert(android.provider.MediaStore.Files.getContentUri("external"), contentValues)
            if (uri != null) {
                try {
                    resolver.openOutputStream(uri)?.bufferedWriter().use { writer ->
                        writer?.apply {
                            val header = bssidList.joinToString(",") + ",Latitude,Longitude,Building,Area,Floor,Timestamp\n"
                            write(header)

                            for (record in recordsCopy) {
                                val row = buildString {
                                    for (bssid in bssidList) {
                                        append(record.rssiMap[bssid] ?: -110) // -110 if not seen
                                        append(",")
                                    }
                                    append("${record.latitude},${record.longitude},${record.building},${record.area},${record.floor},${record.timestamp}")
                                }
                                write(row + "\n")
                            }
                            flush()
                        }
                    }
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, "Exported to Documents/WiFiScans", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, "Failed to export data", Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Failed to create file", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    private fun updateExportButtonVisibility() {
        val hasData = scanRecords.isNotEmpty()
        binding?.exportBtn?.visibility = if (hasData && binding?.WifiScanStopBtn?.visibility==View.VISIBLE) View.VISIBLE else View.GONE
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean
    {
        if (event.action == MotionEvent.ACTION_DOWN) {
            val v = currentFocus
            if (v is EditText) {
                val outRect = Rect()
                v.getGlobalVisibleRect(outRect)
                if (!outRect.contains(event.rawX.toInt(), event.rawY.toInt())) {
                    v.clearFocus()
                    val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.hideSoftInputFromWindow(v.windowToken, 0)
                }
            }
        }
        return super.dispatchTouchEvent(event)
    }

    override fun onDestroy() {
        super.onDestroy()
        stopRepeatingScan()
    }

}