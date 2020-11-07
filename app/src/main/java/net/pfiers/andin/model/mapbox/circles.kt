package net.pfiers.andin.model.mapbox

import android.graphics.Color
import androidx.core.graphics.ColorUtils
import com.mapbox.mapboxsdk.plugins.annotation.Circle
import com.mapbox.mapboxsdk.plugins.annotation.CircleOptions
import com.mapbox.mapboxsdk.style.layers.PropertyFactory
import net.pfiers.andin.model.map.Entrance


fun createCircleOptions(entrance: Entrance): CircleOptions? {
    return CircleOptions().withGeometry(entrance.geometry.coordinate.mapboxPoint).withCircleRadius(
        4.0F
    ).withData(entrance.toDataJson())
}

fun Circle.updateStyle(entrance: Entrance, selected: Boolean = false) {
    var color = Color.parseColor("#dddddd")
    if (selected)
        color = ColorUtils.blendARGB(color, Color.BLACK, 0.2f)

    circleColor = PropertyFactory.fillColor(color).value
}
