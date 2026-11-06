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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import dev.phantomwatch.R

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
            TabIcon(R.drawable.ic_tab_home)
            TabLabel(R.string.tab_home)
        }
        LiquidBottomTab(onClick = { tab = 1; onSelect(1) }) {
            TabIcon(R.drawable.ic_search)
            TabLabel(R.string.tab_search)
        }
        LiquidBottomTab(onClick = { tab = 2; onSelect(2) }) {
            TabIcon(R.drawable.ic_tab_library)
            TabLabel(R.string.tab_library)
        }
        LiquidBottomTab(onClick = { tab = 3; onSelect(3) }) {
            TabIcon(R.drawable.ic_tab_you)
            TabLabel(R.string.tab_you)
        }
    }
}

@Composable
private fun TabIcon(id: Int) {
    Icon(painterResource(id), null, tint = TabDim, modifier = Modifier.size(22.dp))
}

@Composable
private fun TabLabel(id: Int) {
    Text(
        stringResource(id),
        color = TabDim,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        lineHeight = 14.sp,
    )
}
