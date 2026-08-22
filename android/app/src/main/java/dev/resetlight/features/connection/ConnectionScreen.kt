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
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.resetlight.R
import dev.resetlight.diagnostics.DecodedDtc
import dev.resetlight.domain.ConnectionState
import dev.resetlight.domain.DistanceUnit
import dev.resetlight.domain.MotorcycleDistanceUnits
import dev.resetlight.domain.NextServiceDateRules
import dev.resetlight.domain.ServiceIntervalConstraints
import dev.resetlight.domain.ServiceIntervalError
import dev.resetlight.features.dtc.DtcClearUiState
import dev.resetlight.features.dtc.DtcReadState
import dev.resetlight.features.research.InstrumentReadState
import dev.resetlight.features.research.ReadOnlyCaptureState
import dev.resetlight.features.service.ServiceResetUiState
import dev.resetlight.ui.label
import dev.resetlight.ui.resolved
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun ConnectionScreen(
    connectionState: ConnectionState,
    readOnlyCaptureState: ReadOnlyCaptureState,
    dtcReadState: DtcReadState,
    instrumentReadState: InstrumentReadState,
    dtcClearState: DtcClearUiState,
    serviceResetState: ServiceResetUiState,
    operationInProgress: Boolean,
    distanceUnits: MotorcycleDistanceUnits,
    intervalConstraints: ServiceIntervalConstraints?,
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
    onResetServiceReminder: (Int, DistanceUnit, LocalDate) -> Unit,
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
        operationInProgress = operationInProgress,
        distanceUnits = distanceUnits,
        intervalConstraints = intervalConstraints,
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
    operationInProgress: Boolean,
    distanceUnits: MotorcycleDistanceUnits,
    intervalConstraints: ServiceIntervalConstraints?,
    onPairOrSelect: () -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onReadOnlyCapture: () -> Unit,
    onReadDtc: () -> Unit,
    onReadInstrument: () -> Unit,
    onClearDtc: () -> Unit,
    onResetServiceReminder: (Int, DistanceUnit, LocalDate) -> Unit,
    onFailureAction: (FailureAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val actionsEnabled = !operationInProgress
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
                style = ActionTitleStyle,
            )
            ConnectionCard(
                state = state,
                onPairOrSelect = onPairOrSelect,
                onConnect = onConnect,
                onDisconnect = onDisconnect,
                disconnectEnabled = !operationInProgress,
                onFailureAction = onFailureAction,
            )
            if (state.showReadOnlyCapture) {
                ReadOnlyCaptureCard(
                    captureState = readOnlyCaptureState,
                    onCapture = onReadOnlyCapture,
                    actionsEnabled = actionsEnabled,
                )
            }
            HorizontalDivider()
            Text(
                text = stringResource(R.string.diagnostics_section_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            if (state.showDtcRead) {
                DtcReadCard(
                    dtcReadState = dtcReadState,
                    onReadDtc = onReadDtc,
                    actionsEnabled = actionsEnabled,
                )
            }
            if (state.showServiceInfoRead) {
                ServiceInfoReadCard(
                    instrumentReadState = instrumentReadState,
                    distanceUnits = distanceUnits,
                    onReadInstrument = onReadInstrument,
                    actionsEnabled = actionsEnabled,
                )
            }
            if (state.showDtcClear) {
                DtcClearCard(
                    dtcClearState = dtcClearState,
                    onClearDtc = onClearDtc,
                    actionsEnabled = actionsEnabled,
                )
            }
            if (state.showServiceReset) {
                ServiceResetCard(
                    serviceResetState = serviceResetState,
                    distanceUnits = distanceUnits,
                    intervalConstraints = intervalConstraints,
                    onResetServiceReminder = onResetServiceReminder,
                    actionsEnabled = actionsEnabled,
                )
            } else if (state.showUnavailableServiceCard) {
                UnavailableFeatureCard(state.serviceCard)
            }
            Text(
                text = stringResource(R.string.reset_disclaimer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private val ActionTitleStyle = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = FontWeight.Black,
    fontStyle = FontStyle.Italic,
    fontSize = 30.sp,
    lineHeight = 32.sp,
    letterSpacing = 1.2.sp,
)

@Composable
private fun ReadOnlyCaptureCard(
    captureState: ReadOnlyCaptureState,
    onCapture: () -> Unit,
    actionsEnabled: Boolean,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(
                    when (captureState) {
                        ReadOnlyCaptureState.Idle -> R.string.capture_title_idle
                        ReadOnlyCaptureState.Running -> R.string.capture_title_running
                        is ReadOnlyCaptureState.Complete -> R.string.capture_title_complete
                        is ReadOnlyCaptureState.Blocked -> R.string.capture_title_blocked
                        is ReadOnlyCaptureState.Failed -> R.string.capture_title_failed
                    },
                ),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = when (captureState) {
                    ReadOnlyCaptureState.Idle -> stringResource(R.string.capture_body_idle)
                    ReadOnlyCaptureState.Running -> stringResource(R.string.capture_body_running)
                    is ReadOnlyCaptureState.Complete -> stringResource(
                        R.string.capture_body_complete,
                        captureState.dtcCount,
                        captureState.responseCount,
                    )
                    is ReadOnlyCaptureState.Blocked -> stringResource(
                        R.string.capture_body_blocked,
                        captureState.reason.resolved(),
                    )
                    is ReadOnlyCaptureState.Failed -> captureState.reason.resolved()
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            if (captureState is ReadOnlyCaptureState.Running) {
                CircularProgressIndicator()
            }
            if (captureState is ReadOnlyCaptureState.Idle) {
                Button(
                    onClick = onCapture,
                    enabled = actionsEnabled,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.capture_button))
                }
            }
        }
    }
}

@Composable
private fun DtcReadCard(
    dtcReadState: DtcReadState,
    onReadDtc: () -> Unit,
    actionsEnabled: Boolean,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.dtc_read_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = when (dtcReadState) {
                    DtcReadState.Idle -> stringResource(R.string.dtc_read_body_idle)
                    DtcReadState.Running -> stringResource(R.string.dtc_read_body_running)
                    is DtcReadState.Complete -> if (dtcReadState.reportedCount == 0) {
                        stringResource(R.string.dtc_read_body_none)
                    } else {
                        stringResource(R.string.dtc_read_body_count, dtcReadState.reportedCount)
                    }
                    is DtcReadState.Failed -> dtcReadState.reason.resolved()
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
                    enabled = actionsEnabled,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        stringResource(
                            if (dtcReadState is DtcReadState.Idle) {
                                R.string.dtc_read_button
                            } else {
                                R.string.action_read_again
                            },
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun ServiceInfoReadCard(
    instrumentReadState: InstrumentReadState,
    distanceUnits: MotorcycleDistanceUnits,
    onReadInstrument: () -> Unit,
    actionsEnabled: Boolean,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.instrument_read_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = when (instrumentReadState) {
                    InstrumentReadState.Idle -> stringResource(R.string.instrument_read_body_idle)
                    InstrumentReadState.Running -> stringResource(R.string.instrument_read_body_running)
                    is InstrumentReadState.Complete -> stringResource(
                        R.string.instrument_read_body_complete,
                        distanceUnits.wireToDisplay(instrumentReadState.odometerKm),
                        distanceUnits.display.label(),
                        instrumentReadState.statusAscii,
                    )
                    is InstrumentReadState.Blocked -> stringResource(
                        R.string.instrument_read_body_blocked,
                        instrumentReadState.reason.resolved(),
                    )
                    is InstrumentReadState.Failed -> instrumentReadState.reason.resolved()
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            if (instrumentReadState is InstrumentReadState.Running) {
                CircularProgressIndicator()
            } else {
                Button(
                    onClick = onReadInstrument,
                    enabled = actionsEnabled,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        stringResource(
                            if (instrumentReadState is InstrumentReadState.Idle) {
                                R.string.instrument_read_button
                            } else {
                                R.string.action_read_again
                            },
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun DtcClearCard(
    dtcClearState: DtcClearUiState,
    onClearDtc: () -> Unit,
    actionsEnabled: Boolean,
) {
    var confirmArmed by remember { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.dtc_clear_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = when (dtcClearState) {
                    DtcClearUiState.Idle -> stringResource(R.string.dtc_clear_body_idle)
                    DtcClearUiState.Running -> stringResource(R.string.dtc_clear_body_running)
                    is DtcClearUiState.Cleared -> if (dtcClearState.remainingCount == 0) {
                        stringResource(R.string.dtc_clear_body_cleared_none)
                    } else {
                        stringResource(
                            R.string.dtc_clear_body_cleared_remaining,
                            dtcClearState.remainingCount,
                        )
                    }
                    is DtcClearUiState.Blocked -> stringResource(
                        R.string.dtc_clear_body_blocked,
                        dtcClearState.reason.resolved(),
                    )
                    is DtcClearUiState.NeedsVerification -> dtcClearState.reason.resolved()
                    is DtcClearUiState.Failed -> dtcClearState.reason.resolved()
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            if (dtcClearState is DtcClearUiState.Running) {
                CircularProgressIndicator()
            } else if (!confirmArmed) {
                OutlinedButton(
                    onClick = { confirmArmed = true },
                    enabled = actionsEnabled,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        stringResource(
                            if (dtcClearState is DtcClearUiState.Idle) {
                                R.string.dtc_clear_button
                            } else {
                                R.string.dtc_clear_button_again
                            },
                        ),
                    )
                }
            } else {
                ArmedConfirmation(
                    warning = stringResource(R.string.dtc_clear_warning),
                    confirmLabel = stringResource(R.string.dtc_clear_confirm),
                    onCancel = { confirmArmed = false },
                    onConfirm = {
                        confirmArmed = false
                        onClearDtc()
                    },
                    enabled = actionsEnabled,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ServiceResetCard(
    serviceResetState: ServiceResetUiState,
    distanceUnits: MotorcycleDistanceUnits,
    intervalConstraints: ServiceIntervalConstraints?,
    onResetServiceReminder: (Int, DistanceUnit, LocalDate) -> Unit,
    actionsEnabled: Boolean,
) {
    val today = remember { LocalDate.now() }
    var distanceText by remember { mutableStateOf("10000") }
    var selectedUnit by rememberSaveable { mutableStateOf(distanceUnits.display) }
    var selectedDateEpochDay by rememberSaveable {
        mutableStateOf(NextServiceDateRules.default(today).toEpochDay())
    }
    var showDatePicker by remember { mutableStateOf(false) }
    var confirmArmed by remember { mutableStateOf(false) }

    val selectedDate = LocalDate.ofEpochDay(selectedDateEpochDay)
    val unitLabel = selectedUnit.label()
    val intervalUnits = MotorcycleDistanceUnits(selectedUnit, selectedUnit)
    val odometerDisplayUnits = MotorcycleDistanceUnits(distanceUnits.wire, selectedUnit)
    val intervalError = intervalConstraints?.validate(distanceText, intervalUnits)
    val distanceDisplay = distanceText.trim().toIntOrNull()
    val dateValid = NextServiceDateRules.isValid(selectedDate, today)
    val inputsValid =
        intervalConstraints != null && intervalError == null && distanceDisplay != null && dateValid
    val dateFormatter = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.service_reset_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = when (serviceResetState) {
                    ServiceResetUiState.Idle -> stringResource(R.string.service_reset_body_idle)
                    ServiceResetUiState.Running -> stringResource(R.string.service_reset_body_running)
                    is ServiceResetUiState.Committed -> stringResource(
                        R.string.service_reset_body_committed,
                        odometerDisplayUnits.wireToDisplay(serviceResetState.odometerKm),
                        selectedUnit.label(),
                        serviceResetState.distance,
                        serviceResetState.distanceUnit.label(),
                        dateFormatter.format(serviceResetState.nextServiceDate),
                    )
                    is ServiceResetUiState.Blocked -> stringResource(
                        R.string.service_reset_body_blocked,
                        serviceResetState.reason.resolved(),
                    )
                    is ServiceResetUiState.NeedsInspection -> serviceResetState.reason.resolved()
                    is ServiceResetUiState.Failed -> serviceResetState.reason.resolved()
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = stringResource(R.string.service_reset_date_time_reminder),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (serviceResetState is ServiceResetUiState.Running) {
                CircularProgressIndicator()
            } else {
                Text(
                    text = stringResource(R.string.service_reset_unit_label),
                    style = MaterialTheme.typography.labelLarge,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (selectedUnit == DistanceUnit.KILOMETERS) {
                        Button(onClick = {}) { Text(stringResource(R.string.distance_unit_km)) }
                    } else {
                        OutlinedButton(onClick = {
                            selectedUnit = DistanceUnit.KILOMETERS
                            confirmArmed = false
                        }) { Text(stringResource(R.string.distance_unit_km)) }
                    }
                    if (selectedUnit == DistanceUnit.MILES) {
                        Button(onClick = {}) { Text(stringResource(R.string.distance_unit_miles)) }
                    } else {
                        OutlinedButton(onClick = {
                            selectedUnit = DistanceUnit.MILES
                            confirmArmed = false
                        }) { Text(stringResource(R.string.distance_unit_miles)) }
                    }
                }
                OutlinedTextField(
                    value = distanceText,
                    onValueChange = { distanceText = it; confirmArmed = false },
                    label = { Text(stringResource(R.string.service_reset_interval_label, unitLabel)) },
                    singleLine = true,
                    isError = intervalError != null,
                    supportingText = when {
                        intervalError == ServiceIntervalError.FORMAT -> {
                            { Text(stringResource(R.string.service_reset_interval_error_format)) }
                        }
                        intervalError == ServiceIntervalError.RANGE -> {
                            intervalConstraints.let { constraints ->
                                {
                                Text(
                                    stringResource(
                                        R.string.service_reset_interval_error_range,
                                        constraints.stepDisplay(intervalUnits),
                                        unitLabel,
                                        constraints.minDisplay(intervalUnits),
                                        constraints.maxDisplay(intervalUnits),
                                    ),
                                )
                                }
                            }
                        }
                        else -> null
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                // A read-only field that opens the date picker: the picker is
                // the only way to change the date, so format errors are
                // impossible and the selectable window enforces the date rules.
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = dateFormatter.format(selectedDate),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.service_reset_date_label)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clickable {
                                confirmArmed = false
                                showDatePicker = true
                            },
                    )
                }
                if (!confirmArmed) {
                    Button(
                        onClick = { confirmArmed = true },
                        enabled = inputsValid && actionsEnabled,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            stringResource(
                                if (serviceResetState is ServiceResetUiState.Committed) {
                                    R.string.service_reset_button_again
                                } else {
                                    R.string.service_reset_button
                                },
                            ),
                        )
                    }
                } else if (inputsValid) {
                    val confirmedDistance = requireNotNull(distanceDisplay)
                    ArmedConfirmation(
                        warning = stringResource(
                            R.string.service_reset_warning,
                            confirmedDistance,
                            unitLabel,
                            dateFormatter.format(selectedDate),
                        ),
                        confirmLabel = stringResource(R.string.service_reset_confirm),
                        onCancel = { confirmArmed = false },
                        onConfirm = {
                            confirmArmed = false
                            onResetServiceReminder(confirmedDistance, selectedUnit, selectedDate)
                        },
                        enabled = actionsEnabled,
                    )
                }
            }
        }
    }

    if (showDatePicker) {
        NextServiceDatePickerDialog(
            today = today,
            initialDate = selectedDate,
            onConfirm = { picked ->
                selectedDateEpochDay = picked.toEpochDay()
                showDatePicker = false
            },
            onDismiss = { showDatePicker = false },
        )
    }
}

/**
 * Date picker for the next-service date, limited to the window
 * [NextServiceDateRules] allows: today through two years ahead.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NextServiceDatePickerDialog(
    today: LocalDate,
    initialDate: LocalDate,
    onConfirm: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    val selectableDates = remember(today) {
        object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                NextServiceDateRules.isValid(LocalDate.ofEpochDay(utcTimeMillis / MILLIS_PER_DAY), today)

            override fun isSelectableYear(year: Int): Boolean =
                year >= today.year && year <= NextServiceDateRules.latest(today).year
        }
    }
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initialDate.toEpochDay() * MILLIS_PER_DAY,
        selectableDates = selectableDates,
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    state.selectedDateMillis?.let { millis ->
                        onConfirm(LocalDate.ofEpochDay(millis / MILLIS_PER_DAY))
                    }
                },
                enabled = state.selectedDateMillis != null,
            ) {
                Text(stringResource(R.string.action_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    ) {
        DatePicker(state = state)
    }
}

private const val MILLIS_PER_DAY = 86_400_000L

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
    enabled: Boolean = true,
) {
    Text(
        text = warning,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.action_cancel))
        }
        Button(onClick = onConfirm, enabled = enabled, modifier = Modifier.weight(1f)) {
            Text(confirmLabel)
        }
    }
}

@Composable
private fun ConnectionCard(
    state: ConnectionScreenState,
    onPairOrSelect: () -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    disconnectEnabled: Boolean,
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
                        text = state.statusTitle.resolved(),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = state.statusDetail.resolved(),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            state.selectedAdapterName?.let { LabelValue(stringResource(R.string.label_adapter), it) }
            state.elmIdentity?.let { LabelValue(stringResource(R.string.label_elm_identity), it) }
            state.stnIdentity?.let { LabelValue(stringResource(R.string.label_stn_identity), it) }
            state.mapId?.let { LabelValue(stringResource(R.string.label_adapter_map), it) }

            if (state.showPairOrSelect) {
                OutlinedButton(
                    onClick = onPairOrSelect,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.button_pair_or_select))
                }
            }
            if (state.showConnect) {
                Button(
                    onClick = onConnect,
                    enabled = state.connectEnabled,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.button_connect))
                }
            }
            if (state.showDisconnect) {
                OutlinedButton(
                    onClick = onDisconnect,
                    enabled = disconnectEnabled,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.button_disconnect))
                }
            }
            val failureAction = state.failureAction
            val failureActionLabel = state.failureActionLabel
            if (failureAction != null && failureActionLabel != null) {
                Button(
                    onClick = { onFailureAction(failureAction) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(failureActionLabel.resolved())
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
                text = state.title.resolved(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = state.detail.resolved(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
