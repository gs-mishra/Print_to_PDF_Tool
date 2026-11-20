package com.gsm.savetopdf.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gsm.savetopdf.R
import com.gsm.savetopdf.print.MediaFormat

@Composable
fun SaveAsPdfScreen(
    viewModel: MainViewModel,
    onOpenPrintSettings: () -> Unit,
    onPrintTestSheet: () -> Unit,
    onResumePendingSave: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.snackbarMessages.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    SaveAsPdfContent(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onOpenPrintSettings = onOpenPrintSettings,
        onPrintTestSheet = onPrintTestSheet,
        onResumePendingSave = onResumePendingSave,
        onToggleAskEveryTime = viewModel::setAskLocationEveryTime,
        onSelectMediaFormat = viewModel::selectDefaultMediaFormat,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SaveAsPdfContent(
    uiState: MainUiState,
    snackbarHostState: SnackbarHostState,
    onOpenPrintSettings: () -> Unit,
    onPrintTestSheet: () -> Unit,
    onResumePendingSave: () -> Unit,
    onToggleAskEveryTime: (Boolean) -> Unit,
    onSelectMediaFormat: (MediaFormat) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showPaperPicker by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                    ),
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        },
        bottomBar = {
            Text(
                text = stringResource(R.string.footer_developed_by),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(vertical = 14.dp)
                    .testTag("footer_developed_by"),
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 600.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                PendingSaveStrip(
                    pendingCount = uiState.pendingSaveCount,
                    onResumePendingSave = onResumePendingSave,
                )

                ActionButtonsRow(
                    onOpenPrintSettings = onOpenPrintSettings,
                    onPrintTestSheet = onPrintTestSheet,
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                Text(
                    text = stringResource(R.string.section_preferences),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 4.dp),
                )

                SwitchPreferenceRow(
                    iconRes = R.drawable.ic_folder_save,
                    title = stringResource(R.string.ask_location_title),
                    subtitle = if (uiState.askLocationEveryTime) {
                        stringResource(R.string.ask_location_desc_on)
                    } else {
                        stringResource(R.string.ask_location_desc_off)
                    },
                    checked = uiState.askLocationEveryTime,
                    onCheckedChange = onToggleAskEveryTime,
                    switchTestTag = "ask_save_location_switch",
                )

                ActionPreferenceRow(
                    iconRes = R.drawable.ic_paper_size,
                    title = stringResource(R.string.default_paper_title),
                    subtitle = "${uiState.defaultMediaFormat.displayName} · ${uiState.defaultMediaFormat.dimensionsLabel}",
                    onClick = { showPaperPicker = true },
                    rowTestTag = "paper_size_preference_row",
                )
            }
        }
    }

    if (showPaperPicker) {
        PaperSizePickerDialog(
            selectedFormat = uiState.defaultMediaFormat,
            onSelectFormat = onSelectMediaFormat,
            onDismiss = { showPaperPicker = false },
        )
    }
}
