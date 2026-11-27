# KTP-8 · Modo offline (sin internet o modo avión)

**Requerimiento (texto original):**
> Modo offline sin internet o modo avion
> Detalle: Guardar los gastos y tareas y luego sincronizar cuando tenga red

**Fuente:** tarjeta de Spur KTP-8 · escrito el 2026-11-26 · **sin mirar el código** (caja negra)

Nota: hoy la app guarda en Neon por internet; las capturas fallidas quedan en el historial del celular con **Reintentar / Descartar**.
Estos casos prueban que, sin red, nada se pierde y todo sube solo cuando vuelve la red.

## Casos

| ID | Tipo | Dado | Cuando | Entonces | auto/manual | Estado |
|---|---|---|---|---|---|---|
| KTP-8-01 | Feliz | Modo avión activado | Creo una tarea "Prueba offline 1" con fecha Hoy | La tarea aparece de inmediato en Inicio y en Tareas › Hoy, con alguna marca de "pendiente de sincronizar" | manual | pendiente |
| KTP-8-02 | Feliz | La tarea de KTP-8-01 creada sin red | Desactivo el modo avión | En menos de un par de minutos (sin tocar nada) la tarea está en la base y se ve en la web de KTPJ; la marca de pendiente desaparece | manual | pendiente |
| KTP-8-03 | Feliz | Modo avión | Registro un gasto manual de $8.500 en "Tienda prueba" | El gasto se ve en Gastos y suma al total del día; queda pendiente de subir | manual | auto parcial ✗ **BUG-2** (`gastoSinRedSumaAlTotalDelDia`, @Ignore) · resto manual |
| KTP-8-04 | Feliz | Gasto de KTP-8-03 pendiente | Vuelve la red | Se crea la fila en `transactions` y la tarjeta, una sola vez | manual | pendiente |
| KTP-8-05 | Alternativo | Sin red | Llega notificación de Nu / SMS de Davivienda | Se captura y queda pendiente; al volver la red se sube sola (sin depender de tocar **Reintentar**) | manual | pendiente |
| KTP-8-06 | Alternativo | Sin red | Toco un atajo de gasto | Igual que gasto manual: guardado local y sube después | manual | pendiente |
| KTP-8-07 | Alternativo | Sin red | Marco una tarea como Hecha | Se ve tachada en "Terminadas hoy"; al volver la red queda `done` en la base y `done_at` con la **hora real en que la marqué**, no la hora en que subió | manual | ✗ **BUG-4** (revisión de código: `done_at` lo llena un trigger al subir) · manual |
| KTP-8-08 | Alternativo | Sin red | Edito una tarea (título, fecha, ★, épica, pomodoros estimados) | El cambio se ve en la app y sube al volver la red | manual | pendiente |
| KTP-8-09 | Alternativo | Sin red | Marco un hábito con cantidad (10 páginas) | Se ve ✓ y la racha 🔥 sube; al volver la red se crea el `habit_check` del día correcto | manual | pendiente |
| KTP-8-10 | Alternativo | Sin red | Toco **▶ Empezar** en una tarea | Queda en curso y el pomodoro corre normal; `started_at` sube después | manual | pendiente |
| KTP-8-11 | Alternativo | Sin red, varios cambios a la misma tarea (crear → editar título → marcar hecha) | Vuelve la red | En la base queda el estado final correcto, en el orden correcto; no hay 2 tareas | manual | auto ✓ `crearEditarYCompletarSinRed…` · ✗ **BUG-3** `editarDespuesDePosponerSinRed…` (@Ignore) |
| KTP-8-12 | Alternativo | Abro la app sin red (después de haberla usado con red) | Voy a Inicio y Tareas | Se ven las tareas y hábitos que había (la última copia), no una pantalla vacía o un error | manual | auto parcial ✓ `cacheConservaTareasYEpicaEnPausa` · resto manual |
| KTP-8-13 | Borde | Abro la app **por primera vez** o tras borrar datos, sin red | Entro a Tareas | Mensaje claro "sin conexión, no hay datos guardados aún"; no se cuelga | manual | pendiente |
| KTP-8-14 | Borde | 0 cambios pendientes | Vuelve la red | No pasa nada visible ni se duplica nada | auto | auto ✓ `sinCambiosPendientesNoSeEnviaNada` |
| KTP-8-15 | Borde | 1 cambio pendiente | Vuelve la red | Sube exactamente 1 | auto | auto ✓ `unCambioPendienteSubeExactamenteUnaVez` |
| KTP-8-16 | Borde | 100 cambios pendientes (un viaje de varios días: gastos + tareas + hábitos) | Vuelve la red | Suben todos sin perder ninguno; la app sigue usable mientras sube | manual | auto parcial ✓ `cienCambiosSobrevivenAlDisco…` · "app usable mientras sube" manual |
| KTP-8-17 | Borde | Creo tarea sin red a las 11:50 p.m. hora Bogotá con fecha "Hoy" | Vuelve la red a las 12:10 a.m. | La tarea queda con fecha del día en que la creé (no del día siguiente) | auto | auto ✓ `tareaCreadaAntesDeMedianoche…` |
| KTP-8-18 | Borde | Marco hábito sin red a las 11:30 p.m. Bogotá (ya es el día siguiente en UTC) | Sube al otro día | El check queda en el `day` de Bogotá en que lo marqué; la racha no se rompe | auto | auto ✓ `habitoMarcadoAntesDeMedianoche…` |
| KTP-8-19 | Borde | Gasto offline el 31 de octubre 11 p.m., sube el 1 de noviembre | Abro Balance del mes | El gasto cuenta en octubre, no en noviembre | auto | auto ✓ en el celular `gastoDelUltimoDiaDelMes…` · ✗ **BUG-5** en la base (`occurred_at` = hora de subida) |
| KTP-8-20 | Borde | Cambios pendientes y **cierro la app** (deslizar de recientes) | Vuelve la red con la app cerrada | Los cambios suben igual (trabajo en segundo plano) o, como mínimo, al abrir la app | manual | pendiente |
| KTP-8-21 | Borde | Cambios pendientes y **reinicio el teléfono** sin red | Prendo y vuelve la red | Nada se perdió; todo sube | manual | pendiente |
| KTP-8-22 | Borde | Wi-Fi conectado pero sin internet real (portal de hotel/aeropuerto) | Creo una tarea | Se trata como sin red: queda pendiente y reintenta; no se pierde por un "timeout" | manual | pendiente |
| KTP-8-23 | Borde | Red intermitente (se corta a mitad de subir) | Se está sincronizando | Lo que no subió se reintenta; lo que sí subió no se duplica | manual | auto parcial ✓ `reintentoDeCreacionConservaElMismoId` · ✗ **BUG-1** (2 pruebas @Ignore) · resto manual |
| KTP-8-24 | Borde | Sin red | Uso el ID de la tarea nueva | Se define qué ID muestra antes de subir (ej. "SP-?" o provisional); al subir queda un `PREFIJO-n` sin repetir números ya usados en la web | manual | auto parcial ✓ `tareaCreadaSinRedNoMuestraIdHastaSubir` (sin ID hasta subir) · manual |
| KTP-8-25 | Negativo | Sin red, creo tarea y después la **borro / descarto** antes de que haya red | Vuelve la red | No aparece en la base | manual | auto parcial ✓ `descartarCreacionPendiente…` (solo vía "Ver › Descartar" de lo apartado; no hay borrar tarea) · manual |
| KTP-8-26 | Negativo | Sin red, edito una tarea en el celular; con red, alguien la edita en la web o con `/spur` | Vuelve la red al celular | Regla de conflicto clara (ver pregunta 3); nunca se pierden los dos cambios sin aviso | manual | pendiente |
| KTP-8-27 | Negativo | Sin red, marco hecha una tarea que mientras tanto fue borrada en la web | Vuelve la red | No se cuelga ni crea una tarea fantasma; queda aviso o se descarta | manual | pendiente |
| KTP-8-28 | Negativo | Un cambio pendiente falla al subir por un error de la base (no de red) | Vuelve la red | No bloquea la subida de los demás; queda visible con Reintentar / Descartar | manual | auto ✓ `errorDeBaseNoBloqueaLosDemas…` |
| KTP-8-29 | Negativo | Cancelar a mitad: creo tarea sin red y salgo del formulario sin guardar | — | No queda nada pendiente | manual | pendiente |
| KTP-8-30 | Interacción | Épica en pausa | Sin red, pauso o reanudo una épica | Se ve el efecto en la app (salen/entran tareas) y sube al volver la red; las fechas corridas al reanudar quedan iguales en la base | manual | auto parcial ✓ caché guarda la pausa · ⚠ pausar/reanudar épica **no** va por la cola (necesita red) · manual |
| KTP-8-31 | Interacción | Pomodoro corriendo sin red | Termina el enfoque | La alarma suena y el registro de pomodoros se guarda (ya es local); nada depende de la red | manual | pendiente |
| KTP-8-32 | Interacción | ★ prioritaria hoy, sin red | Llega la hora del recordatorio | El recordatorio sale igual; al marcar **Hecha** desde la notificación queda pendiente y sube después | manual | pendiente |
| KTP-8-33 | Interacción | Sin red | Llega la hora del resumen 7:00 a.m. / 9:00 p.m. | El resumen sale con los datos locales | manual | pendiente |
| KTP-8-34 | Interacción | Sin red | Toco **+ Crear "…"** épica nueva en el buscador | O se permite y sube después con su prefijo único, o se avisa que necesita red (ver pregunta 6) | manual | pendiente |
| KTP-8-35 | Interacción | Tarea creada desde `/spur` o la web mientras el celular estaba sin red | Vuelve la red | Llega al celular (con la notificación "Nueva tarea" de siempre, una vez) | manual | pendiente |
| KTP-8-36 | Interacción | Indicador de "sin conexión / pendientes" | Tema oscuro | Se lee bien | manual | pendiente |
| KTP-8-37 | No funcional | Sin red durante 8 horas | Mido batería | La app no se queda reintentando cada pocos segundos; espera a que vuelva la red | manual | pendiente |
| KTP-8-38 | No funcional | TalkBack | Hay cambios pendientes | El estado "pendiente de sincronizar" se anuncia, no es solo un color o ícono | manual | pendiente |

