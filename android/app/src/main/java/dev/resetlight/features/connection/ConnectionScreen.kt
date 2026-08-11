package dev.resetlight.features.connection

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import dev.resetlight.R
import dev.resetlight.diagnostics.DecodedDtc
import dev.resetlight.domain.ConnectionState
import dev.resetlight.features.dtc.DtcClearUiState
import dev.resetlight.features.dtc.DtcReadState
import dev.resetlight.features.research.InstrumentReadState
import dev.resetlight.features.research.ReadOnlyCaptureState
import dev.resetlight.features.service.ServiceResetUiState
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

@Composable
fun ConnectionScreen(
    connectionState: ConnectionState,
    readOnlyCaptureState: ReadOnlyCaptureState,
    dtcReadState: DtcReadState,
    instrumentReadState: InstrumentReadState,
    dtcClearState: DtcClearUiState,
    serviceResetState: ServiceResetUiState,
    researchCaptureEnabled: Boolean,
    writeOperationsEnabled: Boolean,
    selectedAdapterName: String?,
    onPairOrSelect: () -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onReadOnlyCapture: () -> Unit,
    onReadDtc: () -> Unit,
    onReadInstrument: () -> Unit,
    onClearDtc: () -> Unit,
    onResetServiceReminder: (Int, LocalDate) -> Unit,
    onFailureAction: (FailureAction) -> Unit,
    modifier: Modifier = Modifier,
    presenter: ConnectionScreenPresenter = ConnectionScreenPresenter(),
) {
    ConnectionScreen(
        state = presenter.present(
            connectionState,
            selectedAdapterName,
            researchCaptureEnabled,
            writeOperationsEnabled,
        ),
        readOnlyCaptureState = readOnlyCaptureState,
        dtcReadState = dtcReadState,
        instrumentReadState = instrumentReadState,
        dtcClearState = dtcClearState,
        serviceResetState = serviceResetState,
        onPairOrSelect = onPairOrSelect,
        onConnect = onConnect,
        onDisconnect = onDisconnect,
        onReadOnlyCapture = onReadOnlyCapture,
        onReadDtc = onReadDtc,
        onReadInstrument = onReadInstrument,
        onClearDtc = onClearDtc,
        onResetServiceReminder = onResetServiceReminder,
        onFailureAction = onFailureAction,
        modifier = modifier,
    )
}

@Composable
fun ConnectionScreen(
    state: ConnectionScreenState,
    readOnlyCaptureState: ReadOnlyCaptureState,
    dtcReadState: DtcReadState,
    instrumentReadState: InstrumentReadState,
    dtcClearState: DtcClearUiState,
    serviceResetState: ServiceResetUiState,
    onPairOrSelect: () -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onReadOnlyCapture: () -> Unit,
    onReadDtc: () -> Unit,
    onReadInstrument: () -> Unit,
    onClearDtc: () -> Unit,
    onResetServiceReminder: (Int, LocalDate) -> Unit,
    onFailureAction: (FailureAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .padding(contentPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
            )
            ConnectionCard(
                state = state,
                onPairOrSelect = onPairOrSelect,
                onConnect = onConnect,
                onDisconnect = onDisconnect,
                onFailureAction = onFailureAction,
            )
            if (state.showReadOnlyCapture) {
                ReadOnlyCaptureCard(
                    captureState = readOnlyCaptureState,
                    onCapture = onReadOnlyCapture,
                )
            }
            HorizontalDivider()
            Text(
                text = "Motorcycle diagnostics",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            if (state.showDtcRead) {
                DtcReadCard(
                    dtcReadState = dtcReadState,
                    onReadDtc = onReadDtc,
                )
            }
            if (state.showServiceInfoRead) {
                ServiceInfoReadCard(
                    instrumentReadState = instrumentReadState,
                    onReadInstrument = onReadInstrument,
                )
            }
            if (state.showDtcClear) {
                DtcClearCard(
                    dtcClearState = dtcClearState,
                    onClearDtc = onClearDtc,
                )
            }
            if (state.showServiceReset) {
                ServiceResetCard(
                    serviceResetState = serviceResetState,
                    onResetServiceReminder = onResetServiceReminder,
                )
            } else {
                UnavailableFeatureCard(state.serviceCard)
            }
            Text(
                text = "Resetting a reminder does not perform maintenance.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ReadOnlyCaptureCard(
    captureState: ReadOnlyCaptureState,
    onCapture: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = when (captureState) {
                    ReadOnlyCaptureState.Idle -> "Read-only ECU capture"
                    ReadOnlyCaptureState.Running -> "Reading ECU data"
                    is ReadOnlyCaptureState.Complete -> "Capture complete"
                    is ReadOnlyCaptureState.Blocked -> "Capture stopped safely"
                    is ReadOnlyCaptureState.Failed -> "Capture failed"
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = when (captureState) {
                    ReadOnlyCaptureState.Idle ->
                        "Reads known non-sensitive ECU identifiers and DTC information once. It never clears faults or changes the service reminder."
                    ReadOnlyCaptureState.Running ->
                        "Keep the ignition on and engine off. Do not leave the app until the capture finishes."
                    is ReadOnlyCaptureState.Complete ->
                        "Reported DTC count: ${captureState.dtcCount}. ${captureState.responseCount} responses were logged. Disconnect and return with the phone logs."
                    is ReadOnlyCaptureState.Blocked ->
                        "${captureState.reason} Disconnect and return with the phone logs."
                    is ReadOnlyCaptureState.Failed -> captureState.reason
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            if (captureState is ReadOnlyCaptureState.Running) {
                CircularProgressIndicator()
            }
            if (captureState is ReadOnlyCaptureState.Idle) {
                Button(
                    onClick = onCapture,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Capture read-only ECU data")
                }
            }
        }
    }
}

@Composable
private fun DtcReadCard(
    dtcReadState: DtcReadState,
    onReadDtc: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Trouble codes",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = when (dtcReadState) {
                    DtcReadState.Idle ->
                        "Reads confirmed diagnostic trouble codes from the engine ECU. This does not clear or change anything."
                    DtcReadState.Running ->
                        "Reading confirmed trouble codes. Keep the ignition on."
                    is DtcReadState.Complete -> if (dtcReadState.reportedCount == 0) {
                        "No confirmed trouble codes are stored."
                    } else {
                        "${dtcReadState.reportedCount} confirmed trouble code(s) stored."
                    }
                    is DtcReadState.Failed -> dtcReadState.reason
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            if (dtcReadState is DtcReadState.Complete) {
                dtcReadState.dtcs.forEach { dtc ->
                    DtcRow(dtc)
                }
            }
            if (dtcReadState is DtcReadState.Running) {
                CircularProgressIndicator()
            } else {
                Button(
                    onClick = onReadDtc,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (dtcReadState is DtcReadState.Idle) "Read trouble codes" else "Read again")
                }
            }
        }
    }
}

