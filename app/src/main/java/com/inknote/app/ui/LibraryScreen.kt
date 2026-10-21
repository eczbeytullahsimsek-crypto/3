package com.inknote.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.inknote.app.AppViewModel
import com.inknote.app.data.Folder
import com.inknote.app.data.Notebook
import com.inknote.app.data.NotebookKind
import com.inknote.app.data.PageTemplate
import com.inknote.app.data.Scheme
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

fun relTime(ms: Long): String {
    val now = System.currentTimeMillis()
    val d = now - ms
    val min = d / 60_000
    if (min < 1) return "Az önce"
    if (min < 60) return "$min dk önce"
    val cal = Calendar.getInstance().apply { timeInMillis = ms }
    val today = Calendar.getInstance()
    val sameDay = cal.get(Calendar.YEAR) == today.get(Calendar.YEAR) && cal.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR)
    if (sameDay) return "${min / 60} sa önce"
    today.add(Calendar.DAY_OF_YEAR, -1)
    if (cal.get(Calendar.YEAR) == today.get(Calendar.YEAR) && cal.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR))
        return "Dün " + SimpleDateFormat("HH:mm", Locale("tr")).format(Date(ms))
    val days = d / 86_400_000
    return if (days < 7) "$days gün önce" else if (days < 30) "${days / 7} hafta önce" else SimpleDateFormat("d MMM yyyy", Locale("tr")).format(Date(ms))
}

private data class CoverStyle(val from: Color, val to: Color, val fg: Color, val spine: Color, val chipBg: Color)

@Composable
private fun coverStyle(s: Scheme): CoverStyle {
    val c = MaterialTheme.colorScheme
    return when (s) {
        Scheme.PRIMARY -> CoverStyle(c.primaryContainer, c.primary, c.onPrimary, c.primary.copy(alpha = .4f), c.surface.copy(alpha = .2f))
        Scheme.TERTIARY -> CoverStyle(c.tertiaryContainer, c.tertiary, c.onTertiary, c.tertiary.copy(alpha = .4f), c.surface.copy(alpha = .2f))
        Scheme.SECONDARY -> CoverStyle(c.secondaryContainer, c.secondary, c.onSecondaryContainer, c.secondary.copy(alpha = .3f), c.onSecondaryContainer.copy(alpha = .1f))
        Scheme.SURFACE -> CoverStyle(c.surfaceVariant, c.surfaceContainerHigh, c.onSurface, c.outlineVariant.copy(alpha = .6f), c.onSurfaceVariant.copy(alpha = .1f))
        Scheme.PDF -> CoverStyle(c.errorContainer, c.error.copy(alpha = .75f), c.onErrorContainer, c.error.copy(alpha = .3f), c.surface.copy(alpha = .35f))
    }
}

private enum class Chip { ALL, RECENT, STAR, PDF, TAG }

