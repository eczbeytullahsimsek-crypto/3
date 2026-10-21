package com.inknote.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.RectF
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.inknote.app.AppViewModel
import com.inknote.app.Route
import com.inknote.app.data.AudioRecording
import com.inknote.app.data.Exporter
import com.inknote.app.data.Notebook
import com.inknote.app.data.NotebookKind
import com.inknote.app.data.PaperTones
import com.inknote.app.data.StrokeData
import com.inknote.app.ink.InkPageView
import com.inknote.app.ink.Tool
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

private class ViewHolder { var v: InkPageView? = null }

private fun formatMs(ms: Long): String {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSec / 60
    val seconds = totalSec % 60
    return String.format(Locale.US, "%02d:%02d", minutes, seconds)
}

@Composable
fun CanvasScreen(vm: AppViewModel, onPickPdf: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val context = LocalContext.current
    val nb = vm.current
    var showExportDialog by remember { mutableStateOf(false) }
    var showRecordingsDialog by remember { mutableStateOf(false) }

    if (nb == null) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Sym("edit_note", 56.dp, cs.outline)
            Txt("Açık defter yok", TS.titleLg, cs.onSurface, Modifier.padding(top = 12.dp))
            Row(Modifier.padding(top = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Pill("Kitaplığa Git", cs.primary, cs.onPrimary, style = TS.labelLg, onClick = { vm.route = Route.LIBRARY })
                Pill("PDF İçe Aktar", cs.secondaryContainer, cs.onSecondaryContainer, style = TS.labelLg, icon = "picture_as_pdf", onClick = onPickPdf)
            }
        }
        return
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            val started = vm.audioManager.startRecording(nb.id)
            if (started) {
                vm.message = "Ses kaydı başladı. Kalem çizimleri sesle senkronize ediliyor."
            } else {
                vm.message = "Ses kaydı başlatılamadı."
            }
        } else {
            vm.message = "Ses kaydı için mikrofon izni gereklidir."
        }
    }

    fun handleRecordClick() {
        if (vm.audioManager.isRecording) {
            val rec = vm.audioManager.stopRecording()
            if (rec != null) {
                vm.message = "Ses kaydı tamamlandı: ${rec.title}"
            }
        } else {
            val perm = Manifest.permission.RECORD_AUDIO
            if (ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED) {
                val started = vm.audioManager.startRecording(nb.id)
                if (started) {
                    vm.message = "Ses kaydı başladı. Kalem çizimleri sesle senkronize ediliyor."
                } else {
                    vm.message = "Ses kaydı başlatılamadı."
                }
            } else {
                audioPermissionLauncher.launch(perm)
            }
        }
    }

    val tone = PaperTones[nb.paperTone.coerceIn(0, PaperTones.lastIndex)]
    val pageCount = if (nb.kind == NotebookKind.PDF) (vm.pdf?.pageCount ?: nb.pageCount) else nb.pageCount
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(listState, nb.id) {
        snapshotFlow { listState.firstVisibleItemIndex }.collect { vm.activePage = it }
    }
    LaunchedEffect(vm.scrollRequest, pageCount) {
        vm.scrollRequest?.let { if (it in 0 until pageCount) { listState.scrollToItem(it); vm.scrollRequest = null } }
    }

    val screenPaddingHorizontal = if (vm.isFullScreen) 6.dp else 12.dp
    val screenPaddingVertical = if (vm.isFullScreen) 4.dp else 8.dp

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = screenPaddingHorizontal, vertical = screenPaddingVertical)
    ) {
        AppBar(
            vm = vm,
            nb = nb,
            pageCount = pageCount,
            onOpenExport = { showExportDialog = true },
            onToggleRecord = { handleRecordClick() },
            onOpenRecordings = { showRecordingsDialog = true }
        )

        // Ses Kaydı Senkronize Replay Oynatıcı Çubuğu
        AnimatedVisibility(
            visible = vm.audioManager.isReplayMode,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Column {
                Box(Modifier.height(8.dp))
                AudioReplayBar(vm = vm)
            }
        }

        Box(Modifier.height(6.dp))
        Toolbar(vm, tone.third)
        Box(Modifier.height(6.dp))

        Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!vm.isFullScreen) {
                ThumbStrip(vm, nb, pageCount, tone.second, tone.third)
            }
            BoxWithConstraints(Modifier.weight(1f).fillMaxHeight()) {
                if (nb.kind == NotebookKind.PDF && vm.pdfAspects == null) {
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                } else {
                    val baseWidth = 920.dp.coerceAtMost(maxWidth)
                    val effectiveWidth = baseWidth * vm.zoomScale
                    val containerW = constraints.maxWidth.toFloat()
                    val baseWidthPx = with(LocalDensity.current) { baseWidth.toPx() }

                    Box(
                        Modifier
                            .fillMaxSize()
                            .clipToBounds()
                            .pointerInput(containerW, baseWidthPx) {
                                val gap = 20.dp.toPx()
                                awaitEachGesture {
                                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                                    do {
                                        val event = awaitPointerEvent(PointerEventPass.Initial)
                                        val down = event.changes.filter { it.pressed }
                                        val penDown = down.any { it.type == PointerType.Stylus || it.type == PointerType.Eraser }
                                        if (down.size >= 2 && !penDown) {
                                            val z = event.calculateZoom()
                                            val p = event.calculatePan()
                                            val c = event.calculateCentroid()
                                            if (z != 1f || p != Offset.Zero) {
                                                val f = vm.pinch(z, c.x, c.y, p.x, p.y, containerW, baseWidthPx)
                                                val first = listState.layoutInfo.visibleItemsInfo.firstOrNull()
                                                val sPos = listState.firstVisibleItemIndex * ((first?.size ?: 0) + gap) +
                                                        listState.firstVisibleItemScrollOffset
                                                listState.dispatchRawDelta((sPos + c.y) * (f - 1f) - p.y)
                                            }
                                            event.changes.forEach { it.consume() }
                                        } else if (vm.stylusOnly && vm.zoomScale > 1.02f && down.size == 1 &&
                                            down[0].type == PointerType.Touch
                                        ) {
                                            // Yalnızca Kalem modunda tek parmakla yatay kaydırma (dikey LazyColumn'a kalır)
                                            vm.pinch(1f, 0f, 0f, down[0].positionChange().x, 0f, containerW, baseWidthPx)
                                        }
                                    } while (event.changes.any { it.pressed })
                                }
                            },
                        contentAlignment = Alignment.TopCenter
                    ) {
                        LazyColumn(
                            Modifier
                                .requiredWidth(effectiveWidth)
                                .offset { IntOffset(vm.panOffsetX.roundToInt(), 0) }
                                .fillMaxHeight(),
                            state = listState,
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(20.dp),
                            contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 32.dp)
                        ) {
                            items(pageCount, key = { "${nb.id}:$it" }) { i ->
                                val aspect = if (nb.kind == NotebookKind.PDF) vm.pdfAspects?.getOrNull(i) ?: (1f / 1.4142f) else nb.paperSize.ratio
                                PageItem(vm, nb, i, aspect, tone.second, tone.third)
                            }
                        }
                    }

                    // Sağa kaydırma barı (FastScrollBar) - tutulup çekildikçe sayfaları anında kaydırır
                    FastScrollBar(
                        listState = listState,
                        pageCount = pageCount,
                        onScrollToPage = { target ->
                            coroutineScope.launch { listState.scrollToItem(target) }
                        },
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = 2.dp)
                    )
                }
            }
        }
    }

    if (showExportDialog) {
        SaveToDeviceDialog(vm = vm, nb = nb, onDismiss = { showExportDialog = false })
    }

    if (showRecordingsDialog) {
        AudioRecordingsDialog(
            vm = vm,
            nb = nb,
            onDismiss = { showRecordingsDialog = false }
        )
    }
}

