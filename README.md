# Spur

App Android personal (antes **Nu KTPJ**) que hace dos cosas:

1. **Captura gastos sola** — lee las notificaciones de Nu Colombia y los SMS de Davivienda y los guarda como
   tarjetas y transacciones en KTPJ (Neon / Postgres).
2. **Lleva tus tareas** — tareas con fecha, ★ prioridad, épicas (pausables) e IDs tipo Jira, con pomodoro integrado, y **hábitos** con racha; las tareas también se pueden crear
   desde Claude Code con el skill `/spur`.

Paquete: `co.eskala.bktpj_notification` · Kotlin + Jetpack Compose (Material3) · minSdk 26 · targetSdk 34.

---

## Funcionalidades

### Captura de gastos

| Función | Detalle |
|---|---|
| **Notificaciones de Nu** | `NotificationListenerService` lee cada notificación de Nu y crea una tarjeta en **Inbox** (y en **Hoy** si está configurado) más una fila en `transactions`. |
| **SMS de Davivienda** | `SmsReceiver` copia los SMS del remitente **87188** sin borrarlos ni ocultarlos. Compra aprobada → *TC Crédito NNNN*; descuentos, PSE, llave, retiros → *Ahorros NNNN*; depósitos → abono sin transacción. Ignora declinadas, reversadas, códigos y traslados internos (bolsillo, pago de TC, avances) para no contar doble. |
| **Gasto manual** | Formulario con monto, comercio y tipo (cargo / abono). |
| **Atajos** | Botones de gasto rápido (ej. *Peaje Andes*) que se crean y editan; un toque registra el gasto. |
| **Historial con reintento** | Cada captura queda en el celular. Si falla el guardado: notificación con **Reintentar** / **Descartar**. |
| **Gasto fijo** | Casilla **☐ Fijo** en cada movimiento. Se recuerda **por comercio** (arriendo, administración, servicios) y aplica a todos sus movimientos. Sirve para gastos e ingresos fijos. |
| **Balance del mes** | Página propia: cifra héroe con lo gastado en **variables**, barra de reparto variables / fijos, tres cifras que nunca se suman entre sí (variables · fijos · ingresos), top 5 por comercio y el neto pequeño al final. Navegación por meses. |
| **Totales honestos** | El total del día (Inicio y Gastos) muestra **solo gastos variables**: un arriendo pagado no lo infla y un ingreso no lo compensa. |

### Tareas

| Función | Detalle |
|---|---|
| **Crear tareas** | Desde la app (**+ Tarea** en Inicio, con fecha *Hoy*; **+ Nueva tarea** en Tareas) o desde Claude Code con `/spur`. |
| **Fecha** | Sin fecha · Hoy · Mañana · Otra… (calendario). Se muestra como *Hoy*, *Mañana* o *5 nov*; tocarla la cambia. Marca *atrasada*. |
| **★ Prioritaria** | Fondo cálido con franja naranja. Recordatorio **cada hora, de 6:00 a.m. a 8:00 p.m., solo el día de su fecha o si está atrasada**, hasta marcarla Hecha. |
| **En curso** | **▶ Empezar** la marca en curso (franja y fondo azul) y abre su **pomodoro**; tocar **● En curso** la devuelve a pendiente y cierra el pomodoro. |
| **Pomodoro** | Pantalla solo con el tiempo, a color completo: **Enfoque** (naranja) · **Descanso** (verde) · **Descanso largo** (azul). Botón Empezar / Pausar / Seguir, **⏭** saltar, 4 puntos del ciclo y **✓ Terminé la tarea**. Sigue corriendo con la app cerrada: notificación fija con cuenta atrás (Pausar / Seguir), alarma exacta con sonido al terminar, barra fina sobre las pestañas para volver. La pantalla no se apaga mientras está abierta; se rearma tras reiniciar. Los descansos no arrancan solos. |
| **Pomodoros estimados** | Campo − / + al crear o editar (🍅🍅🍅, con el tiempo total de enfoque). La tarjeta muestra **🍅 hechos/estimados** (verde al cumplir) y el pomodoro dice *"Pomodoro 2 de 3"*. |
| **Editar** | Tocar la tarea abre el formulario precargado: título, detalle, fecha, ★, épica, app y pomodoros estimados. |
| **¿Qué sigue?** | Al completar una tarea se abre un diálogo para crear el siguiente paso del hilo, con la épica heredada. |
| **App asociada** | Se elige una app instalada y la tarea muestra **↗ Nombre** para abrirla. |
| **Épicas** | Agrupan tareas (ej. *Revalidar pasaporte*). Se eligen con un **buscador** (por nombre sin tildes o por prefijo; activas primero, pausadas con ⏸), no con una lista. Si no existe, **+ Crear "…"** la crea desde ahí con nombre y **prefijo** sugerido de 3 letras (editable, único). Pantalla propia con avance y secciones **En curso** (azul) · **Pendientes** · **Hechas** (verde, tachadas). Muestra también las tarjetas de la web de KTPJ, solo lectura. |
| **Pausar épica** | **⏸ Pausar** en la pantalla de la épica: sus tareas salen de Hoy, Anteriores, Próximos días, Inicio y del resumen diario, y las ★ dejan de recordar (siguen visibles dentro de la épica; en la lista, *"⏸ En pausa"*). **▶ Reanudar** pide desde qué fecha retomas: la pendiente más próxima pasa a esa fecha y las demás se corren los mismos días (*Quitar fecha* = reanudar sin mover fechas). En la web sigue como Activa. |
| **IDs tipo Jira** | Cada tarea tiene ID: `PREFIJO-n` de su épica (`PAS-3`) o `SP-n` sin épica. Los números no se reutilizan; cambiar de épica cambia el ID. |
| **Pestaña Tareas (acordeón)** | **Hoy** abierta (★ primero) · **Anteriores** cerrada · **Terminadas hoy** abierta y tachadas · **Próximos días** cerrada · **Sin fecha** cerrada · **Épicas** cerrada con avance. |
| **Inicio** | Solo las tareas **de hoy**, ★ primero. |
| **Terminada el** | La base guarda `done_at` (trigger) sin importar desde dónde se marque: app, web o `/spur`. |

