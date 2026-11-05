package co.eskala.bktpj_notification.ui.columns

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.zIndex
import co.eskala.bktpj_notification.NeonRepository.NeonColumn
import co.eskala.bktpj_notification.ui.components.PrimaryButton
import co.eskala.bktpj_notification.ui.components.SecondaryButton
import co.eskala.bktpj_notification.ui.onboarding.StepProgress
import co.eskala.bktpj_notification.ui.theme.KtpjDimens
import co.eskala.bktpj_notification.ui.theme.KtpjShapes
import co.eskala.bktpj_notification.ui.theme.KtpjTheme
import co.eskala.bktpj_notification.ui.theme.ThemeMode
import kotlin.math.roundToInt

enum class Destination(val label: String) { INBOX("Inbox"), HOY("Hoy") }

sealed interface ColumnsState {
    data object Loading : ColumnsState
    data class Error(val message: String) : ColumnsState
    data class Loaded(val columns: List<NeonColumn>) : ColumnsState
}

/** Asignación de columnas a Inbox / Hoy. Una columna no puede estar en las dos zonas. */
@Stable
class DestinationPicks(inbox: String? = null, hoy: String? = null) {
    var inbox by mutableStateOf(inbox)
        private set
    var hoy by mutableStateOf(hoy?.takeIf { it != inbox })
        private set

    fun idFor(dest: Destination) = if (dest == Destination.INBOX) inbox else hoy

    fun isAssigned(columnId: String) = columnId == inbox || columnId == hoy

    fun firstEmpty(): Destination? = Destination.entries.firstOrNull { idFor(it) == null }

    // Hoy es opcional: sin Hoy, cada movimiento crea una sola tarjeta en Inbox.
    val complete get() = inbox != null

    fun assign(dest: Destination, columnId: String?) {
        if (columnId != null) {
            if (inbox == columnId) inbox = null
            if (hoy == columnId) hoy = null
        }
        if (dest == Destination.INBOX) inbox = columnId else hoy = columnId
    }

    /** Descarta IDs que ya no existen en Neon (p. ej. columnas borradas). */
    fun retainOnly(ids: Set<String>) {
        if (inbox != null && inbox !in ids) inbox = null
        if (hoy != null && hoy !in ids) hoy = null
    }
}

/**
 * 03 Selector de columnas — paso 3 del onboarding ([step] = 3) y Ajustes › Destinos ([step] = null).
 * Arrastrar (long press) una columna a una zona la asigna; como alternativa accesible, tocar una
 * columna la asigna a la primera zona vacía y tocar una zona la vacía.
 */
