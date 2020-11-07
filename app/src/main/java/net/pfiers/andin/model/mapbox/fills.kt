package net.pfiers.andin.model.mapbox

import android.content.Context
import android.graphics.Color
import androidx.core.graphics.ColorUtils
import com.mapbox.mapboxsdk.plugins.annotation.Fill
import com.mapbox.mapboxsdk.plugins.annotation.FillOptions
import com.mapbox.mapboxsdk.style.layers.PropertyFactory
import net.pfiers.andin.model.map.*


fun createFillOptions(building: Building): FillOptions? {
    return FillOptions()
        .withGeometry(building.geometry.mapbox)
        .withData(building.toDataJson())
}

fun createFillOptions(room: Room): FillOptions? {
    return FillOptions().withGeometry(room.geometry.mapbox).withData(room.toDataJson())
}

fun createFillOptions(corridor: Corridor): FillOptions? {
    return FillOptions().withGeometry(corridor.geometry.mapbox).withData(corridor.toDataJson())
}

fun Fill.updateStyle(mapElement: MapElement, selected: Boolean = false) {
    when(mapElement) {
        is Building -> updateStyle(mapElement, selected)
        is Room -> updateStyle(mapElement, selected)
        is Corridor -> updateStyle(mapElement, selected)
        is Entrance -> updateStyle(mapElement, selected)
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

fun Fill.updateStyle(corridor: Corridor, selected: Boolean = false) {
    var color = Color.parseColor("#757575")
    if (selected)
        color = ColorUtils.blendARGB(color, Color.BLACK, 0.2f)

    fillColor = PropertyFactory.fillColor(color).value
    fillOutlineColor = PropertyFactory.fillColor(Color.BLACK).value
}

fun Fill.updateStyle(entrance: Entrance, selected: Boolean = false) {
    var color = Color.parseColor("#ffffff")
    if (selected)
        color = ColorUtils.blendARGB(color, Color.BLACK, 0.2f)

    fillColor = PropertyFactory.fillColor(color).value
}

fun getColor(context: Context, resource: Int): Int {
    return context.resources.getColor(resource, context.theme)
}

//fun Symbol.updateStyle(context: Context, mapElement: MapElement, selected: Boolean? = null, isSearchResult: Boolean? = null, isFavorite: Boolean? = null) {
//    textField = mapElement.labelText
//    textColor = PropertyFactory.textColor(Color.WHITE).value
//    updateHighlight(context, isSearchResult, isFavorite)
//
//    val icn = SlippymapMapboxFragment.iconNames
//    val iconImage = when(mapElement) {
//        is Building -> null
//        is FireSuppressionTool -> if (mapElement.toolType != null) {
//            when (mapElement.toolType) {
//                FsToolType.HOSE -> icn[R.drawable.ic_map_fire_hose]
//                FsToolType.EXTINGUISHER -> icn[R.drawable.ic_map_fire_extinguisher]
//            }
//        } else null
//        else -> null
//    }
//    if (iconImage != null)
//        setIcon(iconImage)
//}

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
