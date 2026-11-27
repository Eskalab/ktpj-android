# KTP-10 · Pausar hábitos por viaje u otra razón

**Requerimiento (texto original):**
> Pausar hábitos por viaje u otra razón

**Fuente:** tarjeta de Spur KTP-10 · escrito el 2026-11-26 · **sin mirar el código** (caja negra)

Supuesto principal (confirmar en preguntas): los días en pausa **no rompen la racha 🔥**, igual que los días que "no tocan".

## Casos

| ID | Tipo | Dado | Cuando | Entonces | auto/manual | Estado |
|---|---|---|---|---|---|---|
| KTP-10-01 | Feliz | Hábito "Leer" todos los días, racha 🔥 6 | En la pantalla del hábito toco Pausar (motivo "Viaje") | El hábito sale de Inicio (HÁBITOS · x/y no lo cuenta) y en Tareas › HÁBITOS se ve tenue con "⏸ En pausa" (o similar) | manual | manual (teléfono) |
| KTP-10-02 | Feliz | "Leer" en pausa 4 días | Lo reanudo | La racha sigue en 🔥 6 (no se rompió) y vuelve a Inicio | auto | ✓ auto · `reanudarTrasCuatroDiasDePausaConservaLaRacha` |
| KTP-10-03 | Feliz | "Leer" en pausa con aviso a las 9 p.m. | Llegan las 9 p.m. | No llega el aviso "↻ Leer…" | manual | manual (teléfono) |
| KTP-10-04 | Feliz | "Leer" en pausa | Llega el resumen 7:00 a.m. / 9:00 p.m. | El hábito no aparece como pendiente | manual | manual (teléfono) |
| KTP-10-05 | Alternativo | Pauso con fecha de regreso (ej. "hasta el 3 dic") | Llega el 3 dic | Se reanuda solo ese día (si existe la opción) | auto | ✓ auto · `pausaConFechaSeTerminaSola` |
| KTP-10-06 | Alternativo | Pauso sin fecha de regreso | Pasan 10 días | Sigue en pausa hasta que lo reanude a mano | manual | manual (teléfono) |
| KTP-10-07 | Alternativo | Tengo 3 hábitos | Pauso todos a la vez ("me voy de viaje") | Si existe pausa general, ninguno aparece ni avisa; si no, se puede pausar uno por uno | manual | manual (teléfono) |
| KTP-10-08 | Alternativo | Hábito "Gimnasio" solo entre semana | Lo pauso de viernes a lunes | Sábado y domingo (que no tocan) y viernes/lunes (en pausa) no rompen la racha | auto | ✓ auto · `entreSemanaConPausaDeViernesALunes` |
| KTP-10-09 | Alternativo | Pantalla del hábito, cuadrícula de 15 semanas | Hábito estuvo en pausa una semana | Esos días se ven distintos (ni verde, ni "tocaba y no se hizo"); se entiende que fue pausa | manual | manual (teléfono) |
| KTP-10-10 | Alternativo | % del mes en la pantalla del hábito | Mes con 5 días en pausa | Los días en pausa no cuentan como fallados (se excluyen del total) | auto | ✓ auto · `mesConCincoDiasDePausaLosExcluye` |
| KTP-10-11 | Borde | Hábito ya marcado ✓ hoy | Lo pauso hoy mismo | El ✓ de hoy se conserva y cuenta para la racha | auto | ✓ auto · `pausarDespuesDeMarcarHoyConservaElCheck` |
| KTP-10-12 | Borde | Hábito no marcado hoy | Lo pauso hoy | Hoy cuenta como pausa (no rompe la racha) | auto | ✓ auto · `pausarHoySinMarcarNoRompeLaRacha` |
| KTP-10-13 | Borde | Pausa de 1 solo día | Pauso y reanudo al otro día | Racha intacta | auto | ✓ auto · `pausaDeUnDia` |
| KTP-10-14 | Borde | Pausa de 0 días | Pauso y reanudo enseguida (mismo minuto) | Todo queda como antes, sin días raros en la cuadrícula | auto | ✓ auto · `pausaDeCeroDiasNoDejaRastro` (cuadrícula: manual) |
| KTP-10-15 | Borde | Pausa muy larga (60 días, más que media cuadrícula) | Reanudo | Racha intacta; la mejor racha no cambia; la cuadrícula no se rompe | auto | ✓ auto · `pausaMuyLarga` (cuadrícula: manual) |
| KTP-10-16 | Borde | Pauso a las 11:55 p.m. hora Bogotá (ya es otro día en UTC) | Miro la cuadrícula al día siguiente | La pausa empieza el día correcto de Bogotá | auto | ✓ auto · `pausaPorDiaLocalCercaDeMedianoche` (día local del teléfono; el cambio de zona en viaje queda manual) |
| KTP-10-17 | Borde | Reanudo a las 12:05 a.m. | — | El día de hoy ya "toca" y el hábito aparece en Inicio pendiente | auto | ✓ auto · `reanudarDespuesDeMedianocheHoyToca` |
| KTP-10-18 | Borde | Pausa del 28 nov al 3 dic (cruza mes) | Veo % de noviembre y diciembre | Ambos meses excluyen sus días de pausa | auto | ✓ auto · `pausaQueCruzaElMes` |
| KTP-10-19 | Borde | Hábito en pausa | Toco un día pasado en la cuadrícula (dentro de la pausa) | Comportamiento definido (pregunta 4): o se permite marcarlo y cuenta, o no deja | manual | manual (teléfono) |
| KTP-10-20 | Borde | Pauso sin red | Vuelve la red | La pausa queda guardada en la base (y la ve la web o al reinstalar) | manual | manual (teléfono) |
| KTP-10-21 | Borde | Hábito en pausa, reinicio el teléfono | Llega la hora del aviso | No avisa (la pausa sobrevive al reinicio y no se rearman sus avisos) | manual | manual (teléfono) |
| KTP-10-22 | Borde | Hábito en pausa con aviso; app cerrada | Lo reanudo y llega la hora del aviso | Vuelve a avisar normal | manual | manual (teléfono) |
| KTP-10-23 | Negativo | Abro el diálogo de pausa | Lo cancelo | El hábito sigue activo, sin cambios | manual | manual (teléfono) |
| KTP-10-24 | Negativo | Pongo fecha de regreso en el pasado o igual a hoy | Confirmo | No se acepta o se interpreta como "reanudar ya"; nunca una pausa negativa | auto | ✓ auto · `fechaDeRegresoEnElPasadoNoDaPausaNegativa` |
| KTP-10-25 | Negativo | Hábito en pausa | Toco **Dejar este hábito** | Se archiva normal; lo registrado queda | manual | manual (teléfono) |
| KTP-10-26 | Negativo | Motivo con texto muy largo (300 caracteres) o emojis | Guardo | Se guarda y se muestra cortado con "…" | manual | manual (teléfono) |
| KTP-10-27 | Interacción | Hábito con aviso pendiente de hoy (notificación ya mostrada) | Lo pauso | La notificación desaparece o su **✓ Hecho** ya no rompe nada | manual | manual (teléfono) |
| KTP-10-28 | Interacción | Pomodoro del hábito corriendo | Pauso el hábito | Se define: el pomodoro sigue o se cierra; **✓ Listo por hoy** no deja un estado raro | manual | manual (teléfono) |
| KTP-10-29 | Interacción | Hábito en pausa | Veo la fila | No aparece **▶ Empezar** (o si aparece, funciona) | manual | manual (teléfono) |
| KTP-10-30 | Interacción | Hábito pertenece a una épica en pausa, y además está pausado | Reanudo la épica | El hábito sigue pausado (son pausas independientes) — confirmar | manual | manual (teléfono) |
| KTP-10-31 | Interacción | Hábito pausado | Lo edito (días, meta) | Sigue en pausa tras guardar | manual | manual (teléfono) |
| KTP-10-32 | Interacción | Calendario de Tareas | Día dentro de la pausa | El resumen del día no lo cuenta como hábito fallado | manual | manual (teléfono) |
| KTP-10-33 | Interacción | Web de KTPJ (*Diaria · LMXJV*) | Hábito en pausa | La web no se rompe; idealmente muestra que está en pausa | manual | manual (teléfono) |
| KTP-10-34 | Interacción | Etiqueta "⏸ En pausa" y días de pausa en la cuadrícula | Tema oscuro | Se distinguen bien de "no tocaba" (gris) | manual | manual (teléfono) |
| KTP-10-35 | No funcional | TalkBack | Pauso y reanudo; recorro la cuadrícula | Se anuncia "en pausa" y los días de pausa se leen distinto | manual | manual (teléfono) |

