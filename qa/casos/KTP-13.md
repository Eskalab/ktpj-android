# KTP-13 · Asignar fecha desde la tarjeta en tareas anteriores no terminadas

**Requerimiento (texto original):**
> En las tareas anteriores que no son terminadas poner en la tarjeta la opción de asignar fecha

**Fuente:** tarjeta de Spur KTP-13 · escrito el 2026-11-26 · **sin mirar el código** (caja negra)

Contexto (README): en Tareas, la sección **Anteriores** (cerrada por defecto) muestra tareas atrasadas, marcadas *atrasada*.
Hoy la fecha se cambia tocándola o editando la tarea. Se pide una opción visible **en la tarjeta** para reasignar fecha.

## Casos

| ID | Tipo | Dado | Cuando | Entonces | auto/manual | Estado |
|---|---|---|---|---|---|---|
| KTP-13-01 | Feliz | Tarea "Prueba A" con fecha de hace 3 días, no hecha, en Anteriores | Abro Anteriores | La tarjeta muestra una opción visible de asignar fecha, sin tener que abrir el formulario | manual | manual · implementado (chips Hoy · Mañana · 📅 Otra) |
| KTP-13-02 | Feliz | Tarjeta de KTP-13-01 | Uso la opción y elijo **Hoy** | La tarea sale de Anteriores, aparece en Hoy (e Inicio), deja de decir *atrasada* y la base queda con `due_date` = hoy (Bogotá) | manual | auto parcial ✓ (sale de Anteriores) + manual (base y Inicio) |
| KTP-13-03 | Alternativo | Tarjeta en Anteriores | Elijo **Mañana** | Pasa a Próximos días con "Mañana" | manual | auto parcial ✓ + manual |
| KTP-13-04 | Alternativo | Tarjeta en Anteriores | Elijo **Otra…** y una fecha en el calendario (ej. 5 dic) | Pasa a Próximos días con "5 dic" | manual | pendiente |
| KTP-13-05 | Alternativo | Tarjeta en Anteriores | Elijo **Sin fecha** (si se ofrece) | Pasa a Sin fecha | manual | auto parcial ✓ (vía "Quitar fecha" del calendario) + manual |
| KTP-13-06 | Alternativo | Varias tareas en Anteriores | Reasigno una por una | Cada una se mueve sola; las otras siguen en su lugar y la lista no salta al inicio | manual | auto parcial ✓ + manual (que la lista no salte) |
| KTP-13-07 | Alternativo | Tarea ★ prioritaria atrasada | La paso a mañana | Hoy deja de recordar cada hora; mañana recuerda de 6 a.m. a 8 p.m. | manual | pendiente |
| KTP-13-08 | Borde | Anteriores vacía (0 tareas) | Abro la sección | No hay opción que mostrar; no se rompe | manual | auto ✓ |
| KTP-13-09 | Borde | Tareas **hechas** con fecha pasada | Miro la lista | No muestran la opción (el requerimiento dice "no terminadas") | manual | pendiente |
| KTP-13-10 | Borde | Tarea con fecha **ayer** | Abro Tareas a las 12:05 a.m. hora Bogotá | Ya está en Anteriores con la opción | auto | auto ✓ |
| KTP-13-11 | Borde | Tarea con fecha **hoy** a las 8 p.m. Bogotá (en UTC ya es mañana) | Miro Tareas | Sigue en Hoy, no en Anteriores ni en Próximos días | auto | auto ✓ |
| KTP-13-12 | Borde | Elijo "Hoy" a las 11:58 p.m. Bogotá | Pasan las 12:00 | La tarea queda con la fecha del día en que la asigné (y a medianoche pasa a Anteriores, como cualquier otra) | auto | auto ✓ |
| KTP-13-13 | Borde | Elijo **Mañana** el 30 de noviembre | — | Queda 1 de diciembre; el 31 de diciembre → 1 de enero del año siguiente | auto | auto ✓ |
| KTP-13-14 | Borde | Tarea atrasada hace mucho (1 año) | Veo la tarjeta | La opción está y funciona; la fecha vieja se muestra con año si es de otro año | manual | auto ✓ (orden) · ✗ BUG-2 (año no se muestra, @Ignore) |
| KTP-13-15 | Borde | Calendario de **Otra…** | Intento elegir una fecha pasada | Comportamiento definido (pregunta 3); si se permite, la tarea queda en Anteriores | manual | auto ✓ (se permite; queda en Anteriores) + manual |
| KTP-13-16 | Borde | Sin red | Asigno fecha | O se guarda y sube después (ver KTP-8), o avisa que no se pudo y la tarjeta vuelve a su lugar; nunca dice que guardó si no guardó | manual | pendiente |
| KTP-13-17 | Borde | Título muy largo (2-3 líneas) y tarea con épica, app ↗ y 🍅 | Veo la tarjeta | La opción de fecha cabe sin tapar el título ni los otros datos | manual | pendiente |
| KTP-13-18 | Negativo | Abro el selector de fecha desde la tarjeta | Lo cancelo / atrás | La tarea queda con su fecha original, en Anteriores | manual | pendiente |
| KTP-13-19 | Negativo | Toco la opción de fecha | — | No se marca la tarea como hecha por error ni abre el pomodoro (toque separado de los otros botones) | manual | manual · riesgo por BUG-1 (fallar el chip abre Editar) |
| KTP-13-20 | Interacción | Tarea atrasada **en curso** (franja azul) | Le asigno fecha mañana | Se define si sigue en curso; el pomodoro abierto no se cierra solo | manual | pendiente |
| KTP-13-21 | Interacción | Tarea atrasada de una **épica en pausa** | — | No aparece en Anteriores (regla actual) así que no hay opción; dentro de la épica, se define si se muestra | manual | pendiente |
| KTP-13-22 | Interacción | Pantalla de la épica, tarea pendiente vencida | Miro su tarjeta | ¿También tiene la opción? (pregunta 2) | manual | manual · no implementado en la épica (pregunta 2) |
| KTP-13-23 | Interacción | Hábitos | Miro la sección HÁBITOS | Los hábitos no muestran esta opción (no tienen fecha) | manual | pendiente |
| KTP-13-24 | Interacción | Resumen de la noche con **Pasar a mañana** | Uso las dos cosas el mismo día | No se pisan: la fecha final es la última que elegí | manual | pendiente |
| KTP-13-25 | Interacción | Fecha cambiada en el celular | Miro la web de KTPJ y `/spur list` | Muestran la fecha nueva | manual | pendiente |
| KTP-13-26 | Interacción | Tema oscuro | Veo la opción en la tarjeta atrasada | Se ve bien y contrasta con el estilo "atrasada" | manual | pendiente |
| KTP-13-27 | No funcional | TalkBack | Recorro una tarjeta de Anteriores | La opción se anuncia como "Asignar fecha a <título>", no como "botón" sin nombre | manual | auto parcial ✓ (nombre de la tarea) + manual · ver BUG-1 |
| KTP-13-28 | No funcional | Letra del sistema en tamaño máximo | Veo la tarjeta | La opción sigue visible y se puede tocar (área mínima de toque) | manual | manual · ✗ BUG-1 (chips de 32dp < 44dp) |

