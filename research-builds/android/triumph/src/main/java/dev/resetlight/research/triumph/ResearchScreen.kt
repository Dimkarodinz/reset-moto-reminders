package dev.resetlight.research.triumph

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.io.File

@Composable
fun ResearchScreen(
    model: String,
    modelYear: String,
    validation: VehicleInputValidation,
    selectedAdapterName: String?,
    session: ResearchSessionState,
    clearDtcs: Boolean,
    resetService: Boolean,
    distanceKm: String,
    nextServiceDate: String,
    writesAcknowledged: Boolean,
    writeValidation: ResearchWriteInputValidation,
    onModelChanged: (String) -> Unit,
    onYearChanged: (String) -> Unit,
    onClearDtcsChanged: (Boolean) -> Unit,
    onResetServiceChanged: (Boolean) -> Unit,
    onDistanceChanged: (String) -> Unit,
    onDateChanged: (String) -> Unit,
    onWritesAcknowledgedChanged: (Boolean) -> Unit,
    onSelectAdapter: () -> Unit,
    onStart: () -> Unit,
    onCancel: () -> Unit,
    onShare: (File) -> Unit,
    presenter: ResearchScreenPresenter = ResearchScreenPresenter(),
) {
    val writesRequested = clearDtcs || resetService
    val writeOptionsReady = writeValidation is ResearchWriteInputValidation.Valid &&
        (!writesRequested || writesAcknowledged)
    val presentation = presenter.present(validation, selectedAdapterName != null, session, writeOptionsReady)
    Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.app_intro),
                style = MaterialTheme.typography.bodyMedium,
            )
            VehicleCard(
                model = model,
                modelYear = modelYear,
                validation = validation,
                enabled = !presentation.running,
                onModelChanged = onModelChanged,
                onYearChanged = onYearChanged,
            )
            AdapterCard(
                selectedAdapterName = selectedAdapterName,
                enabled = !presentation.running,
                onSelectAdapter = onSelectAdapter,
            )
            WriteOptionsCard(
                clearDtcs = clearDtcs,
                resetService = resetService,
                distanceKm = distanceKm,
                nextServiceDate = nextServiceDate,
                acknowledged = writesAcknowledged,
                validation = writeValidation,
                enabled = !presentation.running,
                onClearDtcsChanged = onClearDtcsChanged,
                onResetServiceChanged = onResetServiceChanged,
                onDistanceChanged = onDistanceChanged,
                onDateChanged = onDateChanged,
                onAcknowledgedChanged = onWritesAcknowledgedChanged,
            )
            ScanCard(
                presentation = presentation,
                session = session,
                onStart = onStart,
                onCancel = onCancel,
                onShare = onShare,
            )
            HorizontalDivider()
            Text(
                text = stringResource(R.string.scan_boundary),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.unofficial_notice),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun WriteOptionsCard(
    clearDtcs: Boolean,
    resetService: Boolean,
    distanceKm: String,
    nextServiceDate: String,
    acknowledged: Boolean,
    validation: ResearchWriteInputValidation,
    enabled: Boolean,
    onClearDtcsChanged: (Boolean) -> Unit,
    onResetServiceChanged: (Boolean) -> Unit,
    onDistanceChanged: (String) -> Unit,
    onDateChanged: (String) -> Unit,
    onAcknowledgedChanged: (Boolean) -> Unit,
) {
    val writesRequested = clearDtcs || resetService
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(stringResource(R.string.write_tests_title), fontWeight = FontWeight.SemiBold)
            Text(
                stringResource(R.string.write_tests_intro),
                style = MaterialTheme.typography.bodySmall,
            )
            CheckboxRow(
                checked = resetService,
                enabled = enabled,
                label = stringResource(R.string.reset_service_option),
                onCheckedChange = onResetServiceChanged,
            )
            if (resetService) {
                Text(
                    stringResource(R.string.service_baseline_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = distanceKm,
                    onValueChange = onDistanceChanged,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = enabled,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    label = { Text(stringResource(R.string.service_distance_label)) },
                )
                OutlinedTextField(
                    value = nextServiceDate,
                    onValueChange = onDateChanged,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = enabled,
                    singleLine = true,
                    label = { Text(stringResource(R.string.service_date_label)) },
                )
            }
            CheckboxRow(
                checked = clearDtcs,
                enabled = enabled,
                label = stringResource(R.string.clear_dtcs_option),
                onCheckedChange = onClearDtcsChanged,
            )
            if (writesRequested) {
                Text(
                    stringResource(R.string.write_warning),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
                CheckboxRow(
                    checked = acknowledged,
                    enabled = enabled,
                    label = stringResource(R.string.write_acknowledgement),
                    onCheckedChange = onAcknowledgedChanged,
                )
            }
            if (validation is ResearchWriteInputValidation.Invalid) {
                Text(validation.message, color = MaterialTheme.colorScheme.error)
            } else if (resetService && validation is ResearchWriteInputValidation.Valid) {
                validation.options.serviceReset?.let { request ->
                    Text(
                        stringResource(
                            R.string.service_test_values,
                            request.testDistanceKm,
                            request.testNextServiceDate.toString(),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun CheckboxRow(
    checked: Boolean,
    enabled: Boolean,
    label: String,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Checkbox(checked = checked, enabled = enabled, onCheckedChange = onCheckedChange)
        Text(label, modifier = Modifier.padding(top = 12.dp))
    }
}

@Composable
private fun VehicleCard(
    model: String,
    modelYear: String,
    validation: VehicleInputValidation,
    enabled: Boolean,
    onModelChanged: (String) -> Unit,
    onYearChanged: (String) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.motorcycle_title), fontWeight = FontWeight.SemiBold)
            OutlinedTextField(
                value = model,
                onValueChange = onModelChanged,
                modifier = Modifier.fillMaxWidth(),
                enabled = enabled,
                singleLine = true,
                label = { Text(stringResource(R.string.model_label)) },
            )
            OutlinedTextField(
                value = modelYear,
                onValueChange = onYearChanged,
                modifier = Modifier.fillMaxWidth(),
                enabled = enabled,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                label = { Text(stringResource(R.string.model_year_label)) },
            )
            if ((model.isNotBlank() || modelYear.isNotBlank()) && validation is VehicleInputValidation.Invalid) {
                Text(validation.message, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun AdapterCard(
    selectedAdapterName: String?,
    enabled: Boolean,
    onSelectAdapter: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.adapter_title), fontWeight = FontWeight.SemiBold)
            Text(selectedAdapterName ?: stringResource(R.string.adapter_none))
            OutlinedButton(
                onClick = onSelectAdapter,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.select_or_pair_adapter))
            }
        }
    }
}

@Composable
private fun ScanCard(
    presentation: ResearchScreenPresentation,
    session: ResearchSessionState,
    onStart: () -> Unit,
    onCancel: () -> Unit,
    onShare: (File) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(presentation.statusTitle, fontWeight = FontWeight.SemiBold)
            Text(presentation.statusBody)
            if (presentation.running) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.cancel_scan))
                }
            } else {
                Button(
                    onClick = onStart,
                    enabled = presentation.startEnabled,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.start_scan))
                }
            }
            if (session is ResearchSessionState.Complete) {
                CapabilitySummary(session.summary)
            }
            val report = presentation.reportFile
            if (presentation.canShare && report != null) {
                OutlinedButton(onClick = { onShare(report) }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.share_report))
                }
            }
        }
    }
}