// ------------------------------------------------------------------ Ses Kaydı ve Senkronize Replay Çubuğu

@Composable
private fun AudioReplayBar(vm: AppViewModel) {
    val cs = MaterialTheme.colorScheme
    val am = vm.audioManager
    val rec = am.activeRecording ?: return
    val currentPos = am.playbackPositionMs
    val totalDuration = am.playbackDurationMs.coerceAtLeast(1L)
    val progress = (currentPos.toFloat() / totalDuration).coerceIn(0f, 1f)

    Row(
        Modifier
            .fillMaxWidth()
            .shadow(4.dp, FullShape)
            .background(cs.secondaryContainer, FullShape)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Sol başlık ve mod rozeti
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.size(32.dp).background(cs.primary, FullShape), contentAlignment = Alignment.Center) {
                Sym("headphones", 18.dp, cs.onPrimary)
            }
            Column {
                Text(rec.title, style = TS.labelMd, fontWeight = FontWeight.Bold, color = cs.onSecondaryContainer)
                Text("Senkronize Çizim Replay", style = TS.labelSm, color = cs.onSecondaryContainer.copy(alpha = 0.7f), fontSize = 10.sp)
            }
        }

        // Kontroller: Geri 10s, Oynat/Duraklat, İleri 10s
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            IconBtn("replay_10", box = 32.dp, icon = 18.dp, tint = cs.onSecondaryContainer) { am.skipBy(-10000L) }
            Box(
                Modifier
                    .size(38.dp)
                    .clip(FullShape)
                    .background(cs.primary)
                    .clickable { am.togglePlayPause() },
                contentAlignment = Alignment.Center
            ) {
                Sym(if (am.isPlaying) "pause" else "play_arrow", 22.dp, cs.onPrimary)
            }
            IconBtn("forward_10", box = 32.dp, icon = 18.dp, tint = cs.onSecondaryContainer) { am.skipBy(10000L) }
        }

        // Zaman Sayacı
        Text(
            text = "${formatMs(currentPos)} / ${formatMs(totalDuration)}",
            style = TS.labelSm,
            fontWeight = FontWeight.SemiBold,
            color = cs.onSecondaryContainer
        )

        // İlerleme Kaydırıcısı (Scrubber)
        Slider(
            value = progress,
            onValueChange = { frac -> am.seekTo((frac * totalDuration).toLong()) },
            modifier = Modifier.weight(1f)
        )

        // Hız seçici (1.0x, 1.25x, 1.5x, 2.0x)
        val speeds = listOf(1.0f, 1.25f, 1.5f, 2.0f)
        Box(
            Modifier
                .clip(FullShape)
                .background(cs.surfaceContainerHighest)
                .clickable {
                    val nextIdx = (speeds.indexOf(am.playbackSpeed) + 1) % speeds.size
                    am.setSpeed(speeds[nextIdx])
                }
                .padding(horizontal = 10.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "${am.playbackSpeed}x",
                style = TS.labelSm,
                fontWeight = FontWeight.Bold,
                color = cs.onSurface
            )
        }

        // Bilgi ipucu rozeti
        Row(
            Modifier
                .background(cs.surfaceContainerHighest.copy(alpha = 0.6f), FullShape)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Sym("touch_app", 14.dp, cs.primary)
            Text("Çizgiye dokun: sese atla", style = TS.labelSm, fontSize = 10.sp, color = cs.onSurfaceVariant)
        }

        // Kapat butonu
        IconBtn("close", box = 30.dp, icon = 18.dp, tint = cs.onSecondaryContainer) {
            am.closeReplay()
        }
    }
}

