package net.pfiers.andin.domain

import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.math.Vector3

val Vector2.vector3 get() = Vector3(x, 0f, y)

val Collection<Vector2>.average: Vector2
    get() {
        var xSum = 0f
        var ySum = 0f
        for (vector in this) {
            xSum += vector.x
            ySum += vector.y
        }
        return Vector2(xSum / size, ySum / size)
    }

fun Vector2.mul(amount: Float) = Vector2(
    x * amount,
    y * amount
)
