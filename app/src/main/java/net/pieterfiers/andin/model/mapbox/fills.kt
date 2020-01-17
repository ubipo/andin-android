package net.pieterfiers.andin.model.mapbox

import android.content.Context
import android.graphics.Color
import android.util.Log
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
        color = context.resources.getColor(resource, context.theme)
    } else {
        @Suppress("DEPRECATION")
        color = context.resources.getColor(resource)
    }
    return color
}

fun Symbol.updateStyle(context: Context, mapElement: MapElement, selected: Boolean? = null, isSearchResult: Boolean? = null, isFavorite: Boolean? = null) {
    textField = mapElement.labelText
    textColor = PropertyFactory.textColor(Color.WHITE).value
    updateHighlight(context, isSearchResult, isFavorite)

//    when(mapElement) {
//        is Building -> updateStyle(mapElement, selected)
//        is Room -> updateStyle(mapElement, selected)
//        else -> {
//            fillColor = PropertyFactory.fillColor(Color.MAGENTA).value
//        }
//    }
}

fun Symbol.updateHighlight(context: Context, isSearchResult: Boolean? = null, isFavorite: Boolean? = null) {
    val search = isSearchResult == true; val fav = isFavorite == true
    if (search || fav) {
        val color = if (search) R.color.colorAccent else R.color.colorFavorite
        val width = if (search) 1.2f else 0.5f
        textHaloColor = PropertyFactory.textColor(getColor(context, color)).value
        textHaloWidth = PropertyFactory.textHaloWidth(width).value
    } else {
        textHaloColor = PropertyFactory.textColor(Color.TRANSPARENT).value
        textHaloWidth = PropertyFactory.textHaloWidth(0f).value
    }
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
