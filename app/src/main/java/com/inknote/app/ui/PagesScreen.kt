package com.inknote.app.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.inknote.app.AppViewModel
import com.inknote.app.Route
import com.inknote.app.data.Notebook
import com.inknote.app.data.NotebookKind
import com.inknote.app.data.PageTemplate
import com.inknote.app.data.PaperSize
import com.inknote.app.data.PaperTones
import com.inknote.app.data.StrokeData
import kotlin.math.max

@Composable
fun PagesScreen(vm: AppViewModel) {
    val cs = MaterialTheme.colorScheme
    val nb = vm.current
    if (nb == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Txt("Önce Kitaplık'tan bir defter açın.", TS.titleMd, cs.onSurface)
                Pill("Kitaplığa Git", cs.primary, cs.onPrimary, style = TS.labelLg, onClick = { vm.route = Route.LIBRARY })
            }
        }
        return
    }
    val isPdf = nb.kind == NotebookKind.PDF
    val pageCount = if (isPdf) (vm.pdf?.pageCount ?: nb.pageCount) else nb.pageCount
    var selectMode by remember { mutableStateOf(false) }
    var selected by remember(nb.id) { mutableStateOf(setOf<Int>()) }
    var drawerTpl by remember(nb.id) { mutableStateOf(nb.defaultTemplate) }
    var cols by remember { mutableStateOf(4) }
    val tone = PaperTones[nb.paperTone.coerceIn(0, PaperTones.lastIndex)]

    Column(Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        // ------------------------------------------------ komut ve filtre çubuğu
        Column(Modifier.fillMaxWidth().shadow(1.dp, rr(16)).background(cs.surfaceContainerLow, rr(16)).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(40.dp).shadow(1.dp, FullShape).background(cs.secondaryContainer, FullShape), contentAlignment = Alignment.Center) { Sym("auto_stories", 22.dp, cs.onSecondaryContainer) }
                    Column {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Txt("Sayfaları Yönet", TS.titleLg, cs.onSurface, weight = FontWeight.SemiBold)
                            Pill("$pageCount Sayfa", cs.surfaceContainerHighest, cs.onSurfaceVariant)
                        }
                        Txt(nb.title, TS.bodySm, cs.onSurfaceVariant, maxLines = 1)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    ActionBtn("checklist", if (selectMode) "Seçimi Tamamla (${selected.size})" else "Çoklu Seç / Düzenle", if (selectMode) cs.primary else cs.surfaceContainer, if (selectMode) cs.onPrimary else cs.onSurface) {
                        selectMode = !selectMode; if (!selectMode) selected = emptySet()
                    }
                    ActionBtn("dashboard_customize", "Şablon Uygula", cs.secondaryContainer, cs.onSecondaryContainer) {
                        vm.applyTemplate(if (selected.isEmpty()) setOf(vm.activePage) else selected, drawerTpl)
                    }
                    ActionBtn("ios_share", "Dışa Aktar", cs.surfaceContainer, cs.onSurface) { vm.message = "Dışa aktarma bir sonraki sürümde." }
                    Row(
                        Modifier.shadow(1.dp, FullShape).tap(cs.primary, FullShape) { vm.addPage() }.padding(horizontal = 20.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) { Sym("add", 20.dp, cs.onPrimary); Txt("Yeni Sayfa", TS.labelLg, cs.onPrimary) }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.shadow(1.dp, FullShape).background(cs.secondaryContainer, FullShape).padding(horizontal = 14.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Sym("check", 16.dp, cs.onSecondaryContainer, true); Txt("Tüm Sayfalar ($pageCount)", TS.labelMd, cs.onSecondaryContainer, weight = FontWeight.Medium)
                    }
                    listOf("bookmark" to "Yer İmleri", "menu_book" to "Ana Hatlar (Index)", "edit_attributes" to "Ek Açıklamalı Sayfalar").forEach { (ic, label) ->
                        Row(Modifier.tap(cs.surfaceContainer, FullShape) { vm.message = "$label filtresi yakında." }.padding(horizontal = 14.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Sym(ic, 16.dp, if (ic == "bookmark") cs.tertiary else cs.onSurfaceVariant); Txt(label, TS.labelMd, cs.onSurfaceVariant)
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconBtn("grid_view", box = 32.dp, icon = 18.dp, bg = if (cols == 5) cs.surfaceContainerHighest else Color.Transparent) { cols = 5 }
                    IconBtn("view_comfy", box = 32.dp, icon = 18.dp, bg = if (cols == 4) cs.surfaceContainerHighest else Color.Transparent) { cols = 4 }
                }
            }
        }

        // ------------------------------------------------ ızgara + şablon stüdyosu
        Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Txt(if (isPdf) "PDF SAYFALARI" else "BÖLÜM 1: SAYFALAR", TS.labelMd, cs.onSurfaceVariant)
                    Txt(if (selectMode) "Sayfaya dokunarak seçin" else "Açmak için dokunun", TS.labelSm, cs.secondary)
                }
                LazyVerticalGrid(GridCells.Fixed(cols), Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (!isPdf) item(key = "cover") { CoverCard(nb) }
                    items((0 until pageCount).toList(), key = { it }) { i ->
                        PageCard(vm, nb, i, tone.second, tone.third, selected = i in selected, active = vm.activePage == i, selectMode = selectMode) {
                            if (selectMode) selected = if (i in selected) selected - i else selected + i
                            else { vm.scrollRequest = i; vm.activePage = i; vm.route = Route.CANVAS }
                        }
                    }
                    if (!isPdf) item(key = "add") {
                        Column(
                            Modifier.fillMaxWidth().aspectRatio(3f / 4f).clip(rr(16)).background(cs.surfaceContainerLow)
                                .border(2.dp, cs.outlineVariant.copy(alpha = .6f), rr(16)).clickable { vm.addPage() },
                            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                        ) {
                            Box(Modifier.size(48.dp).background(cs.surfaceContainerHigh, FullShape), contentAlignment = Alignment.Center) { Sym("post_add", 24.dp, cs.onSurfaceVariant) }
                            Txt("Sayfa Ekle", TS.labelLg, cs.onSurface, Modifier.padding(top = 8.dp), weight = FontWeight.Medium)
                            Txt("Mevcut Şablonla", TS.labelSm, cs.onSurfaceVariant)
                        }
                    }
                }
            }
            Drawer(vm, nb, drawerTpl, { drawerTpl = it }, selected)
        }
    }
}