// ------------------------------------------------------------------ sağa kaydırma barı (FastScrollBar)

@Composable
private fun FastScrollBar(
    listState: LazyListState,
    pageCount: Int,
    onScrollToPage: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (pageCount <= 1) return
    val cs = MaterialTheme.colorScheme
    var isDragging by remember { mutableStateOf(false) }
    var dragProgress by remember { mutableFloatStateOf(0f) }

    val currentScrollProgress by remember {
        derivedStateOf {
            val idx = listState.firstVisibleItemIndex
            val offset = listState.firstVisibleItemScrollOffset
            val itemSize = listState.layoutInfo.visibleItemsInfo.firstOrNull()?.size ?: 1000
            val fraction = (idx + offset.toFloat() / max(1, itemSize)) / max(1, pageCount - 1)
            fraction.coerceIn(0f, 1f)
        }
    }

    val activeProgress = if (isDragging) dragProgress else currentScrollProgress
    val displayedPage = (activeProgress * (pageCount - 1)).roundToInt().coerceIn(0, pageCount - 1)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxHeight()
            .width(28.dp)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        val trackHeightPx = constraints.maxHeight.toFloat()
        val thumbHeightDp = 52.dp
        val thumbHeightPx = with(LocalDensity.current) { thumbHeightDp.toPx() }
        val maxTravelPx = max(1f, trackHeightPx - thumbHeightPx)

        // Dikey ray (Track)
        Box(
            Modifier
                .width(6.dp)
                .fillMaxHeight()
                .clip(FullShape)
                .background(if (isDragging) cs.primary.copy(alpha = 0.2f) else cs.surfaceContainerHighest.copy(alpha = 0.6f))
                .pointerInput(pageCount, trackHeightPx) {
                    detectTapGestures { offset ->
                        val tapFraction = (offset.y / trackHeightPx).coerceIn(0f, 1f)
                        val target = (tapFraction * (pageCount - 1)).roundToInt().coerceIn(0, pageCount - 1)
                        onScrollToPage(target)
                    }
                }
        )

        val thumbOffsetPx = (activeProgress * maxTravelPx).coerceIn(0f, maxTravelPx)

        Box(
            Modifier
                .offset { IntOffset(0, thumbOffsetPx.roundToInt()) }
                .pointerInput(pageCount, trackHeightPx) {
                    detectVerticalDragGestures(
                        onDragStart = { offset ->
                            isDragging = true
                            val initialFraction = ((thumbOffsetPx + offset.y - thumbHeightPx / 2f) / maxTravelPx).coerceIn(0f, 1f)
                            dragProgress = initialFraction
                            val target = (initialFraction * (pageCount - 1)).roundToInt().coerceIn(0, pageCount - 1)
                            onScrollToPage(target)
                        },
                        onDragEnd = { isDragging = false },
                        onDragCancel = { isDragging = false },
                        onVerticalDrag = { change, dragAmount ->
                            change.consume()
                            val newPx = (dragProgress * maxTravelPx + dragAmount).coerceIn(0f, maxTravelPx)
                            val newFraction = (newPx / maxTravelPx).coerceIn(0f, 1f)
                            dragProgress = newFraction
                            val target = (newFraction * (pageCount - 1)).roundToInt().coerceIn(0, pageCount - 1)
                            onScrollToPage(target)
                        }
                    )
                }
        ) {
            Box(
                Modifier
                    .size(width = 24.dp, height = thumbHeightDp)
                    .shadow(if (isDragging) 8.dp else 2.dp, FullShape)
                    .clip(FullShape)
                    .background(if (isDragging) cs.primary else cs.surfaceContainerHighest)
                    .border(BorderStroke(1.5.dp, if (isDragging) cs.primary else cs.outlineVariant.copy(alpha = 0.6f)), FullShape),
                contentAlignment = Alignment.Center
            ) {
                Sym("unfold_more", 18.dp, if (isDragging) cs.onPrimary else cs.onSurfaceVariant)
            }

            if (isDragging) {
                Box(
                    Modifier
                        .align(Alignment.CenterStart)
                        .offset { IntOffset((-105).dp.roundToPx(), 0) }
                        .shadow(6.dp, FullShape)
                        .clip(FullShape)
                        .background(cs.inverseSurface)
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Sym("auto_stories", 15.dp, cs.inverseOnSurface)
                        Text(
                            text = "Sayfa ${displayedPage + 1} / $pageCount",
                            color = cs.inverseOnSurface,
                            style = TS.labelSm,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ üst bar (AppBar)

@Composable
private fun AppBar(
    vm: AppViewModel,
    nb: Notebook,
    pageCount: Int,
    onOpenExport: () -> Unit,
    onToggleRecord: () -> Unit,
    onOpenRecordings: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val am = vm.audioManager
    val saved = remember(nb.updatedAt) { SimpleDateFormat("HH:mm", Locale("tr")).format(Date(nb.updatedAt)) }
    var menu by remember { mutableStateOf(false) }

    Row(
        Modifier.fillMaxWidth().shadow(1.dp, rr(24)).background(cs.surfaceContainerLow, rr(24))
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        IconBtn("arrow_back", box = 36.dp, icon = 20.dp) {
            if (vm.isFullScreen) vm.isFullScreen = false
            vm.route = Route.LIBRARY
        }
        Column(Modifier.weight(1f).padding(horizontal = 4.dp)) {
            Txt(nb.title, TS.labelLg, cs.onSurface, weight = FontWeight.SemiBold, maxLines = 1)
            Txt("${(vm.activePage + 1).coerceAtMost(pageCount)}/$pageCount · $saved", TS.labelSm, cs.secondary, maxLines = 1)
        }

        IconBtn("undo", box = 36.dp, tint = if (vm.canUndo) cs.onSurface else cs.onSurface.copy(alpha = .35f)) { vm.undo() }
        IconBtn("redo", box = 36.dp, tint = if (vm.canRedo) cs.onSurface else cs.onSurface.copy(alpha = .35f)) { vm.redo() }

        // Zoom: tek chip. Dokun: 100 -> 150 -> 200 -> 100 (iki parmakla da zoom yapılır)
        Box(
            Modifier.clip(FullShape).background(cs.surfaceContainerHigh)
                .clickable { vm.setZoom(when { vm.zoomScale < 1.2f -> 1.5f; vm.zoomScale < 1.8f -> 2f; else -> 1f }) }
                .padding(horizontal = 10.dp, vertical = 6.dp),
        ) { Text("${(vm.zoomScale * 100).roundToInt()}%", style = TS.labelSm, fontWeight = FontWeight.Bold, color = cs.onSurface) }

        // Kalem/Parmak modu: tek dokunuş
        IconBtn(
            "stylus", box = 36.dp,
            bg = if (vm.stylusOnly) cs.primaryContainer else Color.Transparent,
            tint = if (vm.stylusOnly) cs.onPrimaryContainer else cs.onSurfaceVariant,
        ) {
            vm.stylusOnly = !vm.stylusOnly
            vm.message = if (vm.stylusOnly) "Yalnızca kalem çizer, parmak kaydırır" else "Kalem ve parmak çizer"
        }

        if (am.isRecording) {
            Row(
                Modifier.clip(FullShape).background(Color(0xFFDC2626)).clickable { onToggleRecord() }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Sym("stop", 16.dp, Color.White)
                Text(formatMs(am.recordingDurationMs), style = TS.labelSm, fontWeight = FontWeight.Bold, color = Color.White)
            }
        } else {
            IconBtn("mic", box = 36.dp, tint = cs.primary) { onToggleRecord() }
        }
        if (am.recordings.isNotEmpty()) {
            IconBtn("graphic_eq", box = 36.dp, bg = if (am.isReplayMode) cs.primaryContainer else Color.Transparent) { onOpenRecordings() }
        }
        IconBtn("grid_view", box = 36.dp) { vm.route = Route.PAGES }

        Box {
            IconBtn("more_vert", box = 36.dp) { menu = true }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text("Cihaza Kaydet") }, leadingIcon = { Sym("save", 20.dp) },
                    onClick = { menu = false; onOpenExport() })
                DropdownMenuItem(
                    text = { Text(if (vm.isFullScreen) "Tam ekrandan çık" else "Tam ekran") },
                    leadingIcon = { Sym(if (vm.isFullScreen) "fullscreen_exit" else "fullscreen", 20.dp) },
                    onClick = { menu = false; vm.toggleFullScreen() })
                DropdownMenuItem(
                    text = { Text("Sayfaya sığdır") }, leadingIcon = { Sym("fit_screen", 20.dp) },
                    onClick = { menu = false; vm.fitToPage() })
            }
        }
    }
}

// ------------------------------------------------------------------ Ses Kayıtları İletişim Kutusu

@Composable
private fun AudioRecordingsDialog(
    vm: AppViewModel,
    nb: Notebook,
    onDismiss: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val am = vm.audioManager

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Sym("graphic_eq", 24.dp, cs.primary)
                Text("Defter Ses Kayıtları", style = TS.titleMd, fontWeight = FontWeight.SemiBold)
            }
        },
        text = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Ses kaydını oynattığınızda, o esnada çizdiğiniz kalem çizgileri sesle senkronize şekilde ekranda belirecektir.",
                    style = TS.bodySm,
                    color = cs.onSurfaceVariant
                )

                if (am.recordings.isEmpty()) {
                    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text("Henüz ses kaydı yapılmamış.", style = TS.bodyMd, color = cs.onSurfaceVariant)
                    }
                } else {
                    LazyColumn(Modifier.fillMaxWidth().height(260.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(am.recordings, key = { it.id }) { rec ->
                            val isCurrent = am.activeRecording?.id == rec.id
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(rr(12))
                                    .background(if (isCurrent) cs.primaryContainer.copy(alpha = 0.5f) else cs.surfaceContainer)
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Box(
                                        Modifier
                                            .size(36.dp)
                                            .clip(FullShape)
                                            .background(if (isCurrent) cs.primary else cs.secondaryContainer)
                                            .clickable {
                                                am.startReplay(rec)
                                                onDismiss()
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Sym("play_arrow", 20.dp, if (isCurrent) cs.onPrimary else cs.onSecondaryContainer)
                                    }
                                    Column {
                                        Text(rec.title, style = TS.labelMd, fontWeight = FontWeight.SemiBold, color = cs.onSurface)
                                        Text(
                                            "${formatMs(rec.durationMs)} • ${SimpleDateFormat("dd.MM.yyyy HH:mm", Locale("tr")).format(Date(rec.createdAt))}",
                                            style = TS.labelSm,
                                            color = cs.onSurfaceVariant,
                                            fontSize = 11.sp
                                        )
                                    }
                                }

                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Button(
                                        onClick = {
                                            am.startReplay(rec)
                                            onDismiss()
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = cs.primary),
                                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                        modifier = Modifier.height(34.dp)
                                    ) {
                                        Text("Replay", style = TS.labelSm)
                                    }
                                    IconBtn("delete", box = 34.dp, icon = 18.dp, tint = cs.error) {
                                        am.deleteRecording(rec)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Kapat")
            }
        }
    )
}

// ------------------------------------------------------------------ zengin araç çubuğu (Toolbar)

private val toolList = listOf(
    Triple(Tool.PEN, "ink_pen", "Dolma Kalem"),
    Triple(Tool.BALL, "edit", "Tükenmez"),
    Triple(Tool.BRUSH, "brush", "Fırça"),
    Triple(Tool.HIGHLIGHT, "highlight", "Fosforlu"),
    Triple(Tool.ERASER, "ink_eraser", "Silgi"),
    Triple(Tool.LASSO, "gesture", "Kement"),
)

@Composable
private fun Toolbar(vm: AppViewModel, dark: Boolean) {
    val cs = MaterialTheme.colorScheme
    var showColorPicker by remember { mutableStateOf(false) }
    var showWidthDialog by remember { mutableStateOf(false) }

    Row(
        Modifier
            .fillMaxWidth()
            .shadow(3.dp, FullShape)
            .background(cs.surfaceContainer, FullShape)
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        // 1. Kalem ve Çizim Araçları
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
            toolList.forEach { (t, icon, label) ->
                val active = vm.tool == t
                Box(
                    Modifier
                        .size(40.dp, 36.dp)
                        .tap(if (active) cs.secondaryContainer else Color.Transparent, FullShape) {
                            vm.tool = t
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Sym(icon, 20.dp, if (active) cs.onSecondaryContainer else cs.onSurfaceVariant, active)
                    if (active) Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 3.dp).size(8.dp, 3.dp).background(cs.primary, FullShape))
                }
            }
        }

        Box(Modifier.width(1.dp).height(24.dp).background(cs.outlineVariant.copy(alpha = 0.5f)))

        // 2. Çizgi Kalınlığı (Stroke Width) Seçici ve Ayarlayıcı
        Row(
            Modifier
                .background(cs.surfaceContainerLow, FullShape)
                .padding(horizontal = 10.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                Modifier
                    .tap(cs.surfaceContainerHighest, FullShape) { showWidthDialog = true }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(Modifier.size((vm.strokeWidthDp * 2.2f).coerceIn(4f, 16f).dp).background(cs.primary, FullShape))
                Text(
                    text = String.format(Locale.US, "%.1f", vm.strokeWidthDp) + " dp",
                    style = TS.labelSm,
                    fontWeight = FontWeight.SemiBold,
                    color = cs.onSurface
                )
                Sym("tune", 14.dp, cs.onSurfaceVariant)
            }

        }

        Box(Modifier.width(1.dp).height(24.dp).background(cs.outlineVariant.copy(alpha = 0.5f)))

        // 3. Mürekkep Renk Paleti ve Canlı Seçici
        Row(
            Modifier
                .background(cs.surfaceContainerLow, FullShape)
                .padding(horizontal = 10.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            vm.inkColors.take(6).forEachIndexed { i, c ->
                val shown = if (i == 0 && dark) 0xFFF4EFF7.toInt() else c
                val active = vm.colorIdx == i
                Box(
                    Modifier
                        .size(if (active) 26.dp else 22.dp)
                        .scale(if (active) 1.15f else 1f)
                        .then(if (active) Modifier.border(BorderStroke(2.dp, cs.primary), FullShape).padding(2.dp) else Modifier)
                        .background(Color(shown), FullShape)
                        .clickable { vm.selectColor(c) }
                )
            }
            IconBtn("palette", box = 28.dp, icon = 18.dp, bg = cs.surfaceContainerHighest) { showColorPicker = true }
        }
    }

    if (showColorPicker) {
        EnhancedColorPickerDialog(
            currentColor = vm.inkColors.getOrNull(vm.colorIdx) ?: 0xFF1C1B1F.toInt(),
            strokeWidthDp = vm.strokeWidthDp,
            tool = vm.tool,
            onDismiss = { showColorPicker = false },
            onPick = { c ->
                vm.selectColor(c)
                showColorPicker = false
            }
        )
    }

    if (showWidthDialog) {
        StrokeWidthDialog(
            currentWidth = vm.strokeWidthDp,
            currentColor = vm.inkColors.getOrNull(vm.colorIdx) ?: 0xFF1C1B1F.toInt(),
            tool = vm.tool,
            onDismiss = { showWidthDialog = false },
            onApply = { w ->
                vm.setStrokeWidth(w)
                showWidthDialog = false
            }
        )
    }
}

