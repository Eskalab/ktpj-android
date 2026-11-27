# KTP-11 · Opción A — Botón al final

**Idea.** El botón grande **Crear tarea** deja de estar fijo abajo: va al final del formulario y se desplaza con él.
Para llegar a él hay que pasar por fecha, épica, pomodoros, app y ★, así que nada queda oculto detrás del botón.

## Pantalla

```
┌────────────────────────────────────┐
│ TABLERO SPUR            [✓ Crear]  │  ← solo con título escrito
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
│ (Elegir épica…)                    │
│ Pomodoros estimados   (−) 🍅🍅 (+) │
│ App (opcional)  (Elegir app…)      │
│ (★ Prioritaria)                    │
│ Sin estrella solo aparece en la…   │
├ ─ ─ ─ ─ desplazando ─ ─ ─ ─ ─ ─ ─ ┤
│ ┌────────────────────────────────┐ │
│ │          Crear tarea           │ │
│ └────────────────────────────────┘ │
│              Cancelar              │
└────────────────────────────────────┘
```

- Teclado: el título tiene **Siguiente** (pasa a Detalle); en Detalle, **Listo** cierra el teclado y baja con
  animación hasta el botón, pasando por las opciones.
- Atajo **✓ Crear** / **✓ Guardar** compacto arriba a la derecha, solo cuando hay título: deja guardar sin bajar
  y no tapa nada.
- Sin título, debajo del botón: *"Escribe qué hay que hacer para poder crearla."*
- Igual al editar (**Guardar cambios** al final, **✓ Guardar** arriba).

## Pros
- Es la propuesta 1 del usuario, cambio mínimo y predecible.
- El botón sigue siendo grande (ancho completo) y fácil de tocar.
- Toda la pantalla se usa para el formulario; con fuente grande nada se corta.

## Contras
- Para usar el botón grande hay que desplazarse siempre (en pantallas chicas, un gesto).
- El atajo de arriba vuelve a permitir crear sin mirar las opciones (aunque están a la vista debajo).
- El formulario sigue siendo largo.

## Qué mirar al probarlo
1. Crear una tarea solo con título: ¿se encuentra rápido el botón? ¿Se usa el **✓ Crear** de arriba?
2. Escribir título → Siguiente → Detalle → Listo: ¿baja al botón y se alcanzan a ver las opciones?
3. Editar una tarea con registro de pomodoros: el botón queda después del registro.
4. Tema oscuro y fuente al 160 %: nada cortado, el atajo de arriba no se superpone al rótulo.
5. TalkBack: el atajo se lee "✓ Crear, botón, Guardar la tarea".
