# Regresión manual — lo que solo se prueba en el teléfono

Las pruebas automáticas (`qa/regresion.sh`) cubren la lógica. Esto cubre lo que depende de Android:
notificaciones, alarmas, permisos, pantallas y la base real.

No hace falta hacerlo todo cada vez: **revisa solo las áreas que tocó el cambio** (tabla de abajo).
Antes de una versión grande, hazlo completo.

## Qué revisar según lo que cambió

| Si cambió… | Revisa las secciones |
|---|---|
| `Pomodoro.kt`, `PomodoroScreen.kt`, `Pomos.kt` | A, B |
| `Habits.kt`, `HabitScreen.kt`, `NewHabitScreen.kt`, `ui/components/Habits.kt` | C, B |
| `SpurTasks.kt`, `Tasks.kt`, `TasksScreen.kt`, `NewTaskScreen.kt`, `NextTaskDialog.kt` | D, E |
| `Projects.kt`, `EpicScreen.kt` | E, D |
| `DailySummary.kt`, `DaySummaryScreen.kt` | F |
| `NuNotificationService.kt`, `NuParser.kt`, `SmsReceiver.kt`, `DaviviendaSms.kt`, `CardSaver.kt` | G |
| `Balance.kt`, `Money.kt`, `BalanceScreen.kt`, `GastosScreen.kt` | H |
| `NeonRepository.kt` | D, C, G y H (toca todo lo que lee o guarda) |
| `MainScreen.kt`, `TableroApp.kt`, `ui/theme/**` | Smoke completo + I |
| `AndroidManifest.xml`, permisos, `BootReceiver` | A, F, G y reiniciar el teléfono |

## A · Pomodoro en segundo plano
- [ ] Empezar, salir de la app y apagar la pantalla 2 min → al volver el tiempo es correcto.
- [ ] Dejar terminar un enfoque (pon 1 min en Ajustes) con la pantalla apagada → suena y avisa.
- [ ] Desde la notificación: Pausar y Seguir funcionan sin abrir la app.
- [ ] Reiniciar el teléfono con un pomodoro corriendo → sigue (o queda pausado) con el tiempo bien.
- [ ] Después de 4 enfoques toca el descanso largo; los descansos no arrancan solos.
- [ ] Cambiar las duraciones en Ajustes → aplica al siguiente pomodoro, no al que corre.

## B · Pomodoro de tarea y de hábito
- [ ] El contador 🍅 hechos/estimados sube al terminar un enfoque (no al saltarlo).
- [ ] En un hábito el botón dice **✓ Listo por hoy**; con meta pregunta cuánto.
- [ ] Empezar otra tarea con un pomodoro abierto → cambia a la nueva sin dejar dos corriendo.

## C · Hábitos
- [ ] Crear un hábito "solo entre semana" con meta y aviso → aparece en Tareas › HÁBITOS.
- [ ] Un sábado sale tenue con *"hoy no toca"*.
- [ ] Marcar con cantidad, desmarcar, volver a marcar → la racha y el ✓ quedan bien.
- [ ] En la cuadrícula, tocar un día pasado lo marca y la racha se recalcula.
- [ ] El aviso llega una sola vez a la hora elegida y no llega si ya estaba marcado.
- [ ] **Dejar este hábito** → desaparece, y lo registrado sigue en la base.

## D · Tareas
- [ ] Crear con cada fecha (Sin fecha, Hoy, Mañana, Otra) → cae en la sección correcta del acordeón.
- [ ] Una de ayer sin hacer sale en **Anteriores** como atrasada.
- [ ] ★ Prioritaria de hoy → recordatorio cada hora entre 6 a.m. y 8 p.m., nunca de noche.
- [ ] Editar una tarea → los cambios se ven también en la web de KTPJ.
- [ ] Completar → abre **¿Qué sigue?** con la épica heredada.
- [ ] Una tarea creada con `/spur` llega con notificación una sola vez.
- [ ] **Zona horaria:** completar una tarea entre 7 p.m. y medianoche → sale en *Terminadas hoy*, no mañana.

## E · Épicas
- [ ] El buscador encuentra por nombre sin tildes y por prefijo.
- [ ] Crear épica desde el buscador → prefijo sugerido, editable, no repetido.
- [ ] Pausar → sus tareas y hábitos salen de Inicio, Hoy y el resumen; la ★ no recuerda.
- [ ] Reanudar con fecha → las fechas se corren los mismos días.
- [ ] Mover una tarea de épica → cambia su ID (PAS-3 → LAV-4).

## F · Resumen diario
- [ ] 7:00 a.m. llega *Tu día* y 9:00 p.m. *Cierre del día* (o mueve la hora del teléfono para probar).
- [ ] El aviso de Inicio desaparece al abrir el resumen y no vuelve ese día.
- [ ] Tras reiniciar el teléfono, al día siguiente siguen llegando.

## G · Captura de gastos
- [ ] Una compra con Nu crea la tarjeta en Inbox y la transacción.
- [ ] Un SMS de Davivienda (87188) de compra se guarda; uno declinado o de código se ignora.
- [ ] El mismo SMS dos veces no duplica.
- [ ] Sin red: queda en el historial con error y la notificación **Reintentar** funciona después.
- [ ] Gasto manual y atajos guardan bien.

## H · Balance y totales
- [ ] Marcar un comercio como **Fijo** → sale del total variable del día y del mes.
- [ ] Un ingreso no reduce el total de gastos del día.
- [ ] Navegar entre meses cambia las cifras.

## I · General y accesibilidad
- [ ] Tema claro, oscuro y sistema se ven bien en todas las pestañas.
- [ ] Con **TalkBack** se puede marcar una tarea, un hábito y empezar un pomodoro.
- [ ] Con letra grande del sistema nada se corta ni se monta.
- [ ] Títulos muy largos (3 líneas) no rompen las filas ni el pomodoro.
