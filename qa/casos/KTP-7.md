# KTP-7 · Ubicación del teléfono en el momento de la transacción

**Requerimiento (texto original):**
> Agregar la ubicación del teléfono en el momento de la transacción financiera

**Contexto dado:** la tabla `transactions` ya tiene las columnas `location_lat`, `location_lng`, `location_address`.
Las capturas llegan por notificación de Nu, SMS de Davivienda, gasto manual y atajos, muchas veces con la app en segundo plano.

**Fuente:** tarjeta de Spur KTP-7 · escrito el 2026-11-26 · **sin mirar el código** (caja negra)

## Casos

| ID | Tipo | Dado | Cuando | Entonces | auto/manual | Estado |
|---|---|---|---|---|---|---|
| KTP-7-01 | Feliz | Permiso de ubicación concedido, GPS prendido, app abierta | Registro un gasto manual de $12.000 en "Café prueba" | La fila en `transactions` queda con `location_lat` y `location_lng` del lugar donde estoy (±100 m) y `location_address` con una dirección legible | manual | pendiente |
| KTP-7-02 | Feliz | Permiso concedido, app **en segundo plano** (pantalla apagada) | Llega una notificación de Nu de una compra | La transacción se guarda igual que hoy y además trae lat/lng; la captura no se demora ni se pierde por esperar la ubicación | manual | pendiente |
| KTP-7-03 | Feliz | Permiso concedido, app cerrada (deslizada de recientes) | Llega un SMS de Davivienda (remitente 87188) de compra aprobada | La transacción se guarda con lat/lng | manual | pendiente |
| KTP-7-04 | Alternativo | Permiso concedido | Toco un atajo (ej. *Peaje prueba*) | La transacción del atajo trae la ubicación de ese momento, no una guardada en el atajo | manual | pendiente |
| KTP-7-05 | Alternativo | El SMS de Davivienda es un **depósito** (abono sin transacción) | Llega el SMS | No se crea transacción, así que no hay ubicación que guardar; no falla nada | manual | pendiente |
| KTP-7-06 | Alternativo | SMS ignorado (declinada, código, traslado interno) | Llega el SMS | No se pide ubicación ni se crea fila (no gastar batería en algo que se descarta) | manual | pendiente |
| KTP-7-07 | Alternativo | Hay ubicación pero no hay internet para traducirla a dirección | Ocurre una captura | Se guardan lat/lng; `location_address` queda vacía (no inventada) y, si está definido, se completa después | manual | manual · parte pura automatizada (`sinDireccionNoSeInventaUna`); no se completa la dirección después |
| KTP-7-08 | Borde | El usuario **nunca** concedió el permiso de ubicación | Llega una compra de Nu | La transacción se guarda igual que antes, con las 3 columnas vacías (NULL). Ningún error, ninguna notificación de fallo | manual | pendiente |
| KTP-7-09 | Borde | Permiso concedido solo "mientras se usa la app" (no "todo el tiempo") | Llega una compra con la app en segundo plano | Comportamiento definido (ver pregunta 2): o se guarda sin ubicación, o se usa la última conocida marcada como tal. Nunca se bloquea la captura | manual | pendiente |
| KTP-7-10 | Borde | GPS apagado en el teléfono (ajustes rápidos) | Ocurre una captura | Se guarda sin ubicación o con la última conocida (según pregunta 3); no se queda esperando | manual | pendiente |
| KTP-7-11 | Borde | Estoy dentro de un edificio / sótano sin señal GPS | Ocurre una captura | Hay un tiempo máximo de espera (ej. pocos segundos); pasado ese tiempo se guarda sin ubicación o con la última | manual | pendiente |
| KTP-7-12 | Borde | La última ubicación conocida es de hace 3 horas en otra ciudad | Ocurre una captura | No se guarda como si fuera la actual (ver pregunta 3: antigüedad máxima aceptada) | auto | automatizado ✓ (`TxLocationQaTest.ubicacionDeHaceTresHorasNoSeGuarda`, `ubicacionViejaNoLeGanaAUnaReciente`) · máx. 15 min |
| KTP-7-13 | Borde | Coordenadas extremas válidas (lat -4.2, lng -69.9 Leticia; lat 12.5 San Andrés) | Se arma el dato a guardar | Se guardan con signo correcto y sin redondear a pocos decimales (al menos 5 decimales) | auto | automatizado ✓ (`coordenadasExtremasSeConservanConSignoYDecimales`) · JSON a Neon: revisado en código, sin prueba |
| KTP-7-14 | Borde | Coordenadas 0,0 o fuera de rango (lat 95) que devuelva un proveedor roto | Se arma el dato a guardar | Se descartan: se guarda sin ubicación, no un punto en el océano | auto | automatizado ✓ (`coordenadasInvalidasSeDescartan`, `sinUbicacionLasTresColumnasVanNull`) |
| KTP-7-15 | Borde | Formato de número del teléfono en español (coma decimal) | Se guardan lat/lng | En la base queda `4.65` y no `4,65` ni un número corrido | auto | automatizado ✓ (`localeEspanolNoCambiaElSeparadorDecimal`) |
| KTP-7-16 | Borde | Compra a las 11:58 p.m. hora Bogotá (ya es otro día en UTC) | Se guarda con ubicación | La ubicación corresponde a esa transacción y la transacción sigue apareciendo en el día correcto (Bogotá) en Inicio/Gastos | manual | pendiente |
| KTP-7-17 | Borde | Sin internet al momento de la compra | Llega la notificación de Nu | La captura queda en el historial del celular **con** su ubicación; al reintentar (botón **Reintentar**) se sube con la ubicación original, no con la del momento del reintento | manual | manual · revisado en código: el reintento manda la ubicación original (`locate = false`); sin prueba JVM (deuda) |
| KTP-7-18 | Borde | Teléfono recién reiniciado, aún sin ubicación conocida | Llega la primera compra | Se guarda la transacción; la ubicación vacía o pedida en ese momento, sin bloquear | manual | pendiente |
| KTP-7-19 | Borde | Llegan 2 notificaciones de Nu en el mismo segundo | Se capturan | Las dos se guardan, cada una con ubicación; no se duplica ni se pierde ninguna | manual | pendiente |
| KTP-7-20 | Borde | `location_address` muy larga (dirección con barrio, ciudad, departamento, país) | Se guarda | Se guarda completa o recortada sin romper; en pantalla se corta con "…" | auto | automatizado ✓ (`direccionMuyLargaSeRecortaConPuntosSuspensivos`, `direccionDe80CaracteresNoSeRecorta`) · recorta a 80 con "…" |
| KTP-7-21 | Negativo | Quito el permiso de ubicación en Ajustes de Android después de haberlo dado | Llega una compra | La captura funciona igual que sin permiso (KTP-7-08); la app no se cierra | manual | pendiente |
| KTP-7-22 | Negativo | Se muestra el diálogo de permiso de ubicación | Lo cierro con "Rechazar" / atrás | La app sigue usable, no vuelve a preguntar en bucle; hay forma de activarlo luego desde Ajustes de Spur | manual | pendiente |
| KTP-7-23 | Negativo | Proveedor de ubicación tarda o lanza error | Ocurre una captura | No aparece la notificación "No se pudo guardar" por culpa de la ubicación; el guardado de la transacción no depende de la ubicación | manual | pendiente |
| KTP-7-24 | Interacción | Pomodoro corriendo con notificación fija | Llega una compra de Nu | El pomodoro no se pausa ni se reinicia; la compra se guarda con ubicación | manual | pendiente |
| KTP-7-25 | Interacción | Gasto marcado **☐ Fijo** (arriendo) | Se captura | Se guarda la ubicación igual; el balance del mes y los totales no cambian por tener ubicación | manual | pendiente |
| KTP-7-26 | Interacción | Transacciones viejas (antes de KTP-7) sin ubicación | Abro Gastos, Balance del mes, historial | Se ven igual que antes, sin errores ni "0,0" | manual | pendiente |
| KTP-7-27 | Interacción | Si la ubicación se muestra en alguna pantalla (historial, detalle) | Tema oscuro | El texto/mapa se lee bien en tema oscuro | manual | pendiente |
| KTP-7-28 | Interacción | Web de KTPJ lee la misma tabla `transactions` | Se crean filas con ubicación | La web no se rompe por los datos nuevos | manual | pendiente |
| KTP-7-29 | No funcional | Un día normal con ~10 capturas | Mido batería (Ajustes › Batería) | Spur no aparece con consumo notablemente mayor que antes; no hay ubicación continua de fondo (sin ícono de ubicación permanente) | manual | pendiente |
| KTP-7-30 | No funcional | Privacidad | Reviso qué se guarda | Solo se pide ubicación en el momento de una transacción; no se guarda un rastreo continuo. El texto del permiso explica para qué es | manual | pendiente |
| KTP-7-31 | No funcional | TalkBack activo | Llego al diálogo de permiso y a donde se vea la ubicación | Todo se puede leer y aceptar/rechazar con TalkBack | manual | pendiente |

