package com.example.core.model

/** Letter style for the big number and the clock (system fonts, so it works from Android 5 on). */
enum class NumberFont(val displayName: String, val systemFamily: String, val bold: Boolean) {
    THIN("Muy fina", "sans-serif-thin", false),
    LIGHT("Fina", "sans-serif-light", false),
    REGULAR("Normal", "sans-serif", false),
    MEDIUM("Media", "sans-serif-medium", false),
    BOLD("Negrita", "sans-serif", true),
    BLACK("Extra negrita", "sans-serif-black", false),
    CONDENSED("Condensada", "sans-serif-condensed", true),
    SERIF("Serif", "serif", true),
    MONO("Monoespaciada", "monospace", true),
    CURSIVE("Cursiva", "cursive", false)
}

enum class NumberEffect(val displayName: String) {
    NONE("Ninguno"),
    SHADOW("Sombra"),
    GLOW("Resplandor")
}

/** Where the colour of a glucose element comes from. */
enum class ColorSource(val displayName: String) {
    ZONE("Según el nivel de glucosa"),
    CUSTOM("Un color propio")
}

/** Colour of texts that are not tied to a glucose level (clock, widget text). */
enum class TextColorSource(val displayName: String) {
    AUTO("Automático"),
    CUSTOM("Un color propio")
}

enum class NightNumberMode(val displayName: String) {
    SAME("Igual que de día"),
    DIMMED("Atenuado"),
    CUSTOM("Color propio de noche")
}

enum class ChartStyle(val displayName: String) {
    LINE_POINTS("Línea y puntos"),
    LINE("Solo línea"),
    POINTS("Solo puntos"),
    AREA("Área rellena")
}

enum class ClockPosition(val displayName: String) {
    LEFT("Izquierda"),
    CENTER("Centro"),
    RIGHT("Derecha")
}

enum class WidgetBackground(val displayName: String) {
    ZONE_COLOR("Color del nivel"),
    CUSTOM_COLOR("Color propio"),
    DARK("Oscuro"),
    TRANSPARENT("Transparente")
}

enum class WidgetCorner(val displayName: String) {
    SQUARE("Cuadrado"),
    SMALL("Poco redondo"),
    MEDIUM("Redondeado"),
    LARGE("Muy redondeado"),
    PILL("Cápsula")
}

/** Look of the notification card (like GlucoDataHandler: system background, coloured number and rotating arrow). */
enum class NotificationCardStyle(val displayName: String) {
    SYSTEM("Como GlucoDataHandler"),
    LEVEL("Fondo del color del nivel"),
    DARK("Tarjeta oscura"),
    PLAIN("Notificación simple")
}

/** An optional second status-bar icon (GlucoDataHandler offers this too). */
enum class ExtraStatusIcon(val displayName: String) {
    NONE("Ninguno"),
    DELTA("Diferencia"),
    TREND("Flecha de tendencia")
}

/** What the little status-bar icon shows (Android always draws it single-colour). */
enum class StatusIconMode(val displayName: String) {
    VALUE("Número de glucosa"),
    DELTA("Diferencia (+4, -7)"),
    TREND("Flecha de tendencia")
}

/**
 * Everything the user can customise about what is drawn on the reading screen and on the widgets.
 * The defaults reproduce the original look of the app.
 */