// ------------------------------------------------------------------ çizgi kalınlığı ayar iletişim kutusu

@Composable
private fun StrokeWidthDialog(
    currentWidth: Float,
    currentColor: Int,
    tool: Tool,
    onDismiss: () -> Unit,
    onApply: (Float) -> Unit,
) {
    var widthVal by remember { mutableFloatStateOf(currentWidth) }
    val cs = MaterialTheme.colorScheme

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Sym("line_weight", 24.dp, cs.primary)
                Text("Kalem Ucu ve Çizgi Kalınlığı", style = TS.titleMd, fontWeight = FontWeight.SemiBold)
            }
        },
        text = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    text = "Kalemin hassas genişliğini ayarlayın veya önayarlardan birini seçin.",
                    style = TS.bodyMd,
                    color = cs.onSurfaceVariant
                )

                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(72.dp)
                        .clip(rr(12))
                        .background(cs.surfaceContainerHighest)
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    InkStrokePreview(
                        widthDp = widthVal,
                        colorArgb = currentColor,
                        tool = tool,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Column(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Çizgi Genişliği", style = TS.labelMd, color = cs.onSurface)
                        Text(
                            String.format(Locale.US, "%.1f dp", widthVal),
                            style = TS.titleMd,
                            fontWeight = FontWeight.Bold,
                            color = cs.primary
                        )
                    }
                    Slider(
                        value = widthVal,
                        onValueChange = { widthVal = it },
                        valueRange = 0.5f..16f,
                        steps = 30,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Text("Hızlı Kalınlık Seçenekleri", style = TS.labelSm, color = cs.onSurfaceVariant)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        0.8f to "İnce",
                        1.4f to "Normal",
                        2.4f to "Orta",
                        4.0f to "Kalın",
                        6.5f to "Fırça"
                    ).forEach { (w, label) ->
                        val selected = kotlin.math.abs(widthVal - w) < 0.2f
                        Box(
                            Modifier
                                .weight(1f)
                                .clip(rr(8))
                                .background(if (selected) cs.primaryContainer else cs.surfaceContainer)
                                .clickable { widthVal = w }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Box(Modifier.size((w * 2f).coerceIn(4f, 14f).dp).background(if (selected) cs.primary else cs.onSurface, FullShape))
                                Text(label, style = TS.labelSm, color = if (selected) cs.onPrimaryContainer else cs.onSurface, fontSize = 10.sp)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onApply(widthVal) }) {
                Text("Uygula")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("İptal")
            }
        }
    )
}

