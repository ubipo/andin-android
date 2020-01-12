package net.pieterfiers.andin.model.mapbox

import android.content.Context
import android.graphics.Color
import androidx.core.graphics.ColorUtils
import com.mapbox.mapboxsdk.plugins.annotation.Fill
import com.mapbox.mapboxsdk.plugins.annotation.Symbol
import com.mapbox.mapboxsdk.style.layers.PropertyFactory
import net.pieterfiers.andin.R
import net.pieterfiers.andin.model.map.Building
import net.pieterfiers.andin.model.map.MapElement
import net.pieterfiers.andin.model.map.Room


fun Fill.updateStyle(mapElement: MapElement, selected: Boolean = false) {
    when(mapElement) {
        is Building -> updateStyle(mapElement, selected)
        is Room -> updateStyle(mapElement, selected)
        else -> {
            fillColor = PropertyFactory.fillColor(Color.MAGENTA).value
        }
    }
}

fun Fill.updateStyle(building: Building, selected: Boolean = false) {
    var color = Color.parseColor("#3bb2d0")
    if (selected)
        color = ColorUtils.blendARGB(color, Color.BLACK, 0.2f)

    fillColor = PropertyFactory.fillColor(color).value
}

fun Fill.updateStyle(room: Room, selected: Boolean = false) {
    var color = Color.parseColor("#357266")
    if (selected)
        color = ColorUtils.blendARGB(color, Color.BLACK, 0.2f)

    fillColor = PropertyFactory.fillColor(color).value
    fillOutlineColor = PropertyFactory.fillColor(Color.BLACK).value
}

fun getColor(context: Context, resource: Int): Int {
    val color: Int
    if (android.os.Build.VERSION.SDK_INT >= 23) {
        color = context.resources.getColor(R.color.colorAccent, context.theme)
    } else {
        @Suppress("DEPRECATION")
        color = context.resources.getColor(R.color.colorAccent)
    }
    return color
}

fun Symbol.updateStyle(context: Context, mapElement: MapElement, selected: Boolean? = null, isSearchResult: Boolean? = null) {
    textField = mapElement.labelText
    textColor = PropertyFactory.textColor(Color.WHITE).value
    if (isSearchResult == true) {
        textHaloColor = PropertyFactory.textColor(getColor(context, R.color.colorAccent)).value
        textHaloWidth = PropertyFactory.textHaloWidth(1.2f).value
    }

//    when(mapElement) {
//        is Building -> updateStyle(mapElement, selected)
//        is Room -> updateStyle(mapElement, selected)
//        else -> {
//            fillColor = PropertyFactory.fillColor(Color.MAGENTA).value
//        }
//    }
}

//fun Symbol.updateStyle(building: Building, selected: Boolean = false) {
////    var color = Color.parseColor("#3bb2d0")
////    if (selected)
////        color = ColorUtils.blendARGB(color, Color.BLACK, 0.2f)
////
////    fillColor = PropertyFactory.fillColor(color).value
//}
//
//fun Symbol.updateStyle(room: Room, selected: Boolean = false) {
////    var color = Color.parseColor("#357266")
////    if (selected)
////        color = ColorUtils.blendARGB(color, Color.BLACK, 0.2f)
////
////    fillColor = PropertyFactory.fillColor(color).value
////    fillOutlineColor = PropertyFactory.fillColor(Color.BLACK).value
//}
//