### Hábitos

| Función | Detalle |
|---|---|
| **Crear** | En **+ Nueva tarea**, selector **Tarea \| ↻ Hábito** (también **+ Hábito** en Tareas). Qué hábito, **qué días** (Todos · Entre semana · L M X J V S D), **meta del día** opcional con unidad (páginas, min, km, vasos, veces o libre), **aviso** opcional (7 a.m., 12 p.m., 6 p.m., 9 p.m. u otra hora), épica y app. |
| **No se termina** | Es una sola tarjeta que se repite; cada día se marca. En la web se ve como *Diaria · LMXJV*. |
| **Marcar** | Círculo en la fila. Con meta y unidad pregunta **¿Cuánto hiciste?** (meta ya puesta y atajos ½ · meta · 1½ · doble). Queda tachado con ✓ verde hasta mañana; tocar de nuevo lo desmarca. |
| **Racha 🔥** | Días seguidos cumplidos. Los días que no tocan no la rompen, ni hoy mientras no termine. |
| **Inicio y Tareas** | Inicio: **HÁBITOS · 1/2** con los que tocan hoy, pendientes primero. Tareas: sección **HÁBITOS** abierta; los que no tocan hoy salen tenues con *"hoy no toca"*. |
| **Pantalla del hábito** | Racha actual, mejor racha y % del mes; **cuadrícula de 15 semanas** (verde hecho, borde tocaba y no se hizo, gris no tocaba) donde **tocar un día pasado lo marca**; últimos días con cantidad y total; **Editar** y **Dejar este hábito** (se archiva, lo registrado queda). |
| **Aviso** | Los días que toca, desde la hora elegida, **una vez** y solo si no se ha marcado: *"↻ Leer · 10 páginas — Llevas 🔥 6 días"*, con **✓ Hecho** (registra la meta). |
| **Épica en pausa** | Sus hábitos también salen de Inicio y Tareas y no avisan. |

### Resumen diario

- **7:00 a.m. — Tu día** y **9:00 p.m. — Cierre del día**. La notificación solo avisa; al tocarla se abre una
  pantalla con fecha, saludo, **frase motivadora** (rotan por día), números o barra de progreso, la lista y una
  sola acción (**Empezar el día** / **Pasar a mañana** / **Buenas noches**).
- Aviso de una línea en Inicio solo dentro de la ventana (hasta 11:59 a.m. / 11:59 p.m.), que desaparece al abrirlo.
- `AlarmManager` inexacta permitida en reposo; el worker de 15 min hace de respaldo; se rearma tras reiniciar.

### Ajustes

- **Pomodoro:** enfoque (25 min), descanso (5), descanso largo (15) y cada cuántos enfoques toca el largo (4), con − / +. Se aplica al siguiente pomodoro.
- Base de datos, destinos, fuente de Nu, acceso a notificaciones y tema.

### Notificaciones

| Canal | Qué avisa |
|---|---|
| `nu_ktpj_actions` | Tarjeta creada · No se pudo guardar (Reintentar / Descartar) |
| `spur_tasks` | Nueva tarea (una vez, si llega desde fuera del celular) · ★ Pendiente cada hora (acción **Hecha**) |
| `spur_daily` | Resumen de la mañana y de la noche |
| `spur_pomodoro_run` | Pomodoro en curso: cuenta atrás fija, silenciosa (Pausar / Seguir) |
| `spur_habits` | Hábito pendiente a su hora (acción **✓ Hecho**) |
| `spur_pomodoro_end` | Pomodoro o descanso terminado, con sonido (Empezar el siguiente) |