## Preguntas abiertas

1. **¿La pausa congela la racha?** (supuse que sí: ni suma ni se rompe). ¿O prefieres que la racha se reinicie pero sin contarlo como fallo?
2. **¿Pausa uno por uno, todos a la vez ("modo viaje"), o las dos?**
3. **¿Fecha de regreso automática** ("hasta el 3 dic") o solo pausar/reanudar a mano? ¿Pedir motivo (viaje, enfermedad, otro) o no?
4. **Durante la pausa, si igual hago el hábito**, ¿puedo marcarlo y cuenta? ¿Y marcar días pasados de la pausa en la cuadrícula?
5. **¿Se pueden pausar días pasados?** (ej. "ayer estaba enfermo, que no rompa la racha").
6. **Relación con pausar épica:** si la épica del hábito está en pausa, ¿la racha también se congela? (Hoy la épica en pausa solo lo oculta; no sé qué pasa con la racha.)
7. **¿Dónde está el botón?** ¿Solo en la pantalla del hábito, o también en la fila / mantener presionado?

## Casos añadidos en revisión (caja blanca, ya conociendo el código)

| ID | Tipo | Dado | Cuando | Entonces | auto/manual | Estado |
|---|---|---|---|---|---|---|
| KTP-10-36 | Negativo | Motivo con `:` o espacios/mayúsculas | Pauso | La etiqueta `habit_pause:` no se rompe (el motivo con `:` se descarta) | auto | ✓ auto · `motivoRaroNoRompeLaEtiqueta` |
| KTP-10-37 | Borde | Pausa sin fecha desde el 5; lo hago igual el 6 y el 7 | Veo % del mes el 7 | No pasa de 100% | auto | ✗ **BUG-1** · `porcentajeDelMesNoPasaDeCien` (@Ignore) |
| KTP-10-38 | Interacción | Hábito con pausas pasadas | Edito título/meta/aviso y guardo | Las pausas (etiquetas `habit_pause:`) siguen; la racha no cambia | manual | manual (teléfono; es SQL en `updateHabit`) |
| KTP-10-39 | Borde | Hábito en pausa, viaje a otra zona horaria | Cambia la hora del teléfono sin reiniciar la app | Hoy/“toca” siguen bien cerca de medianoche | manual | manual (riesgo previo, ver revisión) |

