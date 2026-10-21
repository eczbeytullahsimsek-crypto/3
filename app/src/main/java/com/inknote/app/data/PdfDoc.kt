package com.inknote.app.data

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Android PdfRenderer sarmalayıcısı. PdfRenderer aynı anda tek sayfa açabildiği ve thread-safe olmadığı için
 * tüm erişim tek bir Mutex ile serileştirilir. Bitmap'ler bayt bazlı LRU önbellekte tutulur.
 */
class PdfDoc(file: File) : Closeable {
    private val pfd: ParcelFileDescriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    private val renderer: PdfRenderer = try { PdfRenderer(pfd) } catch (e: Exception) { pfd.close(); throw e }
    private val mutex = Mutex()
    private var closed = false
    val pageCount: Int = renderer.pageCount

    private val cache = object : LruCache<String, Bitmap>(48 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }
    private var aspects: List<Float>? = null

    /** Her sayfanın genişlik/yükseklik oranı. */
    suspend fun aspects(): List<Float> = withContext(Dispatchers.IO) {
        mutex.withLock {
            aspects ?: run {
                val out = ArrayList<Float>(pageCount)
                for (i in 0 until pageCount) {
                    if (closed) break
                    val a = runCatching {
                        renderer.openPage(i).use { p -> p.width.toFloat() / max(1, p.height) }
                    }.getOrDefault(1f / 1.4142f)
                    out.add(a)
                }
                aspects = out
                out
            }
        }
    }

    /** Verilen genişlikte sayfayı beyaz zeminli bitmap olarak üretir. */
    suspend fun render(index: Int, widthPx: Int): Bitmap? = withContext(Dispatchers.IO) {
        val w = widthPx.coerceIn(200, 2400)
        val key = "$index@$w"
        mutex.withLock {
            if (closed || index !in 0 until pageCount) return@withLock null
            cache.get(key)?.let { return@withLock it }
            runCatching {
                renderer.openPage(index).use { p ->
                    val h = max(1, (w.toFloat() * p.height / max(1, p.width)).roundToInt())
                    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    bmp.eraseColor(Color.WHITE)
                    p.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    cache.put(key, bmp)
                    bmp
                }
            }.getOrElse { err ->
                if (err is OutOfMemoryError) {
                    cache.evictAll()
                    System.gc()
                }
                null
            }
        }
    }

    override fun close() {
        closed = true
        runCatching { renderer.close() }
        runCatching { pfd.close() }
        cache.evictAll()
    }
}
