# Ficha de Google Play — GlucoFrame

**Nombre (máx. 30):** GlucoFrame – Marco de glucosa
**Descripción breve (máx. 80):** Tu glucosa a la vista: marco digital, widgets y notificaciones.

## Descripción completa (español)

GlucoFrame convierte tu tablet o tu móvil en un marco digital de glucosa: una pantalla clara, con números grandes, flecha de tendencia y gráfica, pensada para mirarla de un vistazo desde lejos.

AVISO IMPORTANTE: GlucoFrame es una pantalla secundaria de consulta. No es un producto sanitario, no sustituye a la aplicación ni a las alarmas oficiales de tu sensor y no debe usarse para decidir tratamientos ni dosis de insulina. Consulta siempre a tu equipo médico.

QUÉ PUEDES HACER
• Ver tu glucosa en grande, con flecha de tendencia, variación y gráfica de 1 a 24 horas.
• Colores por rangos (bajo, en rango, alto) y modo noche, totalmente personalizables: números, fuente, gráfica, reloj y fondo.
• Widgets de pantalla de inicio con valor, variación y gráfica.
• Notificación en la barra de estado con el valor y el color de su nivel, y alarmas opcionales de bajada o subida.
• Lectura fiable en segundo plano, que se recupera sola tras reiniciar o ahorrar batería.
• Fondos propios: tus carpetas de fotos, ilustraciones incluidas en la app y fondos gratuitos de dominio público.
• Varios dispositivos: comparte las lecturas con otras pantallas de tu red Wi-Fi.
• Datos del sensor: fecha de activación, día de uso y días restantes.
• Anota insulina, comida y notas, y míralas sobre la gráfica.

FUENTES DE DATOS
Nightscout, Juggluco (local), cuenta de seguidor LibreLinkUp y difusión local de xDrip+/Juggluco.

PRIVACIDAD
Tus credenciales se guardan cifradas en el dispositivo. La app no tiene publicidad ni analíticas, y no envía tus datos a ningún servidor propio.

GlucoFrame es un proyecto independiente y no está afiliado, respaldado ni patrocinado por Abbott, Dexcom ni ningún fabricante. FreeStyle y LibreLink son marcas de sus respectivos propietarios.

## Gráficos
- Icono: `icono-512.png` (512×512, PNG).
- Gráfico de funciones: `grafico-1024x500.png`.
- Capturas de pantalla: faltan; hay que hacerlas desde el móvil (mínimo 2, ideal 4–8) y, si se publica para tablet, de 7" y 10".

## Antes de publicar (Play Console)
1. Cuenta de desarrollador de Google Play (pago único de 25 USD) y verificación de identidad.
2. **AAB firmado** con tu clave de subida: `./gradlew bundleRelease` con `KEYSTORE_PATH`, `STORE_PASSWORD` y `KEY_PASSWORD`. Guarda la clave y copias de seguridad fuera de GitHub.
3. **Política de privacidad** pública (URL obligatoria; la app maneja datos de salud).
4. **Seguridad de los datos** y **Declaración de apps de salud** (categoría de datos de salud y forma física: glucosa; cifrado en tránsito: sí; sin compartir con terceros).
5. **Clasificación de contenido** y público objetivo (adultos; no dirigida a menores).
6. **Declaraciones de permisos especiales** usados por la app: servicio en primer plano «specialUse», alarmas exactas (`SCHEDULE_EXACT_ALARM`), notificaciones a pantalla completa (`USE_FULL_SCREEN_INTENT`) y exención de ahorro de batería. Cada una exige justificar el uso.
7. Pruebas cerradas: las cuentas personales nuevas necesitan 12 probadores durante 14 días seguidos antes de pasar a producción.

## Riesgos de rechazo a tener en cuenta
- **LibreLinkUp:** la app usa la API no oficial de seguidores de Abbott. Puede incumplir sus condiciones de uso y dejar de funcionar sin aviso. Conviene destacar Nightscout/Juggluco y presentar LibreLinkUp como opcional.
- **Aplicaciones médicas:** Google exige que las apps que muestran datos de glucosa no hagan afirmaciones médicas engañosas. El aviso de arriba debe aparecer también en la propia app.
- **Nombre y marcas:** no uses «Libre», «FreeStyle», «Abbott» ni «Dexcom» en el nombre, el icono o las capturas.