## Preguntas abiertas

1. **¿Para qué la quieres?** ¿Solo guardarla en la base, o verla en la app (en el historial, en el detalle del gasto, en un mapa, en el Balance "dónde gasto más")? Sin esto no sé qué pantallas probar. *Sugerencia: por ahora solo guardar + mostrar la dirección en el historial.*
2. **Permiso "todo el tiempo" (segundo plano):** Nu y Davivienda llegan con la app cerrada. Android pide un permiso extra ("Permitir todo el tiempo") que Google trata como sensible. ¿Lo aceptas? Si no, esas capturas quedarán sin ubicación.
3. **¿Vale la "última ubicación conocida"?** Es casi gratis en batería pero puede ser vieja. ¿Cuál es la antigüedad máxima aceptable (2 min, 15 min, 1 h)? ¿Se guarda una marca de que fue aproximada?
4. **La ubicación es del teléfono, no de la compra.** En compras por internet (Rappi, Netflix, PSE) o débitos automáticos, la ubicación será la de tu casa/oficina. ¿Se guarda igual, o hay que excluirlas? ¿Y las compras de Nu que llegan minutos tarde?
5. **Dirección (`location_address`):** ¿qué nivel de detalle? (calle exacta, barrio, solo ciudad). ¿Se puede usar el servicio de Google para traducir coordenadas (necesita internet y manda tus coordenadas a Google)?
6. **Gasto manual y atajos:** ¿se registra la ubicación de donde estás al escribirlo, aunque el gasto haya sido antes? ¿Debería poder editarse o borrarse la ubicación de un gasto?
7. **Al reintentar** una captura que falló, ¿se usa la ubicación del momento original (lo que asumí en KTP-7-17)?
8. **¿Interruptor en Ajustes** para apagar la ubicación por completo? ¿Precisión exacta o aproximada?
9. **Abonos/ingresos:** ¿también llevan ubicación o solo los cargos?