data class DisplayStyle(
    // ---- Big number ----
    val numberFont: NumberFont = NumberFont.BLACK,
    val numberSizeScale: Float = 1.0f,            // 0.5 .. 1.5
    val numberEffect: NumberEffect = NumberEffect.SHADOW,
    val numberColorSource: ColorSource = ColorSource.ZONE,
    val numberCustomHex: String = "#FFFFFF",
    val showZoneBadge: Boolean = true,
    val showTrendArrow: Boolean = true,
    val showUnit: Boolean = true,
    val showInfoRow: Boolean = true,              // time • age
    val showDelta: Boolean = true,
    val infoTextScale: Float = 1.0f,              // 0.7 .. 1.8 (unit, time, age, delta)
    val showPanel: Boolean = true,                // card behind the number
    val panelOpacity: Float = 0.65f,              // 0 .. 1

    // ---- Number at night ----
    val nightNumberMode: NightNumberMode = NightNumberMode.DIMMED,
    val nightDimLevel: Float = 0.6f,              // 0.15 .. 1
    val nightCustomHex: String = "#FF8A65",

    // ---- Chart ----
    val chartVisible: Boolean = true,
    val chartStyle: ChartStyle = ChartStyle.LINE_POINTS,
    val chartColorSource: ColorSource = ColorSource.ZONE,
    val chartCustomHex: String = "#00E5FF",
    val chartLineWidthDp: Float = 3f,             // 1 .. 8
    val chartPointRadiusDp: Float = 3f,           // 1 .. 8
    val chartShowTargetBand: Boolean = true,
    val chartBandOpacity: Float = 0.12f,          // 0 .. 0.5
    val chartShowGrid: Boolean = true,
    val chartShowAxisLabels: Boolean = true,
    val chartShowRefLine: Boolean = true,
    val chartMaxMgDl: Int = 300,                  // top of the vertical axis
    val chartPanelOpacity: Float = 0f,            // panel behind the chart, 0 .. 0.8
    val chartTextScale: Float = 1.0f,             // 0.8 .. 1.8
    val showWindowChips: Boolean = true,          // 1h 3h 6h 12h 24h buttons

    // ---- Clock ----
    val clockVisible: Boolean = true,
    val clockPosition: ClockPosition = ClockPosition.RIGHT,
    val clock24h: Boolean = true,
    val clockShowSeconds: Boolean = false,
    val clockShowDate: Boolean = false,
    val clockSizeSp: Float = 16f,                 // 10 .. 56
    val clockFont: NumberFont = NumberFont.MEDIUM,
    val clockColorSource: TextColorSource = TextColorSource.AUTO,
    val clockCustomHex: String = "#FFFFFF",
    val clockOpacity: Float = 0.85f,              // 0.2 .. 1
    val clockPanel: Boolean = true,

    // ---- Rest of the reading screen ----
    val showConnectionBadge: Boolean = true,
    val showNotificationButton: Boolean = true,   // bell button: all notifications on/off
    val showTreatments: Boolean = true,           // "+" button, insulin/food markers on the chart
    val showSensorDays: Boolean = true,           // small "sensor day x/14" text

    // ---- Home-screen widgets ----
    val widgetBackground: WidgetBackground = WidgetBackground.ZONE_COLOR,
    val widgetCustomBgHex: String = "#1E293B",
    val widgetOpacity: Float = 1.0f,              // 0.1 .. 1
    val widgetCorner: WidgetCorner = WidgetCorner.MEDIUM,
    val widgetTextSource: TextColorSource = TextColorSource.AUTO,
    val widgetTextHex: String = "#FFFFFF",
    val widgetSizeScale: Float = 1.0f,            // 0.7 .. 1.8
    val widgetShowTrend: Boolean = true,
    val widgetShowUnit: Boolean = true,
    val widgetShowZone: Boolean = true,
    val widgetShowAge: Boolean = true,
    val widgetShowDelta: Boolean = true,          // change since previous reading (+4 / -7)
    val widgetGraphHours: Int = 3,                // hours shown in the graph widget: 1, 3, 6, 12, 24

    // ---- Status-bar notification ----
    val notificationCard: NotificationCardStyle = NotificationCardStyle.SYSTEM,
    val extraStatusIcon: ExtraStatusIcon = ExtraStatusIcon.NONE,
    val statusIconMode: StatusIconMode = StatusIconMode.VALUE,
    val statusIconColored: Boolean = true         // status-bar icon drawn in the level colour (shown on phones that allow it)
)
