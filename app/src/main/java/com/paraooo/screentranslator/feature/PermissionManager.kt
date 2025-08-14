package com.paraooo.screentranslator.feature

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

object PermissionManager {

    fun isPermissionGranted(context: Context, permission: String): Boolean {
        return when (permission) {
            Manifest.permission.SYSTEM_ALERT_WINDOW -> {
                Settings.canDrawOverlays(context)
            }
            Manifest.permission.POST_NOTIFICATIONS -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
                } else {
                    true
                }
            }
            else -> {
                ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
            }
        }
    }

    @Composable
    fun rememberPermissionRequester(onResult: (permission: String, isGranted: Boolean) -> Unit): (String) -> Unit {
        val context = LocalContext.current
        val activity = context as ComponentActivity

        val launcher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission(),
            onResult = { isGranted ->
                onResult(activity.intent.getStringExtra("permission_request") ?: "", isGranted)
            }
        )

        return remember {
            { permissionToRequest ->
                activity.intent.putExtra("permission_request", permissionToRequest)

                when {
                    isPermissionGranted(context, permissionToRequest) -> {
                        onResult(permissionToRequest, true)
                    }
                    permissionToRequest == Manifest.permission.SYSTEM_ALERT_WINDOW -> {
                        openAppSettings(context, permissionToRequest)
                        onResult(permissionToRequest, false)
                    }
                    !activity.shouldShowRequestPermissionRationale(permissionToRequest) -> {
                        openAppSettings(context, permissionToRequest)
                        onResult(permissionToRequest, false)
                    }
                    else -> {
                        launcher.launch(permissionToRequest)
                    }
                }
            }
        }
    }

    private fun openAppSettings(context: Context, permission: String) {
        val intent = when (permission) {
            Manifest.permission.SYSTEM_ALERT_WINDOW -> {
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
            }
            Manifest.permission.POST_NOTIFICATIONS -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    }
                } else {
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                    }
                }
            }
            else -> {
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", context.packageName, null)
                }
            }
        }
        context.startActivity(intent)
    }
}