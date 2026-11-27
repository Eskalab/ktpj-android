# QA de Spur — guía para humanos

QA (*quality assurance*) es la parte del trabajo que se pregunta **"¿esto hace lo que se pidió, y no rompió
nada de lo que ya funcionaba?"**. No es solo "correr tests": es decidir **qué** probar, **cuándo** y **cómo**.

Esta carpeta tiene todo lo de QA de Spur:

| Archivo | Para qué |
|---|---|
| `README.md` | Esta guía: tipos de prueba, cómo trabajamos, cómo usar el agente |
| `regresion.sh` | Corre todas las pruebas automáticas (y opcional el APK) |
| `smoke.md` | Revisión de 5 minutos en el teléfono después de instalar un APK |
| `regresion-manual.md` | Lo que no se puede automatizar, por área, y qué revisar según lo que cambió |
| `casos/` | Casos de prueba por requerimiento (`KTP-7.md`…). `_plantilla.md` es el molde |
| `bugs.md` | Bugs encontrados (se crea con el primero) |

El agente **`qa-spur`** (`.claude/agents/qa-spur.md`) sigue esta guía.

---

## 1. Tipos de prueba

Tenías razón en dos cosas: las pruebas se diseñan **a partir del requerimiento, sin mirar el código**, y hay
pruebas de **regresión**. Estas son las demás, con lo que significan para Spur:

| Tipo | Pregunta que responde | En Spur | Estado |
|---|---|---|---|
| **Aceptación (caja negra)** | ¿Hace lo que se pidió? | Casos escritos desde el texto de la tarea KTP, sin ver código | ✓ Agente, modo `casos` |
| **Unitarias** | ¿Cada pieza de lógica calcula bien? | Racha de hábitos, montos, lector de SMS, tiempo del pomodoro, prefijos | ✓ 42 pruebas en `app/src/test` |
| **Regresión** | ¿Lo nuevo rompió lo viejo? | Correr **todas** las pruebas + la lista manual antes de pasar a main | ✓ `regresion.sh` + `regresion-manual.md` |
| **Smoke (humo)** | ¿El APK prende y lo básico anda? | 5 minutos: abre, carga tareas, crea una, pomodoro, gasto | ✓ `smoke.md` |
| **Bordes y negativos** | ¿Qué pasa en los extremos o con datos malos? | Sin red, 0 tareas, medianoche, fecha inválida, cancelar a mitad | ✓ Dentro de cada caso |
| **Exploratoria** | ¿Qué se rompe si uso la app "mal"? | 20 min usando una función a propósito de formas raras, anotando | Manual, cuando quieras |
| **Integración** | ¿La app y la base se entienden? | Consultas SQL a Neon, `done_at` en UTC, IDs tipo Jira | Pendiente: necesita una rama de prueba en Neon |
| **UI / instrumentadas** | ¿Las pantallas responden bien? | Compose UI tests en emulador | Pendiente: más costosas, para después |
| **No funcionales** | ¿Es rápida, accesible, segura, gasta poca batería? | TalkBack, textos grandes, Doze (alarmas en reposo), Android 8 a 14 | En la lista manual |
| **Migración de datos** | ¿Un cambio en la base rompe datos viejos? | Nueva columna en Neon, cambio de formato en SharedPreferences | Revisar en cada migración |

### La pirámide

Muchas **unitarias** (rápidas, baratas, corren en 1 minuto), algunas de **integración** y pocas **manuales/UI**
(lentas, necesitan el teléfono). Si algo se puede probar como unitaria, se prueba así.

### Caja negra y caja blanca

- **Caja negra:** pruebas sin conocer el código, solo el requerimiento. Detectan "se programó otra cosa".
- **Caja blanca:** conociendo el código. Detectan ramas internas sin probar.
- Las 42 pruebas actuales son **caja blanca de regresión**: se escribieron sobre lo que ya funciona para
  que nadie lo rompa sin enterarse. Los casos nuevos (modo `casos`) son caja negra.

---

## 2. Cómo trabajamos un requerimiento

```
 Tú escribes la tarea en Spur (KTP-7: "…")
   │
   ├─▶ 1. qa-spur casos KTP-7      → qa/casos/KTP-7.md + PREGUNTAS   (no mira el código)
   │      Respondes las preguntas; el requerimiento queda claro.
   ├─▶ 2. Se programa en una rama   (ktp-7-…)
   ├─▶ 3. qa-spur automatizar KTP-7 → pruebas JVM para los casos "auto"
   ├─▶ 4. qa-spur regresion ktp-7-… → todo pasa + lista manual corta + veredicto
   ├─▶ 5. Instalas el APK y haces smoke.md + la lista manual
   └─▶ 6. Pasa a main
```

El paso 1 en paralelo con el 2 es justo lo que dijiste: los casos salen del requerimiento y no del código.

---

## 3. Usar el agente

En Claude Code, dentro de `~/yo.inc/nu-ktpj`, pídelo con palabras normales:

- *"Usa qa-spur en modo casos para KTP-7: al terminar un pomodoro quiero que suene distinto si es descanso largo"*
- *"qa-spur automatiza KTP-7"*
- *"qa-spur regresión de la rama ktp-5-calendario"*
- *"qa-spur revisa qué riesgo tiene la rama ktp-4"*

El agente **no cambia código de la app, no toca la base y no hace commits**: reporta bugs y te deja la decisión.

---

## 4. Correr las pruebas tú mismo

```bash
cd ~/yo.inc/nu-ktpj
qa/regresion.sh          # solo pruebas, ~1 min
qa/regresion.sh --apk    # pruebas + APK
```

Sale `Pruebas: 42 de 42 pasan` o la lista de las que fallan con el motivo
(`esperaba 20.600 pero fue 20.601`). El detalle bonito está en
`app/build/reports/tests/testDebugUnitTest/index.html`.

**Si una prueba falla:** no se cambia la prueba para que pase. Primero se entiende: o el código tiene un bug,
o el comportamiento cambió a propósito (y entonces se actualiza la prueba y se dice por qué en el commit).

---

## 5. Riesgos conocidos (para tener en el radar)

- **Credenciales de Neon dentro del APK** (`BuildConfig.NEON_*` desde `local.properties`): quien tenga el APK
  puede sacarlas. Aceptable mientras sea solo para tu teléfono; no compartir el APK.
- **Zona horaria:** la base guarda en UTC y la app muestra hora de Bogotá. Todo lo que filtre "hoy" es zona de
  riesgo entre las 7 p.m. y la medianoche (en UTC ya es mañana).
- **Datos solo en el celular:** historial de capturas, conteo de pomodoros y atajos. Si se borra la app, se pierden.
- **Alarmas y Doze:** pomodoro, resumen diario y avisos de hábitos dependen de que Android no mate la app.
  Probar con la pantalla apagada un rato.

---

## Glosario

- **Caso de prueba:** una situación concreta con resultado esperado. *Dado* (estado inicial) / *Cuando*
  (acción) / *Entonces* (resultado).
- **Regresión:** algo que funcionaba y dejó de funcionar por un cambio.
- **Smoke test:** prueba rápida para saber si vale la pena seguir probando.
- **Flaky:** prueba que a veces pasa y a veces no sin cambiar nada (casi siempre por la hora o la red). Se arregla, no se ignora.
- **Cobertura:** qué parte del código ejecutan las pruebas. Útil como pista, no como meta.