@Composable
fun LibraryScreen(vm: AppViewModel, onPickPdf: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    var sel by remember { mutableStateOf("ALL") }
    var chip by remember { mutableStateOf(Chip.ALL) }
    var query by remember { mutableStateOf("") }
    var grid by remember { mutableStateOf(true) }
    var newDialog by remember { mutableStateOf(false) }

    val all = vm.notebooks
    val base = all.filter { if (sel == "TRASH") it.trashed else !it.trashed && (sel == "ALL" || it.folder.name == sel) }
    val shown = base
        .filter { query.isBlank() || it.title.contains(query, true) || it.tag.contains(query, true) || it.desc.contains(query, true) }
        .filter {
            when (chip) {
                Chip.STAR -> it.starred
                Chip.PDF -> it.kind == NotebookKind.PDF
                Chip.TAG -> it.tag.contains("sınav", true) || it.title.contains("sınav", true)
                else -> true
            }
        }
        .sortedByDescending { it.updatedAt }
        .let { if (chip == Chip.RECENT) it.take(6) else it }
    val recent = all.filter { !it.trashed }.sortedByDescending { it.updatedAt }.take(3)

    Row(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 20.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        // ------------------------------------------------ sol panel (derli toplu, kompakt klasörler)
        Column(Modifier.width(220.dp).fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            // Yeni Defter + Hızlı Butonlar
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    Modifier.fillMaxWidth().height(46.dp).shadow(2.dp, FullShape, spotColor = cs.primary.copy(alpha = .25f))
                        .tap(cs.primary, FullShape) { newDialog = true }.padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Sym("edit_square", 20.dp, cs.onPrimary)
                        Txt("Yeni Defter", TS.labelMd, cs.onPrimary, weight = FontWeight.SemiBold)
                    }
                    Sym("add", 18.dp, cs.onPrimary.copy(alpha = .8f))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        Modifier.weight(1f).height(38.dp).tap(cs.surfaceContainerHigh, FullShape) {
                            vm.createNotebook("Hızlı Not")
                        }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
                    ) {
                        Sym("draw", 16.dp, cs.primary)
                        Txt(" Hızlı Not", TS.labelSm, cs.onSurface, weight = FontWeight.Medium)
                    }
                    Row(
                        Modifier.weight(1f).height(38.dp).tap(cs.surfaceContainerHigh, FullShape, onPickPdf),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
                    ) {
                        Sym("picture_as_pdf", 16.dp, cs.error)
                        Txt(" PDF", TS.labelSm, cs.onSurface, weight = FontWeight.Medium)
                    }
                }
            }

            // klasörler
            Column(
                Modifier.fillMaxWidth().shadow(1.dp, rr(16)).background(cs.surfaceContainerLow, rr(16)).padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Txt("KLASÖRLER", TS.labelSm, cs.secondary, weight = FontWeight.SemiBold)
                    IconBtn("create_new_folder", box = 26.dp, icon = 16.dp, tint = cs.secondary) { vm.message = "Klasörler sabit: yeni klasör yakında." }
                }
                val live = all.filter { !it.trashed }
                FolderRow("folder_special", "Tüm Notlar", live.size, sel == "ALL", cs.onSecondaryContainer, true) { sel = "ALL" }
                FolderRow("school", Folder.UNI.label, live.count { it.folder == Folder.UNI }, sel == Folder.UNI.name, cs.primary) { sel = Folder.UNI.name }
                FolderRow("palette", Folder.UX.label, live.count { it.folder == Folder.UX }, sel == Folder.UX.name, cs.tertiary) { sel = Folder.UX.name }
                FolderRow("book", Folder.DIARY.label, live.count { it.folder == Folder.DIARY }, sel == Folder.DIARY.name, cs.secondary) { sel = Folder.DIARY.name }
                FolderRow("picture_as_pdf", Folder.PDFS.label, live.count { it.folder == Folder.PDFS }, sel == Folder.PDFS.name, cs.error) { sel = Folder.PDFS.name }
                FolderRow("delete", "Çöp Kutusu", all.count { it.trashed }, sel == "TRASH", cs.onSurfaceVariant) { sel = "TRASH" }
            }

            // Altta zarif, sade durum bilgisi
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Sym("cloud_done", 14.dp, cs.primary)
                Txt("Tüm notlar güvende", TS.labelSm, cs.onSurfaceVariant)
            }
        }

        // ------------------------------------------------ sağ bölüm (derli toplu defterler)
        Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            // arama + görünüm + sıralama
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(
                    Modifier.weight(1f).widthIn(max = 600.dp).height(44.dp).tap(cs.surfaceContainerHigh, FullShape).padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Sym("search", 18.dp, cs.onSurfaceVariant)
                    Box(Modifier.weight(1f)) {
                        if (query.isEmpty()) Txt("Defterler veya el yazısında ara...", TS.bodyMd, cs.onSurfaceVariant, maxLines = 1)
                        BasicTextField(query, { query = it }, singleLine = true, textStyle = TS.bodyMd.copy(color = cs.onSurface), cursorBrush = SolidColor(cs.primary), modifier = Modifier.fillMaxWidth())
                    }
                    IconBtn("mic", box = 28.dp, icon = 16.dp) { vm.message = "Sesli arama yakında." }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.tap(cs.surfaceContainerHigh, FullShape).padding(3.dp)) {
                        IconBtn("grid_view", box = 32.dp, icon = 16.dp, tint = if (grid) cs.primary else cs.onSurfaceVariant, bg = if (grid) cs.surfaceContainerLowest else Color.Transparent) { grid = true }
                        IconBtn("view_list", box = 32.dp, icon = 16.dp, tint = if (!grid) cs.primary else cs.onSurfaceVariant, bg = if (!grid) cs.surfaceContainerLowest else Color.Transparent) { grid = false }
                    }
                    Row(
                        Modifier.height(40.dp).tap(cs.surfaceContainerHigh, FullShape).padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Sym("sort", 16.dp, cs.onSurfaceVariant)
                        Txt("Son Değişiklik", TS.labelMd, cs.onSurfaceVariant)
                        Sym("expand_more", 15.dp, cs.onSurfaceVariant)
                    }
                }
            }

            // filtre çipleri
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChipX("Tümü (${base.size})", chip == Chip.ALL, check = true) { chip = Chip.ALL }
                FilterChipX("Son Açılanlar", chip == Chip.RECENT) { chip = Chip.RECENT }
                FilterChipX("Yıldızlılar (${base.count { it.starred }})", chip == Chip.STAR, "star", cs.tertiary) { chip = Chip.STAR }
                FilterChipX("PDF Belgeleri (${base.count { it.kind == NotebookKind.PDF }})", chip == Chip.PDF, "picture_as_pdf", cs.error) { chip = Chip.PDF }
                FilterChipX("Etiketler: #Sınav", chip == Chip.TAG) { chip = Chip.TAG }
            }

            // son düzenlenen sayfalar
            if (recent.isNotEmpty() && sel != "TRASH") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Sym("schedule", 18.dp, cs.primary)
                            Txt("Son Düzenlenen Sayfalar", TS.titleSm, cs.onSurface, weight = FontWeight.SemiBold)
                        }
                        Txt("${recent.size} açık oturum", TS.labelSm, cs.secondary)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        for (i in 0 until 3) {
                            val nb = recent.getOrNull(i)
                            if (nb == null) Box(Modifier.weight(1f)) else Box(Modifier.weight(1f)) { RecentCard(nb) { vm.open(nb.id) } }
                        }
                    }
                }
            }

            // defter koleksiyonu
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Sym("auto_stories", 18.dp, cs.primary)
                        Txt("Defter Koleksiyonu", TS.titleMd, cs.onSurface, weight = FontWeight.SemiBold)
                    }
                    Txt("${shown.size} Defter", TS.labelSm, cs.onSurfaceVariant)
                }
                if (shown.isEmpty()) {
                    Box(Modifier.fillMaxWidth().padding(vertical = 40.dp), contentAlignment = Alignment.Center) {
                        Txt("Burada gösterilecek defter yok.", TS.bodyMd, cs.onSurfaceVariant)
                    }
                } else if (grid) {
                    BoxWithConstraints(Modifier.fillMaxWidth()) {
                        val cols = if (maxWidth > 1100.dp) 5 else if (maxWidth > 800.dp) 4 else if (maxWidth > 540.dp) 3 else 2
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            shown.chunked(cols).forEach { rowItems ->
                                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                    rowItems.forEach { nb -> Box(Modifier.weight(1f)) { NotebookCard(vm, nb) } }
                                    repeat(cols - rowItems.size) { Box(Modifier.weight(1f)) }
                                }
                            }
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { shown.forEach { NotebookRow(vm, it) } }
                }
            }
        }
    }

    if (newDialog) NewNotebookDialog({ newDialog = false }) { name -> newDialog = false; vm.createNotebook(name) }
}