@Composable
fun ColumnPickerScreen(
    step: Int?,
    columns: ColumnsState,
    picks: DestinationPicks,
    confirmLabel: String,
    onRetry: () -> Unit,
    onConfirm: () -> Unit,
) {
    val c = KtpjTheme.colors
    val t = KtpjTheme.type
    val density = LocalDensity.current

    // Estado del arrastre: todo en coordenadas del Box raíz.
    var root by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val rowBounds = remember { mutableStateMapOf<String, Rect>() }
    val zoneBounds = remember { mutableStateMapOf<Destination, Rect>() }
    var dragging by remember { mutableStateOf<NeonColumn?>(null) }
    var dragStart by remember { mutableStateOf(Offset.Zero) }   // pointer al empezar
    var dragDelta by remember { mutableStateOf(Offset.Zero) }

    fun boundsOf(coords: LayoutCoordinates): Rect? {
        val r = root?.takeIf { it.isAttached } ?: return null
        return r.localBoundingBoxOf(coords, clipBounds = false)
    }
    fun zoneUnderPointer(): Destination? {
        val pointer = dragStart + dragDelta
        return zoneBounds.entries.firstOrNull { it.value.contains(pointer) }?.key
    }
    val hoverZone = if (dragging == null) null else zoneUnderPointer()

    Box(
        Modifier
            .fillMaxSize()
            .background(c.bg)
            .onGloballyPositioned { root = it },
    ) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            Column(
                Modifier.padding(start = KtpjDimens.screenPadding, end = KtpjDimens.screenPadding, top = 16.dp),
                verticalArrangement = Arrangement.spacedBy(KtpjDimens.blockGapSmall),
            ) {
                if (step != null) StepProgress(step)
                Text("Arrastra a Inbox y Hoy", style = t.screenTitle, color = c.fg)
                Text(
                    "Hoy es opcional: si lo dejas vacío, cada movimiento crea una sola tarjeta en Inbox.",
                    style = t.secondary,
                    color = c.muted,
                )
                Row(
                    Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(KtpjDimens.cardGap),
                ) {
                    Destination.entries.forEach { dest ->
                        val assigned = picks.idFor(dest)?.let { id ->
                            (columns as? ColumnsState.Loaded)?.columns?.firstOrNull { it.id == id }
                        }
                        DropZone(
                            dest = dest,
                            column = assigned,
                            hovered = hoverZone == dest,
                            onClear = { picks.assign(dest, null) },
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .onGloballyPositioned { coords -> boundsOf(coords)?.let { zoneBounds[dest] = it } },
                        )
                    }
                }
                Text(
                    "COLUMNAS EN NEON",
                    style = t.captionStrong.copy(letterSpacing = 0.04.em),
                    color = c.muted,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (columns) {
                    ColumnsState.Loading -> CircularProgressIndicator(
                        Modifier.align(Alignment.Center).size(32.dp),
                        color = c.fg,
                        strokeWidth = 3.dp,
                    )

                    is ColumnsState.Error -> Column(
                        Modifier.align(Alignment.Center).padding(KtpjDimens.screenPadding),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(KtpjDimens.cardGapSmall),
                    ) {
                        Text("No se pudieron leer las columnas", style = t.rowTitle, color = c.accent)
                        Text(columns.message, style = t.caption, color = c.muted)
                        SecondaryButton("Reintentar", onClick = onRetry)
                    }

                    is ColumnsState.Loaded -> LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            horizontal = KtpjDimens.screenPadding,
                            vertical = KtpjDimens.cardGapSmall,
                        ),
                        verticalArrangement = Arrangement.spacedBy(KtpjDimens.cardGapSmall),
                        userScrollEnabled = dragging == null,
                    ) {
                        val byBoard = columns.columns.groupBy { it.board }
                        byBoard.forEach { (board, cols) ->
                            // Hay títulos repetidos entre tableros ("Por hacer"), así que agrupamos.
                            if (byBoard.size > 1) {
                                item(key = "board:$board") {
                                    Text(board, style = t.captionStrong, color = c.muted, modifier = Modifier.padding(top = 6.dp))
                                }
                            }
                            items(cols, key = { it.id }) { col ->
                                val assigned = picks.isAssigned(col.id)
                                ColumnRow(
                                    column = col,
                                    dimmed = assigned || dragging?.id == col.id,
                                    draggable = !assigned,
                                    tapTarget = if (assigned || dragging != null) null else picks.firstEmpty(),
                                    onAssign = { dest -> picks.assign(dest, col.id) },
                                    onDragStart = { local ->
                                        rowBounds[col.id]?.let { b ->
                                            dragging = col
                                            dragStart = b.topLeft + local
                                            dragDelta = Offset.Zero
                                        }
                                    },
                                    onDrag = { dragDelta += it },
                                    onDragEnd = {
                                        zoneUnderPointer()?.let { picks.assign(it, col.id) }
                                        dragging = null
                                    },
                                    modifier = Modifier.onGloballyPositioned { coords ->
                                        boundsOf(coords)?.let { rowBounds[col.id] = it }
                                    },
                                )
                            }
                        }
                    }
                }
            }

            Column(Modifier.padding(start = KtpjDimens.screenPadding, end = KtpjDimens.screenPadding, top = 14.dp, bottom = 22.dp)) {
                PrimaryButton(confirmLabel, onClick = onConfirm, enabled = picks.complete)
            }
        }

        // Fila arrastrada: overlay sobre toda la pantalla para que el scroll de la lista no la recorte.
        val col = dragging
        val start = col?.let { rowBounds[it.id] }
        if (col != null && start != null) {
            val topLeft = start.topLeft + dragDelta
            ColumnRowContent(
                column = col,
                background = c.invBg,
                content = c.invFg,
                modifier = Modifier
                    .zIndex(1f)
                    .offset { IntOffset(topLeft.x.roundToInt(), topLeft.y.roundToInt()) }
                    .width(with(density) { start.width.toDp() })
                    .graphicsLayer {
                        rotationZ = -2f
                        translationX = 8.dp.toPx()
                        shadowElevation = 12.dp.toPx()
                        shape = KtpjShapes.row
                        clip = true
                        ambientShadowColor = Color.Black.copy(alpha = 0.25f)
                        spotShadowColor = Color.Black.copy(alpha = 0.25f)
                    },
            )
        }
    }
}