@Composable
private fun CapabilitySummary(summary: ResearchScanSummary) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.evidence_title), fontWeight = FontWeight.SemiBold)
        EvidenceRow(stringResource(R.string.dtc_read_evidence), summary.dtcReadConfirmed)
        Text(stringResource(R.string.dtc_count_value, summary.dtcCount?.toString() ?: "unknown"))
        EvidenceRow(stringResource(R.string.dtc_clear_candidate), summary.dtcClearCandidate)
        EvidenceRow(stringResource(R.string.service_read_evidence), summary.serviceReadConfirmed)
        summary.instrumentStatusAscii?.let {
            Text(stringResource(R.string.instrument_status_value, it))
        }
        summary.odometerKm?.let {
            Text(stringResource(R.string.odometer_value, it))
        }
        EvidenceRow(stringResource(R.string.service_reset_candidate), summary.serviceResetCandidate)
        ValidationRow(stringResource(R.string.service_reset_result), summary.writeValidation.serviceReset)
        ValidationRow(stringResource(R.string.dtc_clear_result), summary.writeValidation.dtcClear)
        Text(
            stringResource(R.string.candidate_warning),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ValidationRow(label: String, result: ResearchOperationValidation) {
    if (result.outcome == ResearchWriteOutcome.NOT_REQUESTED) return
    Text("$label: ${result.outcome.name.lowercase().replace('_', ' ')}")
    if (result.restoreOutcome != ResearchRestoreOutcome.NOT_REQUIRED) {
        Text(
            stringResource(
                R.string.service_restore_result,
                result.restoreOutcome.name.lowercase().replace('_', ' '),
            ),
        )
    }
}

@Composable
private fun EvidenceRow(label: String, confirmed: Boolean) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label)
        Text(if (confirmed) stringResource(R.string.yes) else stringResource(R.string.no))
    }
}
