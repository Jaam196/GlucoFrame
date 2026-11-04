# GlucoseFrame — Marco Digital de Glucosa para Tablet

GlucoseFrame convierte una tablet Android en un marco digital pasivo y continuo para monitorizar lecturas de glucosa de sensores FreeStyle Libre 2 Plus (vía cuenta de seguidor LibreLinkUp), Nightscout o Juggluco local.

> **AVISO MÉDICO OBLIGATORIO:**  
> Esta aplicación es una pantalla secundaria pasiva. **NO sustituye las alarmas acústicas oficiales ni la app oficial de Abbott (FreeStyle LibreLink / LibreLinkUp)** y no debe emplearse como única fuente para decisiones de tratamiento ni dosificación de insulina. Consulta siempre a tu equipo médico.

---

## 1. Pasos para crear y configurar la cuenta de seguidor (LibreLinkUp)

Para evitar bloqueos o peticiones 429 de Abbott, la cuenta de seguidor debe configurarse siguiendo estrictamente estos pasos:

1. **En la app FreeStyle Libre del portador del sensor:**
   - Abre el menú lateral y ve a **Conectar / Compartir** (o *Aplicaciones conectadas*).
   - Pulsa en **LibreLinkUp** y luego en **Añadir conexión**.
   - Introduce el nombre y un correo electrónico exclusivo para el seguidor (distinto del correo del portador).
2. **En el dispositivo del seguidor:**
   - Descarga la app **LibreLinkUp** desde Google Play Store.
   - Crea la cuenta de seguidor seleccionando el **mismo país / región** en el que se creó la cuenta del portador (por ejemplo, España).
   - Abre la invitación recibida por correo y acéptala en la app LibreLinkUp para confirmar la vinculación con el paciente.
3. **Paso crítico (Exclusividad de la cuenta):**
   - Una vez aceptada la invitación, **cierra sesión (Log out)** en la app LibreLinkUp de tu teléfono móvil.
   - La cuenta de seguidor debe ser **exclusiva para GlucoseFrame**. Si dos aplicaciones usan la misma cuenta de seguidor simultáneamente, Abbott bloqueará las peticiones con código `HTTP 429` o error de sesión.
4. **En GlucoseFrame (Tablet):**
   - Entra en Configuración (mantén pulsada la pantalla 2 segundos).
   - Ve a la pestaña **LibreLinkUp**.
   - Introduce el correo y contraseña del seguidor y pulsa **Guardar credenciales y comprobar conexión**.

---

## 2. Compilación y Exportación

Para compilar el proyecto en Android Studio o generar el APK:

```bash
# Compilar APK debug
gradle assembleDebug

# Ejecutar pruebas unitarias
gradle :app:testDebugUnitTest
```

### Requisitos:
- Android Studio Ladybug / Meerkat o superior
- JDK 17 o 21 (Gradle toolchain compatible con Java 11 bytecode)
- Dispositivo Android con `minSdk 21` (Android 5.0) o superior y pantalla de tablet recomendada (ancho >= 600 dp). En Android 5 (API 21-22) la clave de cifrado se guarda en un archivo privado de la app, porque AndroidKeyStore solo admite claves AES desde Android 6.

### Móvil y tablet
- Ajustes se adapta al ancho: en pantallas < 600 dp (móvil) las secciones van en pestañas con desplazamiento arriba; en tablet, en un menú lateral con desplazamiento vertical.
- El marco principal cambia entre vertical y horizontal automáticamente.

---

## 2b. Personalización de la pantalla

En **Ajustes → Apariencia → Estilo de pantalla** (con vista previa en directo, de día y de noche):

- **Colores por nivel de glucosa**: un color para cada tramo de azúcar (con su rango), cambiable con rueda de color o código HEX.
- **Número**: tipo de letra, tamaño, efecto (sombra / resplandor), color según el nivel o un color propio, y qué se muestra (icono de nivel, flecha, unidad, hora, diferencia, tarjeta de fondo).
- **Número de noche**: igual que de día, atenuado o con un color propio.
- **Gráfica**: línea / puntos / área, color por nivel o propio, grosor, banda objetivo, cuadrícula, ejes, altura máxima, botones de horas.
- **Reloj**: posición, tamaño, tipo de letra, color, 12/24 h, segundos, fecha, recuadro.
- **Widgets**: fondo (color del nivel, propio, oscuro o transparente), opacidad, esquinas, color y tamaño del texto, qué datos se muestran.

### Fuente «Apps locales» (sin internet)

En **Ajustes → Fuente de datos → Apps locales** la app recibe la glucosa directamente de otras apps del mismo dispositivo, como hace GlucoDataHandler:
xDrip+, Juggluco (difusión «xDrip+» o «Glucodata»), Diabox y Dexcom BYODA. En esa pantalla se muestra el nombre de paquete que hay que escribir en la otra app y el estado de la última lectura recibida. Solo se aceptan lecturas mientras esa fuente esté seleccionada.

