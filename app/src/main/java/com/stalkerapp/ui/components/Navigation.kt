package com.stalkerapp.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusState
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stalkerapp.ui.theme.PortioColors
import com.stalkerapp.ui.theme.PortioShape

data class NavigationTabItem(
    val icon: ImageVector,
    val label: String,
    val onClick: (() -> Unit)? = null
)

/**
 * Portio Yüzen Cam (Glass Pill) Alt Menü Çubuğu (BottomNavBar)
 */
@Composable
fun PortioBottomNavBar(
    items: List<NavigationTabItem>,
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    GlassSurface(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .height(62.dp),
        shape = PortioShape.Pill,
        backgroundAlpha = 0.82f
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEachIndexed { index, item ->
                val selected = index == selectedTabIndex
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .clip(PortioShape.Pill)
                ) {
                    AppleNavItem(
                        item = item,
                        selected = selected,
                        onClick = {
                            if (item.onClick != null) item.onClick.invoke()
                            else onTabSelected(index)
                        }
                    )
                }
            }
        }
    }
}

/**
 * Portio Üst Çubuk (TopAppBar)
 */
@Composable
fun PortioTopAppBar(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.5f))
                    .border(1.dp, PortioColors.Hairline, CircleShape)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Geri",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        actions()
    }
}

/**
 * TV & Tablet Yan Gezinme Menüsü (SideNavRail)
 */
@Composable
fun PortioSideNavRail(
    items: List<NavigationTabItem>,
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    header: @Composable (() -> Unit)? = null
) {
    Surface(
        modifier = modifier
            .fillMaxHeight()
            .width(80.dp),
        color = PortioColors.Surface,
        border = BorderStroke(1.dp, PortioColors.Hairline)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            header?.invoke()
            Spacer(Modifier.height(8.dp))
            items.forEachIndexed { index, item ->
                val selected = index == selectedTabIndex
                var isFocused by remember { mutableStateOf(false) }
                val active = selected || isFocused

                Surface(
                    shape = PortioShape.CardSmall,
                    color = if (active) Color.White.copy(alpha = 0.16f) else Color.Transparent,
                    modifier = Modifier
                        .size(54.dp)
                        .clip(PortioShape.CardSmall)
                        .border(
                            width = if (isFocused) 2.dp else 0.dp,
                            color = if (isFocused) PortioColors.FocusBorder else Color.Transparent,
                            shape = PortioShape.CardSmall
                        )
                        .onFocusChanged { isFocused = it.isFocused }
                        .focusable()
                        .clickable {
                            if (item.onClick != null) item.onClick.invoke()
                            else onTabSelected(index)
                        }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = item.label,
                            tint = if (active) Color.White else PortioColors.OnSurfaceMuted,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Yarı saydam cam yüzey (GlassSurface)
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = PortioShape.Card,
    backgroundAlpha: Float = 0.65f,
    border: BorderStroke? = null,
    content: @Composable BoxScope.() -> Unit
) {
    Surface(
        modifier = modifier.clip(shape),
        shape = shape,
        color = Color.Black.copy(alpha = backgroundAlpha),
        border = border ?: BorderStroke(1.dp, PortioColors.Hairline)
    ) {
        Box(modifier = Modifier.background(PortioColors.GlassGradient)) {
            content()
        }
    }
}

@Composable
fun AppleNavItem(
    item: NavigationTabItem,
    selected: Boolean,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val active = selected || isFocused
    Surface(
        shape = RoundedCornerShape(40),
        color = if (active) Color.White.copy(alpha = 0.14f) else Color.Transparent,
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(40))
            .border(
                width = if (active) 1.dp else 0.dp,
                color = if (active) PortioColors.FocusBorder.copy(alpha = 0.5f) else Color.Transparent,
                shape = RoundedCornerShape(40)
            )
            .onFocusChanged { fs: FocusState -> isFocused = fs.isFocused }
            .focusable()
            .clickable(onClick = onClick)
            .padding(4.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = item.icon,
                contentDescription = item.label,
                tint = if (active) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(8.dp).size(22.dp)
            )
        }
    }
}

/** Bölüm Başlığı Bileşeni */
@Composable
fun SectionTitle(
    title: String,
    modifier: Modifier = Modifier,
    onSeeAll: (() -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.weight(1f)
        )
        if (onSeeAll != null) {
            val pillShape = PortioShape.Pill
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(pillShape)
                    .background(Color.White.copy(alpha = 0.1f))
                    .border(1.dp, PortioColors.Hairline, pillShape)
                    .clickable { onSeeAll() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "Tümü",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
fun AppleSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    onSeeAll: (() -> Unit)? = null,
    content: @Composable (() -> Unit)? = null
) {
    SectionTitle(title = title, modifier = modifier, onSeeAll = onSeeAll)
    content?.invoke()
}

@Composable
fun AppleHairline(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(PortioColors.Hairline)
    )
}
