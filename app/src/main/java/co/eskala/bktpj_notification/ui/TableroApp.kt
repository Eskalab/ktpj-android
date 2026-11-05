package co.eskala.bktpj_notification.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import co.eskala.bktpj_notification.NeonRepository
import co.eskala.bktpj_notification.Prefs
import co.eskala.bktpj_notification.ui.columns.ColumnPickerScreen
import co.eskala.bktpj_notification.ui.columns.ColumnsState
import co.eskala.bktpj_notification.ui.columns.DestinationPicks
import co.eskala.bktpj_notification.ui.onboarding.ConnectionStatus
import co.eskala.bktpj_notification.ui.onboarding.NeonConnectScreen
import co.eskala.bktpj_notification.ui.onboarding.NotificationAccessScreen
import co.eskala.bktpj_notification.ui.theme.ThemeMode
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private enum class Step { CONNECT, COLUMNS, DONE }

/**
 * Flujo de arranque: sin permiso → 01; onboarding sin terminar → 02 → 03; si no → Inicio.
 * [onLegacyConfig] abre la configuración anterior (Ajustes › Avanzado).
 */
@Composable
fun TableroApp(
    hasListenerAccess: Boolean,
    onRecheckAccess: () -> Boolean,
    onOpenAccessSettings: () -> Unit,
    onLegacyConfig: () -> Unit,
    theme: ThemeMode,
    onThemeChange: (ThemeMode) -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var step by rememberSaveable { mutableStateOf(if (Prefs.isOnboarded(ctx)) Step.DONE else Step.CONNECT) }
    var notGrantedYet by rememberSaveable { mutableStateOf(false) }

    // Sin rememberSaveable a propósito: no queremos la contraseña en el estado guardado.
    var connectionString by remember {
        mutableStateOf(if (Prefs.hasConnection(ctx)) Prefs.getNeonConfig(ctx).connectionString else "")
    }
    var status by remember { mutableStateOf<ConnectionStatus>(ConnectionStatus.Idle) }
    var testJob by remember { mutableStateOf<Job?>(null) }

    var columns by remember { mutableStateOf<ColumnsState>(ColumnsState.Loading) }
    val picks = remember { DestinationPicks(Prefs.getColumnInbox(ctx).ifEmpty { null }, Prefs.getColumnHoy(ctx).ifEmpty { null }) }

    fun loadColumns() {
        columns = ColumnsState.Loading
        scope.launch {
            columns = NeonRepository.fetchColumnsWithCounts(ctx).fold(
                onSuccess = { list ->
                    picks.retainOnly(list.map { it.id }.toSet())
                    ColumnsState.Loaded(list)
                },
                onFailure = { ColumnsState.Error(it.message ?: "Error desconocido") },
            )
        }
    }

    fun testConnection() {
        testJob?.cancel()
        val cfg = Prefs.parseConnectionString(connectionString)
        if (cfg == null) {
            status = ConnectionStatus.Error("URL inválida — debe ser postgresql://usuario:contraseña@host/db")
            return
        }
        status = ConnectionStatus.Testing
        testJob = scope.launch {
            status = NeonRepository.checkConnection(cfg).fold(
                onSuccess = { ConnectionStatus.Ok(it.columnCount, it.millis) },
                onFailure = { ConnectionStatus.Error(it.message ?: "Error desconocido") },
            )
        }
    }

    when {
        // Ya configurada: si el permiso se pierde, Inicio lo muestra con el chip "Sin acceso".
        step == Step.DONE -> MainScreen(
            hasListenerAccess = hasListenerAccess,
            onOpenAccessSettings = onOpenAccessSettings,
            theme = theme,
            onThemeChange = onThemeChange,
            onLegacyConfig = onLegacyConfig,
        )

        !hasListenerAccess -> NotificationAccessScreen(
            sourcePackage = Prefs.getNuPackage(ctx),
            notGrantedYet = notGrantedYet,
            onOpenSettings = {
                notGrantedYet = false
                onOpenAccessSettings()
            },
            onAlreadyEnabled = { notGrantedYet = !onRecheckAccess() },
        )

        step == Step.CONNECT -> NeonConnectScreen(
            connectionString = connectionString,
            status = status,
            onConnectionStringChange = {
                connectionString = it
                testJob?.cancel()
                status = ConnectionStatus.Idle
            },
            onTest = ::testConnection,
            onContinue = {
                Prefs.parseConnectionString(connectionString)?.let { Prefs.saveNeonConfig(ctx, it) }
                step = Step.COLUMNS
            },
        )

        step == Step.COLUMNS -> {
            LaunchedEffect(Unit) { loadColumns() }
            ColumnPickerScreen(
                step = 3,
                columns = columns,
                picks = picks,
                confirmLabel = "Empezar a escuchar",
                onRetry = ::loadColumns,
                onConfirm = {
                    Prefs.setColumnInbox(ctx, picks.inbox ?: return@ColumnPickerScreen)
                    Prefs.setColumnHoy(ctx, picks.hoy ?: "")
                    Prefs.setOnboarded(ctx, true)
                    step = Step.DONE
                },
            )
        }
    }
}