// ------------------------------------------------------------------ canlı çizgi önizleme

@Composable
private fun InkStrokePreview(
    widthDp: Float,
    colorArgb: Int,
    tool: Tool,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val strokeWidthPx = widthDp * density
        val path = Path().apply {
            moveTo(w * 0.1f, h * 0.5f)
            cubicTo(w * 0.35f, h * 0.15f, w * 0.65f, h * 0.85f, w * 0.9f, h * 0.5f)
        }
        val alpha = if (tool == Tool.HIGHLIGHT) 0.45f else 1.0f
        val color = Color(colorArgb).copy(alpha = alpha)
        drawPath(
            path = path,
            color = color,
            style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}

// ------------------------------------------------------------------ gelişmiş renk seçici

private val paletteCategories = listOf(
    "Temel & Not Alma" to listOf(
        0xFF1C1B1F, 0xFF424242, 0xFF616161, 0xFF9E9E9E, 0xFFE0E0E0, 0xFFFFFFFF,
    ),
    "Mavi & İndigo" to listOf(
        0xFF1E3A8A, 0xFF1976D2, 0xFF4F55C4, 0xFF0288D1, 0xFF00ACC1, 0xFF303F9F,
    ),
    "Doğa & Zümrüt" to listOf(
        0xFF0D9488, 0xFF00796B, 0xFF2E6B56, 0xFF388E3C, 0xFF689F38, 0xFF8BC34A,
    ),
    "Sıcak & Vurgu" to listOf(
        0xFFD97706, 0xFFF57C00, 0xFFFBC02D, 0xFFDC2626, 0xFFD32F2F, 0xFFE64A19,
    ),
    "Pastel & Mor" to listOf(
        0xFF7C3AED, 0xFF7B1FA2, 0xFFC2185B, 0xFF8D6E63, 0xFF795548, 0xFF455A64,
    ),
)

@Composable
private fun EnhancedColorPickerDialog(
    currentColor: Int,
    strokeWidthDp: Float,
    tool: Tool,
    onDismiss: () -> Unit,
    onPick: (Int) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    var selectedColor by remember { mutableStateOf(currentColor) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Sym("palette", 24.dp, cs.primary)
                Text("Mürekkep Rengi Seç", style = TS.titleMd, fontWeight = FontWeight.SemiBold)
            }
        },
        text = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                        .clip(rr(12))
                        .background(cs.surfaceContainerHighest)
                        .padding(6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    InkStrokePreview(
                        widthDp = strokeWidthDp,
                        colorArgb = selectedColor,
                        tool = tool,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    paletteCategories.forEach { (categoryName, colors) ->
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(categoryName, style = TS.labelSm, color = cs.onSurfaceVariant)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                colors.forEach { rawColor ->
                                    val c = rawColor.toInt()
                                    val isCurrent = c == selectedColor
                                    Box(
                                        Modifier
                                            .size(34.dp)
                                            .scale(if (isCurrent) 1.15f else 1f)
                                            .clip(FullShape)
                                            .background(Color(c))
                                            .border(
                                                BorderStroke(
                                                    if (isCurrent) 3.dp else 1.dp,
                                                    if (isCurrent) cs.primary else Color(0x33000000)
                                                ),
                                                FullShape
                                            )
                                            .clickable { selectedColor = c },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (isCurrent) {
                                            Sym("check", 18.dp, if (c == 0xFFFFFFFF.toInt()) Color.Black else Color.White)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onPick(selectedColor) }) {
                Text("Seç")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("İptal")
            }
        }
    )
}

// ------------------------------------------------------------------ cihaz belleğine kaydetme iletişim kutusu

@Composable
private fun SaveToDeviceDialog(
    vm: AppViewModel,
    nb: Notebook,
    onDismiss: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val context = LocalContext.current
    val result = vm.exportResult

    AlertDialog(
        onDismissRequest = {
            if (!vm.isExporting) {
                vm.clearExportResult()
                onDismiss()
            }
        },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Sym("folder_special", 24.dp, cs.primary)
                Text("Cihaz Belleğine Kaydet", style = TS.titleMd, fontWeight = FontWeight.SemiBold)
            }
        },
        text = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                if (vm.isExporting) {
                    Column(
                        Modifier.fillMaxWidth().padding(vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(color = cs.primary)
                        Text(
                            text = if (vm.exportProgress.second > 0)
                                "Sayfalar ve çizimler işleniyor: ${vm.exportProgress.first} / ${vm.exportProgress.second}"
                            else
                                "Dosya hazırlanıyor ve cihaz belleğine aktarılıyor…",
                            style = TS.bodyMd,
                            color = cs.onSurface
                        )
                        if (vm.exportProgress.second > 0) {
                            LinearProgressIndicator(
                                progress = { vm.exportProgress.first.toFloat() / max(1, vm.exportProgress.second) },
                                modifier = Modifier.fillMaxWidth().clip(FullShape)
                            )
                        }
                    }
                } else if (result != null) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(rr(12))
                            .background(cs.primaryContainer.copy(alpha = 0.4f))
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Sym("check_circle", 24.dp, cs.primary)
                            Text("Başarıyla Kaydedildi!", style = TS.titleSm, fontWeight = FontWeight.Bold, color = cs.onSurface)
                        }
                        Text(
                            text = "Dosya cihazınızın 'İndirilenler (Downloads/InkNote)' klasörüne kaydedildi:\n${result.fileName}",
                            style = TS.bodySm,
                            color = cs.onSurfaceVariant
                        )
                    }

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        result.uri?.let { uri ->
                            Button(
                                onClick = { Exporter.openExportedFile(context, uri, result.mimeType) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Sym("visibility", 18.dp, cs.onPrimary)
                                Text(" Dosyayı Aç")
                            }
                            OutlinedButton(
                                onClick = { Exporter.shareExportedFile(context, uri, result.mimeType) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Sym("share", 18.dp, cs.primary)
                                Text(" Paylaş")
                            }
                        }
                    }
                } else {
                    Text(
                        text = "Defterinizi cihaz belleğine (Downloads klasörüne) kaydedin. Çizimleriniz tam vektör kalitesinde korunur.",
                        style = TS.bodyMd,
                        color = cs.onSurfaceVariant
                    )

                    // 1. PDF Olarak Kaydet (Birincil)
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .clip(rr(12))
                            .background(cs.surfaceContainerHigh)
                            .clickable { vm.exportPdf() }
                            .padding(14.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(Modifier.size(40.dp).background(cs.primary, FullShape), contentAlignment = Alignment.Center) {
                                Sym("picture_as_pdf", 22.dp, cs.onPrimary)
                            }
                            Column(Modifier.weight(1f)) {
                                Text("PDF Olarak Kaydet (Tavsiye Edilen)", style = TS.labelLg, fontWeight = FontWeight.SemiBold, color = cs.onSurface)
                                Text("Tüm sayfalar ve kalem çizimleri vektör formatında PDF'e aktarılır.", style = TS.bodySm, color = cs.onSurfaceVariant)
                            }
                        }
                    }

                    // 2. Mevcut Sayfayı PNG Görsel Olarak Kaydet
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .clip(rr(12))
                            .background(cs.surfaceContainer)
                            .clickable { vm.exportPageImage(vm.activePage) }
                            .padding(14.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(Modifier.size(40.dp).background(cs.secondaryContainer, FullShape), contentAlignment = Alignment.Center) {
                                Sym("image", 22.dp, cs.onSecondaryContainer)
                            }
                            Column(Modifier.weight(1f)) {
                                Text("Mevcut Sayfayı Görsel Olarak Kaydet", style = TS.labelLg, fontWeight = FontWeight.SemiBold, color = cs.onSurface)
                                Text("Sayfa ${vm.activePage + 1} yüksek çözünürlüklü PNG resmi olarak kaydedilir.", style = TS.bodySm, color = cs.onSurfaceVariant)
                            }
                        }
                    }

                    // 3. InkNote Proje Yedeği (.inknote)
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .clip(rr(12))
                            .background(cs.surfaceContainer)
                            .clickable { vm.exportBackup() }
                            .padding(14.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(Modifier.size(40.dp).background(cs.tertiaryContainer, FullShape), contentAlignment = Alignment.Center) {
                                Sym("backup", 22.dp, cs.onTertiaryContainer)
                            }
                            Column(Modifier.weight(1f)) {
                                Text("InkNote Proje Yedeği (.inknote)", style = TS.labelLg, fontWeight = FontWeight.SemiBold, color = cs.onSurface)
                                Text("Tüm ham vuruş verilerini cihazda yedekler.", style = TS.bodySm, color = cs.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (result != null) {
                Button(onClick = {
                    vm.clearExportResult()
                    onDismiss()
                }) {
                    Text("Tamam")
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    vm.clearExportResult()
                    onDismiss()
                },
                enabled = !vm.isExporting
            ) {
                Text(if (result != null) "Kapat" else "İptal")
            }
        }
    )
}

