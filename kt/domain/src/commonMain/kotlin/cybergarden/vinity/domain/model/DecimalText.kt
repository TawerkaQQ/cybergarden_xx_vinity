package cybergarden.vinity.domain.model

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sign

object DecimalText {

    fun sameAtReportedPrecision(left: String, right: String): Boolean {
        val x = left.toDoubleOrNull()?.takeIf { it.isFinite() } ?: return false
        val y = right.toDoubleOrNull()?.takeIf { it.isFinite() } ?: return false
        if (x == y) return true
        val placesX = places(left)
        val placesY = places(right)
        if (placesX == placesY) return false
        val (coarse, fine, places) = if (placesX < placesY) Triple(x, y, placesX) else Triple(y, x, placesY)
        val scale = 10.0.pow(places)
        val rounded = sign(fine) * floor(abs(fine) * scale + 0.5 + ROUNDING_SLACK) / scale
        return abs(rounded - coarse) <= ROUNDING_SLACK * max(1.0, abs(coarse))
    }

    private fun places(text: String): Int {
        val mantissa = text.substringBefore('e').substringBefore('E')
        val exponent = text.substringAfter('e', text.substringAfter('E', "0")).toIntOrNull() ?: 0
        return mantissa.substringAfter('.', "").length - exponent
    }

    private const val ROUNDING_SLACK = 1e-9
}
