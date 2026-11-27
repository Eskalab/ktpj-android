# Bugs de Spur

## BUG-2 · Fechas de otro año se muestran sin año · 2026-11-26
Gravedad: baja (visual)
Pasos: 1. Tener una tarea pendiente con fecha 26 nov 2025 (hoy 26 nov 2026). 2. Abrir Tareas › Anteriores. 3. Pasarla con 📅 Otra a una fecha de 2025 y leer el aviso.
Esperado: "26 nov 2025" (o similar), para no confundirla con este año.   Obtenido: "26 nov", igual que la de este año.
Nota: viene de antes de KTP-13 (`dueLabel` en Tasks.kt) y se repite en `dueTargetLabel` (Reschedule.kt). El requerimiento no lo pide; es la expectativa del caso: confirmar con el dueño.
Caso: KTP-13-14  ·  Prueba: AnterioresReprogramarQaTest.kt:fechaDeOtroAnoMuestraElAno (@Ignore)  ·  Rama/commit: ktp-13-fecha-anteriores / de0508e

## BUG-1 · Botones Hoy · Mañana · 📅 Otra · Todas a hoy con área de toque de 32dp · 2026-11-26
Gravedad: media (accesibilidad; la función sirve pero es fácil fallar el toque)
Pasos: 1. Abrir Tareas › Anteriores con una tarea atrasada. 2. Tocar el borde de "Hoy" o "Mañana". 3. Repetir con letra grande / TalkBack.
Esperado: cada botón con área mínima de toque de 44dp (`KtpjDimens.minTouchTarget`, la que usan ★, checks y botones de la app; Android recomienda 48dp).
Obtenido: `SmallChip` usa `heightIn(min = 32.dp)` y 8dp/6dp de separación. Los chips van dentro de la columna que abre **Editar tarea** al tocarla, así que un toque que falla por poco abre el formulario de edición en vez de cambiar la fecha (riesgo para KTP-13-19). "Todas a hoy" está dentro del encabezado que abre/cierra la sección: fallar el toque cierra Anteriores.
Sugerencia: dejar el área clicable en 44dp y conservar la píldora visual de 32dp (padding exterior, o `minimumInteractiveComponentSize()`).
Caso: KTP-13-28 (y -19, -27)  ·  Prueba: manual (no se puede en JVM)  ·  Rama/commit: ktp-13-fecha-anteriores / de0508e
