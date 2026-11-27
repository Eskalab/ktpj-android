package co.eskala.bktpj_notification.ui.tasks

import co.eskala.bktpj_notification.TaskProject

/** Opciones de la tarea que se muestran como chips compactos (KTP-11 opción C). */
enum class TaskOption { FECHA, PRIORIDAD, EPICA, POMOS, APP, DETALLE }

/** Texto del chip, su lectura para TalkBack y si la opción tiene valor (chip relleno). */
data class OptionChipText(val label: String, val spoken: String, val set: Boolean)

/**
 * Chips en orden: fecha, ★, épica, 🍅, app, detalle. [due] es la etiqueta ya calculada ("Hoy", "5 nov"),
 * [epic] y [app] los nombres cortos a mostrar.
 */
fun taskOptionChips(
    due: String?,
    priority: Boolean,
    epic: String?,
    pomos: Int,
    app: String?,
    detail: String,
): List<Pair<TaskOption, OptionChipText>> {
    val epicName = epic?.takeIf { it.isNotBlank() }
    val appName = app?.takeIf { it.isNotBlank() }
    return listOf(
        TaskOption.FECHA to OptionChipText("📅 " + (due ?: "Sin fecha"), "Fecha: " + (due ?: "sin fecha"), due != null),
        TaskOption.PRIORIDAD to OptionChipText(
            if (priority) "★ Prioritaria" else "☆ Prioritaria",
            if (priority) "Prioritaria: sí" else "Prioritaria: no",
            priority,
        ),
        TaskOption.EPICA to OptionChipText(
            epicName?.let { "◆ " + shorten(it) } ?: "+ Épica",
            "Épica: " + (epicName ?: "sin épica"),
            epicName != null,
        ),
        TaskOption.POMOS to OptionChipText(
            if (pomos > 0) "🍅 $pomos" else "+ 🍅",
            "Pomodoros estimados: " + if (pomos > 0) "$pomos" else "sin estimar",
            pomos > 0,
        ),
        TaskOption.APP to OptionChipText(
            appName?.let { "↗ " + shorten(it) } ?: "+ App",
            "App: " + (appName ?: "ninguna"),
            appName != null,
        ),
        TaskOption.DETALLE to OptionChipText(
            if (detail.isBlank()) "+ Detalle" else "✎ Detalle",
            "Detalle: " + if (detail.isBlank()) "vacío" else "escrito",
            detail.isNotBlank(),
        ),
    )
}

/** Recorta nombres largos para que el chip no ocupe toda la fila. */
fun shorten(text: String, max: Int = 16): String =
    text.trim().let { if (it.length <= max) it else it.take(max - 1).trimEnd() + "…" }

/** Nombre corto de la épica: su prefijo (LAV) o, si no tiene, el título. */
fun epicShortName(project: TaskProject?): String? =
    project?.let { p -> p.prefix?.takeIf { it.isNotBlank() } ?: p.title.takeIf { it.isNotBlank() } }
