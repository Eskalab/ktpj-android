# Bugs de Spur

Los más nuevos arriba. Formato en `.claude/agents/qa-spur.md`.

## BUG-1 · Editar una tarea mientras su creación se está subiendo pierde la edición · 2026-11-26
Gravedad: alta (pierde datos)
Pasos: 1. Crear una tarea (sin red o con red lenta). 2. Mientras la creación se está enviando (al volver la red, o
justo después de crearla con red lenta), editarla: cambiar título/fecha/épica. 3. Esperar a que suba.
Esperado: en la base queda la versión editada.   Obtenido: queda la versión original; la edición desaparece de la
cola sin aviso. `OutboxPolicy.enqueue` funde la edición **dentro** de la entrada de creación (mismo `seq`) aunque esa
entrada ya esté en vuelo; al confirmar, `Outbox.remove(seq)` borra la entrada con la edición. Si la creación falla en la
base, `Outbox.replace` la reescribe con la copia vieja tomada antes del envío y también borra la edición.
Caso: KTP-8-23 / KTP-8-39  ·  Prueba: ModoOfflineKtp8Test.kt:edicionMientrasSeSubeLaCreacionNoSePierde, edicionMientrasFallaLaCreacionNoSePierde  ·  Rama/commit: ktp-8-offline / 0f62742

## BUG-3 · Crear → posponer → editar sin red: gana la fecha vieja · 2026-11-26
Gravedad: media (dato final equivocado, con salida: volver a editar)
Pasos: 1. Sin red, crear una tarea para hoy. 2. Posponerla a mañana (cambio de fecha). 3. Editarla y poner el día 30.
4. Volver la red.
Esperado: fecha 30 (el último cambio).   Obtenido: fecha de mañana. La edición se funde en la creación (que va
primero en la cola) y el cambio de fecha del paso 2 se envía después y la pisa. Pasa igual con ★ y "en curso"
(`SetPriority`, `SetStarted`). En pantalla también "vuelve" a la fecha vieja en la siguiente recarga.
Caso: KTP-8-11 / KTP-8-40  ·  Prueba: ModoOfflineKtp8Test.kt:editarDespuesDePosponerSinRedGanaLaUltimaFecha  ·  Rama/commit: ktp-8-offline / 0f62742

## BUG-2 · Un gasto guardado sin red no suma al total del día ni al balance · 2026-11-26
Gravedad: media (cifra mal mientras no hay red; se corrige sola al subir)
Pasos: 1. Modo avión. 2. Registrar un gasto manual de $8.500. 3. Mirar Inicio / Gastos.
Esperado: el gasto suma al total del día y queda marcado como pendiente.   Obtenido: queda con estado ERROR
("Sin conexión · se sube sola…") y `Balance.counted` solo cuenta capturas OK: el total no lo incluye. Inicio además
lo cuenta entre los errores del día.
Caso: KTP-8-03  ·  Prueba: ModoOfflineKtp8Test.kt:gastoSinRedSumaAlTotalDelDia  ·  Rama/commit: ktp-8-offline / 0f62742

## BUG-4 · Tarea marcada Hecha sin red queda con la hora de subida · 2026-11-26
Gravedad: media (dato mal en la base)
Pasos: 1. Sin red, a las 11:30 p.m., marcar una tarea Hecha. 2. Volver la red al día siguiente.
Esperado: `done_at` = 11:30 p.m. del día en que se marcó (sale ese día en Terminadas hoy y en el Calendario).
Obtenido (por lectura de código): `markTaskDone` solo hace `SET done = true`; `done_at` lo llena un trigger de la base
en el momento del UPDATE, o sea, al subir. La tarea cae en el día siguiente en Terminadas hoy y en el Calendario.
Caso: KTP-8-07  ·  Prueba: no automatizable (trigger en la base); revisar en el teléfono  ·  Rama/commit: ktp-8-offline / 0f62742

## BUG-5 · Gasto capturado sin red queda en la base con la fecha de subida · 2026-11-26
Gravedad: media (en la web/base el gasto cae en otro día o mes; en el celular el balance sí usa la hora real)
Pasos: 1. Sin red, el 31 a las 11 p.m., registrar un gasto (o llega Nu/SMS). 2. Volver la red el día 1.
Esperado: `transactions.occurred_at` = 31 a las 11 p.m.   Obtenido (por lectura de código): `insertCard` inserta
`occurred_at = NOW()` y `received_at = NOW()`; la hora real de la captura (`Capture.at`) no se envía.
Caso: KTP-8-19  ·  Prueba: no automatizable (SQL); revisar en la base de prueba o en la web  ·  Rama/commit: ktp-8-offline / 0f62742
