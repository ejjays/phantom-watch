package dev.phantomwatch.ui

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
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
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

private val TabAccent = Color(0xFF00BCD4)
private val TabDim = Color(0xFFB4BCC5)
private val TabContainer = Color(0xFF17212B).copy(alpha = 0.34f)

@Composable
fun HomeLiquidTabs(
    backdrop: Backdrop,
    selectedTabIndex: () -> Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    // local state so the bubble starts sliding in the same snapshot, not after the route recomposes
    var tab by remember { mutableIntStateOf(selectedTabIndex()) }
    LaunchedEffect(Unit) {
        snapshotFlow { selectedTabIndex() }.collect { index ->
            if (index != tab) tab = index
        }
    }

    LiquidBottomTabs(
        selectedTabIndex = { tab },
        onTabSelected = { index ->
            tab = index
            onSelect(index)
        },
        backdrop = backdrop,
        tabsCount = 4,
        modifier = modifier,
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
            TabIcon(Res.drawable.ic_tab_you)
            TabLabel(Res.string.tab_you)
        }
    }
}

@Composable
private fun TabIcon(icon: DrawableResource) {
    Icon(painterResource(icon), null, tint = TabDim, modifier = Modifier.size(22.dp))
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