@Composable
private fun FolderRow(icon: String, label: String, count: Int, active: Boolean, iconTint: Color, fill: Boolean = false, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val shape = rr(10)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (active) cs.secondaryContainer else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Sym(icon, 18.dp, if (active) cs.onSecondaryContainer else iconTint, fill)
            Txt(label, TS.labelMd, if (active) cs.onSecondaryContainer else cs.onSurfaceVariant, maxLines = 1)
        }
        Box(
            Modifier
                .clip(FullShape)
                .background(if (active) cs.surfaceContainerLowest else cs.surfaceContainer)
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Text(
                count.toString(),
                color = if (active) cs.onSecondaryContainer else cs.secondary,
                style = TS.labelSm.copy(fontSize = 11.sp, fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium)
            )
        }
    }
}

@Composable
private fun FilterChipX(text: String, active: Boolean, icon: String? = null, iconTint: Color? = null, check: Boolean = false, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.tap(if (active) cs.secondaryContainer else cs.surfaceContainerLow, FullShape, onClick).padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (active && check) Sym("check", 15.dp, cs.onSecondaryContainer)
        else if (icon != null) Sym(icon, 15.dp, iconTint ?: cs.onSurfaceVariant)
        Txt(text, TS.labelMd, if (active) cs.onSecondaryContainer else cs.onSurfaceVariant, maxLines = 1)
    }
}

