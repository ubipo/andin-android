package net.pfiers.andin.domain.geometry

import net.pfiers.andin.domain.fmod
import kotlin.math.PI
import kotlin.math.abs


const val TAU = PI * 2

val Double.degrees
    get() = (this / TAU) * 360.0

val Double.radians
    get() = (this / 360.0) * TAU

fun positiveAngle(angle: Double): Double =
    angle.fmod(TAU)

infix fun Double.smallestAngleTo(other: Double) =
    abs(((this - other + PI) fmod TAU) - PI)