## Preguntas abiertas

1. **¿Qué es "la opción"?** ¿Botones rápidos en la tarjeta (**Hoy · Mañana · 📅**) o un solo botón que abre el selector actual (Hoy / Mañana / Otra…)? *Sugerencia: "→ Hoy" directo + 📅 para lo demás, porque lo más común es pasar lo atrasado a hoy.*
2. **¿Solo en Anteriores** o en cualquier tarjeta atrasada (Inicio, pantalla de la épica)? ¿Y en tareas Sin fecha?
3. **¿Se permite una fecha pasada** desde ahí? ¿Y "Sin fecha"?
4. **¿Acción para varias a la vez** ("pasar todas las anteriores a hoy")? Parece útil después de un viaje; ¿entra en esta tarjeta o es otra?
5. ¿Tocar la fecha (que ya funciona) no basta? ¿El problema es que no se nota? Esto ayuda a decidir si es un botón nuevo o hacer visible el actual.

## Deuda de testabilidad

Automatizado el 2026-11-26 (modo `automatizar`, ya viendo el código) en
`app/src/test/java/co/eskala/bktpj_notification/ui/tasks/AnterioresReprogramarQaTest.kt` (13 pruebas, 1 ignorada por BUG-2).
Las funciones puras `quickDueDate`, `isOverdueOn`, `overdueTasks`, `dueTargetLabel`, `taskRef` (Reschedule.kt) reciben "hoy",
así que los bordes de hora se prueban pasando la fecha de Bogotá.