// ------------------------------------------------------------------ sol küçük resim şeridi

@Composable
private fun ThumbStrip(vm: AppViewModel, nb: Notebook, pageCount: Int, toneArgb: Int, dark: Boolean) {
    val cs = MaterialTheme.colorScheme
    Column(
        Modifier
            .width(84.dp)
            .fillMaxHeight()
            .shadow(1.dp, rr(32))
            .background(cs.surfaceContainerLow, rr(32))
            .padding(vertical = 12.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Txt("SAYFALAR", TS.labelSm, cs.onSurfaceVariant, weight = FontWeight.SemiBold)
            Sym("tune", 16.dp, cs.secondary)
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            items(pageCount, key = { it }) { i ->
                val active = vm.activePage == i
                Box(
                    Modifier.fillMaxWidth().aspectRatio(3f / 4f).shadow(if (active) 3.dp else 1.dp, rr(6)).clip(rr(6)).background(Color(toneArgb))
                        .then(if (active) Modifier.border(2.dp, cs.primary, rr(6)) else Modifier)
                        .clickable { vm.scrollRequest = i },
                ) {
                    if (nb.kind == NotebookKind.PDF) {
                        val pdf = vm.pdf
                        val bmp by produceState<Bitmap?>(null, pdf, i) { value = pdf?.render(i, 240) }
                        bmp?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds, filterQuality = FilterQuality.Low) }
                    } else {
                        PaperCanvas(nb.templateOf(i), dark, Modifier.fillMaxSize())
                    }
                    Text("${i + 1}", Modifier.align(Alignment.BottomEnd).padding(5.dp), color = if (active) cs.primary else Color(0xFF767684),
                        style = TS.labelSm.copy(fontSize = androidx.compose.ui.unit.TextUnit(9f, androidx.compose.ui.unit.TextUnitType.Sp), fontWeight = if (active) FontWeight.Bold else FontWeight.Medium))
                }
            }
        }
        if (nb.kind == NotebookKind.NOTEBOOK) {
            Row(
                Modifier.fillMaxWidth().tap(cs.surfaceContainer, rr(6)) { vm.addPage(); vm.scrollRequest = nb.pageCount }.padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
            ) {
                Sym("add", 18.dp, cs.onSurfaceVariant)
                Txt(" Ekle", TS.labelSm, cs.onSurfaceVariant)
            }
        }
    }
}