## Deuda de testabilidad

Escrito en modo `automatizar` (ya mirando el código, commit dfebcbe). Pruebas en
`app/src/test/java/co/eskala/bktpj_notification/TxLocationQaTest.kt` (10) además de `TxLocationTest.kt` del desarrollador (12).

- Lo puro quedó en `TxLocation.kt` (`LocationPick`, `AddressFormat`, `TxLocation.sqlParams`) y está cubierto.
- **Historial viejo sin ubicación (`captures.json`)**: `CaptureLog.read/readLocation` son privados y usan el
  `org.json` de Android, que no funciona en la JVM sin agregar `testImplementation("org.json:json:…")`.
  Revisado en código: una entrada sin `"location"` da `location = null` (compatible). Para probarlo haría falta
  extraer `fun captureFromJson(o: JSONObject): Capture` / `toJson` públicos y la dependencia de prueba.
- **Reintento con la ubicación original (KTP-7-17)**: `CardSaver.save/retry` dependen de `Context`, `Prefs` y Neon.
  Sugerencia: extraer `fun locationFor(retry: Capture?, tx): …` o inyectar el localizador.
- **INSERT**: no hay forma de ver la consulta sin `Context`; la prueba `insertDeTransaccionTieneTantosParametrosComoValores`
  lee el SQL del archivo fuente (frágil si cambia el formato, pero protege la numeración `$9..$11`).
- `TxLocator` (tiempos máximos, sin permiso, GPS apagado) es solo Android: queda manual (KTP-7-08..11, -18, -21, -23).

## Hallazgos de la revisión (`git diff main...HEAD`, sin confirmar en teléfono)

1. **SMS de Davivienda y el límite de tiempo del receptor (media, riesgo):** `SmsReceiver` usa `goAsync()` y ahora
   cada guardado puede esperar hasta 7 s por la ubicación antes de ir a Neon. Si Android trata ese broadcast con el
   límite corto (~10 s), un SMS + red lenta puede pasarse y Android puede matar el proceso. Probar KTP-7-03 con red lenta.
2. **Sin «Permitir todo el tiempo»** las capturas en segundo plano esperan hasta 4 s un fix que no llega y se
   guardan sin ubicación. No falla, pero demora; `capture()` no mira `hasBackgroundPermission`.
3. **El resultado del INSERT en `transactions` se ignora** (ya era así): si las columnas `location_*` no existen o
   tienen otro tipo en Neon, la tarjeta queda bien, la captura sale OK y la transacción se pierde en silencio.
   Confirmar tipos en Neon (`double precision`/`numeric` y `text`) antes de instalar.
4. **Permisos (baja):** en Android 11+ «Permitir todo el tiempo» abre los ajustes del sistema; si vuelves sin
   elegirlo, se abre otra vez la pantalla de ajustes de la app. Y si niegas el permiso y luego lo das desde
   ajustes de Android, el interruptor sigue apagado (hay que tocarlo otra vez).
5. **Gasto manual y atajos** esperan la ubicación (hasta 7 s) antes del toast "Tarjeta creada".
6. **Privacidad:** apagado por defecto ✓, solo en transacciones (`recordTransaction`) ✓, sin rastreo continuo ✓.
   La dirección se pide al `Geocoder` (servicio de Google: manda las coordenadas). Los abonos también llevan ubicación
   (pregunta 9 sin responder).
7. **Bien resuelto:** tiempo total máximo 7 s con `withTimeoutOrNull`, Geocoder fuera del hilo principal y con su
   propio tiempo, `runCatching` alrededor de todo, `$9..$11` coinciden con los valores, el reintento no vuelve a
   pedir ubicación, historial viejo compatible.
