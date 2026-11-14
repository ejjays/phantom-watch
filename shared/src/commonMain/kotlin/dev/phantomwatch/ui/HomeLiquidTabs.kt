package dev.phantomwatch.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import dev.phantomwatch.res.Res
import dev.phantomwatch.res.ic_clips
import dev.phantomwatch.res.ic_tab_home
import dev.phantomwatch.res.ic_tab_library
import dev.phantomwatch.res.ic_tab_you
import dev.phantomwatch.res.tab_home
import dev.phantomwatch.res.tab_library
import dev.phantomwatch.res.tab_clips
import dev.phantomwatch.res.tab_you
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

private val TabAccent = Color(0xFF00BCD4)
private val TabDim = Color(0xFFB4BCC5)
private val TabBadge = Color(0xFFFF453A)
private val TabContainer = Color(0xFF17212B).copy(alpha = 0.34f)

@Composable
fun HomeLiquidTabs(
    backdrop: Backdrop,
    selectedTabIndex: () -> Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    updateBadge: Boolean = false,
) {
    // local state so bubble slides in same snapshot not after route recompose
    var tab by remember { mutableIntStateOf(selectedTabIndex()) }
    LaunchedEffect(Unit) {
        snapshotFlow { selectedTabIndex() }.collect { index ->
            if (index != tab) tab = index
        }
    }

    // bubble paints accent tinted row copy over selected tab, badge drawn in
    // row would be recoloured then covered, so it draws last on top at the
    // spot the you icon reported
    var barOrigin by remember { mutableStateOf<Offset?>(null) }
    var youIcon by remember { mutableStateOf<Rect?>(null) }

    Box(modifier.onGloballyPositioned { barOrigin = it.positionInRoot() }) {
        LiquidBottomTabs(
            selectedTabIndex = { tab },
            onTabSelected = { index ->
                tab = index
                onSelect(index)
            },
            backdrop = backdrop,
            tabsCount = 4,
            accentColor = TabAccent,
            containerColor = TabContainer,
        ) {
            LiquidBottomTab(onClick = { tab = 0; onSelect(0) }) {
                TabIcon(Res.drawable.ic_tab_home)
                TabLabel(Res.string.tab_home)
            }
            LiquidBottomTab(onClick = { tab = 1; onSelect(1) }) {
                TabIcon(Res.drawable.ic_clips)
                TabLabel(Res.string.tab_clips)
            }
            LiquidBottomTab(onClick = { tab = 2; onSelect(2) }) {
                TabIcon(Res.drawable.ic_tab_library)
                TabLabel(Res.string.tab_library)
            }
            LiquidBottomTab(onClick = { tab = 3; onSelect(3) }) {
                TabIcon(Res.drawable.ic_tab_you, onBounds = { youIcon = it })
                TabLabel(Res.string.tab_you)
            }
        }
        val origin = barOrigin
        val icon = youIcon
        if (updateBadge && origin != null && icon != null) {
            Box(
                Modifier
                    .offset {
                        IntOffset(
                            (icon.right - origin.x).roundToInt() - 4.dp.roundToPx(),
                            (icon.top - origin.y).roundToInt() - 3.dp.roundToPx(),
                        )
                    }
                    .size(7.dp)
                    .background(TabBadge, CircleShape),
            )
        }
    }
}

@Composable
private fun TabIcon(icon: DrawableResource, onBounds: ((Rect) -> Unit)? = null) {
    // tinted copy sits 4dp higher so only real row reports where icon landed
    val report = if (LocalLiquidBottomTabTinted.current) null else onBounds
    Icon(
        painterResource(icon),
        null,
        tint = TabDim,
        modifier = Modifier
            .size(22.dp)
            .then(
                if (report == null) {
                    Modifier
                } else {
                    Modifier.onGloballyPositioned { report(it.boundsInRoot()) }
                },
            ),
    )
}

@Composable
private fun TabLabel(label: StringResource) {
    Text(
        stringResource(label),
        color = TabDim,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        lineHeight = 14.sp,
    )
}