@Composable
private fun ActionBtn(icon: String, label: String, bg: Color, fg: Color, onClick: () -> Unit) {
    Row(Modifier.tap(bg, FullShape, onClick).padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Sym(icon, 18.dp, fg); Txt(label, TS.labelLg, fg, maxLines = 1)
    }
}

@Composable
private fun CoverCard(nb: Notebook) {
    val cs = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().shadow(1.dp, rr(16)).background(cs.surfaceContainerLow, rr(16)).padding(10.dp)) {
        Box(Modifier.fillMaxWidth().aspectRatio(3f / 4f).clip(rr(48)).background(cs.primaryContainer)) {
            Box(Modifier.width(12.dp).fillMaxHeight().background(cs.primary.copy(alpha = .4f)))
            Column(Modifier.fillMaxSize().padding(start = 22.dp, top = 14.dp, end = 14.dp, bottom = 14.dp), verticalArrangement = Arrangement.SpaceBetween) {
                Pill("Kapak", cs.onPrimary.copy(alpha = .2f), cs.onPrimary)
                Column {
                    Txt(nb.title, TS.titleMd, cs.onPrimary, weight = FontWeight.Medium, maxLines = 3)
                    Txt(nb.tag, TS.labelSm, cs.onPrimaryContainer, Modifier.padding(top = 4.dp))
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 10.dp, start = 4.dp, end = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Txt("Kapak", TS.labelMd, cs.onSurface, weight = FontWeight.SemiBold)
            Sym("drag_indicator", 16.dp, cs.outline)
        }
    }
}

@Composable
private fun PageCard(vm: AppViewModel, nb: Notebook, index: Int, toneArgb: Int, dark: Boolean, selected: Boolean, active: Boolean, selectMode: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val isPdf = nb.kind == NotebookKind.PDF
    LaunchedEffect(nb.id, index) { if (!isPdf) vm.ensurePage(index) }
    val strokes = vm.pageData[index]
    val bg = if (selected || active) cs.primary.copy(alpha = 0.0f).let { Color(0xFFE0E0FF) } else cs.surfaceContainer
    Column(
        Modifier.fillMaxWidth().shadow(if (active) 3.dp else 1.dp, rr(16)).background(bg, rr(16))
            .then(if (selected || active) Modifier.border(2.dp, cs.primary, rr(16)) else Modifier).clickable(onClick = onClick).padding(10.dp),
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(3f / 4f).clip(rr(48)).background(Color(if (isPdf) 0xFFFFFFFF.toInt() else toneArgb))) {
            if (isPdf) {
                val pdf = vm.pdf
                val bmp by produceState<Bitmap?>(null, pdf, index) { value = pdf?.render(index, 320) }
                bmp?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds, filterQuality = FilterQuality.Low) }
            } else {
                PaperCanvas(nb.templateOf(index), dark, Modifier.fillMaxSize())
                if (strokes != null && strokes.isNotEmpty()) StrokePreview(strokes, Modifier.fillMaxSize())
            }
            if (!isPdf) Pill(nb.templateOf(index).label.substringBefore(" ("), cs.surfaceContainer, cs.onSurfaceVariant, Modifier.align(Alignment.BottomStart).padding(8.dp), style = TS.labelSm)
            if (selectMode) Box(Modifier.align(Alignment.TopEnd).padding(10.dp).size(22.dp).background(if (selected) cs.primary else cs.surfaceContainerHighest, FullShape), contentAlignment = Alignment.Center) {
                if (selected) Sym("check", 16.dp, cs.onPrimary)
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 10.dp, start = 4.dp, end = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Txt(if (active) "Sayfa ${index + 1} (Aktif)" else "Sayfa ${index + 1}", TS.labelMd, if (active) cs.primary else cs.onSurface, weight = if (active) FontWeight.SemiBold else null)
            Sym("drag_indicator", 16.dp, if (active) cs.primary else cs.outline)
        }
    }
}

