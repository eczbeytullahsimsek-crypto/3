package com.inknote.app.ink

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Region
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import androidx.ink.authoring.InProgressStrokeId
import androidx.ink.authoring.InProgressStrokesFinishedListener
import androidx.ink.authoring.InProgressStrokesView
import androidx.ink.brush.Brush
import androidx.ink.brush.BrushFamily
import androidx.ink.brush.InputToolType
import androidx.ink.brush.StockBrushes
import androidx.ink.rendering.android.canvas.CanvasStrokeRenderer
import androidx.ink.strokes.MutableStrokeInputBatch
import androidx.ink.strokes.Stroke
import androidx.input.motionprediction.MotionEventPredictor
import com.inknote.app.data.Pt
import com.inknote.app.data.StrokeData
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

enum class Tool { PEN, BALL, BRUSH, HIGHLIGHT, ERASER, LASSO, SHAPE, TEXT, STICKER }

/**
 * Tek bir sayfanın çizim yüzeyi.
 *
 * Mimari Özellikleri:
 * 1. ViewPort State Manager: Çizim koordinat sistemi (Document Space - [ViewPort.DOC_WIDTH]) ile ekran
 *    koordinat sistemini (View Pixels) birbirinden tamamen ayırır.
 * 2. Donanım Matrisi Eşlemesi: [InProgressStrokesView]'a [ViewPort.viewToStrokeMatrix] verilerek
 *    ekran koordinatları doğrudan stroke koordinatlarına sıfır kayma (zero offset) ile dönüştürülür.
 * 3. Donanım Hızlandırmalı Render: [CanvasStrokeRenderer.draw] viewport matrisi ile GPU üzerinde
 *    çizim yapar; zoom ve pan yapıldığında stroke vektör yolları asla yeniden hesaplanmaz veya recompose edilmez.
 * 4. Düşük Gecikme & Palm Rejection: Ön arabellek (front-buffer), hareket tahmini (motion prediction)
 *    ve kalem çizimlerinde el ayası reddi kesintisiz çalışır.
 */
class InkPageView(context: Context) : FrameLayout(context) {

    interface Listener {
        fun onChanged(before: List<StrokeData>, after: List<StrokeData>)
        fun onSelection(bounds: RectF?)
    }

    var listener: Listener? = null

    // ---- ViewPort State Manager ----
    val viewport = ViewPort()

    var pageAspect: Float = 1f / 1.4142f
        set(value) {
            field = value
            viewport.setViewSize(width.toFloat(), height.toFloat(), value)
            finishedLayer.invalidate()
        }

    // ---- Dışarıdan ayarlanan araç durumu ----
    var tool: Tool = Tool.PEN
    var colorArgb: Int = Color.BLACK
    var widthDp: Float = 2.5f
    /** true: yalnızca kalem çizer, parmak sayfayı kaydırır/zoom yapar (palm rejection). */
    var stylusOnly: Boolean = false
    /** vm.zoomScale: kalem kalınlığı sayfa ile birlikte ölçeklenir. */
    var zoomFactor: Float = 1f
    private var multiTouch = false

    // ---- Ses senkronizasyonu ve Replay ----
    var audioOffsetProvider: (() -> Long)? = null
    var replayMaxTimeMs: Long? = null
        set(value) {
            field = value
            finishedLayer.invalidate()
        }
    var onSeekAudioTo: ((Long) -> Unit)? = null

    // ---- Veri (Belge koordinatlarında saklanır) ----
    private var data: List<StrokeData> = emptyList()
    private var strokes: List<Stroke?> = emptyList()
    private var gestureActive = false
    private var selected: Set<Int> = emptySet()

    // ---- Ink motoru ----
    private val density = context.resources.displayMetrics.density
    private val renderer = CanvasStrokeRenderer.create()
    private val identity = Matrix()
    private val inProgress = InProgressStrokesView(context)
    private val predictor: MotionEventPredictor = MotionEventPredictor.newInstance(this)
    private val kinds = HashMap<InProgressStrokeId, String>()
    private var currentId: InProgressStrokeId? = null
    private var currentPointer = -1
    private var activeTool: Tool = Tool.PEN
    private var activeToolType = 0

    // eraser / lasso durumu
    private var eraseBefore: List<StrokeData>? = null
    private val lassoPath = Path()
    private val lassoDocPath = Path()
    private val lassoPoints = ArrayList<Float>()

