package com.inknote.app.data

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.ink.brush.Brush
import androidx.ink.brush.InputToolType
import androidx.ink.brush.StockBrushes
import androidx.ink.rendering.android.canvas.CanvasStrokeRenderer
import androidx.ink.strokes.MutableStrokeInputBatch
import androidx.ink.strokes.Stroke
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max

object Exporter {

    private val renderer = CanvasStrokeRenderer.create()
    private val identity = Matrix()

    private val PRESSURE_PEN by lazy { StockBrushes.pressurePen(version = StockBrushes.PressurePenVersion.V1) }
    private val BALL by lazy { StockBrushes.marker(version = StockBrushes.MarkerVersion.V1) }
    private val HIGHLIGHTER by lazy { StockBrushes.highlighter(version = StockBrushes.HighlighterVersion.V1) }

    fun strokeDataToStroke(d: StrokeData, w: Float): Stroke? {
        if (d.pts.isEmpty()) return null
        val family = when (d.kind) {
            "ball" -> BALL
            "hl" -> HIGHLIGHTER
            else -> PRESSURE_PEN
        }
        val brush = Brush.createWithColorIntArgb(
            family = family,
            colorIntArgb = d.argb,
            size = max(0.5f, d.size * w),
            epsilon = 0.1f,
        )
        val type = when (d.tool) {
            0 -> InputToolType.STYLUS
            1 -> InputToolType.TOUCH
            else -> InputToolType.MOUSE
        }
        val batch = MutableStrokeInputBatch()
        var lastT = -1L
        for (p in d.pts) {
            val t = if (p.t <= lastT) lastT + 1 else p.t
            lastT = t
            try {
                batch.add(type = type, x = p.x * w, y = p.y * w, elapsedTimeMillis = t, pressure = p.p)
            } catch (_: Throwable) {
                // Ignore any invalid coordinates
            }
        }
        return try {
            Stroke(brush, batch)
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Tüm defteri (sayfalar, kağıt şablonları / orijinal PDF ve tüm Ink kalem çizimleri dahil)
     * PDF olarak cihazın İndirilenler (Downloads) klasörüne kaydeder.
     */
    suspend fun exportToPdf(
        context: Context,
        notebook: Notebook,
        cachedPages: Map<Int, List<StrokeData>>,
        pdfDoc: PdfDoc?,
        repo: Repository,
        onProgress: (Int, Int) -> Unit = { _, _ -> },
    ): ExportResult = withContext(Dispatchers.IO) {
        val isPdf = notebook.kind == NotebookKind.PDF
        val pageCount = if (isPdf) (pdfDoc?.pageCount ?: notebook.pageCount) else notebook.pageCount
        val pdfDocument = PdfDocument()

        val tone = PaperTones[notebook.paperTone.coerceIn(0, PaperTones.lastIndex)]
        val baseToneColor = if (isPdf) Color.WHITE else tone.second
        val isDark = tone.third

        val pageAspects = if (isPdf) pdfDoc?.aspects() else null

        // Standart sayfa genişliği (yüksek çözünürlüklü baskı ve netlik için 1240px)
        val targetWidth = 1240

        for (i in 0 until pageCount) {
            onProgress(i + 1, pageCount)
            val aspect = if (isPdf) {
                pageAspects?.getOrNull(i) ?: (1f / 1.4142f)
            } else {
                notebook.paperSize.ratio
            }
            val targetHeight = max(100, (targetWidth / aspect).toInt())

            val pageInfo = PdfDocument.PageInfo.Builder(targetWidth, targetHeight, i + 1).create()
            val page = pdfDocument.startPage(pageInfo)
            val canvas = page.canvas

            // 1. Zemin çizimi
            if (isPdf && pdfDoc != null) {
                val pdfBmp = pdfDoc.render(i, targetWidth)
                if (pdfBmp != null) {
                    canvas.drawBitmap(pdfBmp, null, Rect(0, 0, targetWidth, targetHeight), null)
                } else {
                    canvas.drawColor(Color.WHITE)
                }
            } else {
                canvas.drawColor(baseToneColor)
                drawPaperTemplate(canvas, notebook.templateOf(i), targetWidth.toFloat(), targetHeight.toFloat(), isDark)
            }

            // 2. Ink kalem çizimleri
            val strokesData = cachedPages[i] ?: repo.loadPage(notebook.id, i)
            if (strokesData.isNotEmpty()) {
                val wf = targetWidth.toFloat()
                for (sd in strokesData) {
                    try {
                        val stroke = strokeDataToStroke(sd, wf) ?: continue
                        renderer.draw(canvas, stroke, identity)
                    } catch (_: Throwable) {}
                }
            }

            pdfDocument.finishPage(page)
        }

        // Geçici dosyaya yaz
        val tempFile = File(context.cacheDir, "export_${notebook.id}_${System.currentTimeMillis()}.pdf")
        FileOutputStream(tempFile).use { out ->
            pdfDocument.writeTo(out)
        }
        pdfDocument.close()

        // Cihaz belleğine (Downloads/InkNote) kaydet
        val sanitizedTitle = notebook.title.replace(Regex("[^a-zA-Z0-9çÇğĞıİöÖşŞüÜ_-]"), "_").take(32)
        val fileName = "${sanitizedTitle}_${System.currentTimeMillis()}.pdf"
        val savedUri = saveToDownloads(context, tempFile, fileName, "application/pdf")

        ExportResult(
            title = notebook.title,
            fileName = fileName,
            uri = savedUri,
            localFile = tempFile,
            mimeType = "application/pdf",
        )
    }

    /**
     * Tek bir sayfayı yüksek kaliteli PNG görseli olarak cihazın İndirilenler klasörüne kaydeder.
     */
    suspend fun exportPageToImage(
        context: Context,
        notebook: Notebook,
        pageIndex: Int,
        cachedPages: Map<Int, List<StrokeData>>,
        pdfDoc: PdfDoc?,
        repo: Repository,
    ): ExportResult = withContext(Dispatchers.IO) {
        val isPdf = notebook.kind == NotebookKind.PDF
        val tone = PaperTones[notebook.paperTone.coerceIn(0, PaperTones.lastIndex)]
        val baseToneColor = if (isPdf) Color.WHITE else tone.second
        val isDark = tone.third

        val aspect = if (isPdf) {
            pdfDoc?.aspects()?.getOrNull(pageIndex) ?: (1f / 1.4142f)
        } else {
            notebook.paperSize.ratio
        }

        val targetWidth = 1440
        val targetHeight = max(100, (targetWidth / aspect).toInt())
        val bmp = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bmp)

        if (isPdf && pdfDoc != null) {
            val pdfBmp = pdfDoc.render(pageIndex, targetWidth)
            if (pdfBmp != null) {
                canvas.drawBitmap(pdfBmp, null, Rect(0, 0, targetWidth, targetHeight), null)
            } else {
                canvas.drawColor(Color.WHITE)
            }
        } else {
            canvas.drawColor(baseToneColor)
            drawPaperTemplate(canvas, notebook.templateOf(pageIndex), targetWidth.toFloat(), targetHeight.toFloat(), isDark)
        }

        val strokesData = cachedPages[pageIndex] ?: repo.loadPage(notebook.id, pageIndex)
        val wf = targetWidth.toFloat()
        for (sd in strokesData) {
            try {
                val stroke = strokeDataToStroke(sd, wf) ?: continue
                renderer.draw(canvas, stroke, identity)
            } catch (_: Throwable) {}
        }

        val tempFile = File(context.cacheDir, "page_${notebook.id}_${pageIndex + 1}_${System.currentTimeMillis()}.png")
        FileOutputStream(tempFile).use { out ->
            bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
        }

        val sanitizedTitle = notebook.title.replace(Regex("[^a-zA-Z0-9çÇğĞıİöÖşŞüÜ_-]"), "_").take(32)
        val fileName = "${sanitizedTitle}_Sayfa_${pageIndex + 1}.png"
        val savedUri = saveToDownloads(context, tempFile, fileName, "image/png")

        ExportResult(
            title = "${notebook.title} - Sayfa ${pageIndex + 1}",
            fileName = fileName,
            uri = savedUri,
            localFile = tempFile,
            mimeType = "image/png",
        )
    }

    /**
     * Tüm defteri (sayfalar ve çizimler) InkNote JSON yedeği olarak cihaz belleğine kaydeder.
     */
    suspend fun exportBackupJson(
        context: Context,
        notebook: Notebook,
        cachedPages: Map<Int, List<StrokeData>>,
        repo: Repository,
    ): ExportResult = withContext(Dispatchers.IO) {
        val root = JSONObject()
        root.put("notebook", notebook.toJson())
        val pagesArr = JSONArray()
        for (i in 0 until notebook.pageCount) {
            val pObj = JSONObject()
            pObj.put("page", i)
            val list = cachedPages[i] ?: repo.loadPage(notebook.id, i)
            pObj.put("strokes", StrokeCodec.encode(list))
            pagesArr.put(pObj)
        }
        root.put("pages", pagesArr)

        val tempFile = File(context.cacheDir, "backup_${notebook.id}_${System.currentTimeMillis()}.inknote")
        tempFile.writeText(root.toString(2))

        val sanitizedTitle = notebook.title.replace(Regex("[^a-zA-Z0-9çÇğĞıİöÖşŞüÜ_-]"), "_").take(32)
        val fileName = "${sanitizedTitle}_Yedek.inknote"
        val savedUri = saveToDownloads(context, tempFile, fileName, "application/json")

        ExportResult(
            title = "${notebook.title} Yedeği",
            fileName = fileName,
            uri = savedUri,
            localFile = tempFile,
            mimeType = "application/json",
        )
    }

    private fun saveToDownloads(context: Context, srcFile: File, displayName: String, mimeType: String): Uri? {
        return try {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/InkNote")
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
            }

            val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
            if (uri != null) {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    srcFile.inputStream().use { input -> input.copyTo(out) }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    contentValues.clear()
                    contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                    context.contentResolver.update(uri, contentValues, null, null)
                }
                uri
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun drawPaperTemplate(canvas: android.graphics.Canvas, template: PageTemplate, w: Float, h: Float, dark: Boolean) {
        val u = w * 5f / 210f // 5 mm
        val inkColor = if (dark) 0xFFFFFFFF.toInt() else 0xFF1C1B20.toInt()
        val faintColor = (inkColor and 0x00FFFFFF) or 0x24000000
        val hair = max(1f, w * 0.0011f)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = hair
            color = faintColor
        }

        when (template) {
            PageTemplate.BLANK -> Unit
            PageTemplate.DOT -> {
                paint.style = Paint.Style.FILL
                paint.color = (inkColor and 0x00FFFFFF) or 0x4D000000
                val radius = max(1.2f, w * 0.0015f)
                var y = u * 2
                while (y < h - u) {
                    var x = u * 2
                    while (x < w - u) {
                        canvas.drawCircle(x, y, radius, paint)
                        x += u
                    }
                    y += u
                }
            }
            PageTemplate.RULED -> {
                val s = w * 6.5f / 210f
                var y = s * 3
                while (y < h) {
                    canvas.drawLine(0f, y, w, y, paint)
                    y += s
                }
                val redPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = hair
                    color = 0x40BA1A1A
                }
                canvas.drawLine(u * 4, 0f, u * 4, h, redPaint)
            }
            PageTemplate.GRID -> {
                var x = 0f
                while (x <= w) {
                    canvas.drawLine(x, 0f, x, h, paint)
                    x += u
                }
                var y = 0f
                while (y <= h) {
                    canvas.drawLine(0f, y, w, y, paint)
                    y += u
                }
            }
            PageTemplate.CORNELL -> {
                val vx = w * 0.30f
                val hy = h * 0.82f
                val strongPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = hair * 1.6f
                    color = (inkColor and 0x00FFFFFF) or 0x59000000
                }
                canvas.drawLine(vx, u * 3, vx, hy, strongPaint)
                canvas.drawLine(0f, hy, w, hy, strongPaint)
                canvas.drawLine(0f, u * 3, w, u * 3, strongPaint)
                val s = w * 7f / 210f
                var y = u * 3 + s
                while (y < hy) {
                    canvas.drawLine(vx, y, w, y, paint)
                    y += s
                }
            }
            PageTemplate.WEEKLY -> {
                val m = w * 0.05f
                val gap = w * 0.025f
                val cw = (w - 2 * m - gap) / 2
                val ch = (h - 2 * m - 3 * gap) / 4
                val roundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = hair * 1.4f
                    color = (inkColor and 0x00FFFFFF) or 0x38000000
                }
                for (r in 0 until 4) {
                    for (c in 0 until 2) {
                        val left = m + c * (cw + gap)
                        val top = m + r * (ch + gap)
                        canvas.drawRoundRect(left, top, left + cw, top + ch, w * 0.012f, w * 0.012f, roundPaint)
                    }
                }
            }
            PageTemplate.STAFF -> {
                val sp = w * 2f / 210f * 1.2f
                val groupH = sp * 4
                var y = u * 4
                val staffPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = hair
                    color = (inkColor and 0x00FFFFFF) or 0x66000000
                }
                while (y + groupH < h - u * 2) {
                    for (i in 0 until 5) {
                        canvas.drawLine(u * 2, y + i * sp, w - u * 2, y + i * sp, staffPaint)
                    }
                    y += groupH + sp * 5
                }
            }
        }
    }

    fun openExportedFile(context: Context, uri: Uri, mimeType: String) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(Intent.createChooser(intent, "Dosyayı Görüntüle"))
        } catch (_: Exception) {}
    }

    fun shareExportedFile(context: Context, uri: Uri, mimeType: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(Intent.createChooser(intent, "Dosyayı Paylaş"))
        } catch (_: Exception) {}
    }
}

data class ExportResult(
    val title: String,
    val fileName: String,
    val uri: Uri?,
    val localFile: File,
    val mimeType: String,
)