### General

- Rediseño "Tablero": pestañas **Inicio · Tareas · Gastos · Ajustes**, tema claro / oscuro / sistema,
  tipografías Bricolage Grotesque e IBM Plex Mono.
- Onboarding: acceso a notificaciones, conexión a Neon y columnas destino.

---

## Skill `/spur` (Claude Code)

`~/.claude/skills/spur/spur.py` — crea y consulta tareas desde cualquier sesión de Claude.

```bash
spur.py add "Llamar al técnico" [--desc ...] [--due YYYY-MM-DD] [--prioritaria] [--epica LAV]
spur.py list                    # pendientes con ID
spur.py epica PAS               # tareas de una épica con avance
spur.py ver PAS-2 PAS-5         # detalle por ID
spur.py hecha PAS-2             # marcar hecha por ID
spur.py prioridad "texto" [--quitar]
```

---

## Datos (Neon, compartidos con la web de KTPJ)

| Tabla | Uso en Spur |
|---|---|
| `boards` / `columns` | Tablero **Spur** (`spur_board`, columna `spur_col_todo`, prefijo `SP`) y tablero **Nu Transacciones** (Inbox / Hoy). |
| `cards` | Tareas (`source_type` `spur` / `claude`) y tarjetas de gastos. Campos usados: `key`, `due_date`, `priority` (HIGH = ★), `started_at` (en curso), `done`, `done_at`, `project_id` (épica), `labels` (`app:<paquete>`, `pomos:N` = pomodoros estimados). |
| `projects` | **Épicas**: `title`, `color`, `prefix`, `key_seq` (contador de IDs), `paused_at` (en pausa). En la web se llaman Proyectos. |
| `habit_checks` | Hábitos: un check por día cumplido (`card_id`, `day`, `amount`). El hábito es una tarjeta de Spur con `recurrence` DAILY, `recurrence_days` (0 = domingo; vacío = todos) y etiquetas `habit_goal:N`, `habit_unit:…`, `habit_at:H`. |
| `transactions` | Una fila por movimiento capturado (monto, cuenta, banco, last4). |

Migraciones (repo `ktpj`): `20261104000000_add_card_done_at`, `20261106000000_epic_keys_and_started_at`, `20261107000000_project_paused_at`, `20261108000000_habit_checks`.

Solo en el celular: historial de capturas (`captures.json`), atajos (`shortcuts.json`), comercios fijos, estado
de recordatorios / resúmenes, pomodoro en curso, sus ajustes y **pomodoros hechos por tarea** (SharedPreferences).

---

## Compilar e instalar

1. `local.properties` (no se versiona) con `sdk.dir` y la conexión: `neon.host`, `neon.user`, `neon.pass`, `neon.db`.
2. `./gradlew testDebugUnitTest assembleDebug`
3. Instalar `app/build/outputs/apk/debug/app-debug.apk` en el celular.
4. Dar permisos: acceso a notificaciones (Nu), notificaciones (Android 13+) y SMS (Davivienda). Las alarmas exactas del
   pomodoro (`USE_EXACT_ALARM`) se conceden al instalar.

## Estructura

```
app/src/main/java/co/eskala/bktpj_notification/
├── NuNotificationService.kt · NuParser.kt        captura de Nu
├── SmsReceiver.kt · DaviviendaSms.kt             captura de Davivienda
├── CardSaver.kt · CaptureLog.kt · Shortcuts.kt   guardado, historial, atajos
├── NeonRepository.kt                             SQL por HTTP a Neon
├── SpurTasks.kt · SpurNotifications.kt           tareas, sync, recordatorios
├── DailySummary.kt                               resumen 7:00 / 21:00
├── Habits.kt                                     hábitos: racha, checks, aviso
├── Pomodoro.kt                                   temporizador, alarma, notificación fija, ajustes
├── Balance.kt                                    fijos y balance del mes
└── ui/  home · tasks (Tasks, NewTask, NextTask, Epic, Pomodoro, Habit, NewHabit) · gastos · history · balance · summary · settings · theme
```

## Limitaciones conocidas

- El **Balance** usa el historial del celular (las transacciones en la base no traen tipo ni comercio de Nu).
- El lector de Nu guarda como "desconocido" avisos que no son movimientos (compras rechazadas, solicitudes de
  pago, recordatorios), y crean transacción. Pendiente de corregir y limpiar.
- Épicas creadas en la web no tienen prefijo: sus tareas usan `SP-n`.
- Los pomodoros hechos se cuentan solo en el celular (no se suben a la base).
- `/spur` todavía no pone pomodoros estimados ni crea hábitos.
- El resumen de la noche aún no menciona los hábitos; tampoco hay avance de libro (total de páginas).
