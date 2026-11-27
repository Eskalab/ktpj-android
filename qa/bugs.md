# Bugs encontrados por QA

## BUG-1 · Al editar, el texto de ayuda dice "crearla" · 2026-11-26
Gravedad: baja (visual)
Pasos: 1. Abrir una tarea existente para editar. 2. Borrar todo el título. 3. Bajar al final del formulario.
Esperado: un texto que hable de guardar (p. ej. "Escribe qué hay que hacer para poder guardarla.").   Obtenido: "Escribe qué hay que hacer para poder crearla." (el texto no distingue crear de editar).
Caso: KTP-11-06 / KTP-11-16  ·  Prueba: manual (revisión de código, `NewTaskScreen.kt`; no hay función pura que probar)  ·  Rama/commit: ktp-11-opcion-a / 6c3235c
