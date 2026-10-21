package com.inknote.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.inknote.app.AppViewModel
import com.inknote.app.Route
import com.inknote.app.data.NotebookKind

private data class Hit(val icon: String, val title: String, val meta: String, val text: String, val badge: String, val time: String, val notebookId: String? = null)

private val samples = listOf(
    Hit("draw", "İleri Algoritmalar & Veri Yapıları", "Sayfa 14 • Defter Notu", "...İkili Arama Algoritması Karmaşıklığı: En kötü senaryoda O(log n) adımla sonlanır...", "%98 Doğruluk", "Dün 16:42"),
    Hit("picture_as_pdf", "Veri Yapıları Ders Kitabı.pdf", "Sayfa 112 / 450", "...Dijkstra yol bulma algoritması yönlendirilmiş ağırlıklı graflarda en kısa patikayı hesaplar...", "PDF Metni", "3 gün önce"),
    Hit("draw", "UX/UI Tasarım Fikirleri", "Sayfa 3 • Quick Note", "...kullanıcı öneri algoritması akışı sadeleştirilmeli, kart bazlı geçişler...", "%92 OCR", "12 Eki"),
)

@Composable
private fun hl(text: String, q: String): AnnotatedString {
    val cs = MaterialTheme.colorScheme
    if (q.isBlank()) return AnnotatedString(text)
    return buildAnnotatedString {
        var i = 0
        while (i < text.length) {
            val m = text.indexOf(q, i, ignoreCase = true)
            if (m < 0) { append(text.substring(i)); break }
            append(text.substring(i, m))
            withStyle(SpanStyle(background = Fx.amber200, color = Fx.amber950, fontWeight = FontWeight.Medium)) { append(text.substring(m, m + q.length)) }
            i = m + q.length
        }
    }
}

