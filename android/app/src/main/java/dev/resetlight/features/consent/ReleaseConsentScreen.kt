package dev.resetlight.features.consent

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.resetlight.R

@Composable
fun ReleaseConsentScreen(
    onAccept: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var consentAccepted by rememberSaveable { mutableStateOf(false) }

    Scaffold(modifier = modifier.fillMaxSize()) { contentPadding ->
        Column(
            modifier = Modifier
                .padding(contentPadding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.consent_title),
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(stringResource(R.string.consent_intro))
            ConsentPoint(R.string.consent_unofficial)
            ConsentPoint(R.string.consent_no_warranty)
            ConsentPoint(R.string.consent_reset_not_maintenance)
            ConsentPoint(R.string.consent_clear_not_repair)
            ConsentPoint(R.string.consent_owner_responsibility)
            Text(
                text = stringResource(R.string.consent_right_to_repair),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(
                onClick = { consentAccepted = !consentAccepted },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Checkbox(
                    checked = consentAccepted,
                    onCheckedChange = { consentAccepted = it },
                )
                Text(stringResource(R.string.consent_checkbox))
            }
            Button(
                onClick = onAccept,
                enabled = consentAccepted,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.consent_continue))
            }
        }
    }
}

@Composable
private fun ConsentPoint(resourceId: Int) {
    Text("• ${stringResource(resourceId)}")
}
