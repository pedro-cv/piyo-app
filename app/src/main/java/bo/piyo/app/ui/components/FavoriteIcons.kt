package bo.piyo.app.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Church
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LocalHospital
import androidx.compose.material.icons.rounded.Park
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.ShoppingCart
import androidx.compose.material.icons.rounded.Work
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import bo.piyo.app.R

/** The small set of icons and colours a saved place can use (CONTRIBUTIONS_DESIGN.md §4.4). Keys are stored, not icons. */
object FavoriteIcons {
    data class Entry(val key: String, val icon: ImageVector, val label: Int)

    val all = listOf(
        Entry("home", Icons.Rounded.Home, R.string.icon_home),
        Entry("work", Icons.Rounded.Work, R.string.icon_work),
        Entry("school", Icons.Rounded.School, R.string.icon_school),
        Entry("gym", Icons.Rounded.FitnessCenter, R.string.icon_gym),
        Entry("health", Icons.Rounded.LocalHospital, R.string.icon_health),
        Entry("shopping", Icons.Rounded.ShoppingCart, R.string.icon_shopping),
        Entry("food", Icons.Rounded.Restaurant, R.string.icon_food),
        Entry("family", Icons.Rounded.Favorite, R.string.icon_family),
        Entry("church", Icons.Rounded.Church, R.string.icon_church),
        Entry("park", Icons.Rounded.Park, R.string.icon_park),
        Entry("place", Icons.Rounded.Place, R.string.icon_place),
    )

    fun icon(key: String): ImageVector = all.firstOrNull { it.key == key }?.icon ?: Icons.Rounded.Place

    /** Eight toned colours that read well under white glyphs in both themes. */
    val palette = listOf("#0E7C5F", "#2F6FB6", "#7A4FB8", "#C2452D", "#D07B16", "#B8325E", "#2B8A8A", "#5C636A")

    fun color(hex: String): Color = runCatching { Color(("FF" + hex.removePrefix("#")).toLong(16)) }.getOrDefault(Color(0xFF0E7C5F))
}
