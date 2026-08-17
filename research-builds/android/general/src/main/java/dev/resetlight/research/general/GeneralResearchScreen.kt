package dev.resetlight.research.general

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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
fun GeneralResearchScreen(
    manufacturer: String,
    model: String,
    modelYear: String,
    validation: GeneralVehicleValidation,
    selectedAdapterName: String?,
    session: GeneralSessionState,
    onManufacturerChanged: (String) -> Unit,
    onModelChanged: (String) -> Unit,
    onYearChanged: (String) -> Unit,
    onSelectAdapter: () -> Unit,
    onStart: () -> Unit,
    onCancel: () -> Unit,
    onShare: (File) -> Unit,
    presenter: GeneralResearchScreenPresenter = GeneralResearchScreenPresenter(),
) {
    val presentation = presenter.present(validation, selectedAdapterName != null, session)
    Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(stringResource(R.string.app_intro), style = MaterialTheme.typography.bodyMedium)
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedTextField(
                        value = manufacturer,
                        onValueChange = onManufacturerChanged,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !presentation.running,
                        singleLine = true,
                        label = { Text(stringResource(R.string.manufacturer_label)) },
                    )
                    OutlinedTextField(
                        value = model,
                        onValueChange = onModelChanged,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !presentation.running,
                        singleLine = true,
                        label = { Text(stringResource(R.string.model_label)) },
                    )
                    OutlinedTextField(
                        value = modelYear,
                        onValueChange = onYearChanged,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !presentation.running,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        label = { Text(stringResource(R.string.model_year_label)) },
                    )
                    if ((manufacturer.isNotBlank() || model.isNotBlank() || modelYear.isNotBlank()) &&
                        validation is GeneralVehicleValidation.Invalid
                    ) {
                        Text(validation.message, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(selectedAdapterName ?: stringResource(R.string.adapter_none))
                    OutlinedButton(
                        onClick = onSelectAdapter,
                        enabled = !presentation.running,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.select_adapter))
                    }
                }
            }
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
                    val report = presentation.reportFile
                    if (presentation.canShare && report != null) {
                        OutlinedButton(onClick = { onShare(report) }, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.share_report))
                        }
                    }
                }
            }
            HorizontalDivider()
            Text(
                stringResource(R.string.scan_boundary),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                stringResource(R.string.unofficial_notice),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
