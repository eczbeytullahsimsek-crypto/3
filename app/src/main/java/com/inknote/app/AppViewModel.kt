package com.inknote.app

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.inknote.app.data.Folder
import com.inknote.app.data.Notebook
import com.inknote.app.data.NotebookKind
import com.inknote.app.data.PageTemplate
import com.inknote.app.data.PaperSize
import com.inknote.app.data.PdfDoc
import com.inknote.app.data.Repository
import com.inknote.app.data.Scheme
import com.inknote.app.data.StrokeData
import com.inknote.app.ink.Tool
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

enum class Route { LIBRARY, CANVAS, PAGES, SEARCH, CLOUD, SETTINGS }

private class Edit(val page: Int, val before: List<StrokeData>, val after: List<StrokeData>)

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = Repository(app)
    val audioManager = com.inknote.app.audio.AudioManager(app, viewModelScope, repo)
    private var persistJob: kotlinx.coroutines.Job? = null

    var route by mutableStateOf(Route.LIBRARY)
    val notebooks = mutableStateListOf<Notebook>()
    var openId by mutableStateOf<String?>(null)
        private set
    var message by mutableStateOf<String?>(null)

    // --- Açık defterin sayfa verisi ---
    val pageData = mutableStateMapOf<Int, List<StrokeData>>()
    private val undoStack = ArrayList<Edit>()
    private val redoStack = ArrayList<Edit>()
    var canUndo by mutableStateOf(false); private set
    var canRedo by mutableStateOf(false); private set

    // --- PDF ---
    var pdf by mutableStateOf<PdfDoc?>(null); private set
    var pdfAspects by mutableStateOf<List<Float>?>(null); private set
    var pdfLoading by mutableStateOf(false); private set

    // --- Araç durumu ---
    var tool by mutableStateOf(Tool.PEN)
    var colorIdx by mutableStateOf(0)
    var widthIdx by mutableStateOf(2)
    var strokeWidthDp by mutableStateOf(2.4f)
    var stylusOnly by mutableStateOf(false)
    var activePage by mutableStateOf(0)
    var scrollRequest by mutableStateOf<Int?>(null)

    // Tam ekran durumu
    var isFullScreen by mutableStateOf(false)

    // Zoom & Pan Durumu (akıcı parmakla zoom, transformable modifier, pan, %100, sayfaya sığdır)
    var zoomScale by mutableFloatStateOf(1.0f)
    var panOffsetX by mutableFloatStateOf(0f)
    var panOffsetY by mutableFloatStateOf(0f)

    fun applyZoomAndPan(zoomChange: Float, panChangeX: Float, panChangeY: Float) {
        val newZoom = (zoomScale * zoomChange).coerceIn(0.5f, 3.5f)
        zoomScale = newZoom
        if (newZoom <= 1.02f) {
            panOffsetX = 0f
            panOffsetY = 0f
        } else {
            val maxPanX = 920f * (newZoom - 1f) * 1.5f
            val maxPanY = 1400f * (newZoom - 1f) * 2f
            panOffsetX = (panOffsetX + panChangeX).coerceIn(-maxPanX, maxPanX)
            panOffsetY = (panOffsetY + panChangeY).coerceIn(-maxPanY, maxPanY)
        }
    }

    /** İki parmak: odak noktası sabit kalacak şekilde zoom + yatay pan. Gerçek uygulanan oranı döner. */
    fun pinch(factor: Float, cx: Float, cy: Float, dx: Float, dy: Float, containerW: Float, baseWidthPx: Float): Float {
        val old = zoomScale
        val new = (old * factor).coerceIn(0.5f, 4f)
        val f = new / old
        zoomScale = new
        if (new <= 1.02f) {
            panOffsetX = 0f
        } else {
            val raw = cx - (cx - containerW / 2f - panOffsetX) * f - containerW / 2f + dx
            val maxPan = kotlin.math.max(0f, (baseWidthPx * new - containerW) / 2f + 48f)
            panOffsetX = raw.coerceIn(-maxPan, maxPan)
        }
        return f
    }

    fun setZoom(scale: Float) {
        zoomScale = scale.coerceIn(0.5f, 3.5f)
        if (zoomScale <= 1.02f) {
            panOffsetX = 0f
            panOffsetY = 0f
        }
    }

    fun zoomIn() {
        setZoom(zoomScale + 0.25f)
    }

    fun zoomOut() {
        setZoom(zoomScale - 0.25f)
    }

    fun fitToPage() {
        zoomScale = 1.0f
        panOffsetX = 0f
        panOffsetY = 0f
    }

    fun setZoom100() {
        zoomScale = 1.0f
        panOffsetX = 0f
        panOffsetY = 0f
    }

    // Dışa aktarma durumu
    var isExporting by mutableStateOf(false)
    var exportProgress by mutableStateOf(0 to 0)
    var exportResult by mutableStateOf<com.inknote.app.data.ExportResult?>(null)

    val inkColors = mutableStateListOf(
        0xFF1C1B1F.toInt(), // Siyah / Koyu Kömür
        0xFF1E3A8A.toInt(), // Lacivert
        0xFF4F55C4.toInt(), // InkNote İndigo
        0xFF0D9488.toInt(), // Zümrüt Mavisi
        0xFF2E6B56.toInt(), // Orman Yeşili
        0xFFD97706.toInt(), // Amber / Hardal
        0xFFDC2626.toInt(), // Canlı Kırmızı
        0xFF7C3AED.toInt(), // Mor
        0xFF475569.toInt(), // Grafit Gri
        0xFFFFFFFF.toInt(), // Beyaz (karanlık mod / zemin için)
    )
    val widthsDp = listOf(0.8f, 1.4f, 2.4f, 4.0f, 6.0f)

    fun toggleFullScreen() {
        isFullScreen = !isFullScreen
    }

    fun setStrokeWidth(w: Float) {
        strokeWidthDp = w.coerceIn(0.5f, 20f)
        val closestIdx = widthsDp.indexOfFirst { kotlin.math.abs(it - strokeWidthDp) < 0.2f }
        widthIdx = if (closestIdx >= 0) closestIdx else -1
    }

    fun selectWidthPreset(idx: Int) {
        if (idx in widthsDp.indices) {
            widthIdx = idx
            strokeWidthDp = widthsDp[idx]
        }
    }

    fun selectColor(c: Int) {
        val idx = inkColors.indexOf(c)
        if (idx >= 0) {
            colorIdx = idx
        } else {
            inkColors.add(c)
            colorIdx = inkColors.lastIndex
        }
    }

    init {
        notebooks.addAll(repo.loadNotebooks())
    }

    val current: Notebook? get() = notebooks.firstOrNull { it.id == openId } ?: null

    // ============================================================ defter işlemleri

    private fun persist() { val snap = notebooks.toList(); viewModelScope.launch(Dispatchers.IO) { repo.saveNotebooks(snap) } }

    private fun update(id: String, f: (Notebook) -> Notebook) {
        val i = notebooks.indexOfFirst { it.id == id }
        if (i >= 0) { notebooks[i] = f(notebooks[i]); persist() }
    }

    fun createNotebook(title: String, goCanvas: Boolean = true): Notebook {
        val nb = Notebook(UUID.randomUUID().toString().take(8), title.ifBlank { "Yeni Defter" }, "Defter",
            "Yeni oluşturulan defter", NotebookKind.NOTEBOOK,
            listOf(Scheme.PRIMARY, Scheme.TERTIARY, Scheme.SECONDARY).random(), Folder.UNI, 1)
        notebooks.add(0, nb); persist()
        if (goCanvas) open(nb.id, Route.CANVAS)
        return nb
    }

    fun importPdf(uri: Uri) {
        viewModelScope.launch {
            message = "PDF içe aktarılıyor…"
            val result = withContext(Dispatchers.IO) { runCatching { repo.importPdf(uri) } }
            result.onSuccess { nb ->
                notebooks.add(0, nb); persist(); message = "PDF eklendi: ${nb.title}"
                open(nb.id, Route.CANVAS)
            }.onFailure {
                message = when (it) {
                    is SecurityException -> "Bu PDF şifreli, açılamadı."
                    else -> "PDF açılamadı: ${it.message ?: "bozuk veya desteklenmeyen dosya"}"
                }
            }
        }
    }

    fun toggleStar(id: String) = update(id) { it.copy(starred = !it.starred) }
    fun trash(id: String) = update(id) { it.copy(trashed = true) }
    fun restore(id: String) = update(id) { it.copy(trashed = false) }
    fun deleteForever(id: String) {
        if (openId == id) closeNotebook()
        notebooks.removeAll { it.id == id }
        persist()
        viewModelScope.launch(Dispatchers.IO) { repo.deleteNotebookFiles(id) }
    }

    private val loadedPages = HashSet<Int>()

    fun open(id: String, to: Route = Route.CANVAS) {
        if (openId != id) {
            closeNotebook()
            openId = id
            val nb = notebooks.firstOrNull { it.id == id }
            activePage = nb?.lastPage ?: 0
            scrollRequest = activePage.takeIf { it > 0 }
            zoomScale = 1.0f
            if (nb != null) {
                loadedPages.clear()
                preloadPages(nb.id, nb.pageCount, activePage)
                audioManager.loadRecordings(nb.id)
            }
            if (nb?.kind == NotebookKind.PDF) openPdf(nb)
        }
        route = to
    }

    private fun preloadPages(id: String, count: Int, initialPage: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            // 1. Önce aktif sayfa ve komşularını hemen yükle
            val immediate = listOf(initialPage, initialPage + 1, initialPage - 1).filter { it in 0 until count }
            for (p in immediate) {
                if (openId != id) break
                if (loadedPages.add(p)) {
                    val list = repo.loadPage(id, p)
                    withContext(Dispatchers.Main) {
                        if (openId == id) pageData[p] = list
                    }
                }
            }
            // 2. Kalan sayfaları arka planda yükle
            for (p in 0 until count) {
                if (openId != id) break
                if (loadedPages.add(p)) {
                    val list = repo.loadPage(id, p)
                    if (list.isNotEmpty()) {
                        withContext(Dispatchers.Main) {
                            if (openId == id) pageData[p] = list
                        }
                    }
                }
            }
        }
    }

    private fun openPdf(nb: Notebook) {
        pdfLoading = true
        viewModelScope.launch {
            val doc = withContext(Dispatchers.IO) { runCatching { PdfDoc(repo.pdfFile(nb.id)) }.getOrNull() }
            if (doc == null) { message = "PDF dosyası okunamadı."; pdfLoading = false; return@launch }
            if (openId != nb.id) { doc.close(); return@launch }
            pdf = doc
            pdfAspects = runCatching { doc.aspects() }.getOrNull()
            pdfLoading = false
        }
    }

    private fun closeNotebook() {
        persistJob?.cancel()
        openId?.let { id ->
            val page = activePage
            update(id) { it.copy(updatedAt = System.currentTimeMillis(), lastPage = page) }
        }
        audioManager.stopPlayback()
        if (audioManager.isRecording) audioManager.stopRecording()
        pdf?.close(); pdf = null; pdfAspects = null; pdfLoading = false
        pageData.clear(); loadedPages.clear(); undoStack.clear(); redoStack.clear(); refreshUndo()
        openId = null
    }

    override fun onCleared() {
        audioManager.stopPlayback()
        if (audioManager.isRecording) audioManager.stopRecording()
        pdf?.close()
        super.onCleared()
    }

    // ============================================================ sayfa verisi

    fun ensurePage(page: Int) {
        val id = openId ?: return
        if (loadedPages.contains(page)) return
        loadedPages.add(page)
        viewModelScope.launch(Dispatchers.IO) {
            val list = repo.loadPage(id, page)
            withContext(Dispatchers.Main) {
                if (openId == id) pageData[page] = list
            }
        }
    }

    fun commit(page: Int, before: List<StrokeData>, after: List<StrokeData>) {
        val id = openId ?: return
        loadedPages.add(page)
        pageData[page] = after
        undoStack.add(Edit(page, before, after))
        if (undoStack.size > 200) undoStack.removeAt(0)
        redoStack.clear(); refreshUndo()
        saveAsync(id, page, after)
        // Her çizgide tüm UI ve liste recompose olmasın diye debounced güncelleme
        persistJob?.cancel()
        persistJob = viewModelScope.launch {
            kotlinx.coroutines.delay(2000)
            update(id) { it.copy(updatedAt = System.currentTimeMillis(), lastPage = page) }
        }
    }

    fun undo() {
        val e = undoStack.removeLastOrNull() ?: return
        apply(e.page, e.before); redoStack.add(e); refreshUndo(); scrollRequest = e.page
    }

    fun redo() {
        val e = redoStack.removeLastOrNull() ?: return
        apply(e.page, e.after); undoStack.add(e); refreshUndo(); scrollRequest = e.page
    }

    private fun apply(page: Int, list: List<StrokeData>) {
        val id = openId ?: return
        pageData[page] = list
        saveAsync(id, page, list)
    }

    private fun saveAsync(id: String, page: Int, list: List<StrokeData>) {
        viewModelScope.launch(Dispatchers.IO) { runCatching { repo.savePage(id, page, list) } }
    }

    private fun refreshUndo() { canUndo = undoStack.isNotEmpty(); canRedo = redoStack.isNotEmpty() }

    // ============================================================ sayfa yönetimi

    fun addPage() {
        val nb = current ?: return
        if (nb.kind == NotebookKind.PDF) { message = "PDF belgelerine sayfa eklenemez."; return }
        update(nb.id) { it.copy(pageCount = it.pageCount + 1, updatedAt = System.currentTimeMillis()) }
    }

    fun applyTemplate(pages: Set<Int>, t: PageTemplate) {
        val nb = current ?: return
        if (nb.kind == NotebookKind.PDF) { message = "PDF sayfalarına şablon uygulanamaz."; return }
        update(nb.id) { n -> n.copy(templates = n.templates + pages.associateWith { t }) }
    }

    fun setDefaultTemplate(t: PageTemplate) { current?.let { nb -> update(nb.id) { it.copy(defaultTemplate = t) } } }
    fun setPaperTone(i: Int) { current?.let { nb -> update(nb.id) { it.copy(paperTone = i) } } }
    fun setPaperSize(s: PaperSize) { current?.let { nb -> update(nb.id) { it.copy(paperSize = s) } } }

    // ============================================================ dışa aktarma / cihaz belleğine kaydetme

    fun exportPdf() {
        val nb = current ?: return
        if (isExporting) return
        isExporting = true
        message = "PDF hazırlanıyor ve cihaz belleğine kaydediliyor…"
        viewModelScope.launch {
            try {
                val result = com.inknote.app.data.Exporter.exportToPdf(
                    context = getApplication(),
                    notebook = nb,
                    cachedPages = pageData,
                    pdfDoc = pdf,
                    repo = repo,
                    onProgress = { cur, total -> exportProgress = cur to total }
                )
                exportResult = result
                message = if (result.uri != null) "PDF cihaz belleğine kaydedildi: ${result.fileName}" else "PDF hazırlandı."
            } catch (e: Exception) {
                message = "PDF dışa aktarılamadı: ${e.message}"
            } finally {
                isExporting = false
            }
        }
    }

    fun exportPageImage(page: Int) {
        val nb = current ?: return
        if (isExporting) return
        isExporting = true
        viewModelScope.launch {
            try {
                val result = com.inknote.app.data.Exporter.exportPageToImage(
                    context = getApplication(),
                    notebook = nb,
                    pageIndex = page,
                    cachedPages = pageData,
                    pdfDoc = pdf,
                    repo = repo
                )
                exportResult = result
                message = if (result.uri != null) "Sayfa görseli cihaz belleğine kaydedildi: ${result.fileName}" else "Görsel kaydedildi."
            } catch (e: Exception) {
                message = "Görsel kaydedilemedi: ${e.message}"
            } finally {
                isExporting = false
            }
        }
    }

    fun exportBackup() {
        val nb = current ?: return
        if (isExporting) return
        isExporting = true
        viewModelScope.launch {
            try {
                val result = com.inknote.app.data.Exporter.exportBackupJson(
                    context = getApplication(),
                    notebook = nb,
                    cachedPages = pageData,
                    repo = repo
                )
                exportResult = result
                message = if (result.uri != null) "Yedek dosyası kaydedildi: ${result.fileName}" else "Yedek hazırlandı."
            } catch (e: Exception) {
                message = "Yedek kaydedilemedi: ${e.message}"
            } finally {
                isExporting = false
            }
        }
    }

    fun clearExportResult() {
        exportResult = null
    }
}