## Deuda de testabilidad

- `Habits.pause/resume` (cancelar la notificación, revertir si falla la base) y `remind()` dependen de `Context` y Neon: no se prueban en JVM. Se probó la lógica pura (`HabitPause.add/resume`, `Habit.dueOn/streak/bestStreak/monthRate`).
- La consulta de `NeonRepository.updateHabit`/`setHabitPauses` (conservar `habit_pause:` al editar) solo se puede probar contra una base de prueba. Sugerencia: extraer a una función pura el armado del arreglo de etiquetas (`habitLabels`/lista de pausas) para probar el escape de comillas.
- `HabitDates` guarda un `SimpleDateFormat` con la zona horaria del arranque: no se puede probar un cambio de zona en caliente sin reiniciar la JVM.

## Respuestas que da el código a las preguntas abiertas (revisión)

1. Racha congelada: sí (los días en pausa cuentan como “no toca”). Si se hace igual, suma.
2. Solo uno por uno; no hay “modo viaje” general.
3. Fecha de regreso: Mañana · 1 semana · Elegir fecha · Sin fecha fija; se reanuda sola. Motivo opcional: Viaje, Enfermo, Descanso, Otro.
4. Durante la pausa se puede marcar hoy y días pasados; cuenta.
5. No se pueden pausar días pasados (la pausa empieza hoy).
6. Pausa de épica y de hábito son independientes; la de épica sigue sin congelar la racha.
7. Botón ⏸ Pausar solo en la pantalla del hábito (junto a Editar); ▶ Reanudar en la tarjeta de pausa.
