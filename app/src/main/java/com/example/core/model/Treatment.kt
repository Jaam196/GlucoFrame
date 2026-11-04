package com.example.core.model

/** Kind of entry in the insulin / food log. */
enum class TreatmentKind { INSULIN, FOOD, NOTE }

/**
 * One entry of the log: an insulin dose (units), a meal (grams of carbohydrates) or a free note.
 * [origin] is "LOCAL" (typed in this app) or "NIGHTSCOUT" (read from the Nightscout treatments).
 */
data class Treatment(
    val id: Long = 0L,
    val atMs: Long,
    val kind: TreatmentKind,
    val amount: Double = 0.0,
    val note: String = "",
    val origin: String = ORIGIN_LOCAL,
    val externalId: String = ""
) {
    companion object {
        const val ORIGIN_LOCAL = "LOCAL"
        const val ORIGIN_NIGHTSCOUT = "NIGHTSCOUT"
    }
}
