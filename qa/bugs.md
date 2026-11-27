# Bugs encontrados por QA

## BUG-1 · El % del mes pasa de 100% si se hace el hábito durante una pausa · 2026-11-26
Gravedad: baja (visual; la racha y lo guardado están bien)
Pasos: 1. Hábito diario hecho del 1 al 4 del mes. 2. El 5, ⏸ Pausar sin fecha fija. 3. El 6 y el 7 márcalo igual ("si lo haces igual, cuenta"). 4. Abre la pantalla del hábito.
Esperado: "este mes" ≤ 100% (p. ej. 100% · 6/6 o 100% · 4/4).   Obtenido: "150% · este mes 6/4".
Causa probable: `Habit.monthRate` suma al numerador todo día hecho, pero el denominador solo los días que tocan y no están en pausa. Ya pasaba antes con días que no tocan (hacer el gimnasio un sábado), la pausa lo vuelve frecuente.
Caso: KTP-10-37  ·  Prueba: app/src/test/java/co/eskala/bktpj_notification/HabitPauseCasosTest.kt:porcentajeDelMesNoPasaDeCien (@Ignore)  ·  Rama/commit: ktp-10-pausar-habitos @ eb1b5f2
