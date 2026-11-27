# Bugs encontrados por QA (por requerimiento)

## KTP-8 · rama ktp-8-offline


### BUG-1 · Editar una tarea mientras su creación se está subiendo pierde la edición · 2026-11-26
Gravedad: alta (pierde datos)
Pasos: 1. Crear una tarea (sin red o con red lenta). 2. Mientras la creación se está enviando (al volver la red, o
justo después de crearla con red lenta), editarla: cambiar título/fecha/épica. 3. Esperar a que suba.
Esperado: en la base queda la versión editada.   Obtenido: queda la versión original; la edición desaparece de la
cola sin aviso. `OutboxPolicy.enqueue` funde la edición **dentro** de la entrada de creación (mismo `seq`) aunque esa
entrada ya esté en vuelo; al confirmar, `Outbox.remove(seq)` borra la entrada con la edición. Si la creación falla en la
base, `Outbox.replace` la reescribe con la copia vieja tomada antes del envío y también borra la edición.
Caso: KTP-8-23 / KTP-8-39  ·  Prueba: ModoOfflineKtp8Test.kt:edicionMientrasSeSubeLaCreacionNoSePierde, edicionMientrasFallaLaCreacionNoSePierde  ·  Rama/commit: ktp-8-offline / 0f62742
Corregido en cbdfdd2.

### BUG-3 · Crear → posponer → editar sin red: gana la fecha vieja · 2026-11-26
Gravedad: media (dato final equivocado, con salida: volver a editar)
Pasos: 1. Sin red, crear una tarea para hoy. 2. Posponerla a mañana (cambio de fecha). 3. Editarla y poner el día 30.
4. Volver la red.
Esperado: fecha 30 (el último cambio).   Obtenido: fecha de mañana. La edición se funde en la creación (que va
primero en la cola) y el cambio de fecha del paso 2 se envía después y la pisa. Pasa igual con ★ y "en curso"
(`SetPriority`, `SetStarted`). En pantalla también "vuelve" a la fecha vieja en la siguiente recarga.
Caso: KTP-8-11 / KTP-8-40  ·  Prueba: ModoOfflineKtp8Test.kt:editarDespuesDePosponerSinRedGanaLaUltimaFecha  ·  Rama/commit: ktp-8-offline / 0f62742
Corregido en cbdfdd2.

### BUG-2 · Un gasto guardado sin red no suma al total del día ni al balance · 2026-11-26
Gravedad: media (cifra mal mientras no hay red; se corrige sola al subir)
Pasos: 1. Modo avión. 2. Registrar un gasto manual de $8.500. 3. Mirar Inicio / Gastos.
Esperado: el gasto suma al total del día y queda marcado como pendiente.   Obtenido: queda con estado ERROR
("Sin conexión · se sube sola…") y `Balance.counted` solo cuenta capturas OK: el total no lo incluye. Inicio además
lo cuenta entre los errores del día.
Caso: KTP-8-03  ·  Prueba: ModoOfflineKtp8Test.kt:gastoSinRedSumaAlTotalDelDia  ·  Rama/commit: ktp-8-offline / 0f62742
Corregido en cbdfdd2.

### BUG-4 · Tarea marcada Hecha sin red queda con la hora de subida · 2026-11-26
Gravedad: media (dato mal en la base)
Pasos: 1. Sin red, a las 11:30 p.m., marcar una tarea Hecha. 2. Volver la red al día siguiente.
Esperado: `done_at` = 11:30 p.m. del día en que se marcó (sale ese día en Terminadas hoy y en el Calendario).
Obtenido (por lectura de código): `markTaskDone` solo hace `SET done = true`; `done_at` lo llena un trigger de la base
en el momento del UPDATE, o sea, al subir. La tarea cae en el día siguiente en Terminadas hoy y en el Calendario.
Caso: KTP-8-07  ·  Prueba: no automatizable (trigger en la base); revisar en el teléfono  ·  Rama/commit: ktp-8-offline / 0f62742
Corregido en cbdfdd2.

### BUG-5 · Gasto capturado sin red queda en la base con la fecha de subida · 2026-11-26
Gravedad: media (en la web/base el gasto cae en otro día o mes; en el celular el balance sí usa la hora real)
Pasos: 1. Sin red, el 31 a las 11 p.m., registrar un gasto (o llega Nu/SMS). 2. Volver la red el día 1.
Esperado: `transactions.occurred_at` = 31 a las 11 p.m.   Obtenido (por lectura de código): `insertCard` inserta
`occurred_at = NOW()` y `received_at = NOW()`; la hora real de la captura (`Capture.at`) no se envía.
Caso: KTP-8-19  ·  Prueba: no automatizable (SQL); revisar en la base de prueba o en la web  ·  Rama/commit: ktp-8-offline / 0f62742
Corregido en cbdfdd2.

