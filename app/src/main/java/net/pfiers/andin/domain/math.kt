package net.pfiers.andin.domain

infix fun Double.fmod(n: Double): Double =
    ((this % n) + n) % n

val Double.sqrd get() =
    this * this

val Double.round2 get() =
    String.format("%.2f", this)
