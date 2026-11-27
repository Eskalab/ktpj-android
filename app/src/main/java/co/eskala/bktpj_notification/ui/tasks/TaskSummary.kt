package co.eskala.bktpj_notification.ui.tasks

import co.eskala.bktpj_notification.TaskProject

/**
 * Resumen de una línea de lo que se va a guardar (KTP-11 opción B), p. ej. "Hoy · ★ · LAV · 🍅2 · ↗ Waze".
 * [due] es la etiqueta ya calculada ("Hoy", "5 nov") o null si no tiene fecha.
 */
fun taskSummaryLine(due: String?, priority: Boolean, epic: String?, pomos: Int, app: String?): String =
    buildList {
        add(due ?: "Sin fecha")
        if (priority) add("★")
        epic?.takeIf { it.isNotBlank() }?.let { add(it) }
        if (pomos > 0) add("🍅$pomos")
        app?.takeIf { it.isNotBlank() }?.let { add("↗ $it") }
    }.joinToString(" · ")

/** Lo mismo en palabras, para TalkBack. */
fun taskSummarySpoken(due: String?, priority: Boolean, epic: String?, pomos: Int, app: String?): String =
    "Se guardará: " + buildList {
        add(due ?: "sin fecha")
        add(if (priority) "prioritaria" else "sin prioridad")
        epic?.takeIf { it.isNotBlank() }?.let { add("épica $it") }
        if (pomos > 0) add(if (pomos == 1) "1 pomodoro" else "$pomos pomodoros")
        app?.takeIf { it.isNotBlank() }?.let { add("abre $it") }
    }.joinToString(", ")

/** Nombre corto de la épica para el resumen: su prefijo (LAV) o, si no tiene, el título. */
fun epicShortName(project: TaskProject?): String? =
    project?.let { p -> p.prefix?.takeIf { it.isNotBlank() } ?: p.title.takeIf { it.isNotBlank() } }
