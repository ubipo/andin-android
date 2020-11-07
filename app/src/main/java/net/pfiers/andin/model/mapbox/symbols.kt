package net.pfiers.andin.model.mapbox

import android.content.Context
import android.graphics.Color
import com.mapbox.mapboxsdk.plugins.annotation.Symbol
import com.mapbox.mapboxsdk.plugins.annotation.SymbolOptions
import com.mapbox.mapboxsdk.style.layers.PropertyFactory
import net.pfiers.andin.model.map.*
import net.pfiers.andin.R
import net.pfiers.andin.view.fragments.support.SlippymapMapboxFragment
import org.locationtech.jts.algorithm.Centroid
import org.locationtech.jts.geom.Coordinate
import java.lang.UnsupportedOperationException


private fun toIconName(id: Int) = SlippymapMapboxFragment.iconNames[id]

const val SORT_KEY_BOTTOM = 10F
const val SORT_KEY_TOP = 5F

fun createSymbolOptions(building: Building): SymbolOptions? {
    val centroid = Centroid.getCentroid(building.geometry)
    return SymbolOptions()
        .withGeometry(centroid.mapboxPoint)
        .withData(building.toDataJson())
        .withTextField(building.labelText)
        .withTextColor(PropertyFactory.textColor(Color.WHITE).value)
}

val SymbolOptions.textFieldIsNull: Boolean get() {
    try {
        textField
    } catch (ex: UnsupportedOperationException) {
        return ex.message == "JsonNull"
    }
    return false
}

fun updateSymbolOptionsIcon(options: SymbolOptions, iconId: Int) {
    if (options.textFieldIsNull) {
        options.withTextAnchor("top")
        options.withIconAnchor("bottom")
    }
    options.withIconSize(0.07F)
    options.withIconImage(toIconName(iconId))
}

fun createSymbolOptions(room: Room): SymbolOptions? {
    val centroid = Centroid.getCentroid(room.geometry)
    val iconId = when {
        room.firstAidKit == true -> R.drawable.ic_map_first_aid
        room.drinkCoffee == true -> R.drawable.ic_map_coffee
        room.toilet != null -> when (room.toilet) {
            Toilet.MALE -> R.drawable.ic_map_toilet_male
            Toilet.FEMALE -> R.drawable.ic_map_toilet_female
            else -> null
        }
        else -> null
    }
    val options = SymbolOptions()
        .withGeometry(centroid.mapboxPoint)
        .withData(room.toDataJson())
        .withTextField(room.labelText)
        .withTextColor(PropertyFactory.textColor(Color.WHITE).value)
        .withSymbolSortKey(SORT_KEY_BOTTOM)
    if (iconId != null)
        updateSymbolOptionsIcon(options, iconId)
    return options
}

fun createSymbolOptions(fireSuppressionTool: FireSuppressionTool): SymbolOptions? {
    val geom = fireSuppressionTool.geometry.coordinate
    val iconId = when (fireSuppressionTool.toolType) {
        FsToolType.HOSE -> R.drawable.ic_map_fire_hose
        else -> R.drawable.ic_map_fire_extinguisher
    }
    val options = SymbolOptions()
        .withGeometry(geom.mapboxPoint)
        .withData(fireSuppressionTool.toDataJson())
        .withSymbolSortKey(SORT_KEY_BOTTOM)
    updateSymbolOptionsIcon(options, iconId)
    return options
}

fun createSymbolOptions(entrance: Entrance): SymbolOptions? {
    return SymbolOptions().withGeometry(entrance.geometry.coordinate.mapboxPoint).withData(entrance.toDataJson())
}

fun createSymbolOptions(levelRange: LevelRange, coordinate: Coordinate, imageId: Int): SymbolOptions {
    return SymbolOptions()
        .withGeometry(coordinate.mapboxPoint)
        .withIconImage(SlippymapMapboxFragment.iconNames[imageId])
        .withData(levelRange.asJsonObject())
        .withIconSize(0.09F)
        .withSymbolSortKey(SORT_KEY_TOP)
}

fun createSymbolOptionsStart(rangeSection: SameLevelRangeNavSection, prevRangeSection: SameLevelRangeNavSection?): SymbolOptions {
    val (levelRange, section) = rangeSection
    val startImageId = if (prevRangeSection == null) {
        R.drawable.ic_nav_start
    } else {
        val levelRangePrev = prevRangeSection.first
        when {
            levelRangePrev < levelRange -> R.drawable.ic_nav_down
            levelRangePrev > levelRange -> R.drawable.ic_nav_up
            else -> error("Shouldn't happen")
        }
    }
    return createSymbolOptions(levelRange, section.first().coordinate, startImageId)
}

fun createSymbolOptionsFinish(rangeSection: SameLevelRangeNavSection, nextRangeSection: SameLevelRangeNavSection?): SymbolOptions {
    val (levelRange, section) = rangeSection
    val finishImageId = if (nextRangeSection == null) {
        R.drawable.ic_nav_finish
    } else {
        val levelRangeNext = nextRangeSection.first
        when {
            levelRangeNext < levelRange -> R.drawable.ic_nav_down
            levelRangeNext > levelRange -> R.drawable.ic_nav_up
            else -> {
                error("Shouldn't happen, levelRange: $levelRange, levelRangeNext: $levelRangeNext")
            }
        }
    }
    return createSymbolOptions(levelRange, section.last().coordinate, finishImageId)
}

fun Symbol.updateHighlight(
    context: Context,
    selected: Boolean? = null,
    isSearchResult: Boolean? = null,
    isFavorite: Boolean? = null
) {
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
