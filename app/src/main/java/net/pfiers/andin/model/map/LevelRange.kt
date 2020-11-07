package net.pfiers.andin.model.map

import kotlin.math.max
import kotlin.math.min

data class LevelRange(
    val from: Double,
    val to: Double? = null
) {
    fun contains(level: Double) = when (to) {
        null -> level == from
        else -> (from..to).contains(level)
    }

    fun overlaps(other: LevelRange) = when (to) {
        null -> other.contains(from)
        else -> other.contains(from) || other.contains(to)
    }

    fun intersect(other: LevelRange): LevelRange? {
        if (!overlaps(other))
            return null
        val highestFrom = max(from, other.from)
        val lowestTo = if (to != null && other.to != null) {
            min(to, other.to)
        } else {
            null
        }
        if (lowestTo != null && highestFrom > lowestTo)
            return null
        return LevelRange(highestFrom, lowestTo)
    }

    fun union(other: LevelRange): LevelRange {
        val lowest = min(from, other.from)
        val candidatesHighest = mutableListOf(from, other.from)
        if (to != null)
            candidatesHighest.add(to)
        if (other.to != null)
            candidatesHighest.add(other.to)
        val highest = candidatesHighest.maxOrNull() ?: error("Should never occur")
        val to = if (lowest == highest) null else highest
        return LevelRange(lowest, to)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as LevelRange

        if (from != other.from) return false
        if (to != other.to) return false

        return true
    }

    override fun hashCode(): Int {
        var result = from.hashCode()
        result = 31 * result + (to?.hashCode() ?: 0)
        return result
    }

    fun closestMatch(desired: Double): Double = when {
            contains(desired) -> desired
            to != null && desired > to -> to
            else -> from
        }

    operator fun compareTo(other: LevelRange): Int {
        val highestThis = to ?: from
        val highestOther = other.to ?: other.from

        return when {
            from > highestOther -> 1
            highestThis < other.from -> -1
            else -> 0
        }
    }
}