- La pantalla calcula "hoy" con `LocalDate.now()` (zona del teléfono). Está bien si el teléfono está en Bogotá; no hay prueba
  de eso porque no recibe un `Clock`. Sugerencia: pasar `today: LocalDate` a `TasksScreen` o un `Clock`.
- La lógica de `reschedule` (no repetir si ya tiene esa fecha, parar si una falla, Deshacer que restaura la fecha anterior)
  vive dentro del composable `TasksScreen`; no se puede probar en JVM. Sugerencia: extraer una función
  `suspend fun rescheduleAll(tasks, due, apply: suspend (SpurTask, String?) -> Boolean): List<SpurTask>`.
- Anteriores usa `isOverdueOn` (java.time) y Hoy/Próximos usan `isOverdue`/`isToday` (SimpleDateFormat). Hoy coinciden
  (prueba `cadaTareaCaeEnUnaSolaSeccion`), pero son dos implementaciones de lo mismo.

## Revisión del diff (modo `revisar`, ya viendo el código, 2026-11-26)

Lo que se hizo: en cada tarjeta de **Anteriores** salen los chips **Hoy · Mañana · 📅 Otra** (Otra abre el calendario de siempre,
que también trae "Quitar fecha"); aviso "LAV-3 → Mañana" con **Deshacer**; y en el encabezado, **Todas a hoy** si hay 2 o más.
Responde así las preguntas 1 (botones rápidos + calendario), 3 (se permite fecha pasada y sin fecha) y 4 (sí hay "todas a hoy").

Hallazgos:
1. **Toque de 32dp** (BUG-1): menor que los 44dp de la app; fallar el chip abre Editar o cierra la sección.
2. **Solo en Tareas › Anteriores** (pregunta 2 sin responder): Inicio y la pantalla de la épica siguen sin la opción (KTP-13-22).
3. **Deshacer se pierde** si se reprograma otra tarea antes de que se vaya el aviso (se descarta el aviso anterior) o si se cambia de pestaña
   (el `scope` es de la pantalla). Con "Todas a hoy" eso significa no poder deshacer el lote. Riesgo bajo; avisar al dueño.
4. **Cambiar de pestaña a mitad de "Todas a hoy"** cancela el ciclo: unas quedan movidas y otras no, sin aviso. Riesgo bajo (son pocas llamadas).
5. **Sin red** (KTP-13-16): la tarjeta no se mueve y sale un Toast "No se pudo cambiar la fecha"; no dice que guardó. Correcto, pero en lote se
   detiene en la primera que falla y muestra "N tareas → Hoy" solo con las que sí.
6. **TalkBack**: cada chip se anuncia "Pasar a hoy LAV-3" (o el título recortado a 24 si no hay ID); el `onClickLabel` repite el mismo texto
   ("doble toque para Pasar a hoy LAV-3"). Funciona; revisar que no suene redundante.
7. "Hoy" se calcula con `LocalDate.now()` (zona del teléfono): correcto en Bogotá; dos implementaciones de "vencida" (ver deuda de testabilidad).
8. Tareas hechas no salen (la consulta trae solo `NOT done`) y las de épica en pausa tampoco (`activeTasks`): KTP-13-09 y -21 cumplen por diseño.
