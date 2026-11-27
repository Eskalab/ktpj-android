# KTP-11 · Opción B — Barra fija compacta con resumen

**Idea.** Se mantiene un botón fijo abajo, pero en una barra de una sola fila: a la izquierda el resumen de lo que
se va a guardar (*Hoy · ★ · LAV · 🍅2*) y a la derecha **Crear**. El formulario nunca queda debajo de la barra y un
degradado avisa que hay más opciones al desplazar.

## Pantalla

```
┌────────────────────────────────────┐
│ TABLERO SPUR            Cancelar   │
│ Nueva tarea                        │
│ (Tarea) (↻ Hábito)                 │
│ Qué hay que hacer                  │
│ ┌────────────────────────────────┐ │
│ │ Llamar al notario              │ │
│ └────────────────────────────────┘ │
│ Detalle (opcional)                 │
│ ┌────────────────────────────────┐ │
│ └────────────────────────────────┘ │
│ Fecha                              │
│ (Sin fecha) (Hoy) (Mañana) (Otra…) │
│ Épica (opcional)                   │
│░░░░░░░░ degradado (hay más) ░░░░░░░│
├────────────────────────────────────┤  ← divisor (más oscuro si hay más abajo)
│ SE GUARDA          ┌─────────────┐ │
│ Hoy · ★ · LAV · 🍅2│    Crear    │ │
│                    └─────────────┘ │
└────────────────────────────────────┘
```

- **Cancelar** pasa a la cabecera; la barra ocupa una fila (antes dos botones apilados ≈ 140 dp).
- El resumen siempre dice algo: *Sin fecha* si no hay nada; agrega ★, prefijo de la épica (o su nombre),
  🍅N y ↗ App solo si están puestos.
- Tocar el resumen baja a las opciones. Con TalkBack se lee *"Se guardará: Hoy, prioritaria, épica LAV,
  2 pomodoros"*.
- En Detalle, **Listo** cierra el teclado y baja a las opciones.
- Igual al editar: botón **Guardar**.

## Pros
- El botón grande está siempre a mano (un toque, sin desplazar).
- El resumen hace visible lo que falta (*Sin fecha*, sin ★) justo donde se decide crear.
- Ocupa menos alto que la barra actual, así se ve más formulario.

## Contras
- El botón es la mitad del ancho (sigue siendo alto y fácil de tocar).
- Las opciones siguen debajo del pliegue; se confía en el resumen y el degradado para que se noten.
- Con fuente muy grande el resumen ocupa varias líneas y la barra crece.

## Qué mirar al probarlo
1. Crear una tarea solo con título: ¿el resumen *Sin fecha* hace pensar en ponerle fecha?
2. Cambiar fecha, ★, épica, 🍅 y app: el resumen cambia al instante; una épica recién creada aparece con su prefijo.
3. Tocar el resumen: baja hasta las opciones.
4. El degradado desaparece al llegar al final; el último elemento (★ y su texto) se ve completo.
5. Tema oscuro y fuente al 160 %: el resumen no se corta (envuelve en varias líneas).
