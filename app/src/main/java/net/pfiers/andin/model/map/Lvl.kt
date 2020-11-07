package net.pfiers.andin.model.map


fun str(double: Double): String = double.lvlStr

val Double.lvlStr: String get() {
    val intPart = toInt()
    val decimal = this - intPart
    return if (decimal < 0.01) {
        intPart.toString()
    } else {
        "%.2f".format(this)
    }
}