## Preguntas abiertas

1. **¿Qué entra en "gastos y tareas"?** ¿También hábitos (marcar, crear), épicas (crear, pausar/reanudar), pomodoros y editar? ¿O solo crear tarea y registrar gasto? *Sugerencia: crear/editar/marcar tareas y hábitos + todos los gastos.*
2. **¿Leer sin red?** ¿Debe la app mostrar las tareas que ya tenía (copia local) al abrirla sin red, o solo guardar lo nuevo?
3. **Conflictos:** si cambias la misma tarea en el celular sin red y en la web/`/spur`, ¿gana el último cambio, gana el celular, o se te pregunta?
4. **ID de la tarea sin red:** el `PREFIJO-n` lo reparte la base. ¿Está bien que la tarea muestre un ID provisional hasta subir?
5. **¿Cómo quieres ver que algo está pendiente?** (ícono en cada tarjeta, contador "3 sin subir" arriba, nada). ¿Avisar cuando todo subió?
6. **Crear épicas sin red** (el prefijo debe ser único): ¿se permite o se bloquea?
7. **Relación con el historial de capturas y "Reintentar":** ¿la sincronización automática reemplaza el botón Reintentar o conviven?
8. **¿Cada cuánto/cómo sincroniza?** ¿Al volver la red al instante aunque la app esté cerrada, o basta con el worker de 15 min / al abrir la app?

