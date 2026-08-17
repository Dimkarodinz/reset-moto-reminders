package dev.resetlight.research.general

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import dev.resetlight.transport.bluetooth.BondedDevice
import java.io.File
import java.time.Year

class MainActivity : ComponentActivity() {
    private val container by lazy { (application as GeneralResearchApplication).container }
    private val permissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { refreshDevicesIfPermitted() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightNavigationBars = true
        setContent {
            MaterialTheme {
                val session by container.sessions.state.collectAsState()
                val devices by container.devices.collectAsState()
                var manufacturer by rememberSaveable { mutableStateOf("") }
                var model by rememberSaveable { mutableStateOf("") }
                var modelYear by rememberSaveable { mutableStateOf("") }
                var selectedAddress by rememberSaveable { mutableStateOf<String?>(null) }
                var showDevicePicker by remember { mutableStateOf(false) }
                val validation = GeneralVehicleInput.validate(
                    manufacturer,
                    model,
                    modelYear,
                    Year.now().value,
                )
                val selected = devices.firstOrNull { it.address == selectedAddress }

                GeneralResearchScreen(
                    manufacturer = manufacturer,
                    model = model,
                    modelYear = modelYear,
                    validation = validation,
                    selectedAdapterName = selected?.name,
                    session = session,
                    onManufacturerChanged = { manufacturer = it.take(80) },
                    onModelChanged = { model = it.take(80) },
                    onYearChanged = { modelYear = it.filter(Char::isDigit).take(4) },
                    onSelectAdapter = {
                        if (hasBluetoothPermission()) {
                            container.refreshBondedDevices()
                            if (container.devices.value.isEmpty()) openBluetoothSettings() else showDevicePicker = true
                        } else {
                            requestBluetoothPermission()
                        }
                    },
                    onStart = {
                        val vehicle = (validation as? GeneralVehicleValidation.Valid)?.vehicle
                        val address = selectedAddress
                        if (!hasBluetoothPermission()) requestBluetoothPermission()
                        else if (vehicle != null && address != null) container.sessions.start(vehicle, address)
                    },
                    onCancel = container.sessions::cancel,
                    onShare = ::shareReport,
                )

                if (showDevicePicker) {
                    DevicePickerDialog(
                        devices = devices,
                        onSelected = { device ->
                            selectedAddress = device.address
                            showDevicePicker = false
                        },
                        onPair = {
                            showDevicePicker = false
                            openBluetoothSettings()
                        },
                        onDismiss = { showDevicePicker = false },
                    )
                }

                LaunchedEffect(devices) {
                    if (selectedAddress !in devices.map(BondedDevice::address)) {
                        selectedAddress = devices.singleOrNull()?.address
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshDevicesIfPermitted()
    }

    private fun hasBluetoothPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) ==
            PackageManager.PERMISSION_GRANTED

    private fun requestBluetoothPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissionRequest.launch(arrayOf(Manifest.permission.BLUETOOTH_CONNECT))
        } else {
            refreshDevicesIfPermitted()
        }
    }

    private fun refreshDevicesIfPermitted() {
        if (!hasBluetoothPermission()) return
        try {
            container.refreshBondedDevices()
        } catch (_: SecurityException) {
            requestBluetoothPermission()
        }
    }

    private fun openBluetoothSettings() {
        startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
    }

    private fun shareReport(file: File) {
        val uri = FileProvider.getUriForFile(this, "$packageName.reports", file)
        val share = Intent(Intent.ACTION_SEND).apply {
            type = "application/x-ndjson"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(share, getString(R.string.share_report_chooser)))
    }
}

@Composable
private fun DevicePickerDialog(
    devices: List<BondedDevice>,
    onSelected: (BondedDevice) -> Unit,
    onPair: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(androidx.compose.ui.res.stringResource(R.string.adapter_dialog_title)) },
        text = {
            androidx.compose.foundation.layout.Column {
                devices.forEachIndexed { index, device ->
                    TextButton(onClick = { onSelected(device) }) {
                        Text(
                            device.name ?: androidx.compose.ui.res.stringResource(
                                R.string.adapter_fallback_name,
                                index + 1,
                            ),
                        )
                    }
                }
                Text(androidx.compose.ui.res.stringResource(R.string.adapter_pair_hint))
            }
        },
        confirmButton = {
            TextButton(onClick = onPair) {
                Text(androidx.compose.ui.res.stringResource(R.string.open_bluetooth_settings))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(androidx.compose.ui.res.stringResource(R.string.cancel))
            }
        },
    )
}
