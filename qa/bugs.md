# Bugs encontrados por QA

## BUG-1 · El chip corta un emoji por la mitad en nombres largos · 2026-11-26
Gravedad: baja (visual)
Pasos: 1. Crear o elegir una épica sin prefijo cuyo nombre tenga un emoji cerca del carácter 15 (p. ej. "Viaje a la pla🏖ya y más"). 2. Mirar el chip ◆ de la épica (lo mismo con el nombre de una app).
Esperado: "◆ Viaje a la pla…" (o con el emoji entero).   Obtenido: "◆ Viaje a la pla?…": `shorten()` usa `take(15)` sobre unidades UTF-16 y deja medio emoji (carácter roto).
Caso: KTP-11-17  ·  Prueba: app/src/test/java/co/eskala/bktpj_notification/ui/tasks/Ktp11ChipsQaTest.kt:recorteNoPartaUnEmoji (@Ignore)  ·  Rama/commit: ktp-11-opcion-c / cc36225