## Deuda de testabilidad

Automatizado el 2026-11-26 (modo `automatizar`, ya mirando el código) en
`app/src/test/java/co/eskala/bktpj_notification/ModoOfflineKtp8Test.kt`: 17 pruebas, 13 pasan y 4 marcadas
`@Ignore` por BUG-1 (×2), BUG-2 y BUG-3. La lógica pura está en `OutboxPolicy` (cola), `CacheCodec` (caché),
`CaptureRetryPolicy` (capturas) y `Balance`.

Sin prueba JVM posible hoy (necesitan Context o la base):
- `OfflineSync.flush` (orden capturas → cola, parar en el primer error de red, `remove`/`replace` por `seq`).
  Sugerencia: sacar el bucle a una función pura `flushStep(entries, result)` que reciba el resultado del envío;
  así BUG-1 se prueba sobre el código real y no sobre una simulación.
- `CardSaver.save` (id estable `nu_<captureId>`, decidir si avisar el error): necesita Context/Prefs.
- `OfflineCache.doneToday` (descarta las terminadas de otro día): usa `HabitDates.today()` dentro de un objeto con Context.
  Sugerencia: `CacheSnapshot.doneTodayFor(day)` puro.
- SQL nuevo (`ON CONFLICT DO NOTHING` en `insertTask`, `assignKey onlyIfMissing`), `done_at` y `occurred_at`:
  solo con una rama de prueba en Neon (integración pendiente).

## Revisión (modo `revisar`, conociendo el código)

Casos que faltaban y salen del código:
- KTP-8-39 · manual · Sin red, crear tarea y **enseguida** editarla con red lenta (la creación aún subiendo) → la edición no se pierde (BUG-1).
- KTP-8-40 · manual · Sin red: crear tarea → "Mañana" → editar y poner otra fecha → al volver la red queda la última fecha (BUG-3).
- KTP-8-41 · manual · Con red, crear una tarea con un dato que la base rechace (p. ej. épica borrada en la web) → hoy dice "Tarea creada" y el error solo aparece en la franja tras ~7 min (5 intentos). Decidir si es aceptable.
- KTP-8-42 · manual · Wi-Fi de portal cautivo varias horas con la app cerrada y luego red buena → cuánto tarda en subir (WorkManager puede haber crecido su espera hasta horas).
- KTP-8-43 · manual · Actualizar el APK con cambios en la cola → siguen ahí y suben.