@Composable
private fun ServiceInfoReadCard(
    instrumentReadState: InstrumentReadState,
    onReadInstrument: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Instrument read (research)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = when (instrumentReadState) {
                    InstrumentReadState.Idle ->
                        "First contact with the instrument cluster. Sends only the two observed reads and decodes the odometer. It never changes the service reminder."
                    InstrumentReadState.Running ->
                        "Reading instrument data. Keep the ignition on."
                    is InstrumentReadState.Complete ->
                        "Odometer: ${instrumentReadState.odometerKm} km (${instrumentReadState.odometerRaw}). Status: ${instrumentReadState.statusAscii}. Return with the phone logs."
                    is InstrumentReadState.Blocked ->
                        "${instrumentReadState.reason} Return with the phone logs."
                    is InstrumentReadState.Failed -> instrumentReadState.reason
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            if (instrumentReadState is InstrumentReadState.Running) {
                CircularProgressIndicator()
            } else {
                Button(
                    onClick = onReadInstrument,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (instrumentReadState is InstrumentReadState.Idle) "Read instrument data" else "Read again")
                }
            }
        }
    }
}

@Composable
private fun DtcClearCard(
    dtcClearState: DtcClearUiState,
    onClearDtc: () -> Unit,
) {
    var confirmArmed by remember { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Clear trouble codes (research)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = when (dtcClearState) {
                    DtcClearUiState.Idle ->
                        "Runs security access and clears confirmed engine codes. Only clear codes you have already read and understood."
                    DtcClearUiState.Running ->
                        "Clearing trouble codes. Keep the ignition on and do not leave the app."
                    is DtcClearUiState.Cleared -> if (dtcClearState.remainingCount == 0) {
                        "Cleared. No confirmed trouble codes remain."
                    } else {
                        "Clear completed, but ${dtcClearState.remainingCount} code(s) are still present."
                    }
                    is DtcClearUiState.Blocked -> "${dtcClearState.reason} Nothing was cleared."
                    is DtcClearUiState.Failed -> dtcClearState.reason
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            if (dtcClearState is DtcClearUiState.Running) {
                CircularProgressIndicator()
            } else if (!confirmArmed) {
                OutlinedButton(
                    onClick = { confirmArmed = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (dtcClearState is DtcClearUiState.Idle) "Clear trouble codes" else "Clear again")
                }
            } else {
                ArmedConfirmation(
                    warning = "This sends a write to the engine ECU. Confirm to proceed.",
                    confirmLabel = "Confirm clear",
                    onCancel = { confirmArmed = false },
                    onConfirm = {
                        confirmArmed = false
                        onClearDtc()
                    },
                )
            }
        }
    }
}

