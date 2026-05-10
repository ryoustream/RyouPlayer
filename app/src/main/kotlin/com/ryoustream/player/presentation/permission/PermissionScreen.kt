package com.ryoustream.player.presentation.permission

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.google.accompanist.permissions.*

/**
 * PermissionScreen
 *
 * Handles runtime storage permission request.
 * Shows main content once permissions are granted.
 * On Android 12 (API 31-32): READ_EXTERNAL_STORAGE
 * On Android 13+ (API 33+): READ_MEDIA_VIDEO + READ_MEDIA_AUDIO
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun PermissionScreen(
    onPermissionsGranted: @Composable () -> Unit,
) {
    val context = LocalContext.current

    // Build the correct permission list based on Android version
    val permissions = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            listOf(
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_AUDIO,
            )
        } else {
            listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    val permissionsState = rememberMultiplePermissionsState(permissions = permissions)

    // Auto-request on first composition
    LaunchedEffect(Unit) {
        if (!permissionsState.allPermissionsGranted) {
            permissionsState.launchMultiplePermissionRequest()
        }
    }

    when {
        permissionsState.allPermissionsGranted -> {
            // All granted → show main app
            onPermissionsGranted()
        }

        permissionsState.shouldShowRationale -> {
            // User denied once → show rationale + request again
            PermissionRationale(
                onRequest = { permissionsState.launchMultiplePermissionRequest() },
                onOpenSettings = {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                    }
                    context.startActivity(intent)
                }
            )
        }

        else -> {
            // Waiting for result or permanently denied → show settings prompt
            PermissionDenied(
                onOpenSettings = {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                    }
                    context.startActivity(intent)
                },
                onRetry = { permissionsState.launchMultiplePermissionRequest() }
            )
        }
    }
}

@Composable
private fun PermissionRationale(
    onRequest: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.FolderOpen,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(24.dp))
        Text(
            text = "Media Access Required",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Ryou Player needs access to your media files to display and play your videos.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(32.dp))
        Button(
            onClick = onRequest,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Grant Access")
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = onOpenSettings,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Open Settings")
        }
    }
}

@Composable
private fun PermissionDenied(
    onOpenSettings: () -> Unit,
    onRetry: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.Lock,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.error,
        )
        Spacer(Modifier.height(24.dp))
        Text(
            text = "Permission Denied",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Media access was denied. Please enable it in Settings to use Ryou Player.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(32.dp))
        Button(
            onClick = onOpenSettings,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Open App Settings")
        }
        Spacer(Modifier.height(12.dp))
        TextButton(
            onClick = onRetry,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Try Again")
        }
    }
}