    private val dashPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
        color = 0xFF363BAB.toInt()
        pathEffect = DashPathEffect(floatArrayOf(10f * density, 6f * density), 0f)
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0x14363BAB
    }

    // ---- Donanım Hızlandırma Katmanı (HardwareLayer) & Doküman Önbelleği ----
    private var docCacheBitmap: Bitmap? = null
    private var docCacheCanvas: Canvas? = null
    private var lastCachedStrokeCount = 0

    private val finishedLayer = object : View(context) {
        init {
            // GPU donanım katmanı: Çizim komutlarını donanım hızlandırmalı GPU display-list üzerinden işler
            setLayerType(LAYER_TYPE_HARDWARE, null)
        }

        override fun onDraw(canvas: Canvas) {
            val maxTime = replayMaxTimeMs
            val matrix = viewport.strokeToViewMatrix

            if (maxTime != null) {
                // Replay modunda zaman kısıtlı vuruşlar dinamik çizilir
                for (i in strokes.indices) {
                    val s = strokes[i] ?: continue
                    val d = data.getOrNull(i)
                    if (d != null && d.timeOffsetMs > 0 && d.timeOffsetMs > maxTime) continue
                    runCatching { renderer.draw(canvas, s, matrix) }
                }
            } else {
                // Normal mod: Yüksek vuruş sayılarında doküman önbelleği, aksi halde doğrudan Skia GPU çizimi
                val totalStrokes = strokes.size
                if (totalStrokes > CACHE_THRESHOLD && ensureDocCacheBitmap()) {
                    if (lastCachedStrokeCount < totalStrokes) {
                        renderStrokesToDocCache(lastCachedStrokeCount)
                    }
                    val bmp = docCacheBitmap
                    if (bmp != null && !bmp.isRecycled) {
                        canvas.drawBitmap(bmp, matrix, null)
                    }
                } else {
                    for (i in strokes.indices) {
                        val s = strokes[i] ?: continue
                        runCatching { renderer.draw(canvas, s, matrix) }
                    }
                }
            }

            // Kement çizim yolu (ekran koordinatlarında)
            if (!lassoPath.isEmpty) {
                canvas.drawPath(lassoPath, fillPaint)
                canvas.drawPath(lassoPath, dashPaint)
            }

            // Seçim kutusu
            selectionBoundsView()?.let { r ->
                val pad = 8f * density
                val rf = RectF(r.left - pad, r.top - pad, r.right + pad, r.bottom + pad)
                canvas.drawRoundRect(rf, 12f * density, 12f * density, fillPaint)
                canvas.drawRoundRect(rf, 12f * density, 12f * density, dashPaint)
            }
        }
    }

    // ---- İki Parmakla Zoom & Pan Bildirimi ----
    var onTwoFingerTransform: ((factor: Float, panX: Float, panY: Float) -> Unit)? = null
    private var lastTwoFingerDist = 0f
    private var lastTwoFingerMidX = 0f
    private var lastTwoFingerMidY = 0f
    private var wasTwoFinger = false

    init {
        setWillNotDraw(true)
        addView(finishedLayer, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(inProgress, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        // Düşük gecikmeli ön arabellek (front-buffer) ve GPU hatlarını önceden başlat
        inProgress.eagerInit()
        PRESSURE_PEN.hashCode(); BALL.hashCode(); HIGHLIGHTER.hashCode()

        viewport.onTransformChanged = {
            finishedLayer.invalidate()
        }

        inProgress.addFinishedStrokesListener(object : InProgressStrokesFinishedListener {
            override fun onStrokesFinished(strokes: Map<InProgressStrokeId, Stroke>) {
                handleFinished(strokes)
            }
        })
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        inProgress.eagerInit()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        currentId?.let { id -> runCatching { inProgress.cancelStroke(id) } }
        currentId = null
        gestureActive = false
        kinds.clear()
        docCacheBitmap?.recycle()
        docCacheBitmap = null
        docCacheCanvas = null
        lastCachedStrokeCount = 0
    }

    // ------------------------------------------------------------------ Doküman Önbelleği

    private fun ensureDocCacheBitmap(): Boolean {
        val dw = ViewPort.DOC_WIDTH.toInt()
        val dh = viewport.docHeight.toInt()
        if (dw <= 0 || dh <= 0) return false
        val current = docCacheBitmap
        if (current != null && current.width == dw && current.height == dh && !current.isRecycled) {
            return true
        }
        current?.recycle()
        return try {
            val bmp = Bitmap.createBitmap(dw, dh, Bitmap.Config.ARGB_8888)
            docCacheBitmap = bmp
            docCacheCanvas = Canvas(bmp)
            lastCachedStrokeCount = 0
            true
        } catch (_: Throwable) {
            docCacheBitmap = null
            docCacheCanvas = null
            lastCachedStrokeCount = 0
            false
        }
    }

    private fun renderStrokesToDocCache(startIdx: Int = 0) {
        val cv = docCacheCanvas ?: return
        val total = strokes.size
        for (i in startIdx until total) {
            val s = strokes.getOrNull(i) ?: continue
            runCatching { renderer.draw(cv, s, identity) }
        }
        lastCachedStrokeCount = total
    }

    // ------------------------------------------------------------------ Veri Yönetimi

    fun setData(list: List<StrokeData>) {
        if (list === data || gestureActive) return
        if (list.isEmpty() && data.isEmpty()) return
        if (list.size == data.size && list == data) return
        data = list
        selected = emptySet()
        rebuild()
        listener?.onSelection(null)
    }

    /**
     * Vuruşları sabit Belge Koordinatlarında (ViewPort.DOC_WIDTH) oluşturur.
     * Zoom veya ekran boyutu değiştiğinde vuruşlar ASLA yeniden oluşturulmaz!
     */
    private fun rebuild() {
        strokes = data.map {
            runCatching {
                if (it.pts.isEmpty()) null else toStroke(it)
            }.getOrNull()
        }
        docCacheBitmap?.eraseColor(Color.TRANSPARENT)
        lastCachedStrokeCount = 0
        if (strokes.size > CACHE_THRESHOLD && ensureDocCacheBitmap()) {
            renderStrokesToDocCache(0)
        }
        finishedLayer.invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0) {
            viewport.setViewSize(w.toFloat(), h.toFloat(), pageAspect)
            finishedLayer.invalidate()
        }
    }

    /**
     * Dışarıdan zoom ve pan durumunu günceller (örneğin AppBar kontrolleri).
     */
    fun syncViewPort(zoomScale: Float, panX: Float, panY: Float) {
        viewport.setZoomAndPan(zoomScale, panX, panY)
        finishedLayer.invalidate()
    }

    private fun MutableStrokeInputBatch.addOrIgnore(
        type: InputToolType,
        x: Float,
        y: Float,
        elapsedTimeMillis: Long,
        pressure: Float = 0f
    ) {
        try {
            add(type = type, x = x, y = y, elapsedTimeMillis = elapsedTimeMillis, pressure = pressure)
        } catch (_: Throwable) {
            // Hatalı koordinatları atla
        }
    }

    private fun toStroke(d: StrokeData): Stroke? {
        if (d.pts.isEmpty()) return null
        val docW = ViewPort.DOC_WIDTH
        val brush = Brush.createWithColorIntArgb(
            family = familyFor(d.kind),
            colorIntArgb = d.argb,
            size = max(0.5f, d.size * docW),
            epsilon = 0.1f,
        )
        val type = when (d.tool) { 0 -> InputToolType.STYLUS; 1 -> InputToolType.TOUCH; else -> InputToolType.MOUSE }
        val batch = MutableStrokeInputBatch()
        var lastT = -1L
        for (p in d.pts) {
            val t = if (p.t <= lastT) lastT + 1 else p.t
            lastT = t
            batch.addOrIgnore(type, p.x * docW, p.y * docW, t, pressure = p.p)
        }
        return try {
            Stroke(brush, batch)
        } catch (_: Throwable) {
            null
        }
    }

    private fun familyFor(kind: String): BrushFamily = when (kind) {
        "ball" -> BALL
        "hl" -> HIGHLIGHTER
        else -> PRESSURE_PEN
    }

    private fun kindFor(t: Tool) = when (t) {
        Tool.BALL -> "ball"; Tool.BRUSH -> "brush"; Tool.HIGHLIGHT -> "hl"; else -> "pen"
    }

    private fun sizeMultiplier(t: Tool) = when (t) {
        Tool.BALL -> 0.8f; Tool.BRUSH -> 2.4f; Tool.HIGHLIGHT -> 7f; else -> 1f
    }

    // ------------------------------------------------------------------ Dokunma ve Çizim Olayları

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val tt = e.getToolType(0)
                val isPen = tt == MotionEvent.TOOL_TYPE_STYLUS || tt == MotionEvent.TOOL_TYPE_ERASER
                if (replayMaxTimeMs != null) {
                    val docPt = viewport.viewToDoc(e.x, e.y)
                    val docW = ViewPort.DOC_WIDTH
                    val r = 24f * density * (docW / max(1f, width.toFloat()))
                    for (d in data.reversed()) {
                        if (d.timeOffsetMs > 0 && hits(d, docPt.x, docPt.y, r, docW)) {
                            onSeekAudioTo?.invoke(d.timeOffsetMs); return true
                        }
                    }
                }
                multiTouch = false
                if (stylusOnly && !isPen) return false
                activeTool = if (tt == MotionEvent.TOOL_TYPE_ERASER) Tool.ERASER else tool
                if (activeTool in INACTIVE) return false
                activeToolType = when (tt) {
                    MotionEvent.TOOL_TYPE_STYLUS, MotionEvent.TOOL_TYPE_ERASER -> 0
                    MotionEvent.TOOL_TYPE_MOUSE -> 2
                    else -> 1
                }
                parent?.requestDisallowInterceptTouchEvent(true)
                gestureActive = true
                currentPointer = e.getPointerId(0)
                if (selected.isNotEmpty()) clearSelection()
                when (activeTool) {
                    Tool.ERASER -> { eraseBefore = data; eraseAt(e.x, e.y) }
                    Tool.LASSO -> {
                        lassoPath.rewind(); lassoPoints.clear()
                        lassoPath.moveTo(e.x, e.y); lassoPoints.add(e.x); lassoPoints.add(e.y)
                    }
                    else -> startInk(e)
                }
                return true
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (!gestureActive) return multiTouch
                if (activeToolType == 0) return true      // kalem çizerken avuç içini yok say
                cancelCurrent(e)                           // parmak: 2. parmak = zoom, çizimi iptal et
                multiTouch = true
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (multiTouch) return true
                if (!gestureActive) return false
                val idx = e.findPointerIndex(currentPointer)
                if (idx < 0) return true
                when (activeTool) {
                    Tool.ERASER -> {
                        for (h in 0 until e.historySize) eraseAt(e.getHistoricalX(idx, h), e.getHistoricalY(idx, h))
                        eraseAt(e.getX(idx), e.getY(idx))
                    }
                    Tool.LASSO -> {
                        lassoPath.lineTo(e.getX(idx), e.getY(idx))
                        lassoPoints.add(e.getX(idx)); lassoPoints.add(e.getY(idx))
                        finishedLayer.invalidate()
                    }
                    else -> moveInk(e)
                }
                return true
            }
            MotionEvent.ACTION_POINTER_UP -> {
                if (gestureActive && e.getPointerId(e.actionIndex) == currentPointer) finishCurrent(e)
                return gestureActive || multiTouch
            }
            MotionEvent.ACTION_UP -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                if (multiTouch) { multiTouch = false; return true }
                if (!gestureActive) return false
                finishCurrent(e)
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                multiTouch = false
                if (gestureActive) cancelCurrent(e)
                parent?.requestDisallowInterceptTouchEvent(false)
                return true
            }
        }
        return gestureActive
    }

    private fun finishCurrent(e: MotionEvent) {
        when (activeTool) {
            Tool.ERASER -> commitErase()
            Tool.LASSO -> finishLasso()
            else -> endInk(e)
        }
        gestureActive = false
        currentPointer = -1
    }

    private fun cancelCurrent(e: MotionEvent) {
        when (activeTool) {
            Tool.ERASER -> commitErase()
            Tool.LASSO -> { lassoPath.rewind(); finishedLayer.invalidate() }
            else -> currentId?.let { runCatching { inProgress.cancelStroke(it, e) }; kinds.remove(it) }
        }
        currentId = null
        gestureActive = false
        currentPointer = -1
    }

    // ------------------------------------------------------------------ Canlı Çizim (Ink Rendering)

    private fun startInk(e: MotionEvent) {
        runCatching {
            requestUnbufferedDispatch(e)
            predictor.record(e)
            val kind = kindFor(activeTool)
            // Canlı çizim EKRAN pikselinde (identity). Kalınlık sayfa ile birlikte zoom'lanır.
            val sizePx = max(1f, widthDp * density * sizeMultiplier(activeTool) * zoomFactor)
            val brush = Brush.createWithColorIntArgb(
                family = familyFor(kind),
                colorIntArgb = brushColor(kind),
                size = sizePx,
                epsilon = 0.1f,
            )
            val id = inProgress.startStroke(e, currentPointer, brush, identity)
            currentId = id
            kinds[id] = kind
        }.onFailure {
            currentId = null
            gestureActive = false
        }
    }

    private fun moveInk(e: MotionEvent) {
        val id = currentId ?: return
        runCatching {
            predictor.record(e)
            val prediction = runCatching { predictor.predict() }.getOrNull()
            try {
                inProgress.addToStroke(e, currentPointer, id, prediction)
            } finally {
                prediction?.recycle()
            }
        }
    }

    private fun endInk(e: MotionEvent) {
        val id = currentId ?: return
        currentId = null
        runCatching {
            predictor.record(e)
            inProgress.finishStroke(e, currentPointer, id)
        }
    }

    private fun brushColor(kind: String): Int =
        if (kind == "hl") Color.argb(0xA0, Color.red(colorArgb), Color.green(colorArgb), Color.blue(colorArgb)) else colorArgb

    private fun handleFinished(finished: Map<InProgressStrokeId, Stroke>) {
        if (finished.isEmpty()) {
            inProgress.removeFinishedStrokes(finished.keys)
            return
        }
        val before = data
        val newData = ArrayList<StrokeData>()
        val newStrokes = ArrayList<Stroke?>()
        val docW = ViewPort.DOC_WIDTH
        val m = viewport.viewToStrokeMatrix
        val tmp = FloatArray(2)

        for ((id, stroke) in finished) {
            val kind = kinds.remove(id) ?: "pen"
            val n = stroke.inputs.size
            if (n == 0) continue
            val pts = ArrayList<Pt>(n)
            for (i in 0 until n) {
                val inp = stroke.inputs[i]
                tmp[0] = inp.x; tmp[1] = inp.y
                m.mapPoints(tmp)                       // ekran px -> belge koordinatı
                pts.add(Pt(tmp[0] / docW, tmp[1] / docW, inp.elapsedTimeMillis, inp.pressure))
            }
            val sizeNorm = stroke.brush.size / max(1f, width.toFloat())   // px / görünüm genişliği
            val sd = StrokeData(
                kind, stroke.brush.colorIntArgb, sizeNorm, activeToolType, pts,
                audioOffsetProvider?.invoke() ?: 0L
            )
            newData.add(sd)
            newStrokes.add(toStroke(sd))               // kalıcı vuruş belge uzayında yeniden kurulur
        }

        if (newData.isNotEmpty()) {
            val prevCount = strokes.size
            data = before + newData
            strokes = strokes + newStrokes

            if (docCacheCanvas != null && replayMaxTimeMs == null && strokes.size > CACHE_THRESHOLD) {
                renderStrokesToDocCache(prevCount)
            }
            finishedLayer.invalidate()
            finishedLayer.post { runCatching { inProgress.removeFinishedStrokes(finished.keys) } }
            listener?.onChanged(before, data)
        } else {
            runCatching { inProgress.removeFinishedStrokes(finished.keys) }
        }
    }

    // ------------------------------------------------------------------ Silgi (Eraser)

    private fun eraseAt(viewX: Float, viewY: Float) {
        if (width <= 0 || height <= 0) return
        val docPt = viewport.viewToDoc(viewX, viewY)
        val docW = ViewPort.DOC_WIDTH
        val baseScale = max(0.001f, width.toFloat() / docW)
        val r = 16f * density / (baseScale * viewport.zoomScale)
        var removed = false
        val nd = ArrayList<StrokeData>(data.size)
        val ns = ArrayList<Stroke?>(data.size)

        for (i in data.indices) {
            if (hits(data[i], docPt.x, docPt.y, r, docW)) {
                removed = true
            } else {
                nd.add(data[i])
                ns.add(strokes.getOrNull(i))
            }
        }

        if (removed) {
            data = nd
            strokes = ns
            docCacheBitmap?.eraseColor(Color.TRANSPARENT)
            lastCachedStrokeCount = 0
            if (strokes.size > CACHE_THRESHOLD && ensureDocCacheBitmap()) {
                renderStrokesToDocCache(0)
            }
            finishedLayer.invalidate()
        }
    }

    private fun hits(d: StrokeData, docX: Float, docY: Float, r: Float, docW: Float): Boolean {
        if (d.pts.isEmpty()) return false
        val rr = r + (d.size * docW) / 2f
        var px = d.pts[0].x * docW
        var py = d.pts[0].y * docW
        if (dist(px, py, docX, docY) <= rr) return true
        for (i in 1 until d.pts.size) {
            val cx = d.pts[i].x * docW
            val cy = d.pts[i].y * docW
            if (segDist(docX, docY, px, py, cx, cy) <= rr) return true
            px = cx
            py = cy
        }
        return false
    }

    private fun commitErase() {
        val before = eraseBefore
        eraseBefore = null
        if (before != null && data !== before) {
            val after = data
            gestureActive = false
            listener?.onChanged(before, after)
        }
    }

    // ------------------------------------------------------------------ Kement (Lasso)

    private fun finishLasso() {
        lassoPath.close()
        if (width <= 0 || height <= 0 || lassoPoints.size < 6) {
            lassoPath.rewind()
            finishedLayer.invalidate()
            return
        }

        // Kement ekran yolunu doküman koordinatlarına dönüştür
        lassoDocPath.rewind()
        lassoPath.transform(viewport.viewToStrokeMatrix, lassoDocPath)

        val docW = ViewPort.DOC_WIDTH.toInt()
        val docH = viewport.docHeight.toInt()
        val region = Region()
        region.setPath(lassoDocPath, Region(Rect(0, 0, docW, docH)))

        val sel = HashSet<Int>()
        val wf = ViewPort.DOC_WIDTH
        for (i in data.indices) {
            val pts = data[i].pts
            var inside = 0
            for (p in pts) {
                if (region.contains((p.x * wf).toInt(), (p.y * wf).toInt())) inside++
            }
            if (pts.isNotEmpty() && inside >= pts.size * 0.8f) sel.add(i)
        }
        lassoPath.rewind()
        lassoDocPath.rewind()
        selected = sel
        finishedLayer.invalidate()
        listener?.onSelection(selectionBoundsView())
    }

    /**
     * Seçim kutusunu ekran (View) koordinatlarına dönüştürür.
     */
    fun selectionBoundsView(): RectF? {
        if (selected.isEmpty()) return null
        val docW = ViewPort.DOC_WIDTH
        var l = Float.MAX_VALUE; var t = Float.MAX_VALUE; var r = -Float.MAX_VALUE; var b = -Float.MAX_VALUE
        for (i in selected) {
            val d = data.getOrNull(i) ?: continue
            for (p in d.pts) {
                l = min(l, p.x * docW); r = max(r, p.x * docW)
                t = min(t, p.y * docW); b = max(b, p.y * docW)
            }
        }
        if (l > r) return null
        val docRect = RectF(l, t, r, b)
        val viewRect = RectF()
        viewport.mapRectToView(docRect, viewRect)
        return viewRect
    }

    fun deleteSelection() {
        if (selected.isEmpty()) return
        val before = data
        val after = before.filterIndexed { i, _ -> i !in selected }
        selected = emptySet()
        data = after
        rebuild()
        listener?.onSelection(null)
        listener?.onChanged(before, after)
    }

    fun clearSelection() {
        selected = emptySet()
        finishedLayer.invalidate()
        listener?.onSelection(null)
    }

    // ------------------------------------------------------------------ Yardımcı Fonksiyonlar

    private fun dist(ax: Float, ay: Float, bx: Float, by: Float): Float {
        val dx = ax - bx; val dy = ay - by
        return sqrt(dx * dx + dy * dy)
    }

    private fun segDist(px: Float, py: Float, ax: Float, ay: Float, bx: Float, by: Float): Float {
        val dx = bx - ax; val dy = by - ay
        val len2 = dx * dx + dy * dy
        if (len2 == 0f) return dist(px, py, ax, ay)
        val t = (((px - ax) * dx + (py - ay) * dy) / len2).coerceIn(0f, 1f)
        return dist(px, py, ax + t * dx, ay + t * dy)
    }

    companion object {
        private const val CACHE_THRESHOLD = 200
        private val INACTIVE = setOf(Tool.SHAPE, Tool.TEXT, Tool.STICKER)
        private val PRESSURE_PEN: BrushFamily by lazy { StockBrushes.pressurePen(version = StockBrushes.PressurePenVersion.V1) }
        private val BALL: BrushFamily by lazy { StockBrushes.marker(version = StockBrushes.MarkerVersion.V1) }
        private val HIGHLIGHTER: BrushFamily by lazy { StockBrushes.highlighter(version = StockBrushes.HighlighterVersion.V1) }
    }
}