### Varios dispositivos (red local, sin internet)

Un dispositivo (el «principal») recibe la glucosa del sensor y la comparte por el Wi-Fi; los demás se conectan a él:

1. En el principal: **Ajustes → Fuente de datos → Compartir con otros dispositivos**. Aparecen su dirección y un código de acceso.
2. En cada tablet o móvil: **Fuente de datos → Servidor local**; luego en **Nightscout/Local → Buscar dispositivos GlucoseFrame**, elige el principal y escribe el código en «Token».
3. Los demás consultan al principal cada 20 s, y solo el principal habla con Abbott o con la otra app (así no hay bloqueos por usar la misma cuenta en dos sitios).

El servidor solo responde a la red local (192.168.x.x, 10.x.x.x, 172.16–31.x.x) y a VPN tipo Tailscale (100.64.x.x), solo lectura y siempre con código. Habla el mismo formato que xDrip+/Juggluco (`/sgv.json`), así que otras apps compatibles también pueden leerlo. Fuera de casa, la forma sencilla es una VPN como Tailscale en ambos dispositivos.

### Widgets

- **Glucosa** (compacto y grande): ahora con la diferencia respecto a la lectura anterior (+4, -7…).
- **Glucosa + gráfica** (nuevo, 3×2): número del color del nivel actual, flecha, diferencia, barra de color del nivel, hora y gráfica reciente (1–24 h).
- **Notificación de la barra de estado** (Ajustes → Barra y widget), como GlucoDataHandler: fondo del sistema, número y flecha en el color del nivel, flecha que se inclina según la tendencia (doble en cambios rápidos), «Δ +1», nombre del nivel y hora. Otros aspectos: fondo del color del nivel, tarjeta oscura o simple. El iconito de la barra superior (número, diferencia o flecha) lo dibuja Android en un solo color; opcionalmente se añade un segundo iconito con una notificación extra vacía.

En **Personalización de fondos → Carpeta personalizada** se leen también las subcarpetas.

---

## 3. Arquitectura y Seguridad
- **Room como única fuente de verdad:** La interfaz de usuario observa reactivamente el estado de la base de datos local y nunca realiza lecturas directas de red.
- **Cifrado Keystore AES-GCM:** Las credenciales y tokens se almacenan cifrados con hardware Keystore en `security_credentials.dat`, excluido de copias de seguridad de Android.
- **Modelo de Frescura:**
  - `ACTUAL` (≤ 5 min): Número gigante con color y forma de zona.
  - `RETRASADA` (5–10 min): Número atenuado con banda ámbar y contador de edad.
  - `OBSOLETA` (> 10 min): El número gigante desaparece; muestra "SIN DATOS ACTUALES" y la última lectura en gris pequeño.
  - `SIN DATOS`: Pantalla informativa "Sin datos".

---

## 4. Atribución y Licencia
Lógica de comunicación y protocolo basada en el proyecto de código abierto **GlucoDataHandler**:
Copyright (c) 2023 Michael Pach — Licencia MIT. Consulta el archivo `NOTICE.md` para más información.

## Fondos: novedades

- **Fotos de serie** nuevas (ilustraciones propias, sin derechos de autor) en las categorías *lugares, oficina, gaming, hogar* y *familia* (4 por categoría). Los fondos se pueden ampliar copiando imágenes en `app/src/main/assets/backgrounds/<categoría>/`. Para fotos reales (por ejemplo de tu familia) usa **Carpeta personalizada**.
- **Tiempo de cada foto**: con el cambio automático activado, cada miniatura tiene un «⏱ tiempo general» que se puede cambiar (15 s … 1 h) foto por foto, también para las fotos de tu carpeta.
- **Zoom lento (efecto cine)** y **nombre de la foto** opcionales.

## Fondos de la comunidad (gratis, sin derechos de autor)

En **Personalización de fondos → Descargar fondos gratis de la comunidad** se buscan y descargan fotos de Wikimedia Commons (naturaleza, paisajes, lugares, espacio, oficina, gaming, hogar, familia o búsqueda libre).

- Solo se ofrecen archivos cuya licencia en Commons es **CC0 o dominio público** (no piden atribución). Aun así, se guarda el autor, la licencia y el enlace de origen, visibles en la pestaña «Descargados».
- Se descargan reducidos (≤ 2200 px) y se añaden a la biblioteca en su categoría; «Descargar y aplicar» lo deja como fondo al momento.
- Para publicar en Google Play: la licencia viene de los metadatos de Commons (fiables pero no infalibles), y las fotos podrían contener marcas o personas. Revisa los fondos que vayas a destacar y usa «Borrar» o desactiva (✓) los que no quieras.
- Los 20 fondos ilustrados incluidos en `assets/backgrounds/` son originales creados para este proyecto.