// ------------------------------------------------------------------ tek sayfa (kağıt / PDF + Ink katmanı)

@Composable
private fun PageItem(vm: AppViewModel, nb: Notebook, index: Int, aspect: Float, toneArgb: Int, dark: Boolean) {
    LaunchedEffect(nb.id, index) { vm.ensurePage(index) }
    // Sayfa verisi boş olsa dahi InkLayer anında monte edilir, böylece sonraki sayfaya geçerken donma/kasılma yaşanmaz
    val data = vm.pageData[index] ?: emptyList()
    val isPdf = nb.kind == NotebookKind.PDF
    val shape = if (isPdf) rr(6) else rr(32)

    Box(
        Modifier.fillMaxWidth()
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(aspect).shadow(4.dp, shape).clip(shape).background(Color(if (isPdf) 0xFFFFFFFF.toInt() else toneArgb))) {
            if (isPdf) {
                val pdf = vm.pdf
                // Sabit yüksek çözünürlük: Zoom sırasında bitmap sürekli yeniden render edilmez, donma/çökme yaşanmaz
                val bmp by produceState<Bitmap?>(null, pdf, index) { value = pdf?.render(index, 1440) }
                val b = bmp
                if (b != null) Image(b.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
                else CircularProgressIndicator(Modifier.align(Alignment.Center).size(32.dp))
            } else {
                PaperCanvas(nb.templateOf(index), dark, Modifier.fillMaxSize())
            }
            // InkLayer her zaman hazırdır, gecikme olmadan kalem vuruşlarını karşılar
            InkLayer(vm, index, aspect, data, dark)
        }
    }
}