@Composable
private fun ServiceResetCard(
    serviceResetState: ServiceResetUiState,
    onResetServiceReminder: (Int, LocalDate) -> Unit,
) {
    var distanceText by remember { mutableStateOf("10000") }
    var dateText by remember { mutableStateOf("") }
    var confirmArmed by remember { mutableStateOf(false) }

    val distanceKm = remember(distanceText) { distanceText.trim().toIntOrNull() }
    val parsedDate = remember(dateText) { parseServiceDate(dateText) }
    val inputsValid = distanceKm != null && distanceKm > 0 && parsedDate != null

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Reset service reminder (research)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = when (serviceResetState) {
                    ServiceResetUiState.Idle ->
                        "Writes a new service interval and next-service date to the instrument cluster. Resetting a reminder does not perform maintenance."
                    ServiceResetUiState.Running ->
                        "Writing the service reminder. Keep the ignition on and do not leave the app."
                    is ServiceResetUiState.Committed ->
                        "Committed. Odometer ${serviceResetState.odometerKm} km, interval ${serviceResetState.distanceKm} km, next service ${serviceResetState.nextServiceDate}."
                    is ServiceResetUiState.Blocked -> "${serviceResetState.reason} Nothing was written."
                    is ServiceResetUiState.Failed -> serviceResetState.reason
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            if (serviceResetState is ServiceResetUiState.Running) {
                CircularProgressIndicator()
            } else {
                OutlinedTextField(
                    value = distanceText,
                    onValueChange = { distanceText = it; confirmArmed = false },
                    label = { Text("Service interval (km)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = dateText,
                    onValueChange = { dateText = it; confirmArmed = false },
                    label = { Text("Next service date (YYYY-MM-DD)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (!confirmArmed) {
                    Button(
                        onClick = { confirmArmed = true },
                        enabled = inputsValid,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (serviceResetState is ServiceResetUiState.Committed) "Reset again" else "Reset reminder")
                    }
                } else if (distanceKm != null && parsedDate != null) {
                    ArmedConfirmation(
                        warning = "This writes to the instrument cluster: interval $distanceKm km, " +
                            "next service $parsedDate. Confirm to proceed.",
                        confirmLabel = "Confirm reset",
                        onCancel = { confirmArmed = false },
                        onConfirm = {
                            confirmArmed = false
                            onResetServiceReminder(distanceKm, parsedDate)
                        },
                    )
                }
            }
        }
    }
}

/**
 * The shared arm→confirm affordance for the two research write operations: a red
 * warning line and a Cancel / Confirm button row, so both write flows stay
 * visually and behaviourally identical.
 */
@Composable
private fun ArmedConfirmation(
    warning: String,
    confirmLabel: String,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    Text(
        text = warning,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) {
            Text("Cancel")
        }
        Button(onClick = onConfirm, modifier = Modifier.weight(1f)) {
            Text(confirmLabel)
        }
    }
}

private fun parseServiceDate(text: String): LocalDate? = try {
    if (text.isBlank()) null else LocalDate.parse(text.trim(), DateTimeFormatter.ISO_LOCAL_DATE)
} catch (_: DateTimeParseException) {
    null
}

@Composable
private fun ConnectionCard(
    state: ConnectionScreenState,
    onPairOrSelect: () -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onFailureAction: (FailureAction) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (state.inProgress) {
                    CircularProgressIndicator()
                }
                Column {
                    Text(
                        text = state.statusTitle,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = state.statusDetail,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            state.selectedAdapterName?.let { LabelValue("Adapter", it) }
            state.elmIdentity?.let { LabelValue("ELM identity", it) }
            state.stnIdentity?.let { LabelValue("STN identity", it) }
            state.mapId?.let { LabelValue("Adapter map", it) }

            if (state.showPairOrSelect) {
                OutlinedButton(
                    onClick = onPairOrSelect,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Pair or select adapter")
                }
            }
            if (state.showConnect) {
                Button(
                    onClick = onConnect,
                    enabled = state.connectEnabled,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Connect")
                }
            }
            if (state.showDisconnect) {
                OutlinedButton(
                    onClick = onDisconnect,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Disconnect")
                }
            }
            val failureAction = state.failureAction
            val failureActionLabel = state.failureActionLabel
            if (failureAction != null && failureActionLabel != null) {
                Button(
                    onClick = { onFailureAction(failureAction) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(failureActionLabel)
                }
            }
        }
    }
}

@Composable
private fun LabelValue(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(text = value, style = MaterialTheme.typography.bodyMedium)
    }
}

/**
 * One decoded trouble code. When the message has been localized, the
 * authoritative English wording is available via [DtcMessage.originalMessage];
 * a toggle reveals it so the user can always fall back to the source text.
 */
@Composable
private fun DtcRow(dtc: DecodedDtc) {
    var showOriginal by rememberSaveable(dtc.displayCode) { mutableStateOf(false) }
    val original = dtc.message.originalMessage
    Column {
        LabelValue(dtc.displayCode, dtc.message.message)
        if (original != null) {
            if (showOriginal) {
                Text(
                    text = original,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = stringResource(
                    if (showOriginal) R.string.dtc_hide_original else R.string.dtc_show_original,
                ),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable { showOriginal = !showOriginal },
            )
        }
    }
}

@Composable
private fun UnavailableFeatureCard(state: UnavailableFeatureCardState) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = state.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = state.detail,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
