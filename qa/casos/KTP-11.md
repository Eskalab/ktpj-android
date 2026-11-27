# KTP-11 · Mejorar UX en agregar tarea

**Requerimiento (texto original):**
> Mejorar ux en agregar tarea
> Detalle: El botón crear tarea no deja ver las otras opciones de la nueva tarea y se confunde para no agregar. Ver opciones de UI sin dejar lo viable que es el botón grande. Propuesta 1 dejarlo al final sin que aparezca siempre

**Fuente:** tarjeta de Spur KTP-11 · escrito el 2026-11-26 · **sin mirar el código** (caja negra)

Se construyen tres opciones de diseño en paralelo:
- **A:** botón grande al final del formulario, se desplaza con él.
- **B:** barra fija abajo, compacta, con resumen de las opciones elegidas; el formulario tiene margen para que nada quede tapado.
- **C:** solo título + botón grande visibles; el resto en una sección plegable **"Más opciones"**.

Los casos 01–30 valen para **cualquier** opción. Al final hay verificaciones propias de cada una.
Este archivo es idéntico en las tres ramas.

## Casos (valen para A, B y C)

| ID | Tipo | Dado | Cuando | Entonces | auto/manual | Estado |
|---|---|---|---|---|---|---|
| KTP-11-01 | Feliz | Abro **+ Nueva tarea** en Tareas | Miro la pantalla sin tocar nada | Se ven (o se ve claramente cómo llegar a) todas las opciones: título, detalle, fecha, ★, épica, app, pomodoros estimados, selector Tarea \| ↻ Hábito. El botón de crear no tapa ninguna | manual | manual pendiente · código: nada fijo, todo se ve al desplazar |
| KTP-11-02 | Feliz | Formulario abierto | Escribo "Prueba UX 1", pongo Mañana, ★ y 2 🍅, y toco el botón de crear | Se crea una sola tarea con todos esos valores; se ve en Próximos días | manual | manual pendiente |
| KTP-11-03 | Feliz | — | Mido cuántos toques hay de abrir a crear una tarea solo con título | No más que hoy (título + 1 toque); el botón sigue siendo grande y fácil de tocar | manual | manual pendiente · atajo ✓ Crear arriba = título + 1 toque |
| KTP-11-04 | Alternativo | Abro **+ Tarea** desde Inicio (fecha Hoy precargada) | Creo | Queda con fecha Hoy, igual que antes | manual | manual pendiente |
| KTP-11-05 | Alternativo | Formulario abierto | Cambio a **↻ Hábito** | Se ven las opciones del hábito (días, meta, unidad, aviso, épica, app) y el botón no las tapa; crea un hábito | manual | ⚠ no cubierto: el formulario de Hábito no cambió (sigue con botón fijo) |
| KTP-11-06 | Alternativo | Abro una tarea existente para **editar** | Miro el formulario | Igual de claro; el botón dice guardar (no "crear") y el historial de pomodoros de la tarea se ve completo, no tapado | manual | manual pendiente · código: Guardar cambios va después del historial de pomodoros |
| KTP-11-07 | Alternativo | Formulario abierto | Uso el buscador de épica y **+ Crear "…"** | El buscador y su lista de resultados no quedan detrás del botón | manual | manual pendiente |
| KTP-11-08 | Alternativo | Formulario abierto | Elijo **Otra…** (calendario) y vuelvo | Vuelvo al mismo punto del formulario, con lo escrito intacto | manual | manual pendiente |
| KTP-11-09 | Alternativo | Formulario abierto | Elijo app asociada | Al volver, la app elegida se ve y nada se perdió | manual | manual pendiente |
| KTP-11-10 | Borde | Teclado abierto escribiendo el título | Miro la pantalla | El campo que escribo no queda tapado ni por el teclado ni por el botón; el botón no queda flotando encima del teclado tapando el campo | manual | manual pendiente · código: no hay botón flotando sobre el teclado |
| KTP-11-11 | Borde | Teclado abierto en el campo **detalle** (abajo) | Escribo varias líneas | El cursor siempre visible | manual | manual pendiente |
| KTP-11-12 | Borde | Teléfono pequeño (pantalla 5", o emulador 360×640) | Abro el formulario | Todo alcanzable con desplazamiento; nada queda escondido sin forma de llegar | manual | manual pendiente |
| KTP-11-13 | Borde | Letra del sistema al máximo y "tamaño de pantalla" grande | Abro el formulario | El botón no se corta ni tapa opciones; el texto del botón no se parte feo | manual | manual pendiente |
| KTP-11-14 | Borde | Teléfono con navegación por gestos y con 3 botones | Abro el formulario | El botón/barra no queda debajo de la barra de navegación del sistema | manual | manual pendiente |
| KTP-11-15 | Borde | Teléfono en horizontal | Abro el formulario | Se puede llenar y crear | manual | manual pendiente |
| KTP-11-16 | Borde | Título vacío | Toco crear | No crea; indica que falta el título (como hoy). El botón puede verse deshabilitado, pero se entiende por qué | manual | manual pendiente · código: botón deshabilitado + texto "Escribe qué hay que hacer…" (BUG-1: al editar dice "crearla") |
| KTP-11-17 | Borde | Título y detalle muy largos (500 caracteres) | Creo | Se crea; el formulario no se rompe; el botón no se mueve fuera de la pantalla | manual | manual pendiente |
| KTP-11-18 | Borde | Sin red | Toco crear | Mismo comportamiento que hoy (o el de KTP-8); el botón no queda "cargando" para siempre | manual | manual pendiente |
| KTP-11-19 | Negativo | Toco crear dos veces rápido | — | Se crea **una** sola tarea | manual | manual pendiente · igual que main (estado saving) |
| KTP-11-20 | Negativo | Lleno medio formulario | Toco atrás / cierro | No se crea nada. (¿Pide confirmar si hay datos? — pregunta 4) | manual | manual pendiente |
| KTP-11-21 | Negativo | Me desplazo por el formulario para ver opciones | Toco cerca del borde inferior | No se crea la tarea sin querer (el problema original: "se confunde para no agregar") | manual | manual pendiente · código: sin botón fijo abajo, no hay toque accidental |
| KTP-11-22 | Negativo | Giro el teléfono o la app pasa a segundo plano con el formulario a medias | Vuelvo | Lo escrito sigue ahí | manual | manual pendiente |
| KTP-11-23 | Interacción | Pomodoro corriendo (barra fina sobre pestañas) | Abro el formulario | La barra del pomodoro y el botón de crear no se encimen | manual | manual pendiente |
| KTP-11-24 | Interacción | Elijo una épica en pausa (⏸) | Creo | Se crea en esa épica (comportamiento actual) y no sale en Hoy | manual | manual pendiente |
| KTP-11-25 | Interacción | Al completar una tarea se abre **¿Qué sigue?** | Miro ese diálogo | Si usa el mismo formulario, tiene la misma mejora; épica heredada | manual | n/a: ¿Qué sigue? no cambió (pregunta 3) |
| KTP-11-26 | Interacción | Tema oscuro | Abro el formulario | El botón y sus estados (normal, deshabilitado) se ven bien | manual | manual pendiente |
| KTP-11-27 | Interacción | Pomodoros estimados − / + | Llego a 0 y a un número alto (10) | El texto "🍅🍅🍅 · total de enfoque" cabe y no empuja el botón | manual | manual pendiente |
| KTP-11-28 | No funcional | TalkBack | Recorro el formulario de arriba abajo | Todas las opciones se leen en orden y el botón de crear se anuncia al final (o en un orden lógico), con nombre claro | manual | manual pendiente · código: el atajo ✓ Crear se anuncia primero (arriba) |
| KTP-11-29 | No funcional | — | Comparo con la versión actual | Una persona nueva (prueba de pasillo) encuentra la ★ y la fecha sin ayuda en menos de 10 s | manual | manual pendiente |
| KTP-11-30 | No funcional | — | Abro el formulario 10 veces seguidas | Abre rápido, sin saltos de diseño | manual | manual pendiente |

## Verificaciones propias de cada opción

### Opción A · botón al final, se desplaza con el formulario
| ID | Dado | Cuando | Entonces | auto/manual | Estado |
|---|---|---|---|---|---|
| KTP-11-A1 | Formulario recién abierto en teléfono normal | Miro sin desplazar | Si el botón no se ve, hay una pista de que hay más abajo; si cabe todo, se ve | manual | manual pendiente · código: no hay pista visual de "hay más abajo" |
| KTP-11-A2 | Solo quiero crear con título | Escribo el título | ¿Puedo crear sin bajar? (tecla "Listo"/Enter del teclado crea, o el botón se alcanza) — sin esto, se pierde lo rápido | manual | manual pendiente · código: atajo ✓ Crear arriba con título; Enter en título pasa a Detalle |
| KTP-11-A3 | Cambio a ↻ Hábito (formulario más largo) | Bajo hasta el final | El botón está al final, no a mitad | manual | n/a: Hábito es otra pantalla y no cambió |

### Opción B · barra fija abajo con resumen
| ID | Dado | Cuando | Entonces | auto/manual | Estado |
|---|---|---|---|---|---|
| KTP-11-B1 | Bajo hasta el final del formulario | — | El último campo queda **por encima** de la barra (margen suficiente), nada tapado | manual | n/a en esta opción |
| KTP-11-B2 | Elijo Mañana, ★, épica PAS y 2 🍅 | Miro la barra | El resumen muestra exactamente lo elegido; cambia al instante si cambio algo | auto | n/a en esta opción |
| KTP-11-B3 | Épica con nombre largo y varias opciones | Miro la barra | El resumen se corta con "…" sin empujar ni encoger el botón | manual | n/a en esta opción |
| KTP-11-B4 | Teclado abierto | — | La barra sube con el teclado sin tapar el campo activo, o se esconde | manual | n/a en esta opción |

### Opción C · título + botón, resto en "Más opciones"
| ID | Dado | Cuando | Entonces | auto/manual | Estado |
|---|---|---|---|---|---|
| KTP-11-C1 | Formulario recién abierto | — | Solo título, botón grande y "Más opciones" visibles | manual | n/a en esta opción |
| KTP-11-C2 | Abro "Más opciones", elijo ★ y Mañana, lo cierro | Creo | La tarea se crea con ★ y Mañana (cerrar no borra lo elegido) y, cerrado, se ve algún indicio de lo elegido | manual | n/a en esta opción |
| KTP-11-C3 | Abro desde Inicio (Hoy precargado) | — | Se nota que la fecha es Hoy aunque "Más opciones" esté cerrado | manual | n/a en esta opción |
| KTP-11-C4 | **Editar** una tarea que ya tiene épica, ★ y app | Abro | "Más opciones" se abre solo o resume lo que tiene; no parece que la tarea no tiene nada | manual | n/a en esta opción |
| KTP-11-C5 | Hábito | Cambio a ↻ Hábito | Los días (obligatorios) no quedan escondidos dentro de "Más opciones" sin aviso | manual | n/a en esta opción |

## Preguntas abiertas

1. **¿Cómo vas a elegir entre A, B y C?** ¿Usarlas un par de días cada una? ¿Qué cuenta como "mejor": menos toques, no crear por error, o ver todo de un vistazo?
2. **"Se confunde para no agregar"**: ¿el problema es que creas tareas sin querer al tocar el botón, o que no te atreves a tocar porque no ves si ya pusiste todo? Cambia qué caso es el más importante (KTP-11-21 vs KTP-11-01).
3. **¿El cambio aplica también a editar, a hábitos y a "¿Qué sigue?"** o solo a crear tarea nueva?
4. **Salir con datos a medias**: ¿preguntar "¿descartar?" o salir sin preguntar (como hoy)?
5. **"Sin que aparezca siempre"**: ¿quiere decir que el botón no se vea hasta que haya título, o que no esté fijo en pantalla?
6. ¿Crear con la tecla **Listo/Enter** del teclado es aceptable como atajo rápido?

## Deuda de testabilidad

(Llenado en modo `automatizar`, opción A.)

- Opción A no tiene lógica pura nueva: 0 pruebas JVM nuevas posibles. Sugerencia: extraer `saveLabel(saving, isEdit)` y el texto de ayuda (ver BUG-1).
- **Datos guardados sin cambios vs main:** `build()` (qué se manda a `SpurTasks.create/update`) es una función local dentro
  del composable, así que no se puede probar en JVM. Revisado a mano: no cambió en esta rama. Sugerencia: extraer
  `fun buildNewTask(title, description, due, priority, app, projectId, pomos): NewTask` y probar recortes/nulos.
- **Hábito y ¿Qué sigue?** (`NewHabitScreen`, `NextTaskDialog`) no se tocaron: KTP-11-05/25 quedan fuera hasta responder la pregunta 3.
- Todo lo de pantalla (tapado, teclado, fuente grande, TalkBack real) necesita teléfono o pruebas Compose UI (pendientes en el README).