@Composable
private fun RecentCard(nb: Notebook, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val cv = coverStyle(nb.scheme)
    Row(
        Modifier.fillMaxWidth().shadow(1.dp, rr(12)).tap(cs.surfaceContainerLowest, rr(12), onClick).padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(52.dp, 66.dp).clip(rr(8)).background(Brush.linearGradient(listOf(cv.from, cv.to)))) {
            Sym(if (nb.kind == NotebookKind.PDF) "picture_as_pdf" else "draw", 22.dp, cv.fg.copy(alpha = .85f), modifier = Modifier.align(Alignment.Center))
            Text("s. ${nb.lastPage + 1}", Modifier.align(Alignment.BottomEnd).padding(3.dp).background(cs.surface.copy(alpha = .9f), rr(4)).padding(horizontal = 3.dp),
                style = TS.labelSm.copy(fontSize = 9.sp, fontWeight = FontWeight.SemiBold), color = cs.onSurface)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Txt(nb.tag, TS.labelSm, when (nb.scheme) { Scheme.TERTIARY -> cs.tertiary; Scheme.SECONDARY, Scheme.SURFACE -> cs.secondary; Scheme.PDF -> cs.error; else -> cs.primary })
            Txt(nb.title, TS.titleSm, cs.onSurface, maxLines = 1, weight = FontWeight.SemiBold)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Txt(relTime(nb.updatedAt), TS.labelSm, cs.onSurfaceVariant)
                Sym(if (nb.kind == NotebookKind.PDF) "picture_as_pdf" else "cloud_done", 14.dp, cs.secondary)
            }
        }
    }
}

@Composable
private fun NotebookMenu(vm: AppViewModel, nb: Notebook) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconBtn("more_vert", box = 28.dp, icon = 16.dp) { open = true }
        DropdownMenu(open, { open = false }) {
            if (nb.trashed) {
                DropdownMenuItem(text = { Text("Geri Yükle") }, onClick = { open = false; vm.restore(nb.id) })
                DropdownMenuItem(text = { Text("Kalıcı Olarak Sil") }, onClick = { open = false; vm.deleteForever(nb.id) })
            } else {
                DropdownMenuItem(text = { Text("Aç") }, onClick = { open = false; vm.open(nb.id) })
                DropdownMenuItem(text = { Text(if (nb.starred) "Yıldızı Kaldır" else "Yıldızla") }, onClick = { open = false; vm.toggleStar(nb.id) })
                DropdownMenuItem(text = { Text("Çöp Kutusuna Taşı") }, onClick = { open = false; vm.trash(nb.id) })
            }
        }
    }
}

