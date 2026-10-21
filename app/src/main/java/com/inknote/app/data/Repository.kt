package com.inknote.app.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import org.json.JSONArray
import java.io.File
import java.util.UUID

class Repository(private val ctx: Context) {
    private val root = File(ctx.filesDir, "inknote").apply { mkdirs() }
    private val indexFile = File(root, "notebooks.json")

    private fun dir(id: String) = File(root, "nb_$id").apply { mkdirs() }
    fun pdfFile(id: String) = File(dir(id), "source.pdf")
    private fun pageFile(id: String, page: Int) = File(dir(id), "page_$page.json")

    // ---------------------------------------------------------------- defterler

    fun loadNotebooks(): List<Notebook> {
        if (!indexFile.exists()) {
            val seeded = seed()
            saveNotebooks(seeded)
            return seeded
        }
        return runCatching {
            val arr = JSONArray(indexFile.readText())
            (0 until arr.length()).map { Notebook.fromJson(arr.getJSONObject(it)) }
        }.getOrElse { emptyList() }
    }

    fun saveNotebooks(list: List<Notebook>) {
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        val tmp = File(root, "notebooks.json.tmp")
        tmp.writeText(arr.toString())
        if (!tmp.renameTo(indexFile)) { indexFile.writeText(arr.toString()); tmp.delete() }
    }

    private fun seed(): List<Notebook> {
        val now = System.currentTimeMillis()
        val h = 3_600_000L
        return listOf(
            Notebook("seed1", "İleri Algoritmalar", "CS-401", "Karmaşıklık analizi, ağ akışları ve dinamik modeller.",
                NotebookKind.NOTEBOOK, Scheme.PRIMARY, Folder.UNI, 96, true, now - 20 * h, 33, PageTemplate.GRID),
            Notebook("seed2", "UX Tasarım Fikirleri", "Proje", "Tablet arayüzü mikro etkileşimleri ve renk harmonisi.",
                NotebookKind.NOTEBOOK, Scheme.TERTIARY, Folder.UX, 48, false, now - 48 * h, 11, PageTemplate.DOT),
            Notebook("seed3", "Girişimcilik & Pitch", "Girişim", "Yatırımcı sunumu, pazar analizleri ve gelir modelleri.",
                NotebookKind.NOTEBOOK, Scheme.SECONDARY, Folder.UNI, 62, false, now - 120 * h, 0, PageTemplate.RULED),
            Notebook("seed4", "2024 Ajandası", "2024", "Haftalık hedefler, toplantı tutanakları ve alışkanlık takibi.",
                NotebookKind.NOTEBOOK, Scheme.SURFACE, Folder.DIARY, 140, false, now - 168 * h, 87, PageTemplate.WEEKLY),
        )
    }

    // ---------------------------------------------------------------- sayfalar

    fun loadPage(id: String, page: Int): List<StrokeData> {
        val f = pageFile(id, page)
        if (!f.exists()) return emptyList()
        return runCatching { StrokeCodec.decode(f.readText()) }.getOrElse { emptyList() }
    }

    fun savePage(id: String, page: Int, list: List<StrokeData>) {
        val f = pageFile(id, page)
        if (list.isEmpty()) { f.delete(); return }
        val tmp = File(f.parentFile, f.name + ".tmp")
        tmp.writeText(StrokeCodec.encode(list))
        if (!tmp.renameTo(f)) { f.writeText(StrokeCodec.encode(list)); tmp.delete() }
    }

    fun deleteNotebookFiles(id: String) {
        dir(id).deleteRecursively()
        File(ctx.filesDir, "audio_nb_$id").deleteRecursively()
    }

    // ---------------------------------------------------------------- ses kayıtları

    private fun audioIndexFile(id: String) = File(dir(id), "recordings.json")

    fun loadAudioRecordings(notebookId: String): List<AudioRecording> {
        val f = audioIndexFile(notebookId)
        if (!f.exists()) return emptyList()
        return runCatching {
            val arr = JSONArray(f.readText())
            (0 until arr.length()).map { AudioRecording.fromJson(arr.getJSONObject(it)) }
        }.getOrElse { emptyList() }
    }

    fun saveAudioRecordings(notebookId: String, list: List<AudioRecording>) {
        val f = audioIndexFile(notebookId)
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        val tmp = File(dir(notebookId), "recordings.json.tmp")
        tmp.writeText(arr.toString())
        if (!tmp.renameTo(f)) { f.writeText(arr.toString()); tmp.delete() }
    }

    // ---------------------------------------------------------------- PDF

    /** Seçilen PDF'i uygulama deposuna kopyalar ve doğrular. Hata durumunda istisna fırlatır. */
    fun importPdf(uri: Uri): Notebook {
        val id = UUID.randomUUID().toString().take(8)
        val target = pdfFile(id)
        try {
            ctx.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { out -> input.copyTo(out) }
            } ?: error("Dosya açılamadı")
            val pages = PdfDoc(target).use { it.pageCount }
            if (pages <= 0) error("PDF'de sayfa yok")
            var name = "PDF Belgesi"
            ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0)?.let { name = it.removeSuffix(".pdf").removeSuffix(".PDF") }
            }
            return Notebook(id, name, "PDF", "$pages sayfalık PDF belgesi", NotebookKind.PDF, Scheme.PDF,
                Folder.PDFS, pages, defaultTemplate = PageTemplate.BLANK)
        } catch (e: Exception) {
            dir(id).deleteRecursively()
            throw e
        }
    }
}

private inline fun <T> PdfDoc.use(block: (PdfDoc) -> T): T = try { block(this) } finally { close() }
