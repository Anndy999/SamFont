package com.samfont.ui

import androidx.compose.foundation.layout.height
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SamFontBottomBar(
    selectedTab: MainTab,
    onSelectTab: (MainTab) -> Unit
) {
    NavigationBar(
        modifier = Modifier.height(72.dp)
    ) {
        MainTab.entries.forEach { tab ->
            NavigationBarItem(
                selected = selectedTab == tab,
                onClick = { onSelectTab(tab) },
                label = { Text(text = tab.label()) },
                icon = { Text(text = tab.icon()) }
            )
        }
    }
}

private fun MainTab.label(): String = when (this) {
    MainTab.Installed -> "Installed"
    MainTab.Available -> "Available"
    MainTab.About -> "About"
    MainTab.Search -> "Search"
}

private fun MainTab.icon(): String = when (this) {
    MainTab.Installed -> "✓"
    MainTab.Available -> "↓"
    MainTab.About -> "i"
    MainTab.Search -> "⌕"
}
