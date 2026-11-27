# KTP-11 · Opción C — Chips de opciones (divulgación progresiva)

**Idea.** Debajo del título, una fila de chips muestra cada opción con su valor (📅 Hoy · ★ · ◆ LAV · 🍅 2 · ↗ App ·
Detalle). Tocar un chip abre su editor ahí mismo, y el botón grande va justo debajo: todo cabe en una pantalla.

## Pantalla

```
┌────────────────────────────────────┐
│ TABLERO SPUR                       │
│ Nueva tarea                        │
│ (Tarea) (↻ Hábito)                 │
│ Qué hay que hacer                  │
│ ┌────────────────────────────────┐ │
│ │ Llamar al notario              │ │
│ └────────────────────────────────┘ │
│ [📅 Hoy] (☆ Prioritaria) (+ Épica) │  ← relleno = tiene valor
│ (+ 🍅) (+ App) (+ Detalle)         │
│ ┌ Fecha ─────────────────── Listo ┐│  ← solo si se tocó un chip
│ │(Sin fecha)[Hoy](Mañana)(Otra…)  ││
│ └─────────────────────────────────┘│
│ ┌────────────────────────────────┐ │
│ │          Crear tarea           │ │
│ └────────────────────────────────┘ │
│              Cancelar              │
└────────────────────────────────────┘
```

- Chips: **📅 fecha** (siempre con valor: *Sin fecha*, *Hoy*, *5 nov*), **★/☆ Prioritaria** (se activa directo, sin
  editor; muestra una línea con el recordatorio), **◆ épica**, **🍅 N**, **↗ app**, **✎ Detalle**. Los que no tienen
  valor dicen **+ …**.
- Un editor abierto a la vez, con los mismos controles de hoy (DueChips, buscador de épicas, − / +, elegir app).
  Tocar el chip otra vez o **Listo** lo cierra.
- El título tiene **Listo** en el teclado: lo cierra y deja a la vista chips y botón.
- Al editar, los chips salen con los valores de la tarea; el registro de pomodoros va debajo de los botones.
- Chips de 44 dp de alto mínimo; TalkBack lee *"Fecha: Hoy, toca para cambiar"*, *"Prioritaria: no, desactivada"*.

## Pros
- Todas las opciones y sus valores se ven a la vez, junto al botón: imposible no notarlas.
- Sin desplazar en el caso común; el botón grande de ancho completo se mantiene.
- Pantalla más corta y limpia; el detalle se esconde hasta que se necesita.

## Contras
- Es el cambio más grande: hay que aprender que los chips se tocan para editar.
- Un toque extra para cambiar fecha/pomodoros (antes estaban expuestos).
- El detalle queda escondido; al editar una tarea con detalle hay que abrir el chip para leerlo.

## Qué mirar al probarlo
1. Crear con título + Hoy + ★ sin desplazar: ¿se entiende que los chips abren opciones?
2. Abrir cada chip y cerrarlo con **Listo** o tocando el chip; el valor del chip cambia al instante.
3. Crear una épica desde el buscador: el chip pasa a **◆ PRE**.
4. Editar una tarea con todo puesto: chips rellenos con sus valores; Guardar cambios no pierde nada.
5. Fuente al 160 % y tema oscuro: los chips envuelven en varias filas, nada se corta; el botón sigue visible o a un
   gesto.
