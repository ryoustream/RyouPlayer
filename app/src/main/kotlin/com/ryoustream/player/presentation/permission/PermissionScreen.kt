package com.ryoustream.player.presentation.permission

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.accompanist.permissions.*
import com.ryoustream.player.util.PermissionHelper

/**
 * PermissionScreen
 *
 * Two-stage permission flow:
 *
 * Stage 1 — All Files Access (MANAGE_EXTERNAL_STORAGE, Android 11+)
 *   Allows the app to read ALL files, including those inside .nomedia folders
 *   and dot-prefixed (hidden) directories that the MediaStore index skips.
 *   Requires the user to navigate to the system "Special app access" Settings
 *   page; it cannot be granted via a runtime dialog.
 *
 * Stage 2 — Granular media permissions (READ_MEDIA_VIDEO / READ_EXTERNAL_STORAGE)
 *   Used as a fallback when the user declines All-Files access, or on Android 10
 *   and below where MANAGE_EXTERNAL_STORAGE is not available.
 *
 * The app proceeds once EITHER stage is satisfied.
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun PermissionScreen(
    onPermissionsGranted: @Composable () -> Unit,
) {
    val context      = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // ── All-Files state (API 30+) ────────────────────────────────────────────
    val needsAllFiles = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

    var allFilesGranted by remember {
        mutableStateOf(PermissionHelper.hasAllFilesAccess())
    }

    // Re-check MANAGE_EXTERNAL_STORAGE whenever the Activity resumes.
    // This covers the case where the user grants (or revokes) the permission in
    // Settings and then returns via the system back gesture — the
    // allFilesLauncher result callback fires only when we launched Settings
    // ourselves; the lifecycle observer catches all other resume paths.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                allFilesGranted = PermissionHelper.hasAllFilesAccess()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Launcher to re-check after the user returns from the Settings page
    val allFilesLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        allFilesGranted = PermissionHelper.hasAllFilesAccess()
    }

    fun openAllFilesSettings() {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching {
                Intent(
                    Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    Uri.parse("package:${context.packageName}")
                )
            }.getOrElse {
                Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
            }
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", context.packageName, null))
        }
        allFilesLauncher.launch(intent)
    }

    // ── Granular-media fallback state ────────────────────────────────────────
    val granularPermissions = remember { PermissionHelper.granularMediaPermissions() }
    val granularState = rememberMultiplePermissionsState(permissions = granularPermissions)

    // Auto-request granular permissions only on Android 10 and below (API ≤ 29).
    // On Android 11+ (API 30+) we prefer the MANAGE_EXTERNAL_STORAGE "All files
    // access" flow — don't auto-prompt for READ_MEDIA_VIDEO, which on some ROMs
    // shows a combined "Photos & Videos" dialog that confuses users.
    // Granular access is only requested when the user explicitly taps
    // "Use Limited Access" in AllFilesPermissionUI.
    LaunchedEffect(Unit) {
        if (!allFilesGranted && !granularState.allPermissionsGranted && !needsAllFiles) {
            granularState.launchMultiplePermissionRequest()
        }
    }

    // ── Route ────────────────────────────────────────────────────────────────
    val hasStorage = allFilesGranted || granularState.allPermissionsGranted

    AnimatedContent(
        targetState = hasStorage,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "permission_gate",
    ) { granted ->
        if (granted) {
            onPermissionsGranted()
        } else {
            PermissionGate(
                needsAllFiles     = needsAllFiles,
                allFilesGranted   = allFilesGranted,
                granularState     = granularState,
                onRequestAllFiles = ::openAllFilesSettings,
                onRequestGranular = { granularState.launchMultiplePermissionRequest() },
                onOpenAppSettings = {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", context.packageName, null))
                    )
                },
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Permission gate UI
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalPermissionsApi::class)
@Composable
private fun PermissionGate(
    needsAllFiles: Boolean,
    allFilesGranted: Boolean,
    granularState: MultiplePermissionsState,
    onRequestAllFiles: () -> Unit,
    onRequestGranular: () -> Unit,
    onOpenAppSettings: () -> Unit,
) {
    // Prefer the All-Files flow on API 30+; fall back to granular otherwise
    if (needsAllFiles && !allFilesGranted) {
        AllFilesPermissionUI(
            onGrantAllFiles    = onRequestAllFiles,
            onUseLimitedAccess = onRequestGranular,
        )
    } else {
        when {
            granularState.allPermissionsGranted -> { /* handled above — no-op */ }

            granularState.shouldShowRationale ->
                GranularRationaleUI(
                    onRequest      = onRequestGranular,
                    onOpenSettings = onOpenAppSettings,
                )

            else ->
                GranularDeniedUI(
                    onOpenSettings = onOpenAppSettings,
                    onRetry        = onRequestGranular,
                )
        }
    }
}

// ── All-Files (MANAGE_EXTERNAL_STORAGE) UI ───────────────────────────────────

@Composable
private fun AllFilesPermissionUI(
    onGrantAllFiles: () -> Unit,
    onUseLimitedAccess: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.Storage,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(24.dp))
        Text(
            text = "All Files Access",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Grant full storage access so Ryou Player can find videos " +
                    "inside hidden folders (.nomedia) and dot-prefixed directories " +
                    "that are invisible to the standard media scanner.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "You will be taken to the system Settings page. " +
                    "Toggle \"Allow access to manage all files\" for Ryou Player.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
        )
        Spacer(Modifier.height(32.dp))
        Button(
            onClick = onGrantAllFiles,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Grant All Files Access")
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = onUseLimitedAccess,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Use Limited Access (no hidden files)")
        }
    }
}

// ── Granular rationale UI ─────────────────────────────────────────────────────

@Composable
private fun GranularRationaleUI(
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
        Button(onClick = onRequest, modifier = Modifier.fillMaxWidth()) {
            Text("Grant Access")
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) {
            Text("Open Settings")
        }
    }
}

// ── Granular permanently-denied UI ───────────────────────────────────────────

@Composable
private fun GranularDeniedUI(
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
        Button(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) {
            Text("Open App Settings")
        }
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = onRetry, modifier = Modifier.fillMaxWidth()) {
            Text("Try Again")
        }
    }
}