@Composable
private fun StrokePreview(list: List<StrokeData>, modifier: Modifier) {
    Canvas(modifier) {
        val w = size.width
        for (s in list) {
            if (s.pts.size < 2) continue
            val p = Path()
            p.moveTo(s.pts[0].x * w, s.pts[0].y * w)
            for (i in 1 until s.pts.size) p.lineTo(s.pts[i].x * w, s.pts[i].y * w)
            drawPath(p, Color(s.argb), style = Stroke(max(1f, s.size * w), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

// ------------------------------------------------------------------ sağ panel: Kağıt & Şablon Stüdyosu

@Composable
private fun Drawer(vm: AppViewModel, nb: Notebook, tpl: PageTemplate, onTpl: (PageTemplate) -> Unit, selected: Set<Int>) {
    val cs = MaterialTheme.colorScheme
    var cat by remember { mutableStateOf(0) }
    val cats = listOf("Temel" to listOf(PageTemplate.BLANK, PageTemplate.DOT, PageTemplate.RULED, PageTemplate.GRID),
        "Üretkenlik" to listOf(PageTemplate.CORNELL, PageTemplate.WEEKLY), "Yaratıcı & Akademik" to listOf(PageTemplate.STAFF, PageTemplate.CORNELL))
    Column(
        Modifier.width(360.dp).fillMaxHeight().shadow(1.dp, rr(16)).background(cs.surfaceContainerLow, rr(16)).verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Sym("palette", 20.dp, cs.primary); Txt("Kağıt & Şablon Stüdyosu", TS.titleMd, cs.onSurface, weight = FontWeight.SemiBold, maxLines = 1)
            }
            Pill("${nb.paperSize.label} • Dikey", cs.secondaryContainer, cs.onSecondaryContainer)
        }
        // kağıt boyutu
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Txt("KAĞIT BOYUTU & ORAN", TS.labelSm, cs.onSurfaceVariant, weight = FontWeight.Medium)
            Row(Modifier.fillMaxWidth().background(cs.surfaceContainer, rr(48)).padding(6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                PaperSize.entries.forEach { s ->
                    val a = nb.paperSize == s
                    Box(Modifier.weight(1f).tap(if (a) cs.surfaceContainerLowest else Color.Transparent, rr(32)) { vm.setPaperSize(s) }.padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
                        Txt(s.label, TS.labelMd, if (a) cs.onSurface else cs.onSurfaceVariant, weight = if (a) FontWeight.SemiBold else null)
                    }
                }
            }
        }
        // kağıt tonu
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Txt("KAĞIT TONU & DOKUSU", TS.labelSm, cs.onSurfaceVariant, weight = FontWeight.Medium)
                Txt(PaperTones[nb.paperTone.coerceIn(0, 4)].first, TS.labelSm, cs.primary, weight = FontWeight.Medium)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PaperTones.forEachIndexed { i, (name, argb, _) ->
                    val a = nb.paperTone == i
                    Column(Modifier.weight(1f).tap(cs.surfaceContainer, rr(32)) { vm.setPaperTone(i) }.padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(Modifier.size(36.dp).then(if (a) Modifier.border(2.dp, cs.primary, FullShape) else Modifier).background(Color(argb), FullShape), contentAlignment = Alignment.Center) {
                            if (a) Sym("check", 16.dp, cs.primary, true)
                        }
                        Txt(name, TS.labelSm, if (a) cs.onSurface else cs.onSurfaceVariant, maxLines = 1)
                    }
                }
            }
        }
        // şablonlar
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Txt("ŞABLON KATEGORİLERİ", TS.labelSm, cs.onSurfaceVariant, weight = FontWeight.Medium)
                Txt("${PageTemplate.entries.size} Hazır Şablon", TS.labelSm, cs.secondary, weight = FontWeight.Medium)
            }
            Row(Modifier.fillMaxWidth().background(cs.surfaceContainer, rr(48)).padding(6.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                cats.forEachIndexed { i, (n, _) ->
                    val a = cat == i
                    Box(Modifier.tap(if (a) cs.surfaceContainerLowest else Color.Transparent, rr(32)) { cat = i }.padding(horizontal = 12.dp, vertical = 4.dp)) {
                        Txt(n, TS.labelMd, if (a) cs.onSurface else cs.onSurfaceVariant, weight = if (a) FontWeight.SemiBold else null, maxLines = 1)
                    }
                }
            }
            cats[cat].second.distinct().chunked(2).forEach { rowItems ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    rowItems.forEach { t ->
                        val a = t == tpl
                        Column(Modifier.weight(1f).tap(if (a) cs.secondaryContainer else cs.surfaceContainer, rr(48)) { onTpl(t) }.padding(10.dp)) {
                            Box(Modifier.fillMaxWidth().height(64.dp).clip(rr(32)).background(cs.surfaceContainerLowest)) {
                                PaperCanvas(t, false, Modifier.fillMaxSize())
                                if (a) Box(Modifier.align(Alignment.Center)) { Sym("check_circle", 20.dp, cs.primary, true) }
                            }
                            Txt(t.label, TS.labelMd, if (a) cs.onSecondaryContainer else cs.onSurface, Modifier.padding(top = 6.dp), weight = FontWeight.Medium, maxLines = 1)
                            Txt(t.sub, TS.labelSm, if (a) cs.onSecondaryContainer.copy(alpha = .8f) else cs.onSurfaceVariant, maxLines = 1)
                        }
                    }
                    if (rowItems.size == 1) Box(Modifier.weight(1f))
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                Modifier.fillMaxWidth().tap(cs.primaryContainer, FullShape) {
                    vm.applyTemplate(if (selected.isEmpty()) setOf(vm.activePage) else selected, tpl)
                }.padding(vertical = 10.dp, horizontal = 16.dp),
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
            ) { Sym("done_all", 18.dp, cs.onPrimaryContainer); Txt("  Seçili Sayfalara Uygula", TS.labelLg, cs.onPrimaryContainer, weight = FontWeight.Medium) }
            Row(
                Modifier.fillMaxWidth().tap(cs.surfaceContainer, FullShape) { vm.setDefaultTemplate(tpl); vm.message = "Yeni sayfalar için varsayılan: ${tpl.label}" }.padding(vertical = 8.dp, horizontal = 16.dp),
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
            ) { Sym("bookmark_add", 16.dp, cs.onSurfaceVariant); Txt("  Yeni Sayfalar İçin Varsayılan Yap", TS.labelMd, cs.onSurfaceVariant) }
        }
    }
}