## KTP-10 · rama ktp-10-pausar-habitos
# Bugs encontrados por QA

### BUG-1 · El % del mes pasa de 100% si se hace el hábito durante una pausa · 2026-11-26
Gravedad: baja (visual; la racha y lo guardado están bien)
Pasos: 1. Hábito diario hecho del 1 al 4 del mes. 2. El 5, ⏸ Pausar sin fecha fija. 3. El 6 y el 7 márcalo igual ("si lo haces igual, cuenta"). 4. Abre la pantalla del hábito.
Esperado: "este mes" ≤ 100% (p. ej. 100% · 6/6 o 100% · 4/4).   Obtenido: "150% · este mes 6/4".
Causa probable: `Habit.monthRate` suma al numerador todo día hecho, pero el denominador solo los días que tocan y no están en pausa. Ya pasaba antes con días que no tocan (hacer el gimnasio un sábado), la pausa lo vuelve frecuente.
Caso: KTP-10-37  ·  Prueba: app/src/test/java/co/eskala/bktpj_notification/HabitPauseCasosTest.kt:porcentajeDelMesNoPasaDeCien (@Ignore)  ·  Rama/commit: ktp-10-pausar-habitos @ eb1b5f2

## KTP-13 · rama ktp-13-fecha-anteriores

### BUG-2 · Fechas de otro año se muestran sin año · 2026-11-26
Gravedad: baja (visual)
Pasos: 1. Tener una tarea pendiente con fecha 26 nov 2025 (hoy 26 nov 2026). 2. Abrir Tareas › Anteriores. 3. Pasarla con 📅 Otra a una fecha de 2025 y leer el aviso.
Esperado: "26 nov 2025" (o similar), para no confundirla con este año.   Obtenido: "26 nov", igual que la de este año.
Nota: viene de antes de KTP-13 (`dueLabel` en Tasks.kt) y se repite en `dueTargetLabel` (Reschedule.kt). El requerimiento no lo pide; es la expectativa del caso: confirmar con el dueño.
Caso: KTP-13-14  ·  Prueba: AnterioresReprogramarQaTest.kt:fechaDeOtroAnoMuestraElAno (@Ignore)  ·  Rama/commit: ktp-13-fecha-anteriores / de0508e

### BUG-1 · Botones Hoy · Mañana · 📅 Otra · Todas a hoy con área de toque de 32dp · 2026-11-26
Gravedad: media (accesibilidad; la función sirve pero es fácil fallar el toque)
Pasos: 1. Abrir Tareas › Anteriores con una tarea atrasada. 2. Tocar el borde de "Hoy" o "Mañana". 3. Repetir con letra grande / TalkBack.
Esperado: cada botón con área mínima de toque de 44dp (`KtpjDimens.minTouchTarget`, la que usan ★, checks y botones de la app; Android recomienda 48dp).
Obtenido: `SmallChip` usa `heightIn(min = 32.dp)` y 8dp/6dp de separación. Los chips van dentro de la columna que abre **Editar tarea** al tocarla, así que un toque que falla por poco abre el formulario de edición en vez de cambiar la fecha (riesgo para KTP-13-19). "Todas a hoy" está dentro del encabezado que abre/cierra la sección: fallar el toque cierra Anteriores.
Sugerencia: dejar el área clicable en 44dp y conservar la píldora visual de 32dp (padding exterior, o `minimumInteractiveComponentSize()`).
Caso: KTP-13-28 (y -19, -27)  ·  Prueba: manual (no se puede en JVM)  ·  Rama/commit: ktp-13-fecha-anteriores / de0508e
Corregido en 7ba3a38: área de toque de 44dp (píldora visual de 32dp) en Hoy · Mañana · 📅 Otra y Todas a hoy; los toques en la fila de chips no llegan a "Editar tarea" ni al encabezado.

## KTP-11 · rama ktp-11-opcion-a
# Bugs encontrados por QA

### BUG-1 · Al editar, el texto de ayuda dice "crearla" · 2026-11-26
Gravedad: baja (visual)
Pasos: 1. Abrir una tarea existente para editar. 2. Borrar todo el título. 3. Bajar al final del formulario.
Esperado: un texto que hable de guardar (p. ej. "Escribe qué hay que hacer para poder guardarla.").   Obtenido: "Escribe qué hay que hacer para poder crearla." (el texto no distingue crear de editar).
Caso: KTP-11-06 / KTP-11-16  ·  Prueba: manual (revisión de código, `NewTaskScreen.kt`; no hay función pura que probar)  ·  Rama/commit: ktp-11-opcion-a / 6c3235c
