package dev.resetlight

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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import dev.resetlight.domain.ConnectionState
import dev.resetlight.app.ReleaseConsentStore
import dev.resetlight.features.consent.ReleaseConsentScreen
import dev.resetlight.features.connection.AdapterSelectionPolicy
import dev.resetlight.features.connection.ConnectionScreen
import dev.resetlight.features.connection.FailureAction
import dev.resetlight.transport.bluetooth.BondedDevice
import dev.resetlight.ui.ResetMotoTheme

class MainActivity : ComponentActivity() {
    private val owner by lazy { (application as ResetLightApplication).container.adapterSession }
    private val consentStore by lazy { ReleaseConsentStore(this) }

    private val permissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { refreshDevicesIfPermitted() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightNavigationBars = false
            isAppearanceLightStatusBars = false
        }
        setContent {
            ResetMotoTheme {
                val connectionState by owner.state.collectAsState()
                val readOnlyCaptureState by owner.readOnlyCaptureState.collectAsState()
                val dtcReadState by owner.dtcReadState.collectAsState()
                val instrumentReadState by owner.instrumentReadState.collectAsState()
                val dtcClearState by owner.dtcClearState.collectAsState()
                val serviceResetState by owner.serviceResetState.collectAsState()
                val operationInProgress by owner.operationInProgress.collectAsState()
                val devices by owner.devices.collectAsState()
                var selectedAddress by rememberSaveable { mutableStateOf<String?>(null) }
                var showDevicePicker by remember { mutableStateOf(false) }
                var consentAccepted by rememberSaveable { mutableStateOf(consentStore.isAccepted()) }
                val selected = devices.firstOrNull { it.address == selectedAddress }
                val adapterDefaultName = stringResource(R.string.adapter_default_name)

                if (!consentAccepted) {
                    ReleaseConsentScreen(
                        onAccept = {
                            consentStore.accept()
                            consentAccepted = true
                        },
                    )
                } else {
                    ConnectionScreen(
                    connectionState = connectionState,
                    readOnlyCaptureState = readOnlyCaptureState,
                    dtcReadState = dtcReadState,
                    instrumentReadState = instrumentReadState,
                    dtcClearState = dtcClearState,
                    serviceResetState = serviceResetState,
                    operationInProgress = operationInProgress,
                    distanceUnits = owner.distanceUnits,
                    intervalConstraints = owner.serviceIntervalConstraints,
                    // Data-collection helpers live in the separate research apps.
                    // The main app exposes only bounded rider-facing features.
                    researchCaptureEnabled = false,
                    writeOperationsEnabled = owner.writeOperationsAvailable,
                    selectedAdapterName = selected?.displayName(devices, adapterDefaultName),
                    onPairOrSelect = {
                        if (hasBluetoothPermission()) {
                            owner.refreshBondedDevices()
                            if (owner.devices.value.isEmpty()) openBluetoothSettings() else showDevicePicker = true
                        } else {
                            requestBluetoothPermission()
                        }
                    },
                    onConnect = {
                        val address = selectedAddress
                        if (!hasBluetoothPermission()) requestBluetoothPermission()
                        else if (address != null) owner.connect(address)
                    },
                    onDisconnect = owner::disconnect,
                    onReadOnlyCapture = owner::captureReadOnlyEngineData,
                    onReadDtc = owner::readDiagnosticTroubleCodes,
                    onReadInstrument = owner::readInstrumentServiceInfo,
                    onClearDtc = owner::clearDiagnosticTroubleCodes,
                    onResetServiceReminder = owner::resetServiceReminder,
                    onFailureAction = { action ->
                        when (action) {
                            FailureAction.REQUEST_PERMISSION -> requestBluetoothPermission()
                            FailureAction.OPEN_BLUETOOTH_SETTINGS -> openBluetoothSettings()
                            FailureAction.RETRY_CONNECTION -> selectedAddress?.let(owner::connect)
                            FailureAction.SELECT_ADAPTER -> {
                                owner.disconnect()
                                showDevicePicker = true
                            }
                        }
                    },
                    )

                    if (showDevicePicker) {
                        DevicePickerDialog(
                            devices = devices,
                            onSelected = { device ->
                                selectedAddress = device.address
                                showDevicePicker = false
                            },
                            onPairInSettings = {
                                showDevicePicker = false
                                openBluetoothSettings()
                            },
                            onDismiss = { showDevicePicker = false },
                        )
                    }
                }

                LaunchedEffect(connectionState, devices) {
                    if (connectionState is ConnectionState.Disconnected) {
                        selectedAddress = AdapterSelectionPolicy.reconcile(selectedAddress, devices)
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
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    private fun requestBluetoothPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissionRequest.launch(arrayOf(Manifest.permission.BLUETOOTH_CONNECT))
        } else {
            refreshDevicesIfPermitted()
        }
    }

    private fun refreshDevicesIfPermitted() {
        if (hasBluetoothPermission()) {
            try {
                owner.refreshBondedDevices()
            } catch (_: SecurityException) {
                requestBluetoothPermission()
            }
        }
    }

    private fun openBluetoothSettings() {
        startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
    }
}

@androidx.compose.runtime.Composable
private fun DevicePickerDialog(
    devices: List<BondedDevice>,
    onSelected: (BondedDevice) -> Unit,
    onPairInSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    val defaultName = stringResource(R.string.adapter_default_name)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.device_picker_title)) },
        text = {
            androidx.compose.foundation.layout.Column {
                if (devices.isEmpty()) Text(stringResource(R.string.device_picker_empty))
                devices.forEach { device ->
                    TextButton(onClick = { onSelected(device) }) {
                        Text(device.displayName(devices, defaultName))
                    }
                }
                Text(stringResource(R.string.device_picker_pair_hint))
            }
        },
        confirmButton = {
            TextButton(onClick = onPairInSettings) { Text(stringResource(R.string.device_picker_settings)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

private fun BondedDevice.displayName(all: List<BondedDevice>, defaultName: String): String {
    val base = name ?: defaultName
    return if (all.count { it.name == name } > 1) "$base • …${address.takeLast(5)}" else base
}