@Composable
private fun NotebookCard(vm: AppViewModel, nb: Notebook) {
    val cs = MaterialTheme.colorScheme
    val cv = coverStyle(nb.scheme)
    Column(
        Modifier
            .fillMaxWidth()
            .shadow(1.dp, rr(14))
            .tap(cs.surfaceContainerLowest, rr(14)) { if (!nb.trashed) vm.open(nb.id) }
            .padding(10.dp)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .aspectRatio(3f / 3.8f)
                .shadow(2.dp, rr(12))
                .clip(rr(12))
        ) {
            Box(Modifier.width(10.dp).fillMaxHeight().background(cv.spine))
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(Brush.linearGradient(listOf(cv.from, cv.to), androidx.compose.ui.geometry.Offset(0f, Float.POSITIVE_INFINITY), androidx.compose.ui.geometry.Offset(Float.POSITIVE_INFINITY, 0f)))
                    .padding(10.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                    Text(
                        nb.tag.uppercase(),
                        Modifier.background(cv.chipBg, rr(4)).padding(horizontal = 6.dp, vertical = 2.dp),
                        color = cv.fg,
                        style = TS.labelSm.copy(fontSize = 9.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.6.sp),
                        maxLines = 1
                    )
                    Sym(if (nb.starred) "star" else if (nb.kind == NotebookKind.PDF) "picture_as_pdf" else "push_pin", 16.dp, cv.fg.copy(alpha = if (nb.starred) 1f else .7f), nb.starred)
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Txt(nb.title, TS.titleSm, cv.fg, weight = FontWeight.SemiBold, maxLines = 2)
                    if (nb.desc.isNotBlank()) {
                        Txt(nb.desc, TS.bodySm, cv.fg.copy(alpha = .8f), maxLines = 1)
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("${nb.pageCount} Sayfa", color = cv.fg.copy(alpha = .85f), style = TS.labelSm.copy(fontSize = 11.sp))
                    Sym(if (nb.kind == NotebookKind.PDF) "picture_as_pdf" else "sync", 15.dp, cv.fg.copy(alpha = .8f))
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp, start = 2.dp, end = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Txt(nb.title, TS.labelMd, cs.onSurface, weight = FontWeight.Medium, maxLines = 1)
                Txt(relTime(nb.updatedAt), TS.labelSm, cs.onSurfaceVariant)
            }
            NotebookMenu(vm, nb)
        }
    }
}

@Composable
private fun NotebookRow(vm: AppViewModel, nb: Notebook) {
    val cs = MaterialTheme.colorScheme
    val cv = coverStyle(nb.scheme)
    Row(
        Modifier.fillMaxWidth().shadow(1.dp, rr(16)).tap(cs.surfaceContainerLowest, rr(16)) { if (!nb.trashed) vm.open(nb.id) }.padding(12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(Modifier.size(48.dp, 64.dp).clip(rr(16)).background(Brush.linearGradient(listOf(cv.from, cv.to))), contentAlignment = Alignment.Center) {
            Sym(if (nb.kind == NotebookKind.PDF) "picture_as_pdf" else "auto_stories", 22.dp, cv.fg)
        }
        Column(Modifier.weight(1f)) {
            Txt(nb.title, TS.titleSm, cs.onSurface, maxLines = 1)
            Txt(nb.desc, TS.bodySm, cs.onSurfaceVariant, maxLines = 1)
        }
        Txt("${nb.pageCount} Sayfa", TS.labelMd, cs.onSurfaceVariant)
        Txt(relTime(nb.updatedAt), TS.labelMd, cs.onSurfaceVariant)
        NotebookMenu(vm, nb)
    }
}

@Composable
private fun NewNotebookDialog(onDismiss: () -> Unit, onCreate: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Yeni Defter") },
        text = { OutlinedTextField(name, { name = it }, singleLine = true, label = { Text("Defter adı") }) },
        confirmButton = { TextButton({ onCreate(name.trim()) }) { Text("Oluştur") } },
        dismissButton = { TextButton(onDismiss) { Text("Vazgeç") } },
    )
}
