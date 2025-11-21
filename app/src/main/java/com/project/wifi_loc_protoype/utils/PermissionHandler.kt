package com.project.wifi_loc_protoype.utils

import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.karumi.dexter.Dexter
import com.karumi.dexter.MultiplePermissionsReport
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.multi.MultiplePermissionsListener

interface PermissionResultCallback {
    fun onAllPermissionsGranted()
    fun onPermissionsDenied(denied: List<String>, permanentlyDenied: List<String>)
}

object PermissionHelper {

    fun requestPermissions(
        activity: Activity,
        permissions: List<String>,
        callback: PermissionResultCallback
    ) {
        Dexter.withContext(activity)
            .withPermissions(permissions)
            .withListener(object : MultiplePermissionsListener {
                override fun onPermissionsChecked(report: MultiplePermissionsReport) {
                    val denied = report.deniedPermissionResponses.map { it.permissionName }
                    val permanentlyDenied = report.deniedPermissionResponses
                        .filter { it.isPermanentlyDenied }
                        .map { it.permissionName }

                    if (report.areAllPermissionsGranted()) {
                        callback.onAllPermissionsGranted()
                    } else {
                        callback.onPermissionsDenied(denied, permanentlyDenied)

                        if (permanentlyDenied.isNotEmpty()) {
                            showAppSettingsDialog(activity)
                        }
                    }
                }

                override fun onPermissionRationaleShouldBeShown(
                    permissions: List<PermissionRequest>,
                    token: PermissionToken
                ) {
                    showRationaleDialog(activity, token)
                }
            })
            .onSameThread()
            .check()
    }

    private fun showRationaleDialog(context: Activity, token: PermissionToken) {
        AlertDialog.Builder(context)
            .setTitle("Permissions Required")
            .setMessage("These permissions are essential for the app to function properly.")
            .setPositiveButton("Grant") { _, _ ->
                token.continuePermissionRequest()
            }
            .setNegativeButton("Cancel") { _, _ ->
                token.cancelPermissionRequest()
            }
            .setCancelable(false)
            .show()
    }

    private fun showAppSettingsDialog(context: Context) {
        AlertDialog.Builder(context)
            .setTitle("Permissions Permanently Denied")
            .setMessage("You've permanently denied some permissions. Please enable them from app settings.")
            .setPositiveButton("Open Settings") { _, _ ->
                try {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    val uri = Uri.fromParts("package", context.packageName, null)
                    intent.data = uri
                    context.startActivity(intent)
                } catch (e: ActivityNotFoundException) {
                    e.printStackTrace()
                }
            }
            .setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }
            .show()
    }

    fun isPermissionGranted(context: Context, permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }
}