/** Zona destino: asignada = fondo accent; vacía = borde punteado 2dp fg con "Suelta aquí". */
@Composable
private fun DropZone(
    dest: Destination,
    column: NeonColumn?,
    hovered: Boolean,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = KtpjTheme.colors
    val t = KtpjTheme.type
    val filled = column != null
    Column(
        modifier
            .heightIn(min = KtpjDimens.dropZoneMinHeight)
            .clip(KtpjShapes.card)
            .then(
                when {
                    filled -> Modifier.background(c.accent)
                    hovered -> Modifier.background(c.card).dashedBorder(c.fg)
                    else -> Modifier.dashedBorder(c.fg)
                },
            )
            .clickable(enabled = filled, onClickLabel = "Quitar ${column?.title} de ${dest.label}", onClick = onClear)
            .padding(KtpjDimens.cardPadding),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        val fg = if (filled) c.onAccent else c.fg
        Text("DESTINO ${dest.label.uppercase()}", style = t.overline, color = fg)
        if (column != null) {
            Column {
                Text(column.title, style = t.zoneTitle, color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(column.board, style = t.caption, color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        } else {
            Text("Suelta aquí", style = t.buttonSecondary.copy(fontWeight = FontWeight.SemiBold), color = c.muted)
        }
    }
}

@Composable
private fun ColumnRow(
    column: NeonColumn,
    dimmed: Boolean,
    draggable: Boolean,
    tapTarget: Destination?,
    onAssign: (Destination) -> Unit,
    onDragStart: (Offset) -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = KtpjTheme.colors
    // El gesto vive más que una recomposición: leemos siempre los callbacks más recientes.
    val start by rememberUpdatedState(onDragStart)
    val move by rememberUpdatedState(onDrag)
    val end by rememberUpdatedState(onDragEnd)
    ColumnRowContent(
        column = column,
        background = c.card,
        content = c.fg,
        modifier = modifier
            .alpha(if (dimmed) 0.45f else 1f)
            .semantics {
                customActions = Destination.entries.map { dest ->
                    CustomAccessibilityAction("Asignar a ${dest.label}") { onAssign(dest); true }
                }
            }
            .pointerInput(column.id, draggable) {
                if (!draggable) return@pointerInput
                detectDragGesturesAfterLongPress(
                    onDragStart = { start(it) },
                    onDragEnd = { end() },
                    onDragCancel = { end() },
                    onDrag = { change, amount ->
                        change.consume()
                        move(amount)
                    },
                )
            }
            .clickable(
                enabled = tapTarget != null,
                onClickLabel = tapTarget?.let { "Asignar a ${it.label}" },
            ) { tapTarget?.let(onAssign) },
    )
}

@Composable
private fun ColumnRowContent(column: NeonColumn, background: Color, content: Color, modifier: Modifier = Modifier) {
    val t = KtpjTheme.type
    Row(
        modifier
            .fillMaxWidth()
            .clip(KtpjShapes.row)
            .background(background)
            .padding(KtpjDimens.cardPadding),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            column.title,
            style = t.rowTitle,
            color = content,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        Text("${column.cardCount}", style = t.captionSemi, color = content, modifier = Modifier.padding(start = 12.dp))
    }
}

private fun Modifier.dashedBorder(color: Color) = drawBehind {
    val w = KtpjDimens.borderField.toPx()
    drawRoundRect(
        color = color,
        topLeft = Offset(w / 2, w / 2),
        size = size.copy(width = size.width - w, height = size.height - w),
        cornerRadius = CornerRadius(18.dp.toPx() - w / 2),
        style = Stroke(width = w, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))),
    )
}

private val SAMPLE_COLUMNS = ColumnsState.Loaded(
    listOf(
        NeonColumn("1", "Inbox", "Nu Transacciones", 14),
        NeonColumn("2", "Hoy", "Nu Transacciones", 6),
        NeonColumn("3", "Esta semana", "Nu Transacciones", 9),
        NeonColumn("4", "En curso", "Nu Transacciones", 3),
        NeonColumn("5", "Hecho", "Nu Transacciones", 52),
    ),
)

@Preview(name = "03 Columnas — Claro", widthDp = 360, heightDp = 760)
@Composable
private fun ColumnPickerLightPreview() = KtpjTheme(ThemeMode.LIGHT) {
    ColumnPickerScreen(3, SAMPLE_COLUMNS, remember { DestinationPicks(inbox = "1") }, "Empezar a escuchar", {}, {})
}

@Preview(name = "03 Columnas — Oscuro", widthDp = 360, heightDp = 760)
@Composable
private fun ColumnPickerDarkPreview() = KtpjTheme(ThemeMode.DARK) {
    ColumnPickerScreen(3, SAMPLE_COLUMNS, remember { DestinationPicks(inbox = "1") }, "Empezar a escuchar", {}, {})
}