## Diagnóstico de errores

Si la app se cierra, el motivo queda guardado en el dispositivo y se muestra en **Ajustes → Diagnóstico** (se puede copiar). Nada se envía a ningún sitio.

## Nota técnica: layouts de notificación y widgets

Las notificaciones y los widgets solo admiten un grupo reducido de vistas (FrameLayout, LinearLayout, RelativeLayout, TextView, ImageView, Button, ProgressBar…). Usar `<View>` o `<Space>` en esos layouts hace que Android rechace la notificación y cierre la app («Bad notification… Couldn't inflate contentViews»). Los espaciadores se hacen con `FrameLayout`.

## Sistema de notificaciones (como GlucoDataHandler)

- **Notificación fija** con número y flecha en el color del nivel, flecha que gira según la velocidad de cambio y «Δ». Iconito de la barra a elegir (número, diferencia, flecha) y segundo iconito opcional.
- **Botón de campana** en la pantalla principal (arriba a la izquierda): activa o desactiva de golpe todas las notificaciones y alarmas sin perder los ajustes de cada una. También hay un interruptor general en Ajustes → Barra y widget. Si el servidor local está activo, queda un aviso mínimo y silencioso (Android lo exige mientras el servicio funciona).
- **Alarmas** (desactivadas por defecto) en Ajustes → Barra y widget: muy baja, baja, alta, muy alta y sin datos; con sonido o solo vibración, repetición, botón «Posponer», pantalla completa con el móvil bloqueado y botón de prueba. No sustituyen las alarmas oficiales del sensor.

**Iconito de la barra superior en color:** por defecto se dibuja con el color del nivel. Los móviles que lo permiten (por ejemplo Samsung One UI) lo muestran en ese color, como GlucoDataHandler; los que lo tiñen con el color del sistema lo muestran blanco. Se puede desactivar en Estilo de pantalla → «Barra de estado y notificación», donde también se elige el iconito extra (diferencia o flecha), que usa el mismo color.

## Lectura fiable en segundo plano

La lectura no depende de que la app esté abierta:

- **Despertador del sistema** (`SyncAlarm`): Android despierta el dispositivo cada minuto (incluso con la pantalla apagada o en Doze y aunque se haya cerrado el proceso), lee la glucosa con un bloqueo de CPU de 60 s y programa el siguiente despertador *antes* de leer, para que la cadena no se rompa nunca. Con el permiso de «alarmas exactas» es puntual; sin él se usa la variante que se puede retrasar en Doze.
- **Servicio en primer plano** (activado por defecto, «Lectura siempre activa en segundo plano»): mantiene la app viva con un aviso mínimo y silencioso, y vuelve a lanzarse solo si Android lo detiene.
- **Auto-reparación**: la cadena de alarmas se reactiva al abrir la app, al arrancar el dispositivo, cada vez que se crea el proceso y cada 15 min con el vigilante de WorkManager.
- **Aviso en pantalla** si la app tiene restricciones de batería (pulsar el aviso amarillo pide quitarlas). En Samsung: Batería → Límites de uso en segundo plano → «Apps que nunca se suspenden».

## Más datos: sensor, insulina y comida

- **Sensor (LibreLinkUp):** la app guarda el número de serie, la fecha de activación, el calentamiento, la versión de la app Libre, el objetivo de tu cuenta, los límites del dispositivo y si las alarmas están activadas. Se ve en Ajustes → Fuente de datos → «Sensor» (con día de uso, tiempo restante y fecha de fin) y en la pantalla principal como «Sensor 6/14» (naranja el último día, rojo al caducar). LibreLinkUp no informa de cuántos días dura el sensor: se elige 14 o 15 días en esa misma tarjeta.
- **Insulina y comida:** botón «+» en la pantalla principal para anotar insulina (unidades), comida (gramos de hidratos) o una nota, ahora o hace 15/30/60 min. Se dibujan en la gráfica (triángulo azul = insulina, círculo naranja = comida, rombo gris = nota) y las anotaciones propias se pueden borrar. Se guardan en una base de datos aparte (`treatments.db`), 30 días.
- **Nightscout:** si la fuente es Nightscout, la app lee cada 5 minutos las insulinas, hidratos y notas registradas en `/api/v1/treatments.json` (48 h) y las muestra igual.
- Ambas cosas se pueden ocultar en Estilo de pantalla → «Resto de la pantalla».

**Límite importante:** las notas de insulina y comida que se anotan en la app FreeStyle Libre / LibreLink **no se pueden leer** desde una cuenta de seguidor (LibreLinkUp). Su API solo entrega glucosa, tendencia, datos del sensor y alarmas; las notas viven en la cuenta del paciente y en LibreView, que no tienen una API pública. Por eso la app ofrece el registro propio y la lectura de Nightscout.
