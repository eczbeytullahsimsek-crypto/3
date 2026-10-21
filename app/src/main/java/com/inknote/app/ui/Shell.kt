package com.inknote.app.ui

import android.view.InputDevice
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.inknote.app.AppViewModel
import com.inknote.app.Route

// ------------------------------------------------------------------ ortak yardımcılar

val FullShape: Shape = CircleShape
fun rr(dp: Int) = RoundedCornerShape(dp.dp)

fun Modifier.tap(color: Color, shape: Shape, onClick: (() -> Unit)? = null): Modifier {
    val m = this.clip(shape).background(color)
    return if (onClick != null) m.clickable(onClick = onClick) else m
}

@Composable
fun Pill(
    text: String,
    bg: Color,
    fg: Color,
    modifier: Modifier = Modifier,
    style: TextStyle = TS.labelSm,
    icon: String? = null,
    iconFill: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier.tap(bg, FullShape, onClick).padding(horizontal = 10.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (icon != null) Sym(icon, 14.dp, fg, iconFill)
        Text(text, style = style, color = fg, maxLines = 1)
    }
}

@Composable
fun IconBtn(
    name: String,
    modifier: Modifier = Modifier,
    box: Dp = 40.dp,
    icon: Dp = 20.dp,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    bg: Color = Color.Transparent,
    fill: Boolean = false,
    onClick: () -> Unit = {},
) {
    Box(modifier.size(box).tap(bg, FullShape, onClick), contentAlignment = Alignment.Center) { Sym(name, icon, tint, fill) }
}

@Composable
fun Txt(
    text: String,
    style: TextStyle,
    color: Color = MaterialTheme.colorScheme.onSurface,
    modifier: Modifier = Modifier,
    weight: FontWeight? = null,
    maxLines: Int = Int.MAX_VALUE,
    align: TextAlign? = null,
) {
    Text(
        text, modifier, color = color, maxLines = maxLines, overflow = TextOverflow.Ellipsis, textAlign = align,
        style = if (weight != null) style.copy(fontWeight = weight) else style,
    )
}

@Composable
fun hasStylus(): Boolean = remember {
    InputDevice.getDeviceIds().any { id ->
        InputDevice.getDevice(id)?.let { (it.sources and InputDevice.SOURCE_STYLUS) == InputDevice.SOURCE_STYLUS } ?: false
    }
}

// ------------------------------------------------------------------ kabuk

@Composable
fun Shell(vm: AppViewModel, content: @Composable () -> Unit) {
    val cs = MaterialTheme.colorScheme
    if (vm.isFullScreen && vm.route == Route.CANVAS) {
        Box(Modifier.fillMaxSize().background(cs.background)) {
            content()
        }
    } else {
        Row(Modifier.fillMaxSize().background(cs.background)) {
            Sidebar(vm)
            Column(Modifier.weight(1f).fillMaxHeight()) {
                TopBar()
                Box(Modifier.weight(1f).fillMaxWidth().background(cs.background)) { content() }
            }
        }
    }
}

@Composable
private fun Sidebar(vm: AppViewModel) {
    val cs = MaterialTheme.colorScheme
    Column(
        Modifier.width(96.dp).fillMaxHeight().shadow(2.dp).background(cs.surfaceContainerLow).padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(24.dp)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(Modifier.size(44.dp).shadow(3.dp, FullShape).background(cs.primaryContainer, FullShape), contentAlignment = Alignment.Center) {
                    Sym("ink_pen", 24.dp, cs.onPrimaryContainer)
                }
                Txt("InkNote", TS.labelSm, cs.onSurface, weight = FontWeight.SemiBold)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                NavItem("auto_stories", "Kitaplık", vm.route == Route.LIBRARY) { vm.route = Route.LIBRARY }
                NavItem("edit_note", "Tuval", vm.route == Route.CANVAS) {
                    vm.route = Route.CANVAS
                    if (vm.openId == null) vm.notebooks.firstOrNull { !it.trashed }?.let { vm.open(it.id, Route.CANVAS) }
                }
                NavItem("layers", "Sayfalar", vm.route == Route.PAGES) {
                    if (vm.openId == null) vm.notebooks.firstOrNull { !it.trashed }?.let { vm.open(it.id, Route.PAGES) } else vm.route = Route.PAGES
                }
                NavItem("search", "Arama", vm.route == Route.SEARCH) { vm.route = Route.SEARCH }
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            IconBtn("cloud_done", box = 44.dp, tint = cs.secondary, bg = if (vm.route == Route.CLOUD) cs.secondaryContainer else Color.Transparent) { vm.route = Route.CLOUD }
            IconBtn("settings", box = 44.dp, bg = if (vm.route == Route.SETTINGS) cs.secondaryContainer else Color.Transparent) { vm.route = Route.SETTINGS }
            Box(Modifier.padding(top = 8.dp).size(32.dp).shadow(1.dp, FullShape).background(cs.primary, FullShape), contentAlignment = Alignment.Center) {
                Sym("person", 18.dp, cs.onPrimary)
            }
        }
    }
}

@Composable
private fun NavItem(icon: String, label: String, active: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val fg = if (active) cs.onSecondaryContainer else cs.onSurfaceVariant
    Column(
        Modifier.width(64.dp).tap(if (active) cs.secondaryContainer else Color.Transparent, FullShape, onClick).padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.width(56.dp).height(32.dp), contentAlignment = Alignment.Center) { Sym(icon, 22.dp, fg, active) }
        Txt(label, TS.labelSm, fg, Modifier.padding(top = 2.dp), weight = if (active) FontWeight.SemiBold else null, maxLines = 1)
    }
}

@Composable
private fun TopBar() {
    val cs = MaterialTheme.colorScheme
    val stylus = hasStylus()
    Row(
        Modifier.fillMaxWidth().height(64.dp).background(cs.surface).padding(horizontal = 32.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Txt("InkNote Workspace", TS.titleMd, cs.onSurface, weight = FontWeight.Medium)
            Pill("Tablet Landscape", cs.secondaryContainer, cs.onSecondaryContainer)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(
                Modifier.tap(cs.surfaceContainerHigh, FullShape).padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Sym("stylus_note", 18.dp, cs.onSurfaceVariant)
                Txt(if (stylus) "Stylus Bağlı" else "Stylus Algılanmadı", TS.labelMd, cs.onSurfaceVariant)
            }
            Box(Modifier.size(32.dp).background(cs.primary, FullShape), contentAlignment = Alignment.Center) { Sym("person", 18.dp, cs.onPrimary) }
        }
    }
}