@Composable
fun SearchScreen(vm: AppViewModel) {
    val cs = MaterialTheme.colorScheme
    var q by remember { mutableStateOf("algoritma") }
    var selIdx by remember { mutableStateOf(0) }
    val clip = LocalClipboardManager.current
    val real = vm.notebooks.filter { !it.trashed && q.isNotBlank() && (it.title.contains(q, true) || it.desc.contains(q, true) || it.tag.contains(q, true)) }
        .map { Hit(if (it.kind == NotebookKind.PDF) "picture_as_pdf" else "auto_stories", it.title, "${it.pageCount} sayfa • ${it.tag}", it.desc, "Defter", relTime(it.updatedAt), it.id) }
    val hits = real + samples.filter { q.isBlank() || it.text.contains(q, true) || it.title.contains(q, true) }
    val cur = hits.getOrNull(selIdx.coerceAtMost((hits.size - 1).coerceAtLeast(0)))

    Row(Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 24.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        Column(Modifier.width(410.dp).fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(Modifier.fillMaxWidth().height(56.dp).shadow(1.dp, FullShape).background(cs.surfaceContainerHigh, FullShape).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Sym("search", 22.dp, cs.onSurfaceVariant)
                Box(Modifier.weight(1f)) {
                    if (q.isEmpty()) Txt("Notlar veya PDF içinde ara...", TS.bodyLg, cs.onSurfaceVariant)
                    BasicTextField(q, { q = it; selIdx = 0 }, singleLine = true, textStyle = TS.bodyLg.copy(color = cs.onSurface), cursorBrush = SolidColor(cs.primary), modifier = Modifier.fillMaxWidth())
                }
                IconBtn("close", box = 36.dp, icon = 19.dp) { q = "" }
                IconBtn("mic", box = 36.dp) { vm.message = "Sesle arama yakında." }
            }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.background(cs.secondaryContainer, FullShape).padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Sym("dataset", 16.dp, cs.onSecondaryContainer, true); Txt("Tüm Sonuçlar", TS.labelMd, cs.onSecondaryContainer); Pill("${hits.size}", cs.surfaceContainerLowest, cs.onSecondaryContainer)
                }
                listOf("draw" to "El Yazısı", "picture_as_pdf" to "PDF", "bookmark" to "Yer İmleri").forEach { (i, l) ->
                    Row(Modifier.background(cs.surfaceContainerLow, FullShape).padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Sym(i, 16.dp, cs.onSurfaceVariant); Txt(l, TS.labelMd, cs.onSurfaceVariant)
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill("Defter: Tümü", cs.surfaceContainer, cs.onSurfaceVariant); Pill("Tarih: Son 30 Gün", cs.surfaceContainer, cs.onSurfaceVariant)
                }
                Row(verticalAlignment = Alignment.CenterVertically) { Sym("verified", 15.dp, cs.primary, true); Txt(" OCR > %95", TS.labelSm, cs.onSurfaceVariant) }
            }
            hits.forEachIndexed { i, h ->
                val a = h === cur
                Column(
                    Modifier.fillMaxWidth().shadow(if (a) 3.dp else 1.dp, rr(48)).background(cs.surfaceContainerLowest, rr(48))
                        .then(if (a) Modifier.border(2.dp, cs.primary.copy(alpha = .2f), rr(48)) else Modifier).clickable { selIdx = i }.padding(16.dp),
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(28.dp).background(if (h.icon == "picture_as_pdf") cs.errorContainer else Fx.primaryFixed, FullShape), contentAlignment = Alignment.Center) {
                                Sym(h.icon, 16.dp, if (h.icon == "picture_as_pdf") cs.onErrorContainer else Fx.onPrimaryFixed)
                            }
                            Column(Modifier.weight(1f)) {
                                Txt(h.title, TS.titleSm, cs.onSurface, weight = FontWeight.SemiBold, maxLines = 1)
                                Txt(h.meta, TS.labelSm, cs.onSurfaceVariant, maxLines = 1)
                            }
                        }
                        Pill(h.badge, cs.secondaryContainer, cs.onSecondaryContainer)
                    }
                    Text(hl(h.text, q), Modifier.padding(top = 10.dp).fillMaxWidth().background(cs.surfaceContainerLow, rr(32)).padding(10.dp), color = cs.onSurfaceVariant, style = TS.bodySm)
                    Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Row(verticalAlignment = Alignment.CenterVertically) { Sym("schedule", 15.dp, cs.onSurfaceVariant); Txt(" ${h.time}", TS.labelSm, cs.onSurfaceVariant) }
                        if (a) Row(verticalAlignment = Alignment.CenterVertically) { Txt("Önizleniyor ", TS.labelSm, cs.primary, weight = FontWeight.Medium); Sym("arrow_forward", 16.dp, cs.primary) }
                    }
                }
            }
            if (hits.isEmpty()) Txt("Sonuç bulunamadı.", TS.bodyLg, cs.onSurfaceVariant, Modifier.padding(8.dp))
        }

        Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(Modifier.fillMaxWidth().shadow(2.dp, rr(16)).background(cs.surfaceContainerLowest, rr(16)).padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(32.dp).background(cs.primaryContainer, FullShape), contentAlignment = Alignment.Center) { Sym("description", 18.dp, cs.onPrimaryContainer) }
                    Column { Txt(cur?.title ?: "—", TS.titleSm, cs.onSurface, weight = FontWeight.SemiBold, maxLines = 1); Txt(cur?.meta ?: "", TS.labelSm, cs.onSurfaceVariant, maxLines = 1) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill("Metni Kopyala", cs.surfaceContainer, cs.onSurface, style = TS.labelMd, icon = "content_copy", onClick = { cur?.let { clip.setText(AnnotatedString(it.text)) } })
                    Pill("Düzenleyicide Aç", cs.primary, cs.onPrimary, style = TS.labelMd, icon = "open_in_new", onClick = {
                        (cur?.notebookId ?: vm.notebooks.firstOrNull { !it.trashed }?.id)?.let { vm.open(it, Route.CANVAS) }
                    })
                }
            }
            Column(Modifier.fillMaxWidth().shadow(4.dp, rr(16)).background(Fx.paper, rr(16)).padding(28.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Txt(cur?.title ?: "Sonuç seçin", TS.titleMd, cs.onSurface, weight = FontWeight.Bold)
                Text(hl(cur?.text ?: "", q), color = cs.onSurface, style = TS.bodyLg)
            }
            Column(Modifier.fillMaxWidth().background(cs.surfaceContainerLow, rr(16)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) { Sym("document_scanner", 18.dp, cs.primary); Txt("Arama Kapsamı", TS.labelMd, cs.onSurface, weight = FontWeight.SemiBold) }
                Txt("Defter ve PDF başlıkları ile açıklamaları gerçek zamanlı aranır. Örnek kartlar el yazısı/PDF OCR arayüzünü gösterir; OCR için Google ML Kit Digital Ink Recognition eklenebilir.", TS.bodySm, cs.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun CloudScreen() {
    val cs = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Txt("Bulut Durumu", TS.headlineLg, cs.onSurface)
        Txt("Tüm notlar ve PDF ek açıklamaları şu an cihaz depolamasında tutuluyor.", TS.bodyLg, cs.onSurfaceVariant)
    }
}

@Composable
fun SettingsScreen(vm: AppViewModel) {
    val cs = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Txt("Uygulama Ayarları", TS.headlineLg, cs.onSurface)
        Row(Modifier.fillMaxWidth().background(cs.surfaceContainerLow, rr(16)).padding(20.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Txt("Palm Koruması", TS.titleMd, cs.onSurface)
                Txt("Açıkken yalnızca kalem çizer; parmak sayfayı kaydırır.", TS.bodySm, cs.onSurfaceVariant)
            }
            Switch(vm.stylusOnly, { vm.stylusOnly = it })
        }
    }
}