@Composable
private fun InkLayer(vm: AppViewModel, index: Int, aspect: Float, data: List<StrokeData>, dark: Boolean) {
    val cs = MaterialTheme.colorScheme
    val holder = remember { ViewHolder() }
    var sel by remember { mutableStateOf<RectF?>(null) }
    val am = vm.audioManager
    val replayTime = if (am.isReplayMode) am.playbackPositionMs else null

    Box(Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx -> InkPageView(ctx).also { holder.v = it } },
            modifier = Modifier.fillMaxSize(),
            update = { v ->
                v.pageAspect = aspect
                v.listener = object : InkPageView.Listener {
                    override fun onChanged(before: List<StrokeData>, after: List<StrokeData>) { vm.commit(index, before, after) }
                    override fun onSelection(bounds: RectF?) { sel = bounds?.let { RectF(it) } }
                }
                // İki parmakla kıstırma ve kaydırma (pinch-to-zoom & pan)
                v.onTwoFingerTransform = { factor, panX, panY ->
                    vm.applyZoomAndPan(factor, panX, panY)
                }
                // Ses kaydı esnasında kalemin zamanını senkronize et
                v.audioOffsetProvider = { am.currentAudioOffsetMs() }
                // Replay esnasında ses zamanına göre vuruşları açığa çıkar
                v.replayMaxTimeMs = replayTime
                // Çizgiye dokunulduğunda seste o ana atla
                v.onSeekAudioTo = { targetMs -> am.seekTo(targetMs) }

                v.tool = vm.tool
                v.colorArgb = if (vm.colorIdx == 0 && dark) 0xFFF4EFF7.toInt() else vm.inkColors[vm.colorIdx.coerceIn(0, vm.inkColors.lastIndex)]
                v.widthDp = vm.strokeWidthDp
                v.stylusOnly = vm.stylusOnly
                v.zoomFactor = vm.zoomScale
                v.setData(data)
            },
        )
        sel?.let { r ->
            Row(
                Modifier.offset { IntOffset(r.left.roundToInt().coerceAtLeast(0), (r.top - 56.dp.toPx()).roundToInt().coerceAtLeast(0)) }
                    .shadow(4.dp, FullShape).background(cs.surfaceContainerHighest, FullShape).padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Sil", Modifier.clickable { holder.v?.deleteSelection() }, color = cs.error, style = TS.labelSm)
                Text("|", color = cs.outlineVariant, style = TS.labelSm)
                Text("Seçimi Kaldır", Modifier.clickable { holder.v?.clearSelection() }, color = cs.onSurface, style = TS.labelSm)
            }
        }
    }
}
